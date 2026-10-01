# 10. Hot Reload / Hot Swap

OpenProxy gives you two levers for long-running applications: evicting cached proxy classes (for hot-deployed classes) and swapping interceptors on a live instance.

## Evicting cached proxy classes

```java
OpenProxy.evict(MyClass.class);            // drop proxies keyed on MyClass
OpenProxy.evictClassLoader(pluginClassLoader); // drop proxies for a loader
```

The next `proxy(...)` call regenerates a fresh class. Existing instances are unaffected (they hold a direct reference to their hidden class). Use this in frameworks that hot-deploy classes under a dedicated `ClassLoader`.

Cache-key note: class proxies key on the target class; interface proxies key on the **first** interface, so pass that to `evict`.

## Redeploying into a fresh ClassLoader

Eviction is loader-aware, but generation is not: OpenProxy cannot yet create a
proxy for a class owned by a loader its own classes cannot see. A
restart-classloader redeploy therefore fails with the cross-loader message
described in [JPMS / Strong Encapsulation](11-jpms.md#class-loader-constraints),
and `evict` / `evictClassLoader` cover the shapes that do work today — targets
in the library's loader, or targets in a loader that owns its own OpenProxy
copy.

If you take the private-copy route, create the `Interceptor` inside that loader
as well: handing the copy an interceptor from another loader fails with
`argument type mismatch`.

## Swapping interceptors on a live instance

```java
Greeter proxy = OpenProxy.proxy(Greeter.class, oldInterceptor);
OpenProxy.rebind(proxy, newInterceptor);   // no recreation
```

`rebind` replaces the bound interceptors in place. The array form lets you swap several at once, index-aligned with the generated class's interceptor fields:

```java
OpenProxy.rebind(proxy, new Interceptor[]{a, b});
```

- `ConstructorInterceptor` is **not** rebindable (it runs only at construction).
- `rebind` is a single-writer management operation: a caller that rebinds on one thread and invokes methods on another must establish its own happens-before edge (lock, thread start, latch, or volatile flag).

Next: [JPMS / Strong Encapsulation](11-jpms.md).
