# 2. 安装

## 环境要求

- **Java 25+**（OpenProxy 目标类文件版本 24+，并使用 Java 15 起提供的
  `MethodHandles.Lookup.defineHiddenClass`）
- **ASM 9.7.1**（作为编译依赖声明，无需额外配置）

## 源码构建

```bash
git clone https://github.com/lamspace/openproxy.git
cd openproxy
mvn install -DskipTests
```

这会把 `openproxy` 构件安装到你的本地 Maven 仓库。

## Maven 依赖

OpenProxy 已发布到 Maven Central，直接声明依赖即可：

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>openproxy</artifactId>
    <version>0.2.0</version>
</dependency>
```

## 模块路径部署（JPMS）

默认用法是 classpath。若要代理位于**命名模块**中的目标，改为把库部署为模块：将
`openproxy.jar` 与 ASM jar 放到 `--module-path`，并在目标所在模块加一条边——无需任何
exports 或 opens。

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

发布 jar 已声明 `Automatic-Module-Name: io.github.lamspace.openproxy`。ASM 是真正的命名
模块，而自动模块无法把它拉进模块图，因此启动时需显式作为 root（
`--add-modules ALL-MODULE-PATH` 同样覆盖）。完整契约见
[JPMS / 强封装](11-jpms_cn.md#命名模块中的目标)。

## 快速自检

```bash
mvn test
```

运行完整测试套件（254 个测试）并编译 JMH 基准测试。

下一章：[快速开始](03-quick-start_cn.md)。
