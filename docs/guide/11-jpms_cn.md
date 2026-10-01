# 11. JPMS / 强封装

类代理通过 `MethodHandles.privateLookupIn` 定义在目标类所在包内。当目标位于强封装模块 （任何未 `open` 的包，含 `java.util` 等 `java.base` 包）时，该 lookup 会被拒绝，
`proxy()` 会快速失败并给出可操作报错：

```text
Cannot access java.util.ArrayList in module java.base (package java.util):
the package is not open to the unnamed module. Add --add-opens
java.base/java.util=ALL-UNNAMED to the JVM arguments, ...
```

## 解决办法

1. 加上提示的 JVM 参数：

   ```bash
   java --add-opens java.base/java.util=ALL-UNNAMED ...
   ```

2. 或在目标模块的 `module-info.java` 中声明开放包：

   ```java
   module my.module {
       opens com.example.internal;
   }
   ```

## 接口代理

接口代理使用公共 `Lookup`，仅支持 **public** 接口（与 `java.lang.reflect.Proxy` 的约束一致）。 非 public 接口代理使用 `LookupManager` 把类定义到接口自身包内——见
[多接口代理](09-multi-interface-proxy_cn.md)。

## 类加载器约束

生成的代理是隐藏类，而隐藏类必须定义在**同时**能看见 OpenProxy 自身类型
（`Interceptor`、`DispatchTarget`）与目标类型的加载器中。做不到时，`proxy()` 与
`proxyStatic()` 会失败并点名两个加载器：

```text
Cannot create a proxy for com.acme.plugin.Widget: the type is owned by
com.example.RestartLoader@1b2c3d4 while the OpenProxy classes that define the
proxy are owned by jdk.internal.loader.ClassLoaders$AppClassLoader@5e6f7a8.
A hidden proxy class must be defined in a loader that can see both. Options:
(1) make com.acme.plugin.Widget resolvable from ...AppClassLoader; or
(2) load openproxy and ASM from ...RestartLoader and create the Interceptor
    there too, so the interceptor and the proxy share one loader.
```

今天可用的形态有两种：

1. 目标与库同属一个加载器，或对库的加载器**祖先可见**——`public` 的 JDK 接口即属此类，
   无需 `--add-opens`。
2. 插件加载器持有 OpenProxy 的**私有副本**。此时传入的 `Interceptor` 必须来自同一个
   加载器：把别处创建的拦截器交给私有副本，会以 `argument type mismatch` 失败。

由调用方提供定义用 `Lookup`、从而代理库看不见的加载器里的目标，**当前尚不支持**——
见 [ROADMAP.md](../../ROADMAP.md) 中的 `support-cross-classloader-proxy`。

下一章：[迁移](12-migration_cn.md)。
