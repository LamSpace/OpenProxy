# OpenProxy 路线图

> 本文件取代已删除的 `docs/aps-future-roadmap.md`（后更名 `docs/openproxy-future-roadmap.md`，在 `375222f` 的文档整理中删除）。历史计划文档（`docs/superpowers/plans/`、`openspec/changes/archive/`）保留原文不改，其中对旧路线图路径的引用一律以本文件为准。

本文件只记录**尚未立项的开放事项**。已完成功能的动机、实测数据与设计决策在 `openspec/changes/archive/<日期>-<change>/`，行为契约在 `openspec/specs/`，发布内容在 [CHANGELOG.md](CHANGELOG.md)。

**当前状态（2026-10-01）**：`io.github.lamspace:openproxy:0.1.0` 已发布 Maven Central（tag `v0.1.0`）；无活动 change（22 个已归档）；GitHub issues / PR 为 0。最近完成的是跨 ClassLoader 代理与热部署：`archive/2026-10-01-clarify-cross-classloader-proxy-limits`（诊断 + 回归测试）与 `archive/2026-10-01-support-cross-classloader-proxy`（7 个 `MethodHandles.Lookup` 入口），实测矩阵与命名模块的 `IllegalAccessError` 证据见各自的 `design.md`。

## 候选事项（未确认，暂不立项）

以下几项均已核实（附证据），但项目此前没有把任何一条记录为计划，需要你确认后再决定是否立项。**已按建议起手顺序排列**；2026-10-01 确认并完成了 P1 与两条 P2（小节保留备查），开放事项剩 P0 与 P3：

| 优先级 | 含义 |
|---|---|
| P0 | 立刻做 —— master 的内容与已发布构件有落差，越晚越容易让人误判能力边界 |
| P1 | 与 P0 同批做最省 —— 成本极低 |
| P2 | 有空就还 —— 债或基建，不阻塞功能 |
| P3 | 先决策再做 —— 需要选方向，且影响面最窄 |

### P0 · 本批功能尚未发布，版本号待 bump

`pom.xml` 仍是已发布的 `0.1.0`，而 `CHANGELOG.md` 顶部是 `## Unreleased`（跨 ClassLoader 支持与跨加载器诊断都在其中）—— 从 Maven Central 取 0.1.0 的使用者读不到这些能力。Maven Central 版本号不可重复，发布需先 bump，并同步 [README.md](README.md)、[README_CN.md](README_CN.md) 与 `docs/guide/02-installation*.md` 中的依赖坐标（发布流程见 `fdb64a7`）。

### P1 · ✅ 已完成（2026-10-01）README 未提跨加载器能力

`README.md` 与 `README_CN.md` 的 Features 段各增一条「跨 ClassLoader 代理」（7 个 `proxy` / `intercept` / `proxyStatic` lookup 入口），JPMS 段补上跨加载器用法与命名模块限制，并链到 `docs/guide/10-hot-reload*.md`。纯文档改动；Installation 坐标仍是 0.1.0，待 P0 发布时一并 bump。

### P2 · ✅ 已完成（2026-10-01）4 个主规格缺 `## Requirements` 段头

`multi-interceptor-grouping` 与 `openproxy-unified-proxy` 残留的 delta 段头 `## ADDED Requirements` 改为 `## Requirements`；`multi-interface-proxy` 与 `openproxy-interface-proxy` 在 Purpose 之后插入 `## Requirements`。`openspec validate --all --strict` 由 7 passed / 4 failed 变为 **11 passed / 0 failed**，零行为变更。

### P2 · ✅ 已完成（2026-10-01）无 CI

新增 `.github/workflows/ci.yml`：push（master）与 PR 触发，Temurin JDK 25 上执行 `mvn -B verify`（`pom.xml` 的 source/target 均为 25）。该命令已在本地 JDK 25.0.3 验证通过：249 tests，0 failures，故 CI 首次亮起即为绿。

### P3 · 命名模块中的目标不可代理

需要 OpenProxy 自己提供模块声明（`module-info.java` 或 jar 的 `Automatic-Module-Name`），让目标模块可以 `requires` 它；否则命名模块永远读不到 unnamed module 里的 `Interceptor`/`DispatchTarget`/`Rebindable`。这条曾被 `jpms-strong-encapsulation` 列为 non-goal，现在 `archive/2026-10-01-support-cross-classloader-proxy/design.md` 给出了实测支撑。

要先定的方向：走 `module-info.java` 还是只加 `Automatic-Module-Name`；若走前者，`io.github.lamspace.internal` 的导出策略一并决定（生成的代理类实现 `internal.Rebindable`）。classpath 与 OSGi 式子加载器已全部打通，所以收益面最窄。
