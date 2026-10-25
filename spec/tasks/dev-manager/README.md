# 负责人：公共骨架、核心与主线验收

固定分支 `dev-manager`，主工作区 `H:/IJ-Plugin-Java`，独立 PR 直接到 `main`。负责人承担约五份职责；与甲乙丙并列开发，不是成员 PR 的中转。成员从 `main` 同步公共契约和构建基线。

## 可复制给 agent 的提示

```text
你负责 dev-manager 公共与核心任务。先按本 README 读文件，核对主工作区已有 dev-manager。
在公共/core/model/storage/bootstrap、对应测试、公共fixtures、构建/注册/脚本/治理范围实现；不并发覆盖成员editor/pdf/ui及其任务目录。
Java25、单Gradle Groovy+Wrapper、edu.courseassist，Spring AI必须保留，H2与JVector按确认路线推进。
先提供实际contract/fixtures/统一验证公共基线并PR到main，再逐场景落实资料、ReAct/流式、会话/提交/删除/恢复。
中断/失败/无效分支留在历史界面但不得进入后续模型、检索改写、摘要或checkpoint输入。
真实API/目标IDE/依赖组合与参数仍待证；使用合成数据可验证的先做，不自动发真实API请求，不虚报产品通过。
四个dev各自PR直接main，保留固定分支；成员依赖请求由公共变更解决，不接收成员PR到dev-manager。
```

## 按序阅读

1. 根 `AGENTS.md` → [CURRENT](../../progress/CURRENT.md) → [BASELINE](../../architecture/BASELINE.md) / 开发计划，再依根入口读治理、当前任务 README / [STATUS](STATUS.md) / [HANDOFF](HANDOFF.md)。治理包括 [写入范围](../../governance/WRITE_SCOPE.md)、[工作流](../../governance/WORKFLOW.md)、[分支规则](../../governance/BRANCH_POLICY.md)、[验证规则](../../governance/VALIDATION.md)。
2. [开发计划](../../architecture/F0_DEVELOPMENT_PLAN.md)、[并行接入](../../architecture/PARALLEL_START.md)、[分工](../../architecture/TASK_ALLOCATION.md)。
3. [公共 API](../../contracts/API.md)、当前 contract/fixtures、构建/注册文件与 [统一验证入口](../../../scripts/validate.ps1)，然后读取三位成员任务和依赖请求。
4. [行为](../../architecture/F0_BEHAVIOR.md)、[工程蓝图](../../architecture/ENGINEERING_BLUEPRINT.md)、[资料库](../../architecture/COURSE_LIBRARY.md)、[持久化](../../architecture/PERSISTENCE.md)、[会话上下文](../../architecture/SESSION_CONTEXT.md)。
5. [ReAct](../../architecture/REACT_ORCHESTRATION.md)、[流式回答](../../architecture/STREAMING_RESPONSE.md)、[JVector 检索](../../architecture/JVECTOR_RETRIEVAL.md)、[故障处理](../../architecture/ERROR_HANDLING.md)、[P0](../../architecture/P0_READINESS.md)。
6. [STATUS.md](STATUS.md)、[HANDOFF.md](HANDOFF.md)、[DEPENDENCY_REQUEST.md](DEPENDENCY_REQUEST.md)。

## 第一步与范围

确认公共基线实际存在并运行统一验证：Java25/Wrapper、Gradle Groovy、插件注册、contract 与 fixtures 可以编译/使用。先形成公共基线 PR 到 `main`，让甲乙丙可以独立开工；该成功只证明骨架检查，不证明 P0、目标 IDE 安装或真实模型完成。

可写公共契约、fixtures、`core/model/storage/bootstrap` 及对应测试、构建/注册/脚本/CI/公共文档。具体归属见 [WRITE_SCOPE.md](../../governance/WRITE_SCOPE.md)。负责人不得并发覆盖 editor/pdf/ui 和成员自己的任务记录；接入问题给具体请求和证据，成员修复后仍各自 PR 到 `main`。

## 五组实施工作

1. **公共骨架与运行边界**：维护已落地 API、fixtures、Java 包和依赖；应用服务拥有共享存储、资料 generation 和开关，项目服务协调会话，视图只订阅。公共 DTO 不暴露 Swing/PSI/DB/模型 SDK。验证 Java25、Spring AI 依赖/类加载、Swing 与 PSI；IDEA 始终只验证本机当前版本，目前 Windows build `262.10968.63`，不额外验证旧版本。本机升级后同步构建与CI。当前构建版本是候选组合，不能仅依赖树成功就宣称插件可安装。
2. **资料与检索**：托管快照先复制，再按 MD5 对字节 blob 去重；业务 document/sourceVersion 与 blob 身份分开。PDF 交乙解析，核心分块并记录 physicalPage 证据。远程 embedding 的精确模型、维度、query/document 策略形成 embeddingSpace；同维不同模型不能混用。H2 保存耐久原始 float32/片段/metadata，JVector 是派生图；generation 的图、RAVV、ordinal→chunk/source/page 映射同寿命，按 query 独立 searcher。重启从本地向量后台重建，不远程重嵌入；等待共享 future 不可因单查询取消而取消整库构建。ANN 不承诺等价全扫描，分数口径明确；失败不自动切全扫描/外部服务。替换固定 document/version/materialRevision 与发布基代次，防止并发覆盖其他资料；保留仍被历史或进行中任务引用的来源版本，未引用版本/缓存只按显式清理规则处理。
3. **Spring AI、有限 ReAct 与流式**：只有课程 RAG 和模型基础知识两类知识源，不实现 MCP/项目工具。首轮由核心保证课程检索；之后仅执行结构化完整的 `searchCourse` / `readCourseEvidence` 等已允许课程工具。一个 operation 内可有串行 attempt/branch/ModelStep，每个 ModelStep 恰有一次模型订阅；不让框架循环与自建循环重叠，完整参数后执行一次工具，保存匹配 call/result ID。每步检查含 tools/证据/代码/历史的完整预算，循环/次数/大小/时间参数实测后记录。attempt 建立时固定 corpus generation（含当时无库状态），重试新 attempt 再选最新。正文 delta、thinking、工具元数据分离；真实完成证据才成功，EOF 不等于正常结束，持久化累计前缀并正确终止，不因断流自动整请求重发。最终引用只来自最终有效步骤实际输入的固定证据；工具后检索故障要隔离旧分支，至多一次重建无课程证据的通识分支，无法确认清洁隔离就停止。
4. **会话、提交、恢复与删除**：app 共享 H2、项目/会话归属隔离；稳定 projectId 与运行 projectInstanceId 分开。项目移动/复制识别按证据处理，不静默继承/合并。捕获票据先耐久初始化再回调，代码组只读整组移除；采集只改原组、不覆盖新草稿。提交短事务按“归属/实例/LIVE → 去重命中返回原提交且不重调度 → 新请求 busy/revision/组就绪检查 → 固定输入/operation/消费原草稿”执行，不持事务跨网络。关闭项目由生命周期协调方按旧实例+operation 条件收尾，再开先协调；关闭标签不停止任务。DeleteSession 先持久 DELETING 栅栏并撤销未持久初始化票据，取消/隔离迟到写，清完该会话正文、草稿、代码、协议、摘要、日志布局及私有缓存后最终删除；共享资料仍在，不能用 upsert 复活。应用 OFF/STARTING/ON/STOPPING 与 runtimeEpoch 控制网络资格，OFF 前本地收尾，保存失败维持可见阻塞，不能宣称远端物理取消或重新 ON 即解除故障。
5. **main 主线集成与交付证据**：处理公共依赖提案并从 `dev-manager` PR 到 `main`，成员各自直达 `main`。公共基线与功能逐段集成，验证跨模块原目标/版本/删除/故障行为，再验证真实目标 IDE、插件打包和安装。区分纯 Java/fixture/孤立探针/真实插件/API 证据；English report PDF、English slides PDF 及源码/打包要求按作业最终交付计划另行落实，本次任务包不是最终作业材料。

## 必须贯穿的上下文资格

原始归档与未来输入必须分离。仅耐久成功 attempt 的最终有效 branch 中合法 assistant/tool 协议可进入后续历史；中断、停止、断流、超限、崩溃及废弃分支的正文、thinking、工具记录保持可见/归档，但不得进入任何后续模型请求、检索改写、摘要、checkpoint、缓存或重建工具协议。原始用户问题/代码仍保留并标未完成。

重试创建新 attempt，使用原问题/固定代码，不重复保存问题、不续接失败前缀；同一 turn 的某个分支成功不能使其他失败分支重新合格。当前活动 ReAct 可在自己的有效分支中使用配对工具观察。资格策略改变需使旧污染摘要/checkpoint 失效，不能只改 UI。所有输入构建路径共享同一资格规则。

## 有意义的主线验证

| 场景 | 最小观察结果 |
| --- | --- |
| 同一提交 ID 重复到达；旧 draft revision 新命令；忙碌中编辑新草稿 | 重复返回原 operation 不重订阅；新错误明确；后来新文字不被原提交清除 |
| A/B 项目与会话；项目关闭重开/复制/移动；旧 instance 回调 | 不串会话、不继承复制身份；再开先收尾；迟到回调不写新运行实例 |
| 删除与捕获/流式/工具/摘要写同时竞争 | 删除栅栏后普通写均被拒；所有会话私有内容清完才成功；无回调复活 |
| 停止/断流/超限后重试，旧输出含独特哨兵字符串 | 旧输出仍显示；下一次模型、检索 query、摘要/checkpoint/protocol 输入均无旧哨兵；新 attempt 不继续旧前缀 |
| 资料替换时既有 attempt 继续、并发更新另一资料、重启图恢复 | 原 attempt 固定旧证据；新 attempt 取最新；不丢其他变更；图恢复不远程重嵌入、不误报无库 |
| 非默认 ordinal 与实际 chunk/source/page；embeddingSpace 不同但同维 | 查询证据映射正确；空间不兼容拒绝混用；召回/分数限制如实记录 |
| 分块 stream 中正文/thinking/tool 混合、正常结束缺失/usage仅末帧 | 不误拼元数据；一个 ModelStep 一订阅；无真实正常终止不能成功；partial 为已接受前缀 |
| 工具后检索故障 / 应用停止 / 保存失败 | 旧分支隔离；通识最多一次且无旧证据；停机本地收尾与失败阻塞可见 |

先用合成控制器/模型/向量验证规则，再按单独计划验证真实能力。未验证项包括课程平台精确 base/model/embedding 维度与编码策略、流式/工具/thinking 协议、账户/额度、目标 IDE 与依赖组合。用户报告有 bge-m3/qwen3-embedding 不等于公开 alias 已确定尺寸、维度或实际账号可用；不得填假配置或自动发请求。

已有相应包测试后可用 `./gradlew.bat test --tests "edu.courseassist.core.*"` 等包过滤器缩短局部反馈；公共 contract/model/storage/bootstrap 的相关测试也按实际改动执行。PR 前仍运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All`；`Repository` 通过不能替代完整构建、检查与插件打包。

## 接入点与退出条件

提供成员实际可调用的契约、fixtures、注入和注册，保证 `scripts/validate.ps1` 本地与 CI 共用。处理各任务依赖请求时说明公共变更、迁移和同步 `main` 的位置。

负责人整个任务完成必须有核心实现、范围内场景证据、主线验证和已明确的真实能力边界；初始化完成只更新“骨架已提供”，不能把产品实现标 DONE。提交/合并均遵循实际仓库规则，PR `dev-manager → main`，保留分支。交接必须使下一位 agent 能区分候选设计、已实现能力与尚未执行的验收。

## F2个人任务入口（2026-10-25）

用户明确F2由本人全权负责；新增[F2/README](F2/README.md)、[STATUS](F2/STATUS.md)和[HANDOFF](F2/HANDOFF.md)，共G0–G6七组串行任务。此处仅接入规划入口，不把旧F0剩余状态或四人比例套给F2，也不将方案生成算成实现。

先从G0建立精确F2路径与可信门禁，推荐由用户在既有dev-manager集中交付。现行editor/ui排除范围不自动放开，不把代码放入bootstrap规避归属，不分派其他成员或创建分支。实际方案与契约提案分别见F2入口链接；产品验证仍NOT_RUN。
