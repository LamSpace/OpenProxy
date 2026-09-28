# Changelog

All notable changes to this project are documented in this file.

## v0.1.0 (2026-09-28)

First public release, published to Maven Central as `io.github.lamspace:openproxy:0.1.0`.

- Runtime proxy generation with ASM hidden classes: concrete classes, interfaces, static methods and constructors
- hashCode-driven dispatch with direct `INVOKESPECIAL` super calls — no reflection, no `MethodHandle`, JIT-inlinable
- Functional API (`OpenProxy`) and annotation-driven API, with selective method grouping (`Group.of`)
- Multi-interface proxies, hot reload / rebind support
- Requires Java 25+; single compile dependency (ASM 9.7.1); JPMS-friendly
- Benchmarks vs CGLib and `java.lang.reflect.Proxy` included (see `docs/benchmark-results.md`)
