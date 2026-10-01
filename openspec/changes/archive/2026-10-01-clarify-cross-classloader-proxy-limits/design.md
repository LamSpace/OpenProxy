# Design

## Context

Four definition sites decide which loader a hidden class lands in, and they fail in two different ways across loaders (measured on `openproxy-0.1.0` / JDK 25; spike sources kept at `/tmp/opx-spike`, static site measured during apply as recorded below):

| Site | Defining lookup | Cross-loader symptom |
|---|---|---|
| class proxies | `LookupManager.getLookup(target)` | `IllegalAccessException` ("does not have full privilege access") from `defineHiddenClass`, then double-wrapped by the generic handlers |
| non-public interface anchor | `LookupManager.getLookup(anchor)` | same `IllegalAccessException` |
| all-public interfaces | `MethodHandles.lookup()` (library's own loader) | `NoClassDefFoundError` for the interface |
| `proxyStatic` | `MethodHandles.lookup()` (library's own loader) | `NoClassDefFoundError` for the **target class** — measured in apply, see "Static site measurement" |

The last two matter for the design: `NoClassDefFoundError` is an `Error`, so the existing `catch (Exception e)` never sees it — the raw linkage error escapes to the caller. Translation-by-catch is therefore not available for those paths; they must be prevented before definition.

**Static site measurement (task 1.3).** `OpenProxy.proxyStatic(childLoadedTarget, ...)` threw, unwrapped, out of the library:

```
java.lang.NoClassDefFoundError: com/acme/child/ChildPublicClass
    at java.lang.invoke.MethodHandles$Lookup.defineHiddenClass(MethodHandles.java:2026)
    at io.github.lamspace.OpenProxy.proxyStatic(OpenProxy.java:727)
Caused by: java.lang.ClassNotFoundException: com.acme.child.ChildPublicClass
```

So the static path needed the same pre-check as the all-public interface path (`requireResolvableFrom`), not the catch-translation of the class path. This is the fourth site the proposal's "three sites" wording undercounted; the spec requirement is phrased generically and already covers it.

Established precedent for the message contract: the strongly-encapsulated-module path throws `IllegalArgumentException` from `LookupManager` (rethrown unwrapped at `:321`), which `proxy(...)` then wraps in a `RuntimeException` — and `JpmsStrongEncapsulationTest.java:29-36` asserts exactly that shape (`assertThrows(RuntimeException.class)`, then `getCause() instanceof IllegalArgumentException`, then message containment). This change reuses that contract instead of inventing a second one.

## Goals / Non-Goals

**Goals:**
- Turn both cross-loader symptoms into one diagnostic that names the target type, the target's loader, and the library's loader, plus the two remedies available today.
- Pin the measured matrix in tests so the follow-on cross-loader change flips expectations instead of inventing them.
- Keep the same-loader path byte-for-byte and behavior-for-behavior unchanged.

**Non-Goals:**
- No cross-loader proxying support (that is `support-cross-classloader-proxy`, which adds a caller-supplied definer).
- No new public API, no signature changes, no exception type introduced.
- No change to bytecode generation, dispatch, or benchmarks.
- `HotReloadTest` stays as it is — it is correct, just not about loaders.

## Decisions

**D1: Pre-check the interface path, translate the class path.**
- Interface and static paths (both define with the library's own lookup): before `defineHiddenClass`, resolve each referenced type's binary name through that lookup's loader and compare `Class` identity with the requested type. A resolution failure means the definition would throw `NoClassDefFoundError`; an identity mismatch means the proxy would silently implement a *different* class with the same name (the child-first-loader case) and fail later at the caller's cast. Both are diagnosed before any bytecode is defined — `OpenProxy.requireResolvableFrom`, called for interface arrays and, per the measurement above, for the `proxyStatic` target. Chosen over catching, because `Error` escapes the existing `catch (Exception)`.
- Class/anchor path: keep `privateLookupIn` as-is and translate the `IllegalAccessException` that `defineHiddenClass` throws into the actionable `IllegalArgumentException` at the definition site. Chosen over a loader-identity pre-check, because "same loader" is not the real rule — an ancestor loader that has the package open works today (JDK targets go through the JPMS path), and keying on `getClassLoader() !=` would regress it. Keying on the exception type is safe: `IllegalAccessException` is part of the `java.lang.invoke` contract; the JDK's message text is not.
- Alternative rejected: parse `defineHiddenClass`'s "does not have full privilege access" message. Fragile across JDK releases, and it would keep the failure discoverable only by string matching.

**D2: One internal helper, one message.** A single helper in the internal package — `io.github.lamspace.internal.CrossLoaderDiagnostics.forType(...)`, declared `public` because it is called across packages from `OpenProxy`, exactly as `LookupManager` is — builds the `IllegalArgumentException`, so the four sites cannot drift apart in wording. Shipped message, following the `--add-opens` precedent (one line in source; loaders render as `<loader class>@<identity hex>`, or "the bootstrap loader"):

```
Cannot create a proxy for <type>: the type is owned by <targetLoader> while the
OpenProxy classes that define the proxy are owned by <definingLoader>. A hidden
proxy class must be defined in a loader that can see both. Options:
(1) make <type> resolvable from <definingLoader>; or
(2) load openproxy and ASM from <targetLoader> and create the Interceptor
    there too, so the interceptor and the proxy share one loader.
Cross-ClassLoader proxying with a caller-supplied definer is tracked in
ROADMAP.md (support-cross-classloader-proxy).
Reported by the JVM: <original failure, when there was one>
```

Two deliberate details:
- The thrown `IllegalArgumentException` carries **no cause**. The spec requires the diagnostic to be the terminal cause, so the original `IllegalAccessException` / `ClassNotFoundException` is summarized in the message tail instead of staying at the bottom of the chain.
- The ROADMAP pointer is deliberate: today it names a plan; when `support-cross-classloader-proxy` lands, that sentence is the line that gets deleted.

**D3: Test fixtures — compile at test time into a child-first loader.** Fixture sources are compiled with `ToolProvider.getSystemJavaCompiler()` into a temp directory that is **not** on the parent classpath, loaded by a child-first `ClassLoader`, so the library's loader genuinely cannot see the fixture types. Each test MUST assert the premise (`assertThrows(ClassNotFoundException.class, () -> parent.loadClass(fixtureName))`) before asserting the failure. Reason: an earlier spike compiled fixtures into the same output directory as the driver; the parent loader then resolved them first, every "child loader" case silently degenerated into a same-loader case, and the whole first matrix was wrong. A fixture that is parent-visible makes these tests vacuous rather than failing.
- Alternative considered: ASM-synthesize the fixture classes in-memory (no toolchain dependency). Rejected as the primary approach because the matrix needs package-private modifiers, `default` methods and cross-package layouts, which read far better as source files; ASM stays as a fallback if `jdk.compiler` is ever unavailable at test time.

**D4: Where the tests live.** New `src/test/java/io/github/lamspace/CrossClassLoaderLimitsTest.java` plus a small `ChildFirstClassLoader` test helper, rather than widening `HotReloadTest`: the subject is generation-time access rules, not eviction lifecycle, and the follow-on change will flip this file's expectations while `HotReloadTest` keeps passing untouched.

## Risks / Trade-offs

- [`NoClassDefFoundError` escapes `catch (Exception)`] → the interface path is guarded by D1's pre-check, so no linkage error is ever relied upon; a residual escape becomes a test failure rather than a silent pass.
- [Child-first loaders are easy to get wrong, and a wrong fixture passes vacuously] → D3's premise assertion (`parent cannot resolve the fixture`) is a required first step in every cross-loader test.
- [Regression in the same-loader path] → the pre-check only runs for interface arrays whose members are not resolvable from the library's loader; the byte-for-byte all-public guarantee in `non-public-interface-proxy` and `PublicJdkInterfaceProxyTest` stay green, which is the guard.
- [Callers matching the old generic message] → messages are additive; the exception type (`RuntimeException` caused by `IllegalArgumentException`) already exists for the JPMS path, so no new catch shape.
- [JDK wording drift] → D1 keys on exception type and `Class` identity, never on JDK message text.

## Migration Plan

Purely additive: diagnostics at the four definition sites (two by translation, two by pre-check) plus new tests. No API, no bytecode, no dependency changes. Rollback is a plain revert; nothing to unwind for users except that they see the old opaque failure again.

## Open Questions

None — the one open question (whether `proxyStatic` shares the all-public interface
symptom) was measured in apply and resolved above: it does, so it takes the same
pre-check. The proposal's "three sites" count was one short; the spec requirement is
phrased generically and needed no widening.
