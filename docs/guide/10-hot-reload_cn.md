# 10. 热加载 / 热替换

OpenProxy 为长驻应用提供了两个抓手：驱逐缓存的代理类（用于热部署的类）和在活实例上原地替换拦截器。

## 驱逐缓存的代理类

```java
OpenProxy.evict(MyClass.class);              // 驱逐以 MyClass 为键的代理
OpenProxy.evictClassLoader(pluginClassLoader); // 驱逐某加载器的代理
```

下一次 `proxy(...)` 调用会重新生成新类。已有实例不受影响（它们直接持有自身隐藏类的引用）。 适用于在专用 `ClassLoader` 下热部署类的框架。

缓存键说明：类代理以目标类为键；接口代理以 **第一个接口**为键，因此 `evict` 时请传第一个接口。

## 部署到全新的 ClassLoader

驱逐是加载器感知的，生成不是：OpenProxy 目前无法代理"由库自身看不见的加载器所拥有"的类。
因此 restart-classloader 式的热部署会失败，报错内容见
[JPMS / 强封装](11-jpms_cn.md#类加载器约束) 中的跨加载器诊断；`evict` /
`evictClassLoader` 覆盖的是今天可用的形态——目标在库的加载器里，或在持有 OpenProxy
私有副本的加载器里。

走私有副本路线时，`Interceptor` 也必须在同一加载器内创建：把其他加载器创建的拦截器交给
副本，会以 `argument type mismatch` 失败。

## 在活实例上替换拦截器

```java
Greeter proxy = OpenProxy.proxy(Greeter.class, oldInterceptor);
OpenProxy.rebind(proxy, newInterceptor);   // 无需重建
```

`rebind` 原地替换绑定的拦截器。数组形式可一次替换多个，按生成类的拦截器字段顺序对齐：

```java
OpenProxy.rebind(proxy, new Interceptor[]{a, b});
```

- `ConstructorInterceptor` **不可**热替换（仅在构造期使用）。
- `rebind` 是单写者管理操作：在一线程 rebind、另一线程调用方法时，调用方需自行建立 happens-before 边界（锁、线程启动、latch 或 volatile 标志）。

下一章：[JPMS / 强封装](11-jpms_cn.md)。
