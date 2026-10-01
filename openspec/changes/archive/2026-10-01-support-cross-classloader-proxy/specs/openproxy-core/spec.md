# Spec Delta

## MODIFIED Requirements

### Requirement: Hidden class loading

The system SHALL load both class and interface proxy bytecode through `Lookup.defineHiddenClass(byte[], true)`, avoiding custom ClassLoader usage and ensuring proxy classes are eligible for garbage collection. The lookup used for definition SHALL be the library's own by default, and SHALL be replaceable by a caller-supplied lookup that has full privilege access to the loader and package the generated class is defined in, without switching to any other class-definition mechanism.

#### Scenario: Proxy class is garbage collectable

- **WHEN** all references to a proxy instance and its class are dropped
- **THEN** the proxy class SHALL be eligible for GC without ClassLoader retention

#### Scenario: Definition mechanism is unchanged by a supplied lookup

- **WHEN** a proxy is generated with a caller-supplied lookup
- **THEN** the class is still loaded through `defineHiddenClass(byte[], true)`
- **AND** no custom ClassLoader is created for it
