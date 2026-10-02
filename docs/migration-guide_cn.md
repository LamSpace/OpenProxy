# OpenProxy 迁移指南

如何从 CGLib 或 `java.lang.reflect.Proxy` 迁移到 OpenProxy。

> **重命名说明（0.1.0）：** 入口类 `AcceleratedProxy` 已在首个发布版本之前更名为
> `OpenProxy`（`io.github.lamspace.OpenProxy`）。若你在跟踪预发布快照，请把每一处
> `AcceleratedProxy` 引用替换为 `OpenProxy`；所有方法签名不变。

## CGLib → OpenProxy

### 迁移前（CGLib）

```java
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(MyService.class);
enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> {
    System.out.println("调用前 " + method.getName());
    Object result = proxy.invokeSuper(obj, args);
    System.out.println("调用后 " + method.getName());
    return result;
});
MyService proxy = (MyService) enhancer.create();
```

### 迁移后（OpenProxy）

```java
import io.github.lamspace.OpenProxy;

MyService proxy = OpenProxy.proxy(MyService.class, (obj, method, args) -> {
    System.out.println("调用前 " + method.getName());
    Object result = OpenProxy.invokeSuper(obj, method, args);
    System.out.println("调用后 " + method.getName());
    return result;
});
// 无需强转——泛型推断直接返回 MyService
```

### 主要差异

| CGLib                          | OpenProxy                                        |
|--------------------------------|--------------------------------------------------|
| `Enhancer` 构建器              | `OpenProxy.proxy()` 静态工厂                     |
| `MethodInterceptor`（4 参数）  | `Interceptor`（3 参数，`@FunctionalInterface`）  |
| `proxy.invokeSuper(obj, args)` | `OpenProxy.invokeSuper(obj, method, args)`       |
| 需要显式强转                   | 泛型推断，无需强转                               |
| 自定义 ClassLoader             | 隐藏类，GC 安全                                  |

### 方法过滤

**CGLib（`CallbackFilter` + `NoOp`）：**

```java
enhancer.setCallbacks(new Callback[] {
    interceptor, NoOp.INSTANCE
});
enhancer.setCallbackFilter(method ->
    method.getName().startsWith("get") ? 0 : 1);
```

**OpenProxy（`Group.of`）：**

```java
MyService proxy = OpenProxy.proxy(MyService.class,
        Group.of(m -> m.getName().startsWith("get"), interceptor));
// 未匹配任何 Group 的方法完全跳过拦截 —— 零开销
```

### 构造参数

**CGLib：**

```java
enhancer.create(new Class[] { String.class }, new Object[] { "arg" });
```

**OpenProxy：**

```java
OpenProxy.proxy(MyService.class, new Object[]{"arg"},
        Group.otherwise(interceptor));
```

---

## Java Proxy → OpenProxy

### 迁移前（java.lang.reflect.Proxy）

```java
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;

Service proxy = (Service) Proxy.newProxyInstance(
        Service.class.getClassLoader(),
        new Class<?>[]{Service.class},
        (proxyObj, method, args) -> {
            System.out.println("调用前 " + method.getName());
            return method.invoke(new ServiceImpl(), args);
        }
);
```

### 迁移后（OpenProxy）

```java
import io.github.lamspace.OpenProxy;

ServiceImpl proxy = OpenProxy.proxy(ServiceImpl.class,
        (obj, method, args) -> {
            System.out.println("调用前 " + method.getName());
            return OpenProxy.invokeSuper(obj, method, args);
        }
);
```

### 主要差异

| Java Proxy                    | OpenProxy                                        |
|-------------------------------|--------------------------------------------------|
| 仅基于接口                    | 基于具体类                                       |
| `InvocationHandler`（3 参数） | `Interceptor`（3 参数，`@FunctionalInterface`）  |
| `method.invoke(target, args)` | `OpenProxy.invokeSuper(obj, method, args)`       |
| 需要目标实例                  | 内置父类调用绑定                                 |
| `Proxy.newProxyInstance(...)` | `OpenProxy.proxy(Class, Interceptor)`            |

### 多接口

`java.lang.reflect.Proxy` 支持一个 handler 跨多个接口：

```java
Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[]{A.class, B.class},
        handler);
```

OpenProxy 用 `OpenProxy.proxy(new Class<?>[]{A.class, B.class}, interceptor)` 对应：

```java
Object p = OpenProxy.proxy(new Class<?>[]{A.class, B.class},
        (obj, method, args) -> {
            System.out.println("调用前 " + method.getName());
            return null;
        });
A a = (A) p;   // 一个对象，多个接口视角
B b = (B) p;
```

多个接口中签名与返回类型相同的方法会被合并；有歧义的冲突抛出 `IllegalArgumentException`。

---

## 特性对比

| 特性                           | OpenProxy                            | CGLib                      | Java Proxy                    |
|--------------------------------|--------------------------------------|----------------------------|-------------------------------|
| 代理具体类                     | 是                                   | 是                         | 否（仅接口）                  |
| 分派机制                       | hashCode 开关 + INVOKESPECIAL        | 生成字节码                 | `Method.invoke`               |
| 类加载                         | 隐藏类                               | 自定义 ClassLoader         | 原生 Proxy                    |
| GC 安全                        | 是                                   | 否（ClassLoader 泄漏风险） | 是                            |
| 函数式 API                     | 是                                   | 是                         | 是                            |
| 方法过滤                       | 是（Group.of）                       | 是（CallbackFilter）       | 否                            |
| 无默认构造方法支持             | 是                                   | 是                         | 不适用                        |
| 基本类型装箱                   | 自动                                 | 自动                       | 自动                          |
| 异常传播                       | 受检 → UndeclaredThrowable           | 受检 → InvocationTarget    | 受检 → UndeclaredThrowable    |
| final 类/方法代理              | 否（JVM 限制）                       | 否（JVM 限制）             | 不适用                        |
| 静态方法代理                   | 是                                   | 否                         | 否                            |
| 构造器拦截                     | 是                                   | 是                         | 否                            |
| 热加载 / rebind                | 是（`evict`、`rebind`）              | 否                         | 否                            |
| Maven Central                  | 是（0.1.0）                          | 是                         | 内置（JDK）                   |

---

## 热加载 / 热替换

`evict(Class)`、`evictClassLoader(ClassLoader)` 与 `rebind(proxy, ...)` 都是纯增量
API。CGLib 没有构造后替换回调的能力——等价做法是为每个重新加载的类新建一个代理——
而 `java.lang.reflect.Proxy` 实例创建后不可变，因此两者都没有 `rebind` 的直接对应物。
注意 `evict`/`evictClassLoader` 只管理*缓存*。由子 `ClassLoader`（OpenProxy 位于共享
父加载器）加载的目标同样可以代理：改用 7 个接受调用方传入 `MethodHandles.Lookup`
（以该加载器为根）的 `proxy` / `intercept` / `proxyStatic` 重载之一，生成类会被定义到
该加载器——无需 `--add-opens`。见
[跨类加载器代理](guide/11-jpms_cn.md#跨类加载器代理)。
