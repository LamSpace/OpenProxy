# OpenProxy 路线图

> 本文件取代已删除的 `docs/aps-future-roadmap.md`（后更名 `docs/openproxy-future-roadmap.md`，在 `375222f` 的文档整理中删除）。历史计划文档（`docs/superpowers/plans/`、`openspec/changes/archive/`）保留原文不改，其中对旧路线图路径的引用一律以本文件为准。

本文件只记录**尚未立项的开放事项**。已完成功能的动机、实测数据与设计决策在 `openspec/changes/archive/<日期>-<change>/`，行为契约在 `openspec/specs/`，发布内容在 [CHANGELOG.md](CHANGELOG.md)。

**当前状态（2026-10-02）**：`io.github.lamspace:openproxy:0.2.0` 已发布 Maven Central（tag `v0.2.0`，repo1 上 pom/jar/sources/javadoc 与 GPG 签名齐备）；无活动 change（23 个已归档）；GitHub issues / PR 为 0。最近完成的是命名模块中的目标代理：`archive/2026-10-02-support-named-module-targets`；其前是跨 ClassLoader 代理与热部署：`archive/2026-10-01-clarify-cross-classloader-proxy-limits`（诊断 + 回归测试）与 `archive/2026-10-01-support-cross-classloader-proxy`（7 个 `MethodHandles.Lookup` 入口），实测矩阵与命名模块的 `IllegalAccessError` 证据见各自的 `design.md`。

## 候选事项（未确认，暂不立项）

以下几项均已核实（附证据），但项目此前没有把任何一条记录为计划，需要你确认后再决定是否立项。**已按建议起手顺序排列**；2026-10-01 确认并完成了 P1 与两条 P2（小节保留备查），2026-10-02 完成 P3（`support-named-module-targets`）与 P0（`0.2.0` 已发布），开放事项清零：

| 优先级 | 含义 |
|---|---|
| P0 | 立刻做 —— master 的内容与已发布构件有落差，越晚越容易让人误判能力边界 |
| P1 | 与 P0 同批做最省 —— 成本极低 |
| P2 | 有空就还 —— 债或基建，不阻塞功能 |
| P3 | 先决策再做 —— 需要选方向，且影响面最窄 |

### P0 · ✅ 已完成（2026-10-02）版本号 bump 至 0.2.0 并发布

`pom.xml` 已 bump `0.1.0` → `0.2.0`（新增入口与命名模块支持均为纯增量、零 breaking，拟版本号取 minor），`CHANGELOG.md` 的 `## Unreleased` 定稿为 `## 0.2.0 (2026-10-02)`，[README.md](README.md)、[README_CN.md](README_CN.md) 与 `docs/guide/02-installation*.md` 依赖坐标同步 0.2.0。发布材料验证：`mvn -s /home/lam/repo/settings.xml -Prelease -Dgpg.skip=true clean install` 于 JDK 25.0.3 全绿（254 tests），main/sources/javadoc 三构件齐备。人工发布步骤（交互式 deploy 输 GPG passphrase → Portal *Validated* 后点 **Publish**）已于 2026-10-02 全部执行完毕，Publish 后约 25 分钟 repo1 可拉取全部构件。

### P1 · ✅ 已完成（2026-10-01）README 未提跨加载器能力

`README.md` 与 `README_CN.md` 的 Features 段各增一条「跨 ClassLoader 代理」（7 个 `proxy` / `intercept` / `proxyStatic` lookup 入口），JPMS 段补上跨加载器用法与命名模块限制，并链到 `docs/guide/10-hot-reload*.md`。纯文档改动；Installation 坐标仍是 0.1.0，待 P0 发布时一并 bump。

### P2 · ✅ 已完成（2026-10-01）4 个主规格缺 `## Requirements` 段头

`multi-interceptor-grouping` 与 `openproxy-unified-proxy` 残留的 delta 段头 `## ADDED Requirements` 改为 `## Requirements`；`multi-interface-proxy` 与 `openproxy-interface-proxy` 在 Purpose 之后插入 `## Requirements`。`openspec validate --all --strict` 由 7 passed / 4 failed 变为 **11 passed / 0 failed**，零行为变更。

### P2 · ✅ 已完成（2026-10-01）无 CI

新增 `.github/workflows/ci.yml`：push（master）与 PR 触发，Temurin JDK 25 上执行 `mvn -B verify`（`pom.xml` 的 source/target 均为 25）。该命令已在本地 JDK 25.0.3 验证通过：249 tests，0 failures，故 CI 首次亮起即为绿。

### P3 · ✅ 已完成（2026-10-02）命名模块中的目标不可代理

方向选了 `Automatic-Module-Name: io.github.lamspace.openproxy`（jar manifest，不出 `module-info`：审计后生成字节码同时引用 `internal` 与 `generator` 包，真 module-info 反正要把三包全导出，净收益只剩 ASM 的 requires 边，不值 surefire 与导出维护税）。实测（change `support-named-module-targets` task 1.1/1.2）：自动模块拉不进命名模块 ASM，部署需 `--add-modules org.objectweb.asm`；readability 修复后暴露第二道闸——库对目标未导出包的实例化跨模块反射，故 supplied-lookup 入口的构造改走该 lookup 的 `findConstructor`（默认路径原样保留），用户侧契约收敛为仅一条 `requires` 边、零 exports。`forModuleRead` 文案由"暂不可代理"改为模块路径补救指引。`NamedModuleProxyTest`（真 `ModuleLayer`：模块内落位/invokeSuper/缓存/evict 释放加载器）+ `com.acme.proxyable` fixture，254 测试全绿。部署形态与 0.1.0 的双 flag 逃生门（`--add-reads` + `--add-exports`）见 `docs/guide/11-jpms*.md`。
