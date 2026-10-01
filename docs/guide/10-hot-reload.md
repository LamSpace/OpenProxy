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

Supply the definition lookup and a restart-classloader redeploy works end to end:

```java
OpenProxy.evictClassLoader(oldLoader);   // drop the previous deployment
Object proxy = OpenProxy.proxy(freshClass, freshLookup, interceptor);
```

- `evict(target)` drops every cached proxy class for that target, including the
  ones created with different supplied lookups.
- A cached entry keeps the supplied lookup — and therefore its loader —
  reachable, so a framework that discards a loader MUST call
  `evictClassLoader(loader)`; nothing else releases it.
- Instances created before eviction keep serving from their own hidden class.

See [JPMS / Strong Encapsulation](11-jpms.md#proxies-across-class-loaders) for
the lookup's requirements and the named-module limit.

If you keep a private OpenProxy copy inside the plugin loader instead, create the
`Interceptor` in that loader as well: handing the copy an interceptor from
another loader fails with `argument type mismatch`.

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
