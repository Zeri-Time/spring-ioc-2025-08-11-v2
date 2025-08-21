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
        // 1) 스캔 대상 애노테이션 모아두기
        List<Class<?>> targetAnnotations = List.of(
                Component.class, Service.class, Repository.class, Configuration.class
        );

        // 2) 애노테이션이 "직접" 달린 타입들 수집
        //    - LinkedHashSet: 중복 제거 + 탐색 순서 보존
        Set<Class<?>> candidates = new LinkedHashSet<>();
        for (Class<?> ann : targetAnnotations) {
            candidates.addAll(reflections.get(Scanners.TypesAnnotated.with(ann).asClass()));
        }

        // 3) 구체 클래스(인터페이스X, 애노테이션X, 추상X)만 남기기
        List<Class<?>> concrete = candidates.stream()
                .filter(c -> !c.isInterface())
                .filter(c -> !c.isAnnotation())
                .filter(c -> !Modifier.isAbstract(c.getModifiers()))
                .toList();

        // 4) 아직 생성하지 못한 타입들을 remaining에 담아두고,
        //    생성자 의존성을 충족할 수 있을 때까지 반복 시도
        Set<Class<?>> remaining = new LinkedHashSet<>(concrete);

        // 반복: 한 바퀴에서 뭔가 하나라도 만들었는지 추적
        boolean progressed;
        do {
            progressed = false;

            // remaining을 직접 돌면서 생성 시도
            Iterator<Class<?>> it = remaining.iterator();
            while (it.hasNext()) {
                Class<?> clazz = it.next();

                // --- 생성자 주입을 "init 내부"에서 직접 처리 ---
                try {
                    Constructor<?>[] ctors = clazz.getDeclaredConstructors();
                    Arrays.sort(ctors, Comparator
                            .comparingInt((Constructor<?> c) -> c.getParameterCount())
                            .reversed());

                    boolean created = false;

                    // (b) 각 생성자별로 의존성(파라미터 타입)을 beans에서 타입 매칭으로 해결
                    for (Constructor<?> ctor : ctors) {
                        Class<?>[] paramTypes = ctor.getParameterTypes();
                        Object[] args = new Object[paramTypes.length];

                        boolean canSatisfy = true;

                        // (c) 파라미터 타입마다, 등록된 빈 중 assignable(대입 가능)한 것을 찾는다.
                        for (int i = 0; i < paramTypes.length; i++) {
                            Class<?> neededType = paramTypes[i];

                            Object matched = null;
                            for (Object bean : beans.values()) {
                                if (neededType.isAssignableFrom(bean.getClass())) {
                                    matched = bean;
                                    break;
                                }
                            }

                            if (matched == null) {
                                // 현재 생성자에 필요한 의존성이 아직 없음 → 이 생성자는 지금은 못 만듦
                                canSatisfy = false;
                                break;
                            }
                            args[i] = matched;
                        }

                        if (!canSatisfy) {
                            // 다음 생성자 시도
                            continue;
                        }

                        // (d) 모든 의존성이 충족되면 인스턴스 생성
                        ctor.setAccessible(true);
                        Object instance = ctor.newInstance(args);

                        // (e) bean 이름 규칙: 클래스 simpleName의 첫 글자를 소문자로
                        String beanName = Ut.str.lcfirst(clazz.getSimpleName());

                        // 이미 같은 이름이 있으면 덮어쓸지/에러로 볼지는 정책 문제.
                        // 여기서는 단순 덮어쓰기 대신, 안전하게 예외를 던질 수도 있다.
                        beans.put(beanName, instance);

                        // 생성 성공 → remaining에서 제거 & 이번 라운드에서 진전이 있었음을 표시
                        it.remove();
                        progressed = true;
                        created = true;
                        break; // 이 클래스는 성공적으로 하나 만들었으니 다음 클래스로
                    }

                    // (f) 모든 생성자를 시도했는데도 만들 수 없다면, 이번 라운드에서는 보류
                    if (!created) {
                        // 아무 것도 하지 않음 → 다음 반복에서 다시 시도
                    }

                } catch (Exception fatal) {
                    // 인스턴스 생성 중 발생한 치명적 오류는 즉시 실패 처리
                    throw new RuntimeException("빈 생성 실패: " + clazz.getName(), fatal);
                }
            }

            // (g) 더 이상 진전이 없고, 아직 남은 클래스가 있다면
            //     → 순환참조거나, 만족시킬 수 없는 의존성 구조
            if (!progressed && !remaining.isEmpty()) {
                throw new RuntimeException("순환참조 또는 의존성 해결 불가: " + remaining);
            }
        } while (!remaining.isEmpty()); // 전부 생성될 때까지 반복

        // 여기 도달하면 모든 빈이 성공적으로 등록된 상태
    }

    public <T> T genBean(String beanName) {
        return (T) beans.get(beanName);
    }


}
