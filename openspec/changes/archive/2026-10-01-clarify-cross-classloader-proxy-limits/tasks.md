# Tasks

## 1. Cross-loader test fixture infrastructure

- [x] 1.1 Add a `ChildFirstClassLoader` test helper that resolves a configured package prefix from its own URLs before delegating, and compiles fixture sources at test time with `ToolProvider.getSystemJavaCompiler()` into a temp directory that is not on the parent classpath; verify the helper's self-test passes, asserting the parent loader throws `ClassNotFoundException` for a fixture type while the child loads it (this is the guard against the vacuous-fixture trap recorded in design.md D3).
- [x] 1.2 Add fixture types covering every measured shape — public concrete class, package-private concrete class, `public` interface with a `default` method, package-private interface with a `default` method, class with `public static` methods — and verify each loads from the child loader and proxies successfully when forced onto the same-loader path.
- [x] 1.3 Measure `OpenProxy.proxyStatic` against a child-loaded target (the one path the spike never covered) and verify the observed exception is recorded in design.md, replacing the Open Question, so groups 2-3 know whether `OpenProxy.java:660-661` needs the same treatment.

## 2. Class and anchor definition sites

- [x] 2.1 Translate the `IllegalAccessException` from `defineHiddenClass` at `OpenProxy.java:318-319` (class proxies) and `:310` (non-public interface anchor) into the actionable `IllegalArgumentException` from the shared helper in 4.1; verify new `CrossClassLoaderLimitsTest` cases for the public class, package-private class, package-private anchor, and mixed interface array all assert the cause chain contains that `IllegalArgumentException` naming the target type and both loaders, with no `IllegalAccessException` as terminal cause.
- [x] 2.2 Add the same-loader control cases to `CrossClassLoaderLimitsTest` (target shares the library's loader; `public` JDK interface resolved from an ancestor loader) and verify they still succeed unchanged alongside `PublicJdkInterfaceProxyTest`, `JpmsStrongEncapsulationTest`, and `NonPublicInterfaceProxyTest` in `mvn -s /home/lam/repo/settings.xml -q test`.

## 3. All-public interface definition site

- [x] 3.1 Add a generation-time pre-check before `MethodHandles.lookup().defineHiddenClass(...)` at `OpenProxy.java:308-311`: resolve each interface's binary name through the library's loader and compare `Class` identity with the requested interface; verify the `public`-interface-in-child case (`NoClassDefFoundError` today) now fails at proxy-creation time with the actionable `IllegalArgumentException`, and that no `LinkageError` escapes to the caller.
- [x] 3.2 Cover the child-first silent-mismatch shape, where the library's loader resolves the same binary name to a *different* `Class` than the one requested, and verify it is diagnosed by the pre-check instead of surfacing later as a `ClassCastException` at the caller's cast.

## 4. Message contract and documentation

- [x] 4.1 Add a package-private helper (e.g. `io.github.lamspace.internal.CrossLoaderDiagnostics`) that is the single source of the diagnostic message in design.md D2, and verify every cross-loader test in groups 2-3 asserts through one shared helper rather than repeating string matches.
- [x] 4.2 Document the loader constraint in `docs/guide/11-jpms.md` / `11-jpms_cn.md` and state in `docs/guide/10-hot-reload.md` / `10-hot-reload_cn.md` that redeploying into a fresh loader is not supported until the follow-on change lands; verify each documented statement matches an assertion in `CrossClassLoaderLimitsTest`.
- [x] 4.3 Record in `docs/guide/10-hot-reload*.md` the loader-consistency trap measured in the spike (a private OpenProxy copy in the plugin loader only works when the interceptor comes from that same loader — `argument type mismatch` otherwise); verify the limitation is stated in both languages.

## 5. Integration checks

- [x] 5.1 Run the full suite and verify the all-public interface path is still byte-for-byte unchanged for same-loader targets (the existing guard from `22fdffb` stays green) and that `HotReloadTest` passes untouched.
- [x] 5.2 Verify `openspec validate clarify-cross-classloader-proxy-limits` passes and `openspec/specs/openproxy-core/spec.md` will receive the new requirement at archive time (delta reviewed against the main spec's requirement wording before sync).
