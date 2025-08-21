package com.ll.framework.ioc;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApplicationContext {
    private final Map<String, Object> beanMap = new LinkedHashMap<>();

    public ApplicationContext(String basePackage) {
    }

    public void init() {
        try {
            Class<?> testPostRepositoryClass = Class.forName("com.ll.domain.testPost.testPost.repository.TestPostRepository");
            Class<?> testPostServiceClass = Class.forName("com.ll.domain.testPost.testPost.service.TestPostService");
            Class<?> testFacadePostServiceClass = Class.forName("com.ll.domain.testPost.testPost.service.TestFacadePostService");

            Object testPostRepository = testPostRepositoryClass.getDeclaredConstructor().newInstance();
            beanMap.put("testPostRepository", testPostRepository);

            Constructor<?> testPostServiceConstructor = testPostServiceClass.getDeclaredConstructors()[0];
            testPostServiceConstructor.setAccessible(true);

            Object testPostService = testPostServiceConstructor.newInstance(testPostRepository);
            beanMap.put("testPostService", testPostService);

            Constructor<?> testFacadePostServiceConstructor = testFacadePostServiceClass.getDeclaredConstructors()[0];
            testFacadePostServiceConstructor.setAccessible(true);

            Object[] dependencies = Arrays.stream(testFacadePostServiceConstructor.getParameterTypes())
                    .map(paramType -> findBeanByType(paramType))
                    .toArray();

            Object testFacadePostService = testFacadePostServiceConstructor.newInstance(dependencies);
            beanMap.put("testFacadePostService", testFacadePostService);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Object findBeanByType(Class<?> type) {
        for (Object bean : beanMap.values()) {
            if (type.isInstance(bean)) {
                return bean;
            }
        }
        return null;
    }

    public <T> T genBean(String beanName) {
        return (T) beanMap.get(beanName);
    }
}