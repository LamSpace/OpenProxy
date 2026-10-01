# Design

## Context

See proposal.md for motivation. The JVM rules that shape this change were measured on `openproxy-0.1.0` / JDK 25 (`/tmp/opx-spike`), not assumed:

```
defineHiddenClass requires:  same LOADER and same PACKAGE as the lookup's class
  evidence  K  parent-generated class-proxy bytes, defined by a lookup whose class
               is com.acme.Definer in the child loader   -> PASS, invokeSuper works
  evidence  M prefix "io/github/lamspace/" + that lookup -> IAE:
               "io/github/lamspace/PubGreeter$$OpenProxy$$6 not in same package
                as lookup class"
  evidence  N same bytes, prefix "com/acme/"             -> PASS, child public
               interface proxy routes and returns
  evidence  G child loader with its own library copy      -> PASS (interceptor must
               come from that same copy)
  evidence  J child library copy + parent interceptor     -> argument type mismatch
```

Consequences that the design must respect: the caller is not handing over "a place to put bytes", it is handing over a **capability** (loader + package + privilege). The library's contract types (`Interceptor`, `DispatchTarget`, `internal.Rebindable`) are referenced by every generated class, so the target's loader must be able to resolve them — parent delegation to the library's loader is the normal case, and a child that has its own copy is the other one (G/J).

`InterfaceGenerator` already takes a `packagePrefix` constructor argument (`InterfaceGenerator.java:57-65`), which is how the non-public anchor case places classes today; the all-public case simply passes `"io/github/lamspace/"` (`OpenProxy.java:300-304`).

## Goals / Non-Goals

**Goals:**
- Proxy targets owned by a loader the library cannot see, with no JDK internal APIs and no custom ClassLoader.
- Keep every default (no-lookup) path byte-for-byte identical, so published benchmark numbers and the JDK-interface guarantee stay valid.
- Make the failure modes of a supplied lookup as explicit as the ones added by `clarify-cross-classloader-proxy-limits`.

**Non-Goals:**
- Transparent support for a framework that cannot supply a lookup (no internal bridge-class injection — see proposal Out of scope).
- Any change to dispatch, matching, interception cost, or the public `Interceptor` contract.
- Making an interceptor created by loader A usable by a library copy in loader B (J is a misuse of the copy, not something this change repairs).

## Decisions

**D1: The hook is a `java.lang.invoke.MethodHandles.Lookup`, passed as the first parameter of new overloads.**
Rejected alternatives:
- *`ClassDefiner`-style callback (`Class<?> define(byte[] bytes, String name)`)* — the caller would have to accept the name and package the library chose, which is exactly the coupling the JVM rules in Context make dangerous: get the package wrong and definition throws. It also enlarges the public API with a type whose only job is to re-express `Lookup`.
- *`Unsafe.defineClass` / injected bridge class* — needs no caller cooperation but depends on JDK internals, contradicting the stance set by `e164ad4` and `docs/guide/11-jpms.md`.
- *An options/builder object* — tidier against the existing overload spread, but it hides the one fact a caller must understand ("I am granting this lookup"), and adds a lifecycle type to a 5-method surface.
Chosen because `Lookup` is already the JVM's capability token, callers in the framework position produce it in one line (`MethodHandles.privateLookupIn(targetClass, MethodHandles.lookup())`), and `Lookup` has value semantics (`equals`/`hashCode`) that D3 needs.
Validation at entry, before any generation: `lookup.hasFullPrivilegeAccess()`, `lookup.lookupClass().getClassLoader() == target.getClassLoader()`, and — for a non-`public` target or non-public anchor — `lookup.lookupClass().getPackageName()` equals that type's package. A failure is the actionable `IllegalArgumentException` from the shared diagnostics helper, not a leaked `IllegalAccessException`.

**D2: The generated class's package is derived from the supplied lookup's class.**
`packagePrefix` for interface proxies becomes the lookup class's package instead of `"io/github/lamspace/"`, and the class-proxy naming must follow the same rule (`ClassGenerator` currently derives the name from the target — verify during implementation and thread the prefix in if it does). Default paths are untouched: with no supplied lookup, `OpenProxy.java:308-311` keeps `"io/github/lamspace/"` + `MethodHandles.lookup()`, and the anchor path keeps the anchor's package — both already satisfy the same-package rule, which is why they work today.

**D3: The lookup joins the proxy-class cache identity — keyed by its ROOT CLASS, not by the lookup.**
`CacheParams` gains a lookup field; `equals`/`hashCode` include `lookup.lookupClass()` (identity), not the `Lookup` object.
Corrected during apply by measurement: the premise that "`MethodHandles.Lookup` has value semantics" is **false**. Two lookups produced by `privateLookupIn(sameClass, ...)` on the same class and loader compared `equals=false`, `hashCode` differing. Keying on the lookup itself therefore made the supplied-lookup path miss the cache on **every call** — regenerating a proxy class each time, which is a performance defect far worse than the sharing it was meant to prevent, and it also made `evictClassLoader` look like it was evicting unrelated entries when in fact nothing had ever been cached.
Root class alone is sufficient because every accepted lookup is required to grant full privilege access (see D1), so the root class fully identifies the capability being granted.
Trade-off accepted: a cache entry then strongly retains the lookup and therefore its loader, so a framework that undeploys a loader MUST call `evictClassLoader(loader)` (or `evict(target)`) — the same contract hot-deploy frameworks already keep for their own caches. A characterization test with a `WeakReference` to the plugin loader after eviction plus GC is required, to catch a leak rather than assume one.

**D4: Pre-validate that the target's loader *and module* can see the library's contract types.**
`NoClassDefFoundError`/`IllegalAccessError` for `io.github.lamspace.*` is an `Error` and escapes `catch (Exception)`, so it must be prevented before defining, in two distinct ways: resolve `Interceptor`, `DispatchTarget`, and `Rebindable` through `lookup.lookupClass().getClassLoader()` (loader visibility), **and** check that `lookup.lookupClass().getModule()` can read the module of each contract type (module readability) — the task 1.1 measurement showed loader resolution succeeds while readability fails, so one without the other leaves a hole. A readability failure is reported as the actionable `IllegalArgumentException` naming both modules.

**D5: `proxyStatic` and `intercept` get the same first-parameter overloads.**
Static proxies were never cached, so a supplied lookup only changes where the generated class is defined; per-loader static state is inherent to the design and gets documented rather than engineered away.

**D6: Scope of new overloads — seven, not six.** `proxy(Class<T>, Lookup, Interceptor)`, `proxy(Class<T>, Lookup, Group...)`, `proxy(Class<?>[], Lookup, Interceptor)`, `proxy(Class<?>[], Lookup, Group...)`, `proxyStatic(Class<?>, Lookup, Group...)`, plus `intercept(Class<T>, Lookup, Object)`. plus `proxy(Class<T>, Lookup, Object[], ConstructorInterceptor, Group...)`.
The constructor-interception combination turned out **not** to be reachable through the existing shapes: every existing shape that accepts a `ConstructorInterceptor` has no parameter position for a lookup, so the task 3.4 test could not express the case at all. That is the condition D6 set for adding an overload, so it was added. The other five entry points are as listed.

## Risks / Trade-offs

- [A caller passes a lookup whose loader has its own library copy while the interceptor comes from another loader — measured `argument type mismatch` (J)] → validate loader identity per D1 up front; document the two consistent shapes (delegate to one library, or keep everything — library copy, interceptor, target — inside one loader).
- [Lookup retention keeps undeployed loaders alive] → D3's `evictClassLoader` contract plus the `WeakReference` characterization test.
- [Regression of the JDK-public-interface-without-`--add-opens` guarantee] → the default branch is untouched; `PublicJdkInterfaceProxyTest` and the byte-for-byte guard from `22fdffb` are the checks, and D2 changes placement only when a lookup is supplied.
- [Named JPMS module targets] → **measured in task 1.1: not supported with today's packaging.** A public class in `com.acme.mod`, defined through a full-privilege `privateLookupIn` from inside that same module, links into its module fine but then fails:

  ```
  java.lang.IllegalAccessError: superinterface check failed:
    class com.acme.mod.Widget$$OpenProxy$$0 (in module com.acme.mod)
    cannot access io.github.lamspace.internal.Rebindable (in unnamed module)
    because module com.acme.mod cannot read unnamed module
  ```

  `Method.canRead` was `false` and privilege was `true`, so neither the loader nor the package is the obstacle — **module readability** is, and a named module can never read the unnamed module. Unblocking it needs OpenProxy itself to be a real or automatic module that the target module `requires` (packaging work, tracked as a non-goal by `jpms-strong-encapsulation`), not more lookup plumbing. The residual obligation for this change is only to report it: `IllegalAccessError` is an `Error`, so the same pre-check that guards `NoClassDefFoundError` must also rule out the readability failure instead of letting it escape.
- [D4's loader-only pre-check was insufficient] → measurement above: resolving the contract types by *loader* succeeds even when the *module* cannot read them, so the check must test `Module.canRead` / export, not `Class.forName` alone. D4 is amended accordingly.
- [API growth] → bounded to seven entry points (D6) on a library whose 0.x versioning has just started; the shape is the first thing to revisit in review.
- [The `defineHiddenClass` diagnostic named one loader twice] → found in apply. `privateLookupIn(target, libraryLookup)` returns a lookup **rooted in the target**, so deriving "the defining loader" from `lookup.lookupClass().getClassLoader()` yields the target's own loader, and the message read "owned by X while OpenProxy classes are owned by X" — vacuous. `defineHidden` now reports `OpenProxy.class.getClassLoader()`. The sibling suite's assertion was also too weak to have caught it (it only required two `@` tokens); it now requires two *distinct* loader class names, which is what the spec actually promises.

## Migration Plan

Purely additive: new overloads, new validation, no signature or bytecode change for existing callers. Ship in the next minor release with `CHANGELOG.md` and `docs/guide/10-hot-reload*.md` / `11-jpms*.md` updated; `ROADMAP.md` then drops the "unsupported until supported" wording written by the diagnostics change. Rollback is a plain revert — nothing persisted, no renamed types.

## Open Questions

None. Both were settled by measurement during planning or apply:
- named-module targets are not proxyable with today's packaging, and the obligation reduces to diagnosing it (task 1.1, `CrossClassLoaderProxyTest.namedModuleTargetIsDiagnosedNotLeftAsIllegalAccessError`);
- constructor interception does need its own `Lookup`-aware overload, added as the seventh entry point (task 3.4, `constructorInterceptionWorksWithSuppliedLookup`).
