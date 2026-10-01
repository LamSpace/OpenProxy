# 11. JPMS / Strong Encapsulation

Class proxies are defined in the target class's package via
`MethodHandles.privateLookupIn`. When the target lives in a strongly encapsulated module — any package that is not `open`, including `java.base`
packages such as `java.util` — that lookup is denied and `proxy()` fails fast with an actionable message:

```text
Cannot access java.util.ArrayList in module java.base (package java.util):
the package is not open to the unnamed module. Add --add-opens
java.base/java.util=ALL-UNNAMED to the JVM arguments, ...
```

## Fixes

1. Add the suggested JVM flag:

   ```bash
   java --add-opens java.base/java.util=ALL-UNNAMED ...
   ```

2. Or declare the package open in the target module's `module-info.java`:

   ```java
   module my.module {
       opens com.example.internal;
   }
   ```

## Interface proxies

Interface proxies use a public `Lookup` and support **public** interfaces only (the same constraint as `java.lang.reflect.Proxy`). Non-public interface proxies use `LookupManager` to define the class in the interface's own package — see
[Multi-Interface Proxy](09-multi-interface-proxy.md).

## Class-loader constraints

A generated proxy is a hidden class, and a hidden class must be defined in a
loader that can see **both** OpenProxy's own types (`Interceptor`,
`DispatchTarget`) and the target type. When it cannot, `proxy()` and
`proxyStatic()` fail with a message naming both loaders:

```text
Cannot create a proxy for com.acme.plugin.Widget: the type is owned by
com.example.RestartLoader@1b2c3d4 while the OpenProxy classes that define the
proxy are owned by jdk.internal.loader.ClassLoaders$AppClassLoader@5e6f7a8.
A hidden proxy class must be defined in a loader that can see both. Options:
(1) make com.acme.plugin.Widget resolvable from ...AppClassLoader; or
(2) load openproxy and ASM from ...RestartLoader and create the Interceptor
    there too, so the interceptor and the proxy share one loader.
```

Two shapes work today:

1. The target shares the library's loader, or is visible to it from an ancestor
   loader — including `public` JDK interfaces, which need no `--add-opens`.
2. The plugin loader carries **its own copy** of OpenProxy. Then the
   `Interceptor` you pass must come from that same loader: handing a private
   copy an interceptor created elsewhere fails with `argument type mismatch`.

Proxying a target in a loader the library cannot see, through a definition
lookup supplied by the caller, is **not available yet** — tracked in
[ROADMAP.md](../../ROADMAP.md) as `support-cross-classloader-proxy`.

Next: [Migration](12-migration.md).
