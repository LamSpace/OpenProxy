# Spec Delta

## MODIFIED Requirements

### Requirement: Deterministic proxy-class eviction

The library SHALL provide `OpenProxy.evict(Class<?>)` and `OpenProxy.evictClassLoader(ClassLoader)`. Evicting a target SHALL remove its cached generated proxy classes so that the next `proxy(...)` call for that target generates a fresh class. Eviction MUST NOT affect proxy instances that were already created — they keep working on the class they were built from.

Eviction SHALL also cover entries created with a caller-supplied definition lookup: `evict(target)` SHALL remove every cached proxy class for that target regardless of which lookup defined it, and `evictClassLoader(loader)` SHALL remove the entries whose target class is owned by `loader` — for a cross-loader proxy, that is the same loader that owns the generated class, so unloading a deployment loader drops everything it owned.

#### Scenario: Evict forces regeneration

- **WHEN** a proxy for a target class is created, then `evict(target)` is called
- **THEN** the next `proxy(...)` call for the same target returns an instance of a different generated class

#### Scenario: EvictClassLoader scopes by class loader

- **WHEN** proxy classes exist for targets loaded by two different class loaders, and `evictClassLoader(loaderA)` is called
- **THEN** only the proxy classes keyed by classes from `loaderA` are regenerated on the next `proxy(...)`; entries for the other loader remain cached

#### Scenario: Existing instances survive eviction

- **WHEN** a proxy instance exists and its target is evicted
- **THEN** invoking methods on that instance still works and still routes through its original interceptor

#### Scenario: Eviction rejects null arguments

- **WHEN** `evict(null)` or `evictClassLoader(null)` is called
- **THEN** an `IllegalArgumentException` is thrown

#### Scenario: Redeploy into a fresh loader

- **WHEN** a target is proxied in a child loader with a caller-supplied lookup, the loader is discarded, and the redeployed target is loaded by a new child loader with a lookup from it
- **THEN** `evictClassLoader(oldLoader)` drops only the old loader's entries
- **AND** the new loader's `proxy(...)` call generates a fresh class while instances created from the old loader keep serving

#### Scenario: Evict clears every lookup variant of one target

- **WHEN** the same target `Class` has cached proxy classes from two different supplied lookups and `evict(target)` is called
- **THEN** both cached classes are dropped and the next call regenerates
