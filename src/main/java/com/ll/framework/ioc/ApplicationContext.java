package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import com.ll.framework.ioc.annotations.Repository;
import com.ll.framework.ioc.annotations.Service;
import com.ll.standard.util.Ut;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Stream;

public class ApplicationContext {
    private final Map<String, Object> beans = new HashMap<>();
    private final Reflections reflections;

    public ApplicationContext(String basePackage) {
        this.reflections = new Reflections(
                new ConfigurationBuilder()
                        .forPackage(basePackage)
                        .setScanners(Scanners.TypesAnnotated) // 타입에 붙은 애노테이션만 스캔
        );
    }

    public void init() {
        // 1. @Component, @Service, @Repository, @Configuration 직접 달린 타입들 스캔
        Set<Class<?>> candidates = new LinkedHashSet<>();
        Stream.of(Component.class, Service.class, Repository.class, Configuration.class)
                .forEach(ann -> candidates.addAll(
                        reflections.get(Scanners.TypesAnnotated.with(ann).asClass())
                ));

        // 2. 구체 클래스만 남기기
        List<Class<?>> concrete = candidates.stream()
                .filter(c -> !c.isInterface())
                .filter(c -> !c.isAnnotation())
                .filter(c -> !Modifier.isAbstract(c.getModifiers()))
                .toList();

        // 3. 의존성 주입 고려하면서 객체 생성 & 등록
        Set<Class<?>> remaining = new LinkedHashSet<>(concrete);
        boolean progressed;
        do {
            progressed = false;
            Iterator<Class<?>> it = remaining.iterator();
            while (it.hasNext()) {
                Class<?> clazz = it.next();
                try {
                    Object instance = createWithConstructorInjection(clazz);
                    registerBean(clazz, instance);
                    it.remove();
                    progressed = true;
                } catch (UnsatisfiedDependencyException e) {
                    // 아직 필요한 빈이 없어서 나중에 재시도
                } catch (Exception fatal) {
                    throw new RuntimeException("빈 생성 실패: " + clazz.getName(), fatal);
                }
            }
        } while (progressed && !remaining.isEmpty());

        if (!remaining.isEmpty()) {
            throw new RuntimeException("순환참조 또는 의존성 해결 불가: " + remaining);
        }
    }

    public <T> T genBean(String beanName) {
        return (T) beans.get(beanName);
    }

    //
    private Object createWithConstructorInjection(Class<?> clazz) throws Exception {
        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        Arrays.sort(ctors, Comparator.comparingInt((Constructor<?> c) -> c.getParameterCount()).reversed());

        for (Constructor<?> ctor : ctors) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            boolean canSatisfy = true;

            for (int i = 0; i < paramTypes.length; i++) {
                Object dep = resolveByType(paramTypes[i]);
                if (dep == null) { canSatisfy = false; break; }
                args[i] = dep;
            }
            if (canSatisfy) {
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            }
        }
        throw new UnsatisfiedDependencyException(clazz);
    }

    private Object resolveByType(Class<?> type) {
        // beans 맵에서 타입으로 검색
        for (Object bean : beans.values()) {
            if (type.isAssignableFrom(bean.getClass())) {
                return bean;
            }
        }
        return null;
    }

    private void registerBean(Class<?> clazz, Object instance) {
        String name = Ut.str.lcfirst(clazz.getSimpleName());
        beans.put(name, instance);
    }

    private static class UnsatisfiedDependencyException extends Exception {
        UnsatisfiedDependencyException(Class<?> c) {
            super("deps not ready: " + c.getName());
        }
    }
}
