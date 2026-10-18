# 丙：Tool Window 与状态驱动界面

固定分支 `dev-c`，直接 PR 到 `main`。本任务实现会话、草稿代码组、流式回答、共享资料和应用配置的 Swing 界面。界面通过公共控制器接收状态、发送事件；先用合成 fixtures 完成可观察视图，再接真实核心。

## 可复制给 agent 的提示

```text
你负责 dev-c UI 任务。按本 README 顺序读文件，核对已有 dev-c 分支。
仅修改 ui 实现/测试、src/test/resources/ui/**、spec/tasks/dev-c/**；使用 Java/edu.courseassist。
从 main 同步公共 contract/fixtures，使用 Swing；不修改 build/plugin.xml/contract/fixtures，不直连模型、H2或PSI。
实现会话/草稿只读代码组/流式回答/共享资料/应用配置；状态驱动，异步结果检查版本与目标后才应用。
关闭标签只释放视图，删除会话是独立事件；重试和中断保留旧块并标状态，不覆盖历史。
真实代码多色高亮与Markdown安全渲染须分别验证；fixture不能证明数据库或真实模型端到端成功。
缺少动作/字段/依赖写 DEPENDENCY_REQUEST，记录实际验证；PR 直接 main，保留 dev-c。
```

## 按序阅读

1. 根 `AGENTS.md` → [CURRENT](../../progress/CURRENT.md) → [BASELINE](../../architecture/BASELINE.md) / 开发计划，再依根入口读治理、当前任务 README / [STATUS](STATUS.md) / [HANDOFF](HANDOFF.md)。治理包括 [写入范围](../../governance/WRITE_SCOPE.md)、[工作流](../../governance/WORKFLOW.md)、[分支规则](../../governance/BRANCH_POLICY.md)、[验证规则](../../governance/VALIDATION.md)。
2. [开发计划](../../architecture/F0_DEVELOPMENT_PLAN.md)、[并行接入](../../architecture/PARALLEL_START.md)、[分工](../../architecture/TASK_ALLOCATION.md)。
3. [公共 API](../../contracts/API.md)、`ChatContracts` / `ApplicationContracts`、`SessionController` / `ApplicationController` / `CredentialUpdateSink`、公共 fixtures 当前实现。
4. [行为](../../architecture/F0_BEHAVIOR.md)、[会话上下文](../../architecture/SESSION_CONTEXT.md)、[资料库](../../architecture/COURSE_LIBRARY.md)、[流式回答](../../architecture/STREAMING_RESPONSE.md)、[ReAct](../../architecture/REACT_ORCHESTRATION.md) 中显示与事件边界。
5. [STATUS.md](STATUS.md)、[HANDOFF.md](HANDOFF.md)、[DEPENDENCY_REQUEST.md](DEPENDENCY_REQUEST.md)。

## 第一步与范围

运行统一验证并记录基线。用 `SyntheticStates.project()/application()` 与 `SyntheticControllers.session(state)/application(state)` 渲染一个包含草稿代码组、流式正文和共享资料列表的视图，发送一个事件并检查被假控制器记录。假控制器不模拟持久化或模型，此场景只能证明视图/契约接入。

可写：`src/main/java/edu/courseassist/ui/**`、`src/test/java/edu/courseassist/ui/**`、`src/test/resources/ui/**`、`spec/tasks/dev-c/**`。负责人负责 ToolWindow/服务注册、依赖注入、持久化、模型、真实事件处理与应用生命周期；丙不得编辑插件 XML 或公共类型。commonmark/GFM 表格已预配，增加渲染依赖先提案。

## 实施步骤

1. 用状态渲染项目会话列表、标签顺序、当前会话、草稿文字与类型化代码组。代码组显示原选区/方法/类结构且只读，提供整组移除及重新捕获说明，不提供正文编辑框。用户自己的 Markdown 和类型化代码来源分开保存/渲染，不能先拼成 Markdown 再反解为附件。
2. 通过 `SessionController.dispatch` 发提交、停止、重试、关闭、删除等事件，通过 `observe` 订阅项目状态。忙碌时草稿仍可编辑/移除代码组；发送和手动整理等动作按核心状态禁用，不排队偷发。处理版本确认，不能用旧状态清掉尚未确认的新输入。
3. 显示核心提供的累计流式 Markdown，不直接订阅模型或组装 SDK delta。未闭合代码围栏按当前源文渲染，不修改/补写存储正文。分开显示推理/工具过程（有内容时可折叠小字）与最终正文，不伪造“思考”，不把中间预览拼为答案。
4. 重试产生独立 attempt/分支/步骤块。停止、断流、超限、失败分支保留已显示正文与过程并标记无效/未完成，不能清屏或覆盖旧块。后续模型输入资格归核心，界面只显示状态；不因旧块可见就再次发送其文本。
5. 正确区分关闭标签与删除会话：关闭释放订阅和视图、生成继续；重开从控制器读状态、不触发模型。删除发送独立事件，显示删除中/失败/已完成状态，不以标签消失冒充数据已清理。渲染完成应用时再次检查 projectInstance、会话/attempt、视图存活、状态版本、渲染轮次与配色代次，忽略迟到旧结果。
6. 用户 Markdown 支持正常段落/列表/表格/代码；转为受控 HTML 时转义原生 HTML 和代码，禁止脚本、代码执行、自动加载远程图片。拷贝以原始 Markdown/代码快照为准。代码展示使用 IDEA Java 多色语法高亮并随配色变化重建；未知语言可纯文本，不能把 Java 高亮默默降级当完成。
7. 通过 `ApplicationController` 实现独立共享资料视图：导入、查看索引状态、替换、移除；动作开始固定 documentId、expectedSourceVersionId/materialRevision，异步后选择另一行不能重定向。显示资料名与真实物理页引用；引用身份来自核心证据，不能从回答 Markdown 猜出处。
8. 显示 OFF/STARTING/ON/STOPPING。仅 OFF 可改配置；密钥按 `CredentialUpdateSink.stage` 短期字符载体交付 opaqueRef，清理临时副本，不在 state/日志/fixture 中存 key。关闭/启用由应用事件处理，UI 不直接测试真实 API。完成后释放订阅、编辑器资源和配色监听。

## 有意义的验证

| 场景 | 需要观察的结果 | 证据要求 |
| --- | --- | --- |
| A/B 会话及两个项目各自有草稿 | 切换不串内容，旧状态不清掉未确认输入 | controller fixture 可测事件/投影；耐久恢复由核心验 |
| 草稿代码组只读、整组移除；采集中移除 | 发送正确组身份，迟到状态不复活已移除显示 | 不直接更改核心数据 |
| 关闭生成中标签再重开；删除同一会话 | Close 与 Delete 事件不同；关闭释放视图，重开不产生网络请求 | 假控制器仅能测无额外模型动作事件 |
| 多次流更新、未闭合围栏、旧渲染迟到/换配色/视图释放 | 源文原样，较旧版本不覆盖；已释放视图不更新 | 单元/组件测试 + 真实 IDEA 释放验证 |
| 停止/断流/重试/通识降级 | 旧块仍可见且标未完成/失效；新 attempt/branch 分开；过程不混入 final | 合成序列可测显示，真实模型语义由核心验 |
| HTML/script/远程图、列表表格、Java 代码 | 受控渲染无执行/远程加载；Java 多色和配色随动；复制原文不变 | 高亮与IDE配色须真实 IDEA，截图不能替代安全断言 |
| 资料 A 开始替换后选 B；资料事件无活动会话 | 仍提交 A 固定身份/版本；应用事件不附会话目标 | 检查记录事件，不宣称资料已入库 |
| 应用 ON 时尝试编辑配置、OFF 时更新密钥 | 状态限制明确，key 不进入 state/事件日志；只交付 opaqueRef | 核心二次校验由负责人负责 |

fixtures 的 RUNNING_TOOLS、COMPLETED、INTERRUPTED、UNSAVED、DELETING 可用于不同视图场景；用 `publish(next)` 推动合成状态，`recordedEvents()` 检查事件。dispatch 明确返回 NOT_SUPPORTED，不能按 APPLIED 假定真实命令成功。观察回调不保证 EDT，UI 自行切换并在应用时复验版本/存活。

已有本包测试后可用 `./gradlew.bat test --tests "edu.courseassist.ui.*"` 获得局部反馈；PR 前仍执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All`，记录退出码和实际测试范围。

## 接入点与退出条件

对外使用 [API.md](../../contracts/API.md) 及现有控制器/fixtures。需要新状态、错误提示或组件注册时提最小依赖请求，不能直接访问 core/storage/model 或在 UI 复制状态机。复制/引用按钮是否能够打开原版本由核心证据接口支持，缺口如实记录。

退出条件：本任务视图和事件行为已实现；关闭/删除、版本迟到、未确认草稿、代码只读、旧 attempt 留存、Markdown 安全和高亮分别有证据；统一验证结果准确记录。真实目标 IDEA 或完整核心未接入的场景保持 `NOT_RUN`/待接入，不以 headless fixture 宣称插件端到端完成。PR 直接 `dev-c → main`，保留分支。
