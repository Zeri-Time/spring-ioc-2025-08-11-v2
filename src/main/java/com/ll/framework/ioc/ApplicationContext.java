package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Repository;
import com.ll.framework.ioc.annotations.Service;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApplicationContext {
    private final Map<String, Object> beans = new HashMap<>();
    private final Reflections reflections;

    public ApplicationContext(String basePackage) {
        this.reflections = new Reflections(basePackage);
    }

    public void init() {
        // 1. 컴포넌트 스캔으로 빈 후보 클래스들을 찾습니다.
        // 여기서는 TestPostService, TestPostRepository, TestFacadePostService를 포함하도록
        // 클래스 이름 끝에 "Service"나 "Repository"가 붙은 클래스를 찾습니다.
        // 실제 코드에서는 더 유연한 규칙을 적용할 수 있습니다.
        List<Class<?>> beanClasses = reflections.getTypesAnnotatedWith(Service.class).stream()
                .collect(Collectors.toList());
        beanClasses.addAll(reflections.getTypesAnnotatedWith(Repository.class));

        // 2. 의존성 순서를 고려하여 빈을 생성합니다.
        // 이 예시에서는 단순화를 위해 의존성이 없는 빈부터 생성합니다.
        // 실제로는 의존성 그래프를 분석하여 순환 의존성을 해결하는 로직이 필요할 수 있습니다.
        for (Class<?> beanClass : beanClasses) {
            String beanName = getBeanName(beanClass);
            createBean(beanClass, beanName);
        }
    }

    private void createBean(Class<?> beanClass, String beanName) {
        // 이미 생성된 빈이 있으면 건너뜁니다. (싱글톤)
        if (beans.containsKey(beanName)) {
            return;
        }

        try {
            // 3. 생성자를 통해 의존성을 주입합니다.
            Constructor<?>[] constructors = beanClass.getConstructors();
            if (constructors.length != 1) {
                throw new IllegalStateException("빈은 하나의 public 생성자만 가져야 합니다.");
            }

            Constructor<?> constructor = constructors[0];
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] dependencies = new Object[parameterTypes.length];

            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> dependencyClass = parameterTypes[i];
                String dependencyBeanName = getBeanName(dependencyClass);

                // 의존 빈이 아직 생성되지 않았다면 먼저 생성합니다.
                if (!beans.containsKey(dependencyBeanName)) {
                    createBean(dependencyClass, dependencyBeanName);
                }

                dependencies[i] = beans.get(dependencyBeanName);
            }

            // 4. 리플렉션으로 객체를 생성하고 맵에 저장합니다.
            Object beanInstance = constructor.newInstance(dependencies);
            beans.put(beanName, beanInstance);

        } catch (Exception e) {
            throw new RuntimeException("빈 생성 중 오류 발생: " + beanClass.getName(), e);
        }
    }

    private String getBeanName(Class<?> beanClass) {
        // 클래스 이름의 첫 글자를 소문자로 바꿔 빈 이름을 만듭니다.
        String className = beanClass.getSimpleName();
        return className.substring(0, 1).toLowerCase() + className.substring(1);
    }

    public <T> T genBean(String beanName) {
        return (T) beans.get(beanName);
    }
}