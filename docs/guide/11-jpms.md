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

Three shapes work today:

1. The target shares the library's loader, or is visible to it from an ancestor
   loader — including `public` JDK interfaces, which need no `--add-opens`.
2. The plugin loader carries **its own copy** of OpenProxy. Then the
   `Interceptor` you pass must come from that same loader: handing a private
   copy an interceptor created elsewhere fails with `argument type mismatch`.
3. The caller supplies the definition lookup, which puts the generated class in
   the target's own loader — see [Proxies across class
   loaders](#proxies-across-class-loaders).

## Proxies across class loaders

A framework whose targets live in a loader OpenProxy cannot see can supply the
lookup that defines the generated class:

```java
// called from inside the plugin loader
MethodHandles.Lookup lookup =
        MethodHandles.privateLookupIn(Widget.class, MethodHandles.lookup());
Widget proxy = OpenProxy.proxy(Widget.class, lookup, interceptor);
```

The generated class is placed in the **lookup's** package and defined in the
**lookup's** loader, so it can reference types the library's own loader cannot
see. The lookup is validated before any bytecode is generated, and each
violation is reported as an `IllegalArgumentException` naming the loader or
module at fault:

- it must grant full privilege access (`publicLookup()` is not enough);
- its class must be loaded by the loader that owns the target;
- if the target is not `public`, the lookup must be rooted in the target's own
  package;
- the target's loader must be able to resolve OpenProxy's own types (delegate
  to the library's loader, or carry a copy).

One limit is structural: the lookup's **module** must be able to read the module
owning OpenProxy's types. OpenProxy is published as a classpath artifact (an
unnamed module), and a named module can never read the unnamed module, so a
target inside a named module is not proxyable — the failure is reported rather
than left as an `IllegalAccessError`.

The supplied lookup joins the proxy-class cache identity (by its root class), so
two calls with lookups rooted in the same class reuse one generated proxy class,
while different roots never share one.

Next: [Migration](12-migration.md).
