# Tasks

## 1. Measure before building (design.md D5/D6)

- [x] 1.1 With the 0.1.0 jar renamed as a plain file, run a scratch module layer:
  `ModuleFinder.of(openproxyDir+manifest-attr, asmJar, modsDir)`, app module requires
  only `io.github.lamspace.openproxy`, and check whether ASM resolves into the graph
  (i.e. whether generation actually runs). Record the measured launch shape in
  design.md D5; the packaging choice does not change with the answer.
- [x] 1.2 Scratch-run today's classpath deployment with
  `--add-reads <target-module>=ALL-UNNAMED` and record in design.md whether named-module
  proxying works through the workaround (validates the diagnostic premise and the
  remedy sentence's honesty).

## 2. Packaging

- [x] 2.1 Add `Automatic-Module-Name: io.github.lamspace.openproxy` via a pinned
  maven-jar-plugin `manifestEntries` block in `pom.xml` (snapshot the pre-change jar
  first); verify with `jar --describe-module --file target/openproxy-*.jar` showing
  `io.github.lamspace.openproxy automatic`.
- [x] 2.2 Compare the packaged jar against the pre-change artifact entry-by-entry
  (per-entry SHA-256 of class resources); verify the only differences are
  `META-INF/MANIFEST.MF` entries — no `module-info.class`, no class-byte drift.

## 3. Diagnostic wording

- [x] 3.1 Extend `CrossClassLoaderProxyTest.namedModuleTargetIsDiagnosed...` with an
  assertion that the `forModuleRead` message carries the module-path remedy
  (`requires io.github.lamspace.openproxy`) — watch it fail; then rewrite
  `CrossLoaderDiagnostics.forModuleRead` keeping the both-modules naming and the
  `cannot read module` fragment (existing assertions) — watch it pass.

## 4. Module-graph fixture

- [x] 4.1 Add fixture module `com.acme.proxyable` under
  `src/test/resources/crossmodule/` (sibling of `com.acme.mod`): `module-info.java`
  with only `requires io.github.lamspace.openproxy;` (exports NOTHING), `Widget`, and a
  `ProxyDriver` that constructs an interceptor and calls the supplied-lookup
  `proxy(...)` / `invokeSuper` inside the layer, plus a `classOf`/evict helper
  exposed for the test.
- [x] 4.2 Extend the fixture harness: synthesize `openproxy.jar` from `target/classes`
  with the manifest attribute (`java.util.jar`), locate ASM's jar via
  `org.objectweb.asm.ClassWriter`'s code source, compile the fixture with
  `--module-path` at the synthesized jar, and define the layer from finder roots
  `{com.acme.proxyable, org.objectweb.asm}` with the platform loader as parent (so the
  layer never sees the classpath copy); verify `Widget` loads from the layer loader,
  its module reads `io.github.lamspace.openproxy`, and `Module.isExported` is false
  for the target package.

## 5. Lookup-privileged instantiation (design D6)

- [x] 5.1 RED: new `NamedModuleProxyTest` proxies the named-module target through the
  driver and asserts interception + `invokeSuper` results — watch it fail with the
  instantiation `IllegalAccessException` shape measured in case B (proving the old
  cross-module reflection path is the blocker, not setup errors).
- [x] 5.2 GREEN: change `OpenProxy.instantiateProxy` to use the supplied lookup
  (`findConstructor(...).invokeWithArguments(...)`) on supplied-lookup calls only,
  keeping `getConstructor().newInstance()` for default paths — `NamedModuleProxyTest`
  turns green with no fixture change.
- [x] 5.3 Same file: assert two proxies from lookups rooted in the same class share
  one generated class, and `evictClassLoader` plus GC releases the layer loader
  (weak-reference characterization, per the D3 contract), with failure-path wrapping
  unchanged (`Failed to create proxy` still wraps construction exceptions on default
  paths — verify no regression in existing suites).

## 6. Documentation

- [x] 6.1 Rewrite the "One limit is structural" paragraph in `docs/guide/11-jpms.md`
  and `docs/guide/11-jpms_cn.md` into the `requires` recipe with the measured launch
  shape (ASM needs `--add-modules org.objectweb.asm` or ALL-MODULE-PATH; no target
  exports needed), and record the two-flag classpath workaround from task 1.2.
- [x] 6.2 Update `README.md` / `README_CN.md` JPMS paragraphs and add the
  `CHANGELOG.md` entry under Unreleased; verify both read
  `io.github.lamspace.openproxy` consistently with the pom.
- [x] 6.3 Close ROADMAP P3 in the ✅ style of P1/P2 (decision (b) + launch shape,
  measured evidence links) and note it rides the P0 0.2.0 bump.

## 7. Integration checks

- [x] 7.1 Run `mvn -B verify`: the full suite (249 existing + new) green with the
  22fdffb and `CrossClassLoaderLimitsTest` guards untouched; run
  `openspec validate --all --strict` and verify zero failures with the new
  `jpms-module-deployment` delta included.
