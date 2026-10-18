# F0 四人并行启动包（讨论草稿）

更新：2026-10-16（Asia/Shanghai）。阶段：设计讨论，尚未创建实现工程或正式候选。权威状态为 `WORKING.json`；工时权重仍为 5:1:1:1，具体工作包仍是代理提案。

本文件细化并行所需的最小契约、样例、职责和集成顺序。插件业务源码现已确认纯 Java，字段/事件名称仍是草稿；H2/JVector 已选定，包名、构建模块、UI 库与具体版本尚未最终选定。样例见 `parallel-examples.json`，全部为合成数据，不含实际课程资料、API 地址或凭据。P0 技术前置见 `P0_READINESS.md`，按包分工、Java 签名和提交规则见 `ENGINEERING_BLUEPRINT.md`。

## 1. 并行启动的前置交付（提案）

负责人先完成一小段公共准备，不必先做完整核心：

1. 提供能编译/启动的最小插件骨架、唯一的验证入口，并确认拟用依赖能在目标 IDEA/JBR 内运行；具体兼容性仍待执行验证。
2. 在下述契约中对齐身份、片段类型、分页编号与错误边界，提供可调用的模拟入口/状态。按已确认纯 Java，在公共构建/包名对齐后落为接口代码；当前还没有主工程接口源码。
3. 明确公共定义及适配器的修改归属；每个模块可独立构建/运行其必要样例，不要求真实模型或索引已完成。

准备期间，甲可梳理选区捕获，乙可准备文字 PDF 分页样例，丙可按合成状态准备视图；真实 IDE 接入需等待插件骨架。公共接口第一版可用后，四人进入并行实现，公共准备不能扩大成“等待全部核心完成”。

## 2. 最小跨模块契约（提案）

| 契约 | 生产/消费责任 | 最小内容与规则 |
| --- | --- | --- |
| SelectionCapture → SelectionSink | 甲捕获，核心统一入口消费 | captureId、源 projectInstanceId、文件/版本、原选区及范围、所在方法全文/类结构的捕获结果；核心固定 session 并一次追加草稿，返回接受结果 |
| PdfReadRequest → PdfReadResult | 核心提供固定托管源，乙读取并返回 | documentId、sourceVersionId、可读取的固定托管快照引用；输出同一来源身份、physicalPageCount 及每页文字/状态 |
| ProjectChatViewState / SessionViewState | 核心生产，丙渲染 | 项目打开实例、会话列表、打开标签顺序、当前标签、公共提示；session 消息/草稿/操作/生命周期/回答依据状态 |
| ViewEvent | 丙发事件，核心处理 | eventId、projectInstanceId、动作、明确目标 sessionId（创建时可无），以及该动作所需的草稿版本或操作标识 |
| ViewEventResult | 核心返回，丙呈现 | 对应 eventId；已应用/拒绝/失败及明确原因，区分忙碌、草稿版本过期、操作已终止、会话已删除和保存失败；拒绝不消费草稿 |

### 2.1 选区交付

`captureId` 标识一次选取动作：同一动作重复交付可由统一入口去重，再次选择相同代码生成新 captureId 并追加新片段。partId、snapshotId 和 session 归属由核心接受时建立，甲不直接创建 session、保存聊天或调用模型。

统一入口在动作开始时固定目标 session，再编排方法/类捕获。后台结果按 captureId 回到已绑定目标，不在完成回调按“当前标签”重新选择；实现可由同一入口封装这个过程，不要求新增一套通用路由框架。若目标 session 已删除，丢弃迟到结果且不复活。捕获组未就绪或失败时不能提交半套材料；视图说明捕获中/失败，保留草稿并允许编辑，失败时提供重新捕获。正常“无所在方法”等明确不适用状态需与捕获错误区分，不据此制造空方法或悄悄删掉上下文要求。

建议跨模块选区范围采用从 1 开始的行/列，起点包含、终点不包含；列单位明确为 UTF-16 code unit，由适配器转换。该规则是接口约定提案，不依赖各 IDE API 自身的编号习惯。范围用于定位，`selectedText` 才是固定正文；源文件后来变化不替换正文。空选区不自动扩大为整文件。

用户已确认原选区之外补充所在方法全文、所属类声明和字段等结构信息。建议在同一次文件版本捕获中返回 `automaticContexts`：method 为完整方法，class-structure 为明确标记的类结构摘录，均带来源/版本与捕获状态。核心将三类内容作为关联同一 captureId 的独立片段追加到待发送区，之后编辑器变化不替换历史正文。

甲负责本地定位/捕获，负责人负责 Java PSI 依赖接入等工程公共准备、接受后的身份/存储、预算/投影和集成。类结构不默认加入其他方法/构造器实现或继承成员全文；不能因此扩展为跨文件调用图。无方法、无类、语法不完整、不支持语言、文档版本已变需明确表示，不把“未定位到”伪装为空方法；具体规则和目标平台兼容仍待验证，研究见 `evidence/method-context-research.md`。新增工作量需实测，不能假定原 1 份适配器范围没有成本变化。

### 2.2 PDF 分页交付

乙只读负责人提供的固定托管快照，不在后续阶段回读外部原文件。sourceVersionId 及源 hash 由核心确定，乙不自行修改业务版本。

物理页从 1 开始，每一页都有记录；正常有文字与无文字页分别表示。无文字页不能被删掉后重编号，也不因此触发 OCR。解析异常保留原始原因向上交付，不返回遗漏失败页的“成功分页结果”；实际使用的提示/降级由统一任务边界处理。

分页输出不承担分块、跨页合并、嵌入、向量检索或引用渲染。印刷页标签如能取得可作为可选信息，但不替代 physicalPage。

### 2.3 界面状态与事件

界面以 Markdown 为主渲染 text，code-selection 及建议的 code-context（method / class-structure）继续按独立 schema 展示来源。Markdown 围栏/缩进代码与 typed 代码共用 IDEA 多色高亮并跟随当前编辑器配色；代码不先拼成 Markdown 重解析。历史正文来自保存快照，待发送区来自该 session 草稿；核心给出的可发送/忙碌状态由丙呈现，服务入口仍再次校验，不能把正确性寄托于按钮置灰。渲染路线见 `ENGINEERING_BLUEPRINT.md` 第 8 节，尚未运行验证。

SessionViewState 至少还应给出状态版本、草稿保存状态、活跃操作及独立终止结果、可用/删除中/删除未完成的生命周期、可执行动作。每个回答/轮次视图携带 requestId/turnId、完成或未完成状态、answerMode、retrievalStatus、实际课程引用及长期可见的依据说明。已成功删除的 session 从状态与列表消失，不保存可恢复软档案。

第一版流式显示已确认。核心发布 attempt 的累计 Markdown/内容版本与生成/保存状态，丙不直接订阅模型或逐 chunk 自行拼接。显示允许同一活跃 attempt 的可靠递增前缀；终止/新尝试/配色/视图变化需防止旧回调覆盖。每个 ModelStep 唯一订阅、结束证据与部分保存契约见 `STREAMING_RESPONSE.md`。

ProjectChatViewState 的公共提示关联原 session/操作，用于关闭标签后的后台故障；删除成功后清理对应提示与重开入口。全部标签关闭时 activeSessionId 为空，不把后台 session 当成当前标签。

| 事件建议名 | 主要输入 | 必须保持的行为 |
| --- | --- | --- |
| CreateSession / SelectSession | 项目实例；已有会话的 sessionId | 核心管理身份与当前标签；界面不维护第二套全局活动会话 |
| UpdateDraftText | sessionId、draftId、expectedDraftRevision、正文 | 更新本 session 草稿；与提交/追加的顺序由核心协调 |
| SubmitDraft | sessionId、draftId、expectedDraftRevision | 核心先取得单任务执行权再消费提交；忙时保留草稿，不排队/自动发送 |
| CloseSessionTab | sessionId | 关闭视图，任务继续，记录保留 |
| OpenSessionTab | sessionId | 从列表重开已有会话，不重新发出模型请求 |
| DeleteSession | sessionId | 独立删除动作，清除全部该 session 记录；不等同关闭标签 |
| StopOperation / ManualCompact | 目标 session；停止时带具体操作标识 | 当前为入口提案，具体取消交互尚待细化；手动整理遵守单任务规则且不提交草稿 |

用户已确认草稿代码快照只读，可将同次捕获的原选区/方法/类结构整组移除并重新捕获。新增 RemoveDraftCapture 事件带 captureId、draftId 与 expectedDraftRevision；移除撤销待捕获组回写资格，迟到结果不加回，已提交历史不动。后台捕获完成只补齐原组，不能因期间文字编辑改变草稿 revision 就误拒绝，也不能覆盖新文字。具体规则见 `ENGINEERING_BLUEPRINT.md` 第 3 节。

正常无相关资料、未建课程库均自动通识回答并分别标注；这两者是正常业务状态。检索故障后的实际使用行为是先提示，再自动转为明确标记的通识回答；开发时暴露原错误，三种原因不合并。

## 3. 修改归属与并行交付（提案）

| 工作包 | 可独立修改/交付 | 需要协调的公共部分 |
| --- | --- | --- |
| 你，5 份 | 核心编排、会话/草稿/存储/删除、源版本/分块/索引、模型/检索/引用/压缩、集成 | 维护公共契约、工程构建、插件注册、依赖版本和公共样例 |
| 甲，1 份 | 选区/所在方法/类结构的本地捕获适配器、所属样例及接入说明 | 通过 SelectionSink 接入；Java PSI 工程依赖、公共 DTO/入口和预算规则由负责人协调；新增捕获范围需实测估时 |
| 乙，1 份 | PDF 分页适配器、文字/空白页/解析异常样例及资源释放验证 | 消费固定托管源；新增 PDF 依赖和公共分页结构由负责人协调 |
| 丙，1 份 | Tool Window 视图、标签/消息/草稿渲染、界面事件与样例 | 模型、任务、持久化、删除由核心处理；公共状态结构变更先协调 |

这是模块职责，不是当前目录的硬性权限配置。实际包/目录在插件骨架建立后映射；当前不创建 AGENTS、Git 分支或工作树。四个 AI coding 会话不应同时覆盖公共契约、构建文件或插件注册；各自交付适配器变更，由负责人集成公共变更。

每份交付至少包含：入口/输出说明、可重复的样例结果、相关异常如何上交，以及可供集成的模块变更。模型网关尚未验证不阻止甲/乙/丙对模拟契约工作，但不据此宣称真实问答已通过。

## 4. 集成顺序与完成证据（提案）

| 检查点 | 并行工作 | 通过证据 |
| --- | --- | --- |
| P0 公共准备 | 负责人建立最小骨架/契约/模拟入口；三人准备各自样例 | 插件能启动，公共契约可被三个适配器调用；实际平台兼容检查已执行 |
| P1 模拟交互 | 甲接选区及自动上下文、丙接视图；乙输出分页；负责人接会话/草稿 | 两个项目不串目标，两次选取保留独立身份及三类快照；中间空白页不改页码；忙时不消费草稿；回答依据标记可持续呈现 |
| P2 真实课程证据 | 负责人接入乙的输出、真实嵌入/索引/检索；甲丙继续完善模块 | 回答能回查固定文档版本/物理页，真实网关契约得到验证；故障降级有明确标记 |
| P3 完整会话闭环 | 负责人完成保存/恢复/压缩/删除，丙接状态与事件，甲乙配合回归 | 关闭标签后台继续、重开不重复调用；重启恢复；删除全清且迟到写入不复活；开发/实际使用异常策略分别验证 |

关键路径仍经过负责人的平台兼容、核心数据链和最终集成；5:1:1:1 不是四人同时完工的保证。当前无绝对工时或日期估算，不把模拟数据校验当作真实 IDE/模型测试。

## 5. 本轮样例能证明与不能证明的内容

`parallel-examples.json` 给出相同代码再次选择、三页中间空白、忙时保留草稿、关闭/重开、删除和检索故障的合成状态/预期。其 formatVersion 只用于样例格式，不是架构版本。可用这些数据制作模拟适配器和联调场景；当前未生成真实 PDF 文件或可运行的 mock 服务，也未验证实际解析库、IDE UI、存储和 API。

字段、接口及样例可以先供团队审阅，具体代码在技术选择与工程骨架就绪后再落地。摘要模型、Token 预算、失败参数继续留给上下文压缩任务执行成员确认，本启动包不填入默认数值。

## 6. 已采纳 ReAct 的公共准备

主流程和字段提案见 `REACT_ORCHESTRATION.md`。负责人提供含 branchId/stepId/toolCallId 的模拟状态：无工具直接回答、一次/多次补查、合法工具步骤结束、最终步骤结束、超限/取消和旧分支迟到结果。丙只消费核心状态，切换步骤预览不能将中间文字拼入最终答案；甲乙适配器职责不变。真实模型工具能力尚未验证不阻止适配器按模拟契约并行准备。

## 7. 最新行为对公共契约的影响

user-turn-032 的确认及提案细节分别见 `F0_BEHAVIOR.md`、`COURSE_LIBRARY.md`。负责人提供工作开停/配置版本、过程折叠区、原始档案与输入资格、MD5/资料视图所选 documentId/预期版本的模拟字段。丙保留旧 attempt 渲染并独立显示新重试块，不直接决定哪些历史进入模型；甲乙仍只提供既定快照与分页。停用/删除/中断及上下文过滤在核心复验，不能仅靠按钮状态。

## 8. 应用级公共契约补齐（实现提案）

ApplicationController.dispatch(ApplicationEvent) / observe(ApplicationStateListener) 是应用级入口，不要求 sessionId；SessionController 继续只管项目/session 聊天。两者均为纯 Java 端口提案，尚未编译交付。

| 契约 | 最小字段/作用 |
| --- | --- |
| ApplicationViewState | 状态版本、OFF/STARTING/ON/STOPPING、configRevision/runtimeEpoch、非秘密 profile、资料行、全局故障/未保存标记；不暴露 key |
| EnablePlugin / DisablePlugin | commandId、expectedAppStateRevision；核心校验状态，失效与本地收尾规则见 F0_BEHAVIOR.md 第 6 节 |
| UpdateProviderConfig | commandId、expectedConfigRevision、非秘密设置和受控凭据更新；仅 OFF 接受，不将 key 写入视图/日志 |
| ImportCoursePdf | commandId、文件选择输入、预期库基准；固定副本/MD5与版本状态由核心建立 |
| ReplaceCourseMaterial | commandId、documentId、expectedSourceVersionId、expectedMaterialRevision、所选新文件输入、库基准 |
| RemoveCourseMaterial | commandId、documentId、expectedSourceVersionId、expectedMaterialRevision、库基准 |

SelectCourseMaterial 是资料视图本地选择，不自动产生资料写入；应用动作开始固定目标。更换/移除/导入不挂到当前 session，课件共享结果从 ApplicationViewState 发布。公共拒绝/失败结果保留 commandId 和对应状态版本，不能把异常包装成普通成功。具体 DTO/凭据所有权由负责人在 P0 建立，丙只发事件/呈现状态。
