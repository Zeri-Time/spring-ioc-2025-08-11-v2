package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Repository;
import com.ll.framework.ioc.annotations.Service;
import org.reflections.Reflections;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ApplicationContext {
    private Map<String, Object> beans = new HashMap<>();

    public ApplicationContext(String basePackage) {
        Reflections reflections = new Reflections(basePackage);

        Set<Class<?>> repositories = reflections.getTypesAnnotatedWith(Repository.class);
        for(Class<?> repository: repositories) {
            try {
                String beanName = Character.toLowerCase(repository.getSimpleName().charAt(0))
                        + repository.getSimpleName().substring(1);


                beans.put(beanName, repository.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Set<Class<?>> services = reflections.getTypesAnnotatedWith(Service.class);
        for(Class<?> service: services) {
            try{
                String beanName = Character.toLowerCase(service.getSimpleName().charAt(0))
                        + service.getSimpleName().substring(1);


                var constructor = service.getDeclaredConstructors()[0];
                var paramTypes = constructor.getParameterTypes();
                Object[] params = new Object[paramTypes.length];

                for(int i = 0; i < paramTypes.length; i ++) {
                    String depName = paramTypes[i].getSimpleName();
                    params[i] = beans.get(depName);
                }

                beans.put(beanName, constructor.newInstance(params));
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


    public void init() {
    }

    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        return (T) beans.get(beanName);
    }
}
