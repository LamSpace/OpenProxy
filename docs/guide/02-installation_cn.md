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
    <version>0.1.0</version>
</dependency>
```

## 快速自检

```bash
mvn test
```

运行完整测试套件（220+ 个测试）并编译 JMH 基准测试。

下一章：[快速开始](03-quick-start_cn.md)。
