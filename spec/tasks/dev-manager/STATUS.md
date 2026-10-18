# 负责人：公共骨架、核心与主线验收：状态

- 任务定义：`IN_PROGRESS`。
- 功能实现：第 1 组公共骨架与运行边界已实现且本地验证通过，待 PR/main 门禁；第 2–5 组 `NOT_STARTED`，整体任务未完成。
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
| 发布包检查 | PASS | ZIP 82,652,477 bytes；无测试插件、kotlin-stdlib 或 slf4j-api 重复条目 |
| 其余真实环境边界 | NOT_RUN | 可视人工交互、真实课程/模型 API、业务 schema/资料检索/完整会话恢复/ReAct；合成依赖检查不替代这些验收 |

记录局部测试、模拟控制器、真实 IDE 和真实 API 的证据边界。没有运行的项目保持 NOT_RUN；失败保留原因和退出码，不只改状态文字。真实 API 不因本地任务验证而自动调用。

## 阻塞与下一步

- 当前阻塞：无已知实现阻塞；PR/main 门禁待完成。
- 下一步：完成第 1 组交付记录与 main 集成，再按授权推进第 2 组；本轮不扩大实现范围。
- 公共改动请求：见 [DEPENDENCY_REQUEST.md](DEPENDENCY_REQUEST.md)。
- 结束/换 agent 前：更新本文件并填写 [HANDOFF.md](HANDOFF.md)，记录真实提交、测试和下一条动作。

## 第 1 组验证溯源

本地原始记录 `tmp/manager-task1/baseline-validation.log`（开工 All，0）、`final-validation.log`（最终 All + IdeSmoke，0），JUnit XML/HTML 位于 build/test-results 和 build/reports/tests；可分发运行报告见上表。本轮先后记录了 DNS 下载失败、测试 API/图参数修正、测试插件 sandbox/描述文件/项目释放写操作修正，以及真实插件内 Jackson SPI/YAML VerifyError；均修复后重验，没有把失败改写为通过。

IDE sandbox 默认禁用 Ultimate 标记，日志仍有其依赖插件与安装内服务 override 诊断；F0 及 Java 插件实际加载、服务/PSI/内容释放和模型合成响应断言通过。这不是所有其他插件兼容性结论。JVector 使用标量默认提供器，日志提示可选 Vector API，未增加 incubator/native 运行参数，也未测实际规模性能。未执行付费或真实模型/嵌入请求。
