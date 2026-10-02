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

今天可用的形态有三种：

1. 目标与库同属一个加载器，或对库的加载器**祖先可见**——`public` 的 JDK 接口即属此类，
   无需 `--add-opens`。
2. 插件加载器持有 OpenProxy 的**私有副本**。此时传入的 `Interceptor` 必须来自同一个
   加载器：把别处创建的拦截器交给私有副本，会以 `argument type mismatch` 失败。
3. 调用方提供定义用的 lookup，把生成类放进目标自己的加载器——见
   [跨类加载器代理](#跨类加载器代理)。

## 跨类加载器代理

目标位于库看不见的加载器时，调用方可以直接提供用于定义生成类的 lookup：

```java
// 在插件加载器内部调用
MethodHandles.Lookup lookup =
        MethodHandles.privateLookupIn(Widget.class, MethodHandles.lookup());
Widget proxy = OpenProxy.proxy(Widget.class, lookup, interceptor);
```

生成的类会被放入 **lookup 自己**的包、定义在 **lookup 自己**的加载器中，因此可以引用库的加载器
看不见的类型。传入的 lookup 会在生成任何字节码前被校验，每种违例都抛出 `IllegalArgumentException`
并点名出问题的加载器或模块：

- 必须具备完全特权访问（`publicLookup()` 不够）；
- 其类必须由目标所属的加载器加载；
- 目标不是 `public` 时，lookup 必须根植于目标自己的包；
- 目标的加载器必须能解析 OpenProxy 自身的类型（委托到库的加载器，或自带一份副本）。

有一条限制是结构性的：lookup 的**模块**必须能 read 拥有 OpenProxy 类型的模块——解除该限制的
部署方式见下文[命名模块中的目标](#命名模块中的目标)。

传入的 lookup 会（按其根类）参与代理类的缓存标识，因此根类相同的两次调用复用同一个生成类，
根类不同的永不共享。

## 命名模块中的目标

命名模块永远无法 read unnamed module，因此 classpath 上的 OpenProxy 不能代理命名模块中的
目标；该情形会被明确报错（`IllegalArgumentException`，点名两个模块），而不是留下
`IllegalAccessError`。受支持的形态是把库本身部署为模块：将 `openproxy.jar` 与 ASM jar 放到
`--module-path`，并在目标所在模块加一条边——无需任何 exports 或 opens：

```java
module com.acme.app {
    requires io.github.lamspace.openproxy;
}
```

发布 jar 已声明 `Automatic-Module-Name: io.github.lamspace.openproxy`；传入的 lookup 同时
负责定义与构造生成的 hidden class，用的是它所授予的权限，因此对任何人都不导出的包也能工作。
ASM（`org.objectweb.asm`）是真正的命名模块，自动模块无法把它拉进模块图，启动时需显式作为
root：

```bash
java --module-path openproxy.jar:asm.jar:mods \
     --add-modules org.objectweb.asm \
     -m com.acme.app/com.acme.app.Main
```

（`--add-modules ALL-MODULE-PATH` 同样覆盖。）已发布的 0.1.0 构件若继续走 classpath 逃生门，
每次启动需要两个 flag——`--add-reads <module>=ALL-UNNAMED`（补可读性）与
`--add-exports <module>/<包名>=ALL-UNNAMED`（放行构造）；以上模块路径形态才是受支持的方式。

下一章：[迁移](12-migration_cn.md)。
