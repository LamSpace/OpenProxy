## Purpose

Support proxying package-private (non-`public`) interfaces by defining the generated hidden class in the interface's own package, while leaving the all-public path byte-for-byte unchanged.

## Requirements

### Requirement: Non-public interface proxying

The system SHALL generate a proxy that implements a package-private interface, defining the generated hidden class in that interface's package so the JVM access rule is satisfied.

#### Scenario: Package-private interface proxied

- **WHEN** user calls `proxy(SecretService.class, interceptor)` where `SecretService` is package-private
- **THEN** the returned proxy implements `SecretService`
- **AND** method calls route through `interceptor.intercept(proxy, method, args)`

#### Scenario: invokeSuper on a package-private default method

- **WHEN** a package-private interface declares a `default` method
- **AND** the interceptor calls `invokeSuper(proxy, method, args)`
- **THEN** the `default` implementation runs

### Requirement: Mixed public and non-public interfaces

The system SHALL proxy an array mixing `public` interfaces (any package) with non-public interfaces that share a single package; that shared package becomes the generated class's package.

#### Scenario: Public plus package-private interface

- **WHEN** user calls `proxy(new Class<?>[]{PublicMarker.class, SecretService.class}, interceptor)`
- **THEN** the proxy implements both interfaces and routes both through the interceptor

### Requirement: Cross-package non-public rejection

The system SHALL throw `IllegalArgumentException` when the interface array contains non-public interfaces from different packages.

#### Scenario: Different-package non-public interfaces rejected

- **WHEN** two non-public interfaces reside in different packages
- **THEN** `proxy(...)` throws `IllegalArgumentException`

### Requirement: All-public path unchanged

The system SHALL keep the all-public interface path byte-for-byte unchanged **while every interface in the array is resolvable from the library's own loader**: the generated class stays in `io.github.lamspace` and is defined with `MethodHandles.lookup()`, so public JDK interfaces in strongly-encapsulated modules remain proxyable without `--add-opens`. When an interface is not resolvable from the library's loader — because it belongs to a loader that the library's loader cannot see — the system SHALL NOT force the class into `io.github.lamspace`; it SHALL either reject the call with the cross-loader diagnostic or, when the caller supplies a definition lookup, define the generated class in that lookup's package and loader.

#### Scenario: Public JDK interface still proxied

- **WHEN** user calls `proxy(java.util.function.Function.class, interceptor)`
- **THEN** the proxy works without any `--add-opens` JVM argument

#### Scenario: Package placement is not forced on a loader-invisible interface

- **WHEN** a `public` interface is owned by a loader the library's loader cannot see
- **THEN** the generated class is not placed in `io.github.lamspace` for that request
- **AND** with a caller-supplied lookup the proxy implements the interface from the caller's own loader

