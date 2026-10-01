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

提供定义用的 lookup 之后，restart-classloader 式的热部署即可端到端工作：

```java
OpenProxy.evictClassLoader(oldLoader);   // 丢弃上一次部署
Object proxy = OpenProxy.proxy(freshClass, freshLookup, interceptor);
```

- `evict(target)` 会清除该目标的全部缓存代理类，包括由不同 supplied lookup 创建的条目。
- 缓存条目会让 supplied lookup（连带其加载器）保持可达，所以丢弃加载器的框架**必须**调用
  `evictClassLoader(loader)`，没有别的东西会释放它。
- 驱逐前创建的实例继续基于自己的隐藏类工作。

lookup 的要求与命名模块的限制见
[JPMS / 强封装](11-jpms_cn.md#跨类加载器代理)。

若改为在插件加载器内持有 OpenProxy 私有副本，`Interceptor` 也必须在同一加载器内创建：
把其他加载器创建的拦截器交给副本，会以 `argument type mismatch` 失败。

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
