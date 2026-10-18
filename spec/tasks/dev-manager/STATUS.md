# 负责人：公共骨架、核心与主线验收：状态

- 任务定义：`IN_PROGRESS`。
- 功能实现：第 1–3 组 `DONE`（PR #3 / #4 / #5 已通过门禁并合入 main，第三组已核验）；第 4–5 组 `NOT_STARTED`，整体尚未完成。
- 当前分支：`dev-manager`；PR 目标：`main`，合并保留来源分支。
- 记录日期：2026-10-18。
- 开工 HEAD / 公共 main 基线：`36452814d58dfda228511bd17556dfa5df69094a`；已 fetch 并核实无 main 增量。
- 公共基线：按根 AGENTS、[API](../../contracts/API.md) 和本任务 [README](README.md) 核对；契约/fixtures 与工程骨架不等于本任务实现。

## 当前可观察结果

第 1 组具体范围见 [WORK_GROUP_1.md](WORK_GROUP_1.md)：应用/项目服务、运行 epoch、任务生命周期、共享 H2 短事务和类型化适配器入口；修复实际插件内的 Spring/Jackson/日志类加载冲突。公共业务控制器、资料/检索、ReAct、完整 session 持久化仍待后续组实现。

## 验证记录

| 检查 | 状态 | 命令 / 退出码 / 证据 |
| --- | --- | --- |
| 差异写入范围 | PASS | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-scope.ps1 -Staged`，退出 0，26 paths；未触及成员范围 |
| 仓库结构检查 | PASS | 已由开工/实施 All 入口执行，退出 0 |
| 任务相关测试 | PASS | 9 项 JUnit：TaskScope 2、RuntimeGate 2、H2Store 3、ModelClassLoading 1、依赖受控 chat/SSE/embedding 调用 1 |
| 完整统一入口 | PASS | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'`；退出 0，含 check/buildPlugin/FixtureSmoke |
| 第 1 组 IDEA 场景 | PASS | 上述完整命令增加 `-IdeSmoke`；最终退出 0，真实 IU-262.10968.63 / JBR25.0.4，见 [运行报告](evidence/work-group-1-runtime.json) |
| 发布包检查 | PASS | ZIP 79,474,195 bytes / 60 entries；无测试插件、kotlin-stdlib、kotlin-reflect 或 slf4j-api 重复条目 |
| 主线交付门禁 | 以 PR 实际记录为准 | [PR #3](https://github.com/nilesthump/IJ-Plugin-Java/pull/3)：独立 GitHub runner 的 validation、可信主线 scope 与 main 合入共同满足后，第 1 组交付完成 |
| 其余真实环境边界 | NOT_RUN | 可视人工交互、真实课程/模型 API、业务 schema/资料检索/完整会话恢复/ReAct；合成依赖检查不替代这些验收 |

记录局部测试、模拟控制器、真实 IDE 和真实 API 的证据边界。没有运行的项目保持 NOT_RUN；失败保留原因和退出码，不只改状态文字。真实 API 不因本地任务验证而自动调用。

## 阻塞与下一步

- 当前阻塞：无已知实现阻塞；集成状态由 PR #3 的实际检查与合入记录决定，不因本文件存在而视为通过。
- 下一步：完成第二组 dev-manager → main 的交付门禁与核验；本轮到第二组为止。
- 公共改动请求：见 [DEPENDENCY_REQUEST.md](DEPENDENCY_REQUEST.md)。
- 结束/换 agent 前：更新本文件并填写 [HANDOFF.md](HANDOFF.md)，记录真实提交、测试和下一条动作。

## 第 1 组验证溯源

本地原始记录 `tmp/manager-task1/baseline-validation.log`（开工 All，0）、`final-validation-reflect.log`（最终 All + IdeSmoke，0，排除重复 kotlin-reflect 后重验），JUnit XML/HTML 位于 build/test-results 和 build/reports/tests；可分发运行报告见上表。本轮先后记录了 DNS 下载失败、测试 API/图参数修正、测试插件 sandbox/描述文件/项目释放写操作修正，以及真实插件内 Jackson SPI/YAML VerifyError；均修复后重验，没有把失败改写为通过。

IDE sandbox 默认禁用 Ultimate 标记，日志仍有其依赖插件与安装内服务 override 诊断；F0 及 Java 插件实际加载、服务/PSI/内容释放和模型合成响应断言通过。这不是所有其他插件兼容性结论。JVector 使用标量默认提供器，日志提示可选 Vector API，未增加 incubator/native 运行参数，也未测实际规模性能。未执行付费或真实模型/嵌入请求。

## 第二组交付：资料与检索（2026-10-18）

- 实现：`IMPLEMENTED / VERIFIED`；本交付 PR 的 validation/scope 通过、独立检查通过并合入 main 后为 `DONE`。第 3–5 组仍 `NOT_STARTED`，整体保持 `IN_PROGRESS`。
- 开工与同步基线：`50f50f8b77daa81cbbfbfb2bcacf564109e7f7f0`，即 PR #3 的 main 合入；fetch 确认 HEAD 与 origin/main 一致，已有五分支与 .githooks 生效，未新建分支。
- 实现和接入：[WORK_GROUP_2.md](WORK_GROUP_2.md)。包括固定副本/MD5与独立资料来源、页内原文分块、完整 embeddingSpace、H2 float32/指纹/代次、JVector 查询与共享恢复、CAS 发布、旧 attempt/历史引用及预览清理。公共 UI DTO 和成员实现未修改。
- 最终统一入口：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'`，退出 0；31 项 JUnit（22 项新增 corpus 场景）、FixtureSmoke、check/buildPlugin 和真实本机 IDEA 命令通过。原始记录 `tmp/manager-task2/delivery-validation.log`；公开 [运行报告](evidence/work-group-2-runtime.json) 与 [验证汇总](evidence/work-group-2-validation.json)。
- 独立检查：review 技能的 Standards/Spec 两路只读代理；发现并修正 Unicode 重叠分块边界、执行器拒绝导致任务无终点、锁内完成 future 的回调死锁风险。针对性回归已加入最终验证；取消后本地终止保存失败由 settled 异常暴露。[检查记录](evidence/work-group-2-review.md)。
- 失败溯源保留：开工 All 为 0；实施时两轮 JVector/Java 签名编译失败和一次新增测试类型推断失败均记录于 tmp/manager-task2，修正后完整重跑通过，不把旧失败改写为通过。
- 真实 API、真实乙 PDF 解析联调、可视 UI、实际课程规模/召回/内存/时延仍 `NOT_RUN`；headless IDE 与合成分页/向量及 loopback HTTP 不替代这些证明。没有付费调用，未加入生产模型/分块/阈值默认值。
- 前置方案/PDF改动保持原样且不纳入本次提交。共享 API/COURSE_LIBRARY/JVECTOR 的本次实施回填独立提交；根讨论 WORKING/原附件保持本地，架构未冻结。
- 当前无遗留核心检查问题；完成本次 main 门禁后停止在第二组，不实施第三组。PR URL、head、远端门禁与合入核验另附交付补充。

### 第二组交付 PR

[PR #4](https://github.com/nilesthump/IJ-Plugin-Java/pull/4)：dev-manager → main，实现提交 `8b41abae4b11e24459d9903778e967b3d55d4d47`。后续记录提交不改业务代码；门禁必须按 PR 最新 head 检查。独立 Standards/Spec 已闭合，本地 All + IdeSmoke 和范围检查通过。只有最新 head 的 validation/scope 通过且 PR 合入 main 后，第二组完成条件才全部满足；GitHub 实际检查/merge commit 为远端证据，保留 dev-manager。

## 第三组交付：Spring AI、有限 ReAct 与流式（2026-10-18）

- 实现：`IMPLEMENTED / VERIFIED`。第 1、2 组已分别由 PR #3 / #4 合入 main；第三组在本交付 PR 最新 head 的 validation/scope、独立检查和 main 合入核验共同满足后为 `DONE`。整体仍 `IN_PROGRESS`，第 4–5 组 `NOT_STARTED`。
- 开工 HEAD / origin/main：`5b945fa936b7c5c7d86aea1e2fc01fe04f32609b`。当前 dev-manager / .githooks 已核对，无新建分支；fetch 后无 main 增量。开工前方案/PDF差异保留，第三组提交不包含这些产物。
- 实现及接入：[WORK_GROUP_3.md](WORK_GROUP_3.md)。固定代次首检与两个课程工具、单所有者串行步骤、严格 Spring AI 请求/SSE 边界、完整预算/限额、正文/thinking/tool/usage 分离、实际最终证据引用、故障分支隔离、统一历史资格、H2 累计/终态档案与来源保留、停止/终态保存竞争和删除 guard 接口。公共 UI/PDF DTO 未改，没有替代成员实现。
- 最终统一入口：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'`，退出 0。51 项 JUnit（本组新增 20 项）、FixtureSmoke、check/buildPlugin、真实本机 IU-262.10968.63 / JBR25.0.4 的 Spring AI/SSE/两步 ReAct/H2/JVector/固定物理页引用通过。原始记录 `tmp/manager-task3/final-validation.log`；[运行报告](evidence/work-group-3-runtime.json)、[验证汇总](evidence/work-group-3-validation.json)。
- 独立 Standards / Spec 两路只读检查已闭合：停止与成功保存竞争、取消双原子窗口、Error 分类、严格 JSON、首工具失败/停止后第二请求显示丢失均修复并回归。最终未闭合问题各为 0；[检查记录](evidence/work-group-3-review.md)。
- 失败记录保留：一次测试编译的 Snapshot 导入歧义、首轮定向的取消 future/epoch 竞争失败均修正后重跑；首个 All/IDE 通过后仍按独立检查修复并最终全量重验，没有覆盖旧失败记录。
- `NOT_RUN`：真实 DeepSeek/课程网关、生产思考模式与 tokenizer/额度、真实乙解析器、可视 UI、真实规模；完整 SessionController、项目身份/草稿、摘要/checkpoint 与根删除接线属第四组。严格协议和测试参数不冒充真实平台能力，未请求付费服务。无已知实现阻塞；本轮到第三组结束，不开展第四组。

## 第三组主线核验完成（2026-10-18）

第三组 `DONE`：[PR #5](https://github.com/nilesthump/IJ-Plugin-Java/pull/5) 最新 head `d959b0b329629cc75ba329c8b6315cb1613bba1b` 的 validation 与 scope 均 SUCCESS，已合入 main 并 fetch 核验 merge commit `14d465ea761da45d982691270e782e9a006d96d0`。固定 dev-manager 保留；主工作区同步 main，未触及成员分支。远端证据见 [交付记录](evidence/work-group-3-delivery.json)。第 4–5 组仍 NOT_STARTED，整体 IN_PROGRESS；本轮不开展第四组，原有方案/PDF改动继续保留。

### 第三组收尾：终止查询许可（2026-10-18）

在 PR #5 完成基础交付后，收尾复核将查询许可收紧为仅 Decision.RUNNING，避免取消转入终态保存时、lease 尚未释放的排队查询重新获得嵌入资格。可控执行器/延迟终态保存回归验证旧查询的嵌入与模型调用均为 0，两路独立增量检查均闭合。最新统一 All + IdeSmoke 退出 0，共 52 项 JUnit（本组新增 21 项）；原始日志 `tmp/manager-task3/terminal-fence-validation.log`。最终证据见原 work-group-3-validation.json / runtime.json / review.md。该修复和完成记录经 [PR #6](https://github.com/nilesthump/IJ-Plugin-Java/pull/6) 最新 head 的 validation/scope 并合入 main 后生效；未扩展至第四组，历史 51 项记录属于 PR #5 的真实交付批次。
