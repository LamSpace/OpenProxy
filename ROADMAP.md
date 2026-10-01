# OpenProxy 路线图

> 本文件取代已删除的 `docs/aps-future-roadmap.md`（后更名 `docs/openproxy-future-roadmap.md`，在 `375222f` 的文档整理中删除）。历史计划文档（`docs/superpowers/plans/`、`openspec/changes/archive/`）保留原文不改，其中对旧路线图路径的引用一律以本文件为准。

**当前状态（2026-10-01）**：`io.github.lamspace:openproxy:0.1.0` 已发布 Maven Central（tag `v0.1.0`，见 [CHANGELOG.md](CHANGELOG.md)）；`openspec/changes/` 当前无活动 change（22 个已归档）；GitHub issues / PR 为 0；`openspec/specs/` 的 11 个能力规格与实现一致（新增 `cross-classloader-proxy`；`openproxy-core` 含跨加载器诊断与可替换 lookup 定义两条）。

## 未完成功能项

### 跨 ClassLoader 代理与热部署 —— 已完成（2026-10-01）

老路线图第三阶段 item 6「热加载/热替换」的部分完成遗留（`a0175b5`、`1b807ad`；归档设计文档两处保留该问题：`archive/2026-08-15-add-hot-reload/design.md:61`、`archive/2026-08-15-jpms-strong-encapsulation/design.md:17`），已在 `openproxy-0.1.0` / JDK 25 上实测确认，并拆成两个 OpenSpec change：

| Change | 范围 | 状态 |
|---|---|---|
| `clarify-cross-classloader-proxy-limits` | 跨加载器回归测试 + 可操作诊断 + 文档，不改任何行为 | **已归档** `archive/2026-10-01-clarify-cross-classloader-proxy-limits`；requirement 已同步进 `openspec/specs/openproxy-core` |
| `support-cross-classloader-proxy` | 调用方传入 `MethodHandles.Lookup`，把隐藏类定义到目标自己的加载器与包里 | **已归档** `archive/2026-10-01-support-cross-classloader-proxy`；新能力 `cross-classloader-proxy` 及其 3 条 requirement 已同步 |

实测矩阵（spike 七类场景 + apply 期补测的静态站点，结论已落进两个 change 的 design.md）：

| 场景 | 同加载器 | 目标在子加载器 |
|---|---|---|
| 类代理 public / package-private | PASS | FAIL：`defineHiddenClass` 要求 lookup 类与生成类同加载器、同包 |
| 接口代理 public 接口 | PASS | FAIL：`NoClassDefFoundError`（`Error` 逃过 `OpenProxy.java:325` 的 `catch (Exception)`） |
| 接口代理 package-private 锚点 / 混合数组 | PASS | FAIL：同上加载器限制 |
| 静态方法代理 `proxyStatic` | PASS | FAIL：同接口路径根因（apply 期实测为裸 `NoClassDefFoundError`，故与接口路径共用生成前预检） |
| 子加载器自带 OpenProxy 副本 | — | PASS，但拦截器必须同源，否则 `argument type mismatch` |
| 父生成字节码 + 子用自己的 `privateLookupIn` 定义 | — | **PASS**：`invokeSuper` 与接口路由均正常 —— 即 S3 的可行路径 |

**老路线图低估了范围**：它只提到"目标类在子 ClassLoader 时无法代理"，实际类代理与接口代理都不可，且两者是**不同根因**（一个是 full-privilege 定义限制，一个是定义加载器看不见接口），诊断已分两类落地：类/锚点路径 catch 翻译、接口/静态路径生成前预检（`OpenProxy.requireResolvableFrom`），消息单一来源 `internal/CrossLoaderDiagnostics`（该 IAE 故意不带 cause，规范要求诊断本身是链尾）。
支持侧交付 7 个 lookup 感知入口（类、接口数组、`intercept`、`proxyStatic`、构造器拦截），生成包名跟随 lookup 的包、缓存标识按 lookup 根类、四类前置校验（完全特权 / 目标加载器 / 非 public 目标的包 / 库类型可解析与模块可读），并定义了跨加载器的 `evict` / `evictClassLoader` 语义。用法见 `docs/guide/11-jpms.md`「跨类加载器代理」与 `docs/guide/10-hot-reload.md`「部署到全新的 ClassLoader」。

- **遗留边界**：只剩**命名模块**中的目标不可代理（结构性限制，见下方候选事项）；classpath / OSGi 式子加载器已全部打通，含热部署到新加载器。
- **已回答（apply 期实测）**：目标位于**命名模块**时不可代理 —— `com.acme.mod` 内的目标配上模块内 full-privilege lookup，`defineHiddenClass` 能放下类，但 link 失败：`IllegalAccessError: ... because module com.acme.mod cannot read unnamed module`。根因是模块可读性而非加载器或包，而命名模块永远无法 read unnamed module，所以这是**发布形态**问题，不是 lookup 管道问题（见下方候选事项）。

## 候选事项（未确认，暂不立项）

以下几项是当前仓库状态 observable 的缺口，此前没有被记录为计划，需要你确认后再决定是否立项：

- **命名模块目标不可代理**：需要 OpenProxy 自己提供模块声明（`module-info.java` 或 jar 的 `Automatic-Module-Name`），让目标模块可以 `requires` 它；否则命名模块永远读不到 unnamed module 里的 `Interceptor`/`DispatchTarget`/`Rebindable`。这条曾被 `jpms-strong-encapsulation` 列为 non-goal，现在有了实测依据支撑其必要性。注意若走 `module-info.java`，`io.github.lamspace.internal` 的导出策略要一并决定（生成的代理类实现 `internal.Rebindable`）。
- **无 CI**：仓库没有 `.github/`（或任何 CI 配置），而已发布的构件没有自动化测试/构建门禁。
- **版本号待 bump**：`pom.xml` 仍是已发布的 `0.1.0`。Maven Central 版本号不可重复，下次开发前需 bump，并同步 [README.md](README.md)、[README_CN.md](README_CN.md) 与 `docs/guide/02-installation*.md` 中的依赖坐标（发布流程见 `fdb64a7`）。
