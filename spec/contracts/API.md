# F0 公共契约 API v1

状态：2026-10-18 初始化实现基线。业务包前缀 `edu.courseassist`；公共契约是 `src/main/java/edu/courseassist/contract/` 的实际 Java 签名。本文不能替代已确认需求或证明真实 IDE、PDF、存储、模型已经运行。架构依据为 [综合开发方案](../architecture/F0_DEVELOPMENT_PLAN.md)、[并行蓝图](../architecture/ENGINEERING_BLUEPRINT.md)、[开停与输入资格](../architecture/F0_BEHAVIOR.md)。

契约采用 Java 17 可表达的 records/sealed interfaces，最终项目 Java 25。契约不依赖 IDEA、Swing、Spring、Reactor、H2 或 PDF 库。集合构造时复制为不可变集合；身份为不透明 `String`，版本为非负 `long`，缺失值为 `Optional`，不使用 null 表示业务状态。业务相关版本仍须核心在同一提交边界校验，构造器检查不替代事务。

## 1. 固定公开入口与负责人

| 入口 | 签名 | 所有者 |
| --- | --- | --- |
| `SelectionSink` | `CaptureTicket beginCapture(SelectionStart start)` | manager 实现，dev-a 调用 |
| `CodeCapturePort` | `CompletionStage<CapturedCodeGroup> capture(SelectionStart start)` | dev-a 实现，核心调用 |
| `PdfReader` | `PdfReadResult read(PdfReadRequest request) throws IOException` | dev-b 实现，核心后台调用 |
| `SessionController` | `CompletionStage<CommandResult> dispatch(ViewEvent event)` | manager 实现，dev-c 调用 |
| `SessionController` | `ViewSubscription observe(String projectInstanceId, Consumer<ProjectChatViewState> listener)` | manager 实现，dev-c 观察 |
| `ApplicationController` | `CompletionStage<CommandResult> dispatch(ApplicationEvent event)` | manager 实现，dev-c 调用 |
| `ApplicationController` | `ViewSubscription observe(Consumer<ApplicationViewState> listener)` | manager 实现，dev-c 观察 |
| `CredentialUpdateSink` | `CompletionStage<String> stage(CredentialUpdate update)` | manager 实现，dev-c 凭据表单调用 |

`CaptureContracts`、`PdfContracts`、`ChatContracts`、`ApplicationContracts`、`CommonContracts` 中保存嵌套 DTO/枚举。成员不再声明第二套同名接口。变更公共契约、构建或装配需交 manager 协调。

`SelectionSink` 仅做快速内存路由与调度：动作开始固定源项目当前标签，无会话则预留新身份，立即返回 ticket；不能在 EDT 等待数据库/网络/PDF/耗时 PSI。ticket 不是持久化成功证明。manager 先合法初始化持久组，再调用 `CodeCapturePort`，最后仅补齐原组；成员完成回调不能重读当前标签或自行建立 session。

`PdfReader` 有意同步：适配器负责解析与释放资源，核心负责后台线程和任务边界。其他异步端口 `CompletionStage` 异常完成须保留原始原因；没有万能 catch、空成功或自动换服务。取消 stage 本身不证明底层平台任务已取消，任务失效/取消句柄仍由装配与核心实现。

## 2. dev-a：捕获与同版本边界

`SelectionStart(captureId, projectInstanceId, source, selectedText, selection)` 必须在动作开始固定。`FileSnapshotRef(fileRef, documentVersion, languageId)` 使用文件稳定引用与文档 modification stamp，不传 PSI/Document 对象。行/列从 1 开始，列单位 UTF-16 code unit，起点包含、终点排除；正文精确保留，不 trim。

`CaptureTicket(captureId, projectInstanceId, targetSessionId, draftId, source, reservationRevision)` 由核心分配并固定路由。`CapturedCodeGroup(start, automaticContexts)` 必须返回原始 `SelectionStart` 和同一版本的两个结果，分别为 `METHOD` 与 `CLASS_STRUCTURE`。方法是完整方法；类结构是声明/字段等摘录，不包含其他方法实现或跨文件调用图。

`AutomaticContext` 为 `CAPTURED` 时包含正文和范围；为 `NOT_APPLICABLE` 时只有明确原因。版本变化、解析/平台读取失败通过异常上交，不伪装正常无方法。跨边界、非 Java、语法不完整等场景须在 dev-a 任务中明确并验证。相同文本的新选取仍分配新 captureId；同动作重复交付才可去重。

捕获组先进入待发送区，整体只读可移除。后台完成只补齐原组，不覆盖期间新增草稿文字，不按开始时 draft revision 粗暴拒绝合法完成。移除/删除/项目关闭撤销写入权；manager 执行这些状态规则，dev-a 不直接写数据库。

## 3. dev-b：固定 PDF 分页边界

`PdfReadRequest(documentId, sourceVersionId, managedSnapshot, contentMd5)` 由核心提供。路径必须为绝对托管快照路径；MD5 是该固定源的业务排重 hash，不是让读取器回读原路径或自建版本。适配器不修改托管文件、不调用嵌入、不分块、不建索引、不执行 OCR。

`PdfReadResult(source, physicalPageCount, pages)` 原样保留来源身份；每个物理页均有 `PdfPage(physicalPage, text, status, printedLabel)`。页从 1 连续编号，中间无文字页保留 `EMPTY`，可保留解析器产生的空白字符，不能删除页或重排。`printedLabel` 可选，不替代物理页身份。任一页读取失败使整次读取失败；不得把少页结果当成功。IO/解析异常保留原始 cause，资源在成功、失败路径均释放。

## 4. dev-c：session 视图、事件与流式显示

`ProjectChatViewState` 包含稳定 projectId、本次 projectInstanceId、总 revision、session 列表、打开标签顺序、当前标签和公共提示。全部标签关闭时 `activeSessionId` 为空；关闭标签不删除 session、不取消模型，不把后台 session 当当前标签。`observe` 立即发布当前完整快照，再发布累计快照；回调没有 EDT 保证。`ViewSubscription.close()` 不抛 checked 异常，仅解除观察，不停止业务任务。UI 仍检查视图实例/最新版本与生命周期，不能在组件关闭后应用排队旧回调。

`SessionViewState` 包含 lifecycle、草稿、轮次、活跃操作、writesBlocked、可执行动作和提示。`DraftView` 含 id/revision/text/捕获组/saveState。`DraftCapture` 明确 `PENDING/READY/FAILED/RECAPTURE_REQUIRED`，仅 READY 可有完整代码组。

`TurnView` 保留唯一用户输入和多个尝试；`AttemptView` 有独立 attemptId/operationId/viewRevision、状态、保存状态、输入资格、有效 branch 与最终 step。`BranchView` 包含依据状态和有序步骤；`ModelStepView` 提供该步累计 Markdown、contentRevision、attempt 内 throughSeq、真实 thinking 字段、工具过程、结束原因和可得用量。UI 不直接订阅模型，不把多个 step 的正文拼成最终回答。工具步骤及 thinking 放独立可折叠小字区；无 thinking 时不编造。

正文 `TextPart` 为原始 Markdown；`CodePart` 为独立 typed schema，包含固定来源/范围/正文，直接送 IDEA 多色高亮，不先拼入 Markdown。HTML、配色和渲染缓存不回写 source。流式未闭合围栏允许渲染已有内容，但不在档案中补结束符。

`Citation(evidenceId, documentId, sourceVersionId, materialName, firstPhysicalPage, lastPhysicalPage)` 固定导入版本。展示“资料名称＋PDF物理页码/范围”，点击发 `OpenCitation`，由核心按固定来源定位；普通 Markdown 链接不能伪造课程证据。

### 事件规则

每个 `ViewEvent` 带 `EventMeta(commandId, projectInstanceId, sessionId)`。除创建/项目布局命令外必须显式 sessionId；不能由完成时当前标签推导目标。

| 事件 | 额外输入 | 核心复验规则 |
| --- | --- | --- |
| `CreateSession` | title；sessionId为空 | 核心建立身份 |
| `SelectSession/OpenSessionTab/CloseSessionTab/DeleteSession` | 明确 sessionId | 开关标签与删除分开；重开不发网络 |
| `UpdateDraftText` | draftId、expectedDraftRevision、text | 只更新文字，不回写旧代码列表 |
| `SubmitDraft` | draftId、expectedDraftRevision | 精确版本、捕获完整、单任务、去重、提交后才调模型；忙时保留，不排队 |
| `RemoveDraftCapture` | draftId、expectedDraftRevision、captureId | 整组移除，撤销迟到完成权 |
| `StopOperation` | operationId | 停止指定操作；保留已显示内容 |
| `RetryAttempt` | turnId、previousAttemptId | 新 attempt，从原输入开始，不复制用户消息/续写旧部分正文 |
| `ManualCompact` | sessionId | 单任务入口，不提交草稿；参数继续由实施成员确定 |
| `ReorderTabs` | orderedSessionIds | 仅改变项目标签布局 |
| `OpenCitation` | Citation | 固定版本来源定位 |

`CommandResult` 对应 commandId，区分 APPLIED/REJECTED 及明确原因，如 BUSY、STALE_REVISION、SESSION_DELETED、UNSAVED_STATE；带结果时的 stateRevision 和可得 operationId。异常通过 stage 传播，不伪装拒绝或普通成功。允许动作只是显示状态，服务入口仍必须复验。

停止/重试/超限/中断保留原 attempt 的生成内容。`ContextEligibility.ELIGIBLE_DURABLE_SUCCESS` 只表示已耐久成功的最终有效分支；未完成/未保存/失效生成内容不可进入后续聊天、检索查询、摘要、checkpoint、协议缓存。原始用户问题/已提交代码仍按历史规则使用。UI 不决定上下文投影资格。

## 5. 应用、资料动作与凭据

`ApplicationViewState` 包含 revision、OFF/STARTING/ON/STOPPING、configRevision/runtimeEpoch、非秘密 profiles、课程库状态/基准、资料行及提示。`CorpusState.RESTORING` 与 EMPTY 不同；`CorpusBase` 的可空 generationId 明确表达未建库。每个 attempt 在创建时固定库基准，后续工具仍用原基准。

`ApplicationEventMeta` 带 commandId 与 expectedAppStateRevision。启用/停用通过应用入口，不要求当前 session。`UpdateProviderConfig` 带 expectedConfigRevision、非秘密 generation/embedding 配置，以及可选 credentialUpdateRef，只有 OFF 可接受；服务地址不得包含凭据/query。准确 provider/modelId/维度来自实际配置和验证，合成样例不替产品选值。

资料本地选中行不是共享写入。`ImportCoursePdf` 带选取文件和预期库基准；`ReplaceCourseMaterial/RemoveCourseMaterial` 带动作开始冻结的 `MaterialTarget(documentId, expectedSourceVersionId, expectedMaterialRevision)` 与库基准。之后改变当前行不会改变既发命令。核心复制托管、MD5排重、准备新版本并原子发布；读取器与 UI 不实现这些发布规则。没有 chapter 替换约定。

原始 key 只通过受控 `CredentialUpdateSink`：

1. UI 将密码字段读取为 char[]，构造短期 `CredentialUpdate.Change`（GENERATION/EMBEDDING，REPLACE/REMOVE），原数组立即清零。
2. `stage(update)` 只暂存凭据，返回单次消费 opaque ref，不启用或改配置；成功/异常完成后 UI 关闭 update 清零内部数组。
3. UI 把 ref 放入 `UpdateProviderConfig`。manager 消费时再次校验 OFF/版本；拒绝、超时或消费后清理暂存凭据。底层持久保存由后续 PasswordSafe 适配实现。

未提交 slot 表示保持原凭据。`CredentialUpdate` 是唯一刻意短生命周期且可清除的载体，不是不可变视图/持久 DTO；自定义 toString 脱敏。禁止记录载体、密码数组、凭据 ref 或命令对象全量 dump；ref 不是普通配置，不能写入聊天或状态。stage 复制后所得数组仍须接收方清零；Java内存清除是尽力处理，不作零内存残留保证。

停用先拒绝新工作、撤销旧 runtimeEpoch，再协调旧任务终止与保存；不无限等待远端物理取消。保存失败的 session 保留未保存显示和发送阻塞，再次 ON 不能绕过，不用新 key 续跑旧任务。

## 6. 可调用合成夹具与验证

`src/testFixtures/java/edu/courseassist/fixtures/` 不是产品实现，也不打包为真实核心：

- `SyntheticStates.selection()/codeGroup()`：同版本原选区/方法/类结构。
- `SyntheticStates.project()` 或 `project(Scenario)`：RUNNING_TOOLS、COMPLETED、INTERRUPTED、UNSAVED、DELETING 五种累计状态，双 session/独立草稿/未闭合围栏/过程与固定版本来源。
- `SyntheticStates.application()`：停用、非秘密合成 profiles、共享资料行；endpoint 为 `example.invalid`，不会调用真实 API。
- `SyntheticStates.threePagePdf(request)`：1/2/3物理页，中间无文字；不读取路径、不证明PDF解析。
- `SyntheticControllers.session(state)/application(state)`：观察入口与命令记录器。dispatch 刻意返回 NOT_SUPPORTED，绝不伪装发送/保存/删除成功。测试用 `publish(next)` 驱动状态、`recordedEvents()` 核对事件；不当作真实模型/session核心交付。
- `FixtureSmoke.main`：可在无第三方依赖下执行的夹具检查，需 `-ea`，检查观察释放、事件交付、中断与耐久资格、物理空页和凭据脱敏。

三个适配器成员可立即消费这些签名和状态；真实 manager 服务/IDE装配、持久化、网络、检索、压缩与目标兼容仍由后续任务完成。摘要模型、Token预算、失败数值没有在契约中擅自填默认。

## 7. 第一组运行骨架接入

`bootstrap.CourseAssistantApplication.getInstance()` 是同一 IDE 进程的应用服务；`bootstrap.CourseAssistantProject.getInstance(project)` 是项目服务。项目服务提供本次打开的 `projectInstanceId()`，不把这个值当耐久 `projectId`。应用拥有 H2 短事务入口、后台执行器、运行状态/epoch；项目关闭只撤销所属 TaskScope，应用释放时撤销全部项目 scope。TaskScope 撤销回包资格，不中断 JDBC，也不能替代后续业务事务中的项目/session/operation 条件校验。

当前服务是资源与生命周期骨架，尚未实现 `SessionController` / `ApplicationController` 的业务处理或持久项目身份。UI 继续使用第 6 节的明确合成控制器；不得把空视图或合成控制器作为生产恢复结果。开停 `RuntimeGate` 是 manager 内部状态边界，配置/凭据校验、保存协调与公共事件接线留给第 4 组。

已声明两个类型化 extension point，供负责人在成员实现就绪后集中注册：

```xml
<extensions defaultExtensionNs="edu.courseassist.f0">
    <codeCapture implementation="成员提供的 CodeCapturePort 实现全名"/>
    <pdfReader implementation="成员提供的 PdfReader 实现全名"/>
</extensions>
```

核心经项目服务 `codeCapture()`、应用服务 `pdfReader()` 取得端口。未注册或重复注册会明确失败，不使用反射猜实现、不注入成功空对象；当前基线无成员实现，两个入口预期不可调用。`plugin.xml` 注册仍由负责人处理；成员只提供实际类名与构造需求。公共 DTO 和端口继续不依赖 IDEA/Swing/JDBC/SDK。

## 8. 第二组资料核心接入

应用服务新增惰性 `courses()`，返回 manager 内部 `core.corpus.CourseLibrary`。先后台 `initialize()`；`snapshot()` 显示库/资料/作业状态；importPdf/replacePdf/remove/reindex 返回可取消 Job，结果与后台收尾分别通过 completion/settled 观察。所选资料和库基准从动作开始固定的 MaterialTarget/CorpusBase 传入；晚到结果不能改成当前界面选中项。

每个 attempt 初始化后只调用一次 `openAttempt()` 并持有 Lease 至任务终止；lease 的 search/read 固定原代次，无库也固定，不重新读取活动库。搜索传入准确空间匹配的 CourseEmbeddings、显式 SearchSettings 和冻结 epoch 的资格检查；错误原因为异常，正常无命中为空列表。一个等待者取消不取消全应用图恢复。lease 需 close，不能缓存裸 GenerationHandle 或把 ordinal 跨代次使用。

原始 H2 向量、托管 PDF 及 sourceVersion 不依赖当前索引生存。未来回答提交方需在 lease 释放前为实际来源调用 retainSources(owner, sourceIds)，删除私有记录时 releaseReferences(owner)；这只是共享来源保留入口，尚不是回答/证据/状态共同提交或 session 删除实现。历史引用通过固定 sourceVersionId 查询 sourcePath。清理先 previewCleanup，再显式 cleanup，并在事务内重新检查引用/活动/占用；文件删除失败保留可继续的 blob 身份。

公共 UI/PDF DTO 签名未改；成员无需导入 manager 内部 corpus 类型。生产 ApplicationController 的事件/状态装配、凭据与运行 epoch 配置仍待第 4 组；本组不注册替代成员解析器，不给合成控制器增加假成功。实现、参数及真实验证边界见 [第二组记录](../tasks/dev-manager/WORK_GROUP_2.md)。

## 9. 第三组问答核心接入

manager 内部 `core.react.ReactEngine` 接受冻结 Identity/Input、同 session 已归档历史、attempt 创建时取得的 CourseTools lease、显式 Limits/ErrorPolicy/Budget、运行资格、Journal 和累计 Snapshot 观察者。调用前由第四组完成用户提交与活跃槽，不能把本入口当作草稿提交。每个 step 唯一模型订阅，核心首检和后续课程工具共用原代次；`ReactViews.attempt(snapshot)` 转为既有公共 AttemptView，无新 UI SDK 依赖。

`Job.cancel()` 返回是否接受停止：true 表示停止赢得原子决策，之后不可能耐久成功；false 表示终态保存已开始或已经终止，界面不能宣称已停止。它不等待远端确认或数据库；epoch/session 失效仍由事务内 guard 决定。`completion()` 为隔离取消的观察 future；异常保留原始 cause，累计 snapshot 仍可显示。PRODUCT 检索故障和视图投递异常分别经 retrievalFailures/observerFailures 交任务边界，不写全量输入或凭据日志。

`model.SpringAiCourseModel` 使用固定 Spring AI 请求类型，显式配置 DeepSeek 风格 SSE、模型、thinking/usage 和 HTTP 客户端，无隐式工具循环或重试。真实 DeepSeek/课程网关能力尚未验证；构造不发送请求。正文、thinking、完整工具请求、终止原因与 usage 分离，正常结束、非空最终正文、实际引用和耐久提交共同成立才成功。

`storage.ReactRepository` 需先初始化共享 corpus schema，并提供必需的事务内 Guard；CREATE/LIVE_WRITE/COORDINATOR_TERMINAL/READ/DELETE 均复验目标。运行失效的 guard 应抛 CancellationException，IO/SQL 保存失败则保留原错并阻塞依赖该提交的发送。正文/证据/状态与共享来源保留同事务保存；interrupt 不启动网络；session 删除入口清该组档案/来源引用且不清共享资料。本轮没有生产 no-op guard 或完整 session 控制器。

后续请求、检索改写、摘要/checkpoint 必须共用 ReactContext.history 的耐久最终分支资格规则和 POLICY，不接受未经来源资格校验的旧摘要/cache；第四组负责压缩/根生命周期接线。实现、候选测试参数与明确未验证范围见 [第三组记录](../tasks/dev-manager/WORK_GROUP_3.md)。

## 10. 第四组真实控制器与本地恢复

应用服务 `controllers()` 异步初始化 ApplicationCoordinator / SessionCoordinator 与真实 H2 根；必须观察初始化 future 的异常，不能把失败替换为空恢复视图。项目服务 `openSessions(asNew)` 在后台读取 real path 和可验证目录身份，`sessionController()` 返回公共 SessionController；`selectionSink()` 只有项目绑定初始化成功后可用，beginCapture 本身不等磁盘或网络。UI/成员适配器不直接读写 H2。

稳定项目身份与本次 instance 分开：确切目录或可靠同卷移动可恢复原记录；未知定位保持 IdentityUncertain，显式选择新归属，或对空目标通过协调入口关联旧归属（两端版本/占用共同校验）。复制不按项目 UUID/名称/源码继承。Windows 使用 IDEA 已有 JNA 查询目录 FILE_ID_INFO 与创建时间；句柄关闭，失败显式传播，不回退为仅路径匹配。跨机器和无法取得可靠文件身份的自动识别仍不承诺。

新增兼容事件 `RecoverSession(EventMeta)`，对应可用动作 RECOVER：仅重试原 session 本地未保存草稿/冻结终止快照的存储协调，不发送模型、检索或工具。活跃运行任务拒绝恢复；删除中仍只能继续 DeleteSession。存储失败会立即发布 FAILED/阻塞状态，OFF→ON 不解除阻塞。正常停止、项目关闭及应用关闭按旧 operation 有条件保存累计终止快照；应用 shutdownCompletion 表示本地收尾，JVM 退出 hook 最多等待15s，不把超时说成已保存。强杀只能恢复最后完整提交。

SubmitDraft/RetryAttempt/ManualCompact 的去重在 busy、revision 和未保存阻塞检查之前、项目/instance/LIVE检查之后；重复仅返回原 operation。新提交与用户正文/typed代码、初始attempt、草稿消费及活跃槽共同提交。终态正文/来源与槽释放同事务；停止标志与 checkpoint 激活通过同一 H2 写入顺序裁决。closeTab/observe.close 不取消业务。

摘要复用配置的生成模型，独立无工具请求。自动规划已包含本轮真实输入证据与tools；旧checkpoint复用要检查POLICY/完整来源资格指纹。持续整理使用有效旧摘要加新增完整前缀，旧摘要不升级成课程事实；激活再次检查根、instance、operation撤销、runtimeEpoch、父指针与前缀。原始档案不删除。公开参数与实际/未执行边界见 [第四组记录](../tasks/dev-manager/WORK_GROUP_4.md)。

ApplicationCoordinator 与 CredentialUpdateSink 共用 OFF 门槛；stage复制短期字符值，单次ref两分钟失效，拒绝/消费/关闭均清理。PasswordSafe 是唯一生产凭据持久入口；H2只存非秘密profiles。ConfiguredRuntimeFactory 构造不发连通请求，不选默认alias；当前为显式 DeepSeek风格SSE/OpenAI嵌入兼容路线，真实平台能力未验证。资料任务返回operationId，可在负责人任务边界查询原cause；普通view仅有简短提示。引用命令先校验该session实际citation，再打开固定托管版本及物理页定位。

## 11. 第五组生产装配（2026-10-24）

现已注册唯一 JavaCodeCapturePort、PdfBoxPdfReader，并通过公共 CaptureEntryAction 接入右键菜单；前文“无成员实现/占位 UI”仅是对应组的历史状态。首次右键若项目身份尚未初始化，打开侧栏并提示恢复/身份选择后重新添加，不排队异步改绑原动作。就绪时同步委托成员动作冻结选区与当前目标。

BootstrapToolWindowFactory 后台初始化后在 EDT 构造真实 CourseAssistantView，内容释放关闭观察/高亮资源，不停止 session 业务。身份不明时明确新建或关联未占用的旧归属；关联仍复验空目标及双方版本。初始化失败保留原始诊断且不显示成功空状态；应用存储失败须排障后重启，关联失败可重新选择。

自动验证已区分真实 IDEA/注册 PDF/PSI/H2/界面与本地合成模型响应；真实服务、人工体验/PDF跳页与规模仍待证。详细证据及人工入口见 [第五组](../tasks/dev-manager/WORK_GROUP_5.md)。

## 12. 人工反馈后的本地UI候选（2026-10-24）

用户已更正为单聊天时间线、底部draft和右上新建/历史/搜索/设置。原始typed schema和中断输出资格不变；Markdown正文解析渲染，代码块在原位置高亮。自动保存和单击发送仍通过UpdateDraftText确认后以精确版本SubmitDraft，异常不自动发送。暂停后继续使用RetryAttempt新尝试，不续写中断前缀。项目级临时草稿恢复缓冲仅保留未确认文字，删除/消费清除；关闭视图不停止业务，未保存文字不冒充耐久成功。此项为待用户复测的本地候选，未创建PR。详见负责人FEEDBACK_FIX/FEEDBACK_RETEST。

## 13. 混排草稿与紧凑用户消息（2026-10-25）

DraftView新增captureOffsets（相对于原始文字的UTF-16偏移）和captureOrder（相同文字偏移下的明确捕获顺序）；位置与顺序和原文字共同保存。UpdateDraftText可显式携带两项，核心验证目标/修订/完整capture身份；未携带的旧调用保持旧顺序并将位置限制到合法文本范围。原五参DraftView、四参事件及缺字段旧JSON保持兼容。

提交冻结Input中的原始question、完整typed code、位置及顺序；visibleParts按偏移/顺序呈现原始文字和SELECTION紧凑条，METHOD/CLASS_STRUCTURE只进入模型implicitContext，不在用户消息重复展开。模型输入同时包含有序messageParts和隐式上下文，原始归档与中断输出资格规则不变。显示渲染不回写正文。

用户上翻时暂停更新当前阅读布局，保留最新累计快照，点击↓或主动回底后显示；切换会话立即失效旧缓存和回调。该候选尚待实际用户验收，不宣称PR/main集成完成。

### 2026-10-25 失败资料移除与显式重导入

未发布资料（无 active source）的移除只在短事务复验库基准、资料身份/修订/来源后标记 REMOVED，同时递增库修订；保持 generationId 不变，不要求已有索引或调用嵌入。库修订变化阻止旧导入/更换候选迟到发布。已有 READY 资料的移除仍发布匹配代次，旧 lease/历史引用规则不变。

显式导入与已移除资料同 MD5 的文件时，保留旧资料/来源历史，复用托管 blob 并创建新资料身份进入完整准备流程；未移除的 FAILED/PREPARING 同 MD5 仍定位原条目、不伪装 READY，需对该条目执行更换以手动重试。更换 FAILED 资料保留原 documentId，仍须嵌入服务授权。HTTP 401 与 403 分开提示，不输出响应正文或推定 key 必然错误。

## 2026-10-25 资料硬删除更正（优先于旧软删除/保留历史来源提案）

用户明确要求：移除即彻底删除资料数据，保留聊天文字，旧引用提示“资料已删除”。移除复验库基准及目标修订，在同一事务删除文档全部来源版本、派生页/块/向量、旧代次关联与来源保留记录；READY移除先构造其余资料的新代次，未发布资料不生成索引。列表不保留REMOVED。共享PDF字节只在最后一个文档不再使用时清理，不删除用户原文件。历史聊天归档与引用标识保留，点击已不存在来源提示资料已删除，不阻塞后续发送；聊天归档不再建立已删除来源的保留记录。

删除撤销受影响的固定检索租约及资料候选，迟到派生写入/发布不得复活资料。替换行为仍保留旧版本，只有明确移除覆盖原历史保留提案。旧REMOVED行在启动时按同一规则清理。数据库删除与文件系统不做伪原子事务：未完成文件删除保留deleting记录，失败明确上报，排除占用后重启继续；其余资料不受影响。架构未冻结，等待用户验收，不发PR。

### 第八轮显示更正（2026-10-25，用户原文见本地user-turn-067）

连续输入光标正常前移，刷新保留主动选区/中间编辑，切换打开草稿定位末尾；流式显示复用未变化消息，以聊天viewport布局同步跟底，上翻仍保持阅读快照。过程折叠▶、展开▼。回底按钮为聊天区独立小型悬浮按钮，不占draft或横贯底部遮罩。正文引用按已提供的证据显示文件名及物理页码；不完整/未知标记只显示待确认，代码示例和原始归档不改。非成功归档保留显示标签但不提升引用/未来上下文资格。人工视觉仍待用户验证。

### 选区边缘空白（2026-10-25）
自动方法/类归属仅按选区去除首尾空白后的端点判断；SelectionStart原始正文和坐标不trim。纯空白明确NOT_APPLICABLE，真实跨方法仍拒绝。

## 第十一轮：启动状态与批量导入

用户授权（原文在本地 evidence/user-turn-070.md，哈希登记于 sources.json；本段为代理转述）：持久保存最后一次成功启用/明确停用选择，IDE 退出不覆盖该选择。旧库默认停用，启动恢复仅构建运行资源，不恢复旧请求；恢复失败保持 OFF 并提供诊断及配置入口。资源管理器允许多选 PDF，按返回顺序逐个导入，使用最新资料库基线；失败或停用停止剩余队列，已完成项保留。批量任务期间拒绝其他资料变更。设置页移除整理历史、恢复未保存、关闭标签三个按钮。未冻结，等待用户验收，不创建 PR。

## F2 G1 实际契约增量（2026-10-26）

纯 Java 数据与端口已落地：EditContracts、EditTargetPort.resolveAndCapture、EditPreviewPort.open、EditApplyPort.apply。实际字段以源码为准，详见 [G1 实现记录](../tasks/dev-manager/F2/G1_IMPLEMENTATION.md)。Input 四字段及历史构造/JSON兼容保留。

ViewEvent 新增 OpenEditPreview(meta, ProposalRef)、ApproveEditProposal(meta, PreviewToken)、RejectEditProposal(meta, PreviewToken)；OperationKind 新增 APPLY。前两项在 G1 无真实 adapter 时明确 NOT_SUPPORTED，拒绝走独立仓储本地命令；三者不进入 SubmitDraft/RetryAttempt/ManualCompact，不消费 draft 或创建用户轮次。

ProposalRef 固定 proposalId/revision/beforeHash/afterHash，PreviewToken 再固定宿主展示 id 与 instance。哈希针对精确 UTF-16BE code units，不等于磁盘字节哈希。EditSnapshot 明确完整 before 和允许 offsets；format=1，未知格式拒绝，文本超过1,000,000 Java chars明确失败。该上限是实现防护，不是实际规模验收。

EditRepository 由 SessionRepository 拥有，同一 H2 writer 上建立独立 schema。生成写入验证 LIVE/instance/operation/epoch；审核验证当前根/实例/准确展示和耐久父资格，不要求父生成槽存在。父有效终态、实际成功候选工具来源、资格提升和槽释放共同事务提交。终态 ReAct Snapshot 不回写。

APPLY 意图/结果分开记录，独立 operation 和稳定 applyId 去重；回执区分 INTENT/APPLYING/APPLIED_TO_DOCUMENT/FAILED_NOT_APPLIED/OUTCOME_UNKNOWN/RECEIPT_PENDING 及 UNKNOWN/UNSAVED/SAVED 磁盘状态。重启/停用不自动重放；未知结果维持阻塞，普通恢复不得清除，实际核验适配留 G5。会话删除级联清全部 F2 私有记录，迟到写拒绝。

ToolBoundary 固定课程/目标/候选能力，默认无 F2 adapter，不暴露应用工具。实际目标定位、候选子生成、Diff、Undo/写命令及真实服务均不在 G1 完成声明内。本地状态事实进入未来上下文与 checkpoint 指纹，但候选原文和未完成生成不会因此进入模型。

## F2 G2/G3 实际接线（2026-10-26）

生产 Project 服务为原项目/实例注入 JavaEditTargetPort，原始 SubmitDraft 的 ReAct 开放 resolveEditTarget(path,class,method,parameters) 和 proposeCodeEdit(ref)。`f()` 指明确零参数；`f` 与空参数列表指参数未知。选区取原提交冻结 CodePart，文字不能改绑其目标。源码仅来自项目内 source roots，明确返回 UNIQUE/AMBIGUOUS/NOT_FOUND/NOT_READY/INCOMPLETE/STALE。Target 增加兼容可缺的 fileIdentity；生产快照保留独立 VFS id，旧缺失值不授权应用。

完整同版 Document before 与范围先独立持久化到 ca_edit_snapshot。CodeEditTools 只允许 attempt 首次分支解析；课程降级不得重新采集/生成。ca_edit_generation 在子订阅前预留宿主逻辑请求，重复 callId 变化/并发/失败不触发重订阅；原始指令不可被工具参数替换。

子生成由父唯一 ReAct 所有者驱动，tools=[]，共享超时/取消/步骤与累计输出限制，完整子输入先预算。严格精确替换只构造独立 after，零项目写入；子正文保留档案但不进入 branch.protocol。父有效耐久成功才提升候选。已使用 F2 的 attempt 不可重试；其余旧 F0 retry 不开放 F2，新 draft 才重新生成。Diff/审核 UI/实际应用仍留 G4/G5。实现参数、验证及限制见 [G2/G3 实现](../tasks/dev-manager/F2/G23_IMPLEMENTATION.md)。
