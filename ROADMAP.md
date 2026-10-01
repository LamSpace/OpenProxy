# OpenProxy 路线图

> 本文件取代已删除的 `docs/aps-future-roadmap.md`（后更名 `docs/openproxy-future-roadmap.md`，在 `375222f` 的文档整理中删除）。历史计划文档（`docs/superpowers/plans/`、`openspec/changes/archive/`）保留原文不改，其中对旧路线图路径的引用一律以本文件为准。

**当前状态（2026-10-01）**：`io.github.lamspace:openproxy:0.1.0` 已发布 Maven Central（tag `v0.1.0`，见 [CHANGELOG.md](CHANGELOG.md)）；`openspec/changes/` 无进行中的 change（20 个全部归档）；GitHub issues / PR 为 0；`openspec/specs/` 的 10 个能力规格与实现一致。

## 未完成功能项

### 跨 ClassLoader 热部署

老路线图第三阶段 item 6「热加载/热替换」标记为*部分完成*后遗留的独立待办（`a0175b5`、`1b807ad`），至今**未立项** —— 没有对应的 openspec change，也不在 `openspec/specs/` 中。

- **已交付的部分**：`OpenProxy.evict(Class)` / `evictClassLoader(ClassLoader)`（缓存驱逐）与 `OpenProxy.rebind(...)`（活实例拦截器热替换）。
- **遗留的部分**：目标类位于子 `ClassLoader` 时的热部署场景。归档设计文档两处明确保留该问题：
  - `openspec/changes/archive/2026-08-15-add-hot-reload/design.md:61` — 当时列为 out of scope，"requires the JPMS `privateLookupIn`/`--add-opens` strategy (roadmap item 8)"
  - `openspec/changes/archive/2026-08-15-jpms-strong-encapsulation/design.md:17` — item 8 完成后仍写作 "Cross-ClassLoader hot deployment (a separate concern; `--add-opens` does not address it)"
- **现状待确认，勿照搬旧结论**：`src/main/java/io/github/lamspace/internal/LookupManager.java:51-54` 现已统一走 `MethodHandles.privateLookupIn(targetClass, ...)`，隐藏类因此定义在目标类自身的加载器上下文中。纯 classpath（目标与 OpenProxy 同属 unnamed module）下的子加载器目标**可能已经可用**；真正的边界更可能在命名模块 + 子加载器、以及 OSGi 式模块隔离。
- **建议的第一步**：写复现测试确认上述边界，再决定是否立项，而不是直接按老路线图的结论开工。

## 候选事项（未确认，暂不立项）

以下两项是当前仓库状态 observable 的缺口，但项目此前没有把任何一条记录为计划，需要你确认后再决定是否立项：

- **无 CI**：仓库没有 `.github/`（或任何 CI 配置），而已发布的构件没有自动化测试/构建门禁。
- **版本号待 bump**：`pom.xml` 仍是已发布的 `0.1.0`。Maven Central 版本号不可重复，下次开发前需 bump，并同步 [README.md](README.md)、[README_CN.md](README_CN.md) 与 `docs/guide/02-installation*.md` 中的依赖坐标（发布流程见 `fdb64a7`）。
