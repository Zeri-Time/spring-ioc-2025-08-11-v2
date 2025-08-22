### 기본 경로 필드와, Bean들을 저장할 자료구조 필드

```java
private final String basePackage;
private final Map<String, Object> beans = new HashMap<>();
```

### org.reflections.Reflections의 Reflection객체 생성

```java
// init()
Reflections reflections = new Reflections(
        new ConfigurationBuilder()
                .forPackages(basePackage)
                .addScanners(new SubTypesScanner(false))
);
```
- basePackage 설정으로 해당 경로 하위의 패키지 및 클래스를 탐색하도록 설정
- new SubTypesScannsr()의 기본 값 - ture : getSubTypesOf() 메서드로 Object 클래스 사용 불가
new SubTypesScanner(false) 설정으로 getSubTypesOf(Object.class)를 통해 Object 클래스를 상속하는 모든 클래스를 탐색할 수 있게 설정 → 모든 클래스

### 탐색 범위 안의 Object 클래스를 상속하는 클래스(모든 클래스 및 인터페이스)의 정보를 받아오기

```java
// init()
Set<Class<?>> allClasses = reflections.getSubTypesOf(Object.class);
```

### 인터페이스 필터링 및 애너테이션 필터링

```java
// init()
Set<Class<?>> remaining = new HashSet<>();
for (Class<?> clazz : allClasses) {
    if (clazz.isInterface()) continue;
    if (hasComponentAnnotation(clazz)) {
        remaining.add(clazz);
    }
}
```

- getSubTypesOf() 로 받아온 모든 클래스 정보에서 인터페이스 제외
- hasComponentAnnotation() 로 clazz가 @Component 포함 유무 판단하여 해당 애너테이션 존재 시 Set 자료구조에 클래스 저장

### hasComponentAnnotation()

```java
// hasComponentAnnotation()
private boolean hasComponentAnnotation(Class<?> clazz) {
    for (Annotation annotation : clazz.getAnnotations()) {
        Class<? extends Annotation> annoType = annotation.annotationType();
        if (annoType.equals(Component.class)) return true;
        if (annoType.isAnnotationPresent(Component.class)) return true;
    }
    return false;
}
```
- 파라미터로 받은 클래스 객체가 @Component 애너테이션이 붙어있거나 @Component 애너테이션을 포함하고 있는 애너테이션(@Service, @Repository 등)
이 붙어있다면 true 반환
- 조건에 해당되지 않는다면 false 반환

### 빈 생성 및 저장

```java
// init()
while (!remaining.isEmpty()) {
    Iterator<Class<?>> iterator = remaining.iterator();
    while (iterator.hasNext()) {
        Class<?> clazz = iterator.next();
        try {
            Object instance = createInstance(clazz);
            if (instance != null) {
                String beanName = Ut.str.lcfirst(clazz.getSimpleName());
                beans.put(beanName, instance);
                iterator.remove();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```
- 위에서 필터링(인터페이스, 애너테이션) 한 클래스 정보들을 담은 remaining 자료구조가 비어있을 때 까지 클래스 정보들을 탐색
- 탐색 하면서 createInstance() 를 통해 현재 객체(Bean) 생성이 가능하다면 빈 생성
- 생성이 불가능한 경우 : 객체(Bean) 생성을 위한 의존성이 아직 beans 자료구조에 Bean으로 등록이 되지 않은 경우
- 객체(Bean) 생성 성공 시 초기 프로젝트에서 제공된 Ut.str 의 lcfirst() 를 통해 클래스 이름 → Bean 이름 으로 변경
- 변환한 Bean 이름을 key값으로 createInstance()를 사용해 생성된 객체(Bean)을 beans 자료구조에 저장 및 remaining 자료구조에서 해당 클래스 삭제
- 위 과정을 remaining 자료구조의 클래스가 모두 Bean으로 등록되어 사라질때 까지 반복

### createInstance()

```java
// createInstance()
private Object createInstance(Class<?> clazz) throws Exception {
    Constructor<?>[] constructors = clazz.getConstructors();

    if (constructors.length == 0) {
        return null;
    }

    Constructor<?> constructor = constructors[0];
    Class<?>[] paramTypes = constructor.getParameterTypes();

    if (paramTypes.length == 0) {
        return constructor.newInstance();
    }

    Object[] params = new Object[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
        String depBeanName = Ut.str.lcfirst(paramTypes[i].getSimpleName());
        Object depBean = beans.get(depBeanName);
        if (depBean == null) {
            return null;
        }
        params[i] = depBean;
    }

    return constructor.newInstance(params);
}
```
- getConstructors를 통해 파라미터로 받은 클래스의 생성자들을 받아옴
- 생성자가 없을 경우 객체생성이 불가능 하기 때문에 null 반환
- 받아온 생성자를 getParameterTypes() 로 객체 생성 시 필요한 파라미터(의존성) 타입을 paramTypes 배열에 저장
- paramTypes가 없을 경우 파라미터(의존성) 가 필요 없는 클래스이기 때문에 newInstance()로 객체 생성 및 반환
- 객체 생성 시 필요한 의존성(빈) 들을 beans 자료구조로 부터 가져와서 저장하기 위해 Object[] params 선언
- String depBeanName = Ut.str.lcfirst(paramTypes[i].getSimpleName()) 를 통해 파라미터 타입으로 부터 클래스 이름을 가져와 Bean 이름으로 변환
- 변환한 Bean 이름으로 beans 자료구조에서 해당 객체(Bean)를 조회
- beans 자료구조에서 조회 실패 시 아직 현재 클래스를 생성하기위한 의존성(Bean)이 beans 자료구조에 Bean으로 등록 되기 전이므로 객체 생성 불가 → null 반환
- beans 자료구조에서 조회 성공 시 params 배열에 객체(Bean) 저장
- 모두 조회 성공하여 params에 필요한 의존성(Bean)이 모두 들어있다면 newInstance(params)로 객체(Bean) 생성 및 반환

### genBean()
```java
// init()
@SuppressWarnings("unchecked")
public <T> T genBean(String beanName) {
    return (T) beans.get(beanName);
}
```

- 파라미터로 받은 beanName으로 beans 자료구조에서 Bean 조회
- beans 자료구조는 Map<String, Object>이기 때문에 제네렉<T>로 형 변환하여 반환
- @SuppressWarnings(”unchecked”) - Object → <T> 로 형반환 시 다운캐스팅 이므로 instance 타입 검사를 하지 않고 형 변환 시 경고 메시지가 뜸 따라서 경고 메시지를 무시하는 애너테이션 사용
