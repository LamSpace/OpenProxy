## Purpose

A high-performance dynamic proxy engine for Java that proxies concrete classes at runtime using hashCode-based dispatch with direct INVOKESPECIAL super calls, offering a drop-in replacement for CGLib with near-zero interception overhead.
## Requirements
### Requirement: Proxy class creation

The system SHALL generate a runtime subclass of any non-final concrete class and route all non-final instance method calls through a user-provided single `Interceptor` handler.

#### Scenario: Basic proxy creation and interception

- **WHEN** user calls `OpenProxy.proxy(TargetClass.class, interceptor)`
- **THEN** system returns a proxy instance of type `TargetClass`
- **AND** any method call on the proxy invokes `interceptor.intercept(proxy, method, args)`

#### Scenario: Super method invocation via invokeSuper

- **WHEN** callback calls `OpenProxy.invokeSuper(proxy, method, args)`
- **THEN** the original superclass method executes via direct `INVOKESPECIAL` (no reflection, no MethodHandle)
- **AND** the return value is returned to the callback

### Requirement: HashCode-based super-call dispatch

The system SHALL generate a `dispatch(Method, Object[])` method with a hashCode-driven if-else chain that routes each method to its corresponding `super.method(args)` call. No `MethodHandle[]` array or `MethodHandle.invoke()` SHALL be used.

#### Scenario: Super call is direct INVOKESPECIAL

- **WHEN** `dispatch(method, args)` is invoked
- **THEN** the system computes `method.hashCode()` and matches the pre-computed hash constant
- **AND** the matched branch calls `super.method(args)` directly with type-specific parameter unboxing

### Requirement: Hidden class loading

The system SHALL load both class and interface proxy bytecode through `Lookup.defineHiddenClass(byte[], true)`, avoiding custom ClassLoader usage and ensuring proxy classes are eligible for garbage collection. The lookup used for definition SHALL be the library's own by default, and SHALL be replaceable by a caller-supplied lookup that has full privilege access to the loader and package the generated class is defined in, without switching to any other class-definition mechanism.

#### Scenario: Proxy class is garbage collectable

- **WHEN** all references to a proxy instance and its class are dropped
- **THEN** the proxy class SHALL be eligible for GC without ClassLoader retention

#### Scenario: Definition mechanism is unchanged by a supplied lookup

- **WHEN** a proxy is generated with a caller-supplied lookup
- **THEN** the class is still loaded through `defineHiddenClass(byte[], true)`
- **AND** no custom ClassLoader is created for it

### Requirement: Method filtering

The system SHALL support an optional `ClassFilter` that determines which methods pass through the `Interceptor`. Methods not accepted by the filter SHALL call the superclass implementation directly with zero interception overhead.

#### Scenario: Filtered method skips interception

- **WHEN** user creates a proxy with
  `OpenProxy.proxy(TargetClass.class, interceptor, method -> method.getName().startsWith("get"))`
- **AND** a method not matching the filter is called (e.g., `setValue`)
- **THEN** the method executes the superclass implementation directly without invoking the interceptor

#### Scenario: Unfiltered proxy intercepts all methods

- **WHEN** user creates a proxy with `OpenProxy.proxy(TargetClass.class, interceptor)` (no filter)
- **THEN** all non-final instance method calls are routed through the interceptor

### Requirement: Primitive type handling

The system SHALL correctly box primitive arguments into Object[] for callback delivery and unbox the Object return value back to the expected primitive type.

#### Scenario: Primitive argument boxing

- **WHEN** a method with primitive parameters (e.g., `int add(int a, int b)`) is called on a proxy
- **THEN** the callback receives `args` containing `Integer` objects for each primitive parameter

#### Scenario: Primitive return unboxing

- **WHEN** a callback returns an `Integer` for a method declared to return `int`
- **THEN** the caller receives the unboxed `int` value without ClassCastException

### Requirement: Void method handling

The system SHALL handle methods with void return type by discarding the callback's return value.

#### Scenario: Void method invocation

- **WHEN** a void method is called on a proxy
- **THEN** the callback receives and executes the call
- **AND** the caller receives no return value regardless of what the callback returns

### Requirement: Exception propagation

The system SHALL propagate unchecked exceptions thrown by the callback directly to the caller. Checked exceptions SHALL be wrapped in `java.lang.reflect.UndeclaredThrowableException`.

#### Scenario: RuntimeException from callback

- **WHEN** callback throws a RuntimeException
- **THEN** the caller receives that exact RuntimeException

#### Scenario: Checked exception from callback

- **WHEN** callback throws a checked Exception not declared by the intercepted method
- **THEN** the caller receives an UndeclaredThrowableException wrapping the original exception

### Requirement: Access control for strongly encapsulated modules

The system SHALL attempt to obtain full private access to the target class via `MethodHandles.privateLookupIn`. If the target module is not open, the system SHALL fail fast by throwing `IllegalArgumentException` with an actionable `--add-opens` hint, instead of falling back to a public lookup. Primitive and array types (rejected by `privateLookupIn`) SHALL fall back to a public lookup.

#### Scenario: Private access succeeds

- **WHEN** the target class's package is open for reflection
- **THEN** the system uses a full-access Lookup enabling access to all non-public methods

#### Scenario: Strongly encapsulated module denied with actionable error

- **WHEN** the target class's module does not open its package
- **THEN** the system throws `IllegalArgumentException`
- **AND** the message contains an actionable `--add-opens <module>/<package>=ALL-UNNAMED` hint
- **AND** the original `IllegalAccessException` is retained as the cause

#### Scenario: Primitive or array type falls back

- **WHEN** the target class is a primitive or array type (rejected by `privateLookupIn` with `IllegalArgumentException`)
- **THEN** the system falls back to a public lookup without failing

### Requirement: Proxy class caching

The system SHALL cache generated proxy classes keyed by `{targetClass, filter}` to avoid re-generating bytecode for the same proxy configuration.

#### Scenario: Repeated proxy creation reuses class

- **WHEN** user calls `OpenProxy.proxy(SomeClass.class, interceptor)` twice
- **THEN** the system reuses the previously generated proxy class

### Requirement: No-default-constructor support

The system SHALL support proxying classes that lack a no-argument constructor by accepting constructor arguments at proxy creation time, unboxing boxed primitive arguments to the superclass constructor's declared parameter types.

#### Scenario: Proxy class with constructor arguments

- **WHEN** user calls `OpenProxy.proxy(BeanWithArgs.class, new Object[]{"arg1", 42}, groups)` for a `BeanWithArgs(String, int)` constructor
- **THEN** the system finds a matching constructor on the target class
- **AND** generates a proxy constructor that unboxes the boxed `Integer` argument to `int` and delegates the arguments to `super(...)`
- **AND** returns a properly initialized proxy instance

#### Scenario: Proxy class with reference-only arguments

- **WHEN** user calls `OpenProxy.proxy(Bean.class, new Object[]{"name"}, groups)` for a `Bean(String)` constructor
- **THEN** the system delegates the `String` argument to `super(...)`
- **AND** returns a properly initialized proxy instance

#### Scenario: Null constructor argument

- **WHEN** user calls `OpenProxy.proxy(Bean.class, new Object[]{null}, groups)` for a `Bean(String)` constructor
- **THEN** the system delegates `null` to `super(...)`
- **AND** returns a properly initialized proxy instance

### Requirement: Cross-loader generation diagnostic

The system SHALL detect when a proxy cannot be generated because the generated class cannot live in a loader that sees both the library's contract types and the target type, and SHALL report it through an actionable `IllegalArgumentException` — matching the exception contract already established for the strongly-encapsulated-module case, where the entry point throws a `RuntimeException` whose cause chain carries an `IllegalArgumentException` holding the actionable message. That message SHALL name the target type, the loader that owns the target, and the loader that owns the library, and SHALL state the remedies available in this release: make the target resolvable from the library's loader, or load the library and its bytecode dependency from the target's own loader and create the interceptor from that same loader.

The `IllegalAccessException` and `NoClassDefFoundError` currently produced by these two paths MUST NOT remain the terminal cause of the failure. This requirement changes only the reporting of an already-failing case; it MUST NOT enable any proxy that previously succeeded or change generated bytecode.

#### Scenario: Class proxy of a target the library cannot define into

- **WHEN** a class proxy is requested for a concrete class owned by a loader that the library's own loader cannot see
- **THEN** the cause chain of the thrown exception includes an `IllegalArgumentException`
- **AND** its message names the target class, both class loaders, and the two remedies
- **AND** the terminal cause is that `IllegalArgumentException`, not `IllegalAccessException`

#### Scenario: Interface proxy of an interface the library cannot see

- **WHEN** an interface proxy is requested for a `public` interface owned by a loader that the library's own loader cannot see
- **THEN** the cause chain includes the same actionable `IllegalArgumentException`
- **AND** the failure is reported at proxy-creation time rather than as a `NoClassDefFoundError` escaping class definition

#### Scenario: Package-private interface anchor follows the same rule

- **WHEN** an interface proxy is requested whose array contains a non-`public` interface owned by a loader the library's own loader cannot see
- **THEN** the cause chain includes the same actionable `IllegalArgumentException`

#### Scenario: Loader-visible targets are unaffected

- **WHEN** a proxy is requested and the library can define the generated class with the target types it needs — the target shares the library's loader, or the target is visible to it from an ancestor loader such as a `public` JDK interface
- **THEN** the proxy is created as before, with no diagnostic and unchanged generated bytecode
