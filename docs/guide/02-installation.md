# 2. Installation

## Requirements

- **Java 25+** (OpenProxy targets class-file version 24+ and uses
  `MethodHandles.Lookup.defineHiddenClass`, available since Java 15)
- **ASM 9.7.1** (declared as a compile dependency — no extra setup needed)

## Build from source

```bash
git clone https://github.com/lamspace/openproxy.git
cd openproxy
mvn install -DskipTests
```

This installs the `openproxy` artifact into your local Maven repository.

## Maven dependency

OpenProxy is published on Maven Central:

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>openproxy</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Module path deployment (JPMS)

Classpath usage is the default. To proxy targets that live inside **named**
modules, deploy the library as a module instead: put `openproxy.jar` and the ASM
jar on the `--module-path` and add one edge to the target's module — no exports
or opens required.

```java
module com.acme.app {
    requires io.github.lamspace.openproxy;
}
```

```bash
java --module-path openproxy.jar:asm.jar:mods \
     --add-modules org.objectweb.asm \
     -m com.acme.app/com.acme.app.Main
```

The published jar declares `Automatic-Module-Name: io.github.lamspace.openproxy`.
ASM is a real named module and an automatic module cannot pull it into the graph,
so it must be rooted explicitly (`--add-modules ALL-MODULE-PATH` also covers it).
See [JPMS / Strong Encapsulation](11-jpms.md#named-module-targets) for the full
contract.

## Quick self-check

```bash
mvn test
```

Runs the full test suite (254 tests) and compiles the JMH benchmarks.

Next: [Quick Start](03-quick-start.md).
