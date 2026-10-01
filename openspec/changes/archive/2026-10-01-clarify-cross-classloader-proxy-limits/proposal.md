# Proposal

## Why

Proxying a target class owned by a different class loader than OpenProxy fails today with an opaque wrapped error (`RuntimeException: Failed to generate proxy class` caused by `IllegalAccessException: com.acme.PGreeter/io.github.lamspace.internal.LookupManager does not have full privilege access`), giving hot-deploying frameworks no way to tell what to fix. The old roadmap recorded this as "cross-ClassLoader hot deployment — separate TODO" and every archived change (`add-hot-reload`, `jpms-strong-encapsulation`) explicitly deferred it, but no change was ever opened and no test ever covered it: `HotReloadTest` exercises only app-loader classes, so the gap stayed invisible.

Measured on the published `openproxy-0.1.0` / JDK 25 (spike, seven scenarios): class proxies of public and package-private targets, interface proxies of public and package-private targets, mixed interface arrays, and sibling-loader redeploy ALL fail; same-loader targets of every shape pass; a child loader carrying its own OpenProxy copy passes.

## What Changes

- Add a cross-loader regression test suite that pins the measured matrix: same-loader targets keep passing for every proxy shape, and loader-mismatched targets fail — asserted on the *diagnostic contract*, not on the raw `IllegalAccessException`, so the suite still reads correctly once cross-loader support lands.
- Replace the opaque generation failure for the loader-mismatch case with an actionable error that names both loaders and states the two remedies available today: make the target resolvable from OpenProxy's loader, or load OpenProxy and ASM with the target's loader and create the interceptor from that same loader. This mirrors the precedent set by `e164ad4`, which turned silent wrong-package fallback into a fail-fast `--add-opens` hint.
- Document the constraint in the user guide (JPMS/access chapter) and record the boundary in `ROADMAP.md`, replacing the old roadmap's understated claim that only non-public interface targets were affected.
- No API surface changes. No behavior changes for same-loader users.

## Capabilities

### New Capabilities

_None — this change adds diagnostics and tests, not a new capability._

### Modified Capabilities

- `openproxy-core`: add a requirement that a proxy-generation failure caused by a loader/package access mismatch MUST surface an actionable diagnostic naming the target's loader and OpenProxy's loader, instead of a generic wrapper message.

## Impact

- `src/main/java/io/github/lamspace/OpenProxy.java` — `generateProxyClass` catch block (currently rethrows `IllegalArgumentException` as-is and wraps everything else) gains a loader-mismatch branch.
- `src/main/java/io/github/lamspace/internal/LookupManager.java` — likely location for distinguishing "target not definable from this loader" from the existing not-open case.
- New test class under `src/test/java/io/github/lamspace/` (e.g. `CrossClassLoaderLimitsTest`) with child-loader fixtures; `HotReloadTest` stays as-is.
- `docs/guide/11-jpms*.md` gains the constraint; `docs/guide/10-hot-reload*.md` notes that hot deploy across loaders is unsupported until `support-cross-classloader-proxy` lands; `ROADMAP.md` updated.
- Risk: low. Exception type stays a `RuntimeException` subtype (`IllegalArgumentException`, matching the JPMS precedent), so existing `catch (RuntimeException)` call sites keep working; only the message and cause chain change.
- Depends-on: none. Followed-by: `support-cross-classloader-proxy` (which adds the definer hook and will make the "unsupported" wording in these tests and docs obsolete).
