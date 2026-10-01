# Spec Delta

## MODIFIED Requirements

### Requirement: All-public path unchanged

The system SHALL keep the all-public interface path byte-for-byte unchanged **while every interface in the array is resolvable from the library's own loader**: the generated class stays in `io.github.lamspace` and is defined with `MethodHandles.lookup()`, so public JDK interfaces in strongly-encapsulated modules remain proxyable without `--add-opens`. When an interface is not resolvable from the library's loader — because it belongs to a loader that the library's loader cannot see — the system SHALL NOT force the class into `io.github.lamspace`; it SHALL either reject the call with the cross-loader diagnostic or, when the caller supplies a definition lookup, define the generated class in that lookup's package and loader.

#### Scenario: Public JDK interface still proxied

- **WHEN** user calls `proxy(java.util.function.Function.class, interceptor)`
- **THEN** the proxy works without any `--add-opens` JVM argument

#### Scenario: Package placement is not forced on a loader-invisible interface

- **WHEN** a `public` interface is owned by a loader the library's loader cannot see
- **THEN** the generated class is not placed in `io.github.lamspace` for that request
- **AND** with a caller-supplied lookup the proxy implements the interface from the caller's own loader
