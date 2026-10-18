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
