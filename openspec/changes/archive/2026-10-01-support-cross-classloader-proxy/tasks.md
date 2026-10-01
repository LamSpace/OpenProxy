# Tasks

Depends on `clarify-cross-classloader-proxy-limits` being applied first: this change flips the expectations its `CrossClassLoaderLimitsTest` pins, and reuses its diagnostics helper.

## 1. Measure what the design refuses to assume

- [x] 1.1 Determine whether a target living in a **named** JPMS module can be proxied through a supplied lookup at all (a named module cannot read the unnamed module, so the library's contract types may be unresolvable); verify by building a named-module fixture loaded by a child loader and recording the observed failure or success in design.md's risk entry, and if it fails, add a scenario to `specs/cross-classloader-proxy/spec.md` that promises a diagnostic instead of silence.

## 2. Lookup plumbing and validation

- [x] 2.1 Add the supplied lookup to the proxy-class cache identity (`CacheParams`, `OpenProxy.java:~100-143`, including `equals`/`hashCode`) and verify with tests that the same lookup reuses one cached class while two different lookups over the same target produce two distinct generated classes.
- [x] 2.2 Validate a supplied lookup at every new entry point before generation — `hasFullPrivilegeAccess()`, lookup-class loader equals target loader, and lookup-class package equals the target's package for non-`public` targets or non-public anchors — and verify each rejection is an actionable `IllegalArgumentException` from the shared helper with no `IllegalAccessException` escaping.
- [x] 2.3 Pre-resolve `Interceptor`, `DispatchTarget`, and `Rebindable` through the lookup's loader before defining anything, and verify a target loader that can see neither the library by delegation nor by its own copy fails with the two-loader diagnostic rather than a `NoClassDefFoundError`.

## 3. Definition placement follows the lookup

- [x] 3.1 Derive the generated class's package from the supplied lookup's class for interface proxies (replacing the hard-coded `"io/github/lamspace/"` only on this path) and verify a `public` interface owned by an invisible child loader proxies successfully, implements that exact `Class` object, and `invokeSuper` reaches a `default` method — the spike's case N, now a test.
- [x] 3.2 Do the same for class proxies, verifying `ClassGenerator` names the generated class in the lookup's package for a `public` target and keeps the target's package for package-private targets; verify with the spike's case K as a test (child class proxy + `invokeSuper` returning the real implementation) plus a package-private child target case.
- [x] 3.3 Add the `proxyStatic` and `intercept` lookup-aware overloads and verify a child-loaded static target is proxied through a supplied lookup, that per-loader static state is asserted by a test, and that the existing non-caching behavior of static proxies is unchanged.
- [x] 3.4 Confirm the six entry points listed in design.md D6 are the whole new surface, and resolve the constructor-interception question there by testing `proxy(Class<T>, Lookup, ConstructorInterceptor, Group...)`-shaped use; add an overload only if the test shows the existing shapes cannot express it.

## 4. Hot deployment and retention

- [x] 4.1 Write the redeploy test — proxy a target in a child loader with a supplied lookup, discard that loader, load a redeployed copy in a new child loader with a lookup from it — and verify `evictClassLoader(oldLoader)` drops only the old loader's entries, the new loader regenerates, and instances from the old loader keep serving; verify this flips the corresponding pinned expectations in `clarify-cross-classloader-proxy-limits` while that suite still passes overall.
- [x] 4.2 Add a `WeakReference` characterization test asserting that after `evictClassLoader(loader)` plus GC, the cache no longer strongly retains the lookup or its loader, and verify it fails if the cache key keeps the lookup beyond eviction (this is the accepted trade-off in design.md D3, so it must be observed, not assumed).

## 5. Documentation and regression guards

- [x] 5.1 Document the cross-loader recipe in `docs/guide/10-hot-reload.md` / `10-hot-reload_cn.md` and `docs/guide/11-jpms.md` / `11-jpms_cn.md` — one-line `MethodHandles.privateLookupIn(targetClass, MethodHandles.lookup())` from the plugin side, the two loader-consistency shapes, and the eviction obligation — and verify every claim in the text corresponds to a passing test.
- [x] 5.2 Update `ROADMAP.md` (remove the "unsupported until this lands" wording written by the diagnostics change) and add the `CHANGELOG.md` entry; verify the roadmap's open-item section no longer lists cross-ClassLoader deployment as unmet.
- [x] 5.3 Verify the default path is untouched: the byte-for-byte guard from `22fdffb`, `PublicJdkInterfaceProxyTest`, `JpmsStrongEncapsulationTest`, and `NonPublicInterfaceProxyTest` pass, and re-run the class and interface proxy benchmark scenarios to confirm no steady-state regression against `docs/benchmark-results.md`.

## 6. Integration checks

- [x] 6.1 Run `mvn -s /home/lam/repo/settings.xml -q test` for the full suite and verify it is green.
- [x] 6.2 Run `openspec validate support-cross-classloader-proxy --strict` and verify the delta for `non-public-interface-proxy` reads correctly against the main spec's "All-public path unchanged" requirement before sync/archive.
