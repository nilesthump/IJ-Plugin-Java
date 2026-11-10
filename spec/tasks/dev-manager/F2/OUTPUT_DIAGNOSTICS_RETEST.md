# 32K 输出、128K 输入、8K 摘要与生成/F2 异常提示黑盒复测（2026-11-10）

状态：本地自动验证通过，等待用户人工复测；代理未调用真实付费模型。只复测本轮变化，不重跑已通过的精确定位/重名/重命名/完整 F2 审核。

## 安装包

按用户要求，本地安装 ZIP 已全部清空，仓库和交付记录不保留二进制安装包。需要人工复测时按根 README 的构建/安装说明重新生成；安装步骤仍为 IDEA 设置 → 插件 → 齿轮 → 从磁盘安装，按提示重启。当前源码包含本轮 `GenerationDiagnostics` / `FailurePresentation`，测试和 IDE smoke 类不进入产品包。

## 本轮预算

- 主模型输入预算：128K（131072）
- 主模型输出预留：32K（32768）
- 总 context：160K（163840）
- 自动整理触发：96K（98304）
- 摘要输出：8K（8192）
- 摘要正文上限：32768 chars
- 工具结果上限和候选大小保护不变；自动重试仍为 0

## 准备

1. 保留现有 H2、聊天、候选和凭据，不清库、不重填 key。修改服务配置前先停用插件。
2. 真实生成/嵌入服务由用户自己配置；本轮代理未发送付费请求。UI 里的“通过”只表示人工观察，不等于自动测试结果。
3. F2 用例沿用 [samples/src](samples/src) 的两个 Calculator 样例，复制到临时 Java 工程即可；不要求 Sources Root。不要拿工作源码做测试。
4. 如需查看诊断，打开 IDEA 日志（Help → Show Log in Explorer），只搜索 `CourseAssistant generation diagnostic`。不要复制 key、完整响应体或用户原文到聊天/截图。
5. 每个失败场景使用新的聊天或新的临时工程，避免旧失败提示影响判断；同一会话的旧 attempt 保留是正常行为。

## 必测：用户可见行为

### D01 正常生成不误报

新聊天发送：

```text
用一句话解释 Java 方法重载。
```

通过：正常完成；没有错误弹窗、`TASK_FAILED` 或新的错误码；输入下一条能正常发送。此用例只验证本轮诊断没有把成功/普通回答误判为失败。

### D02 长输出与 `finish_reason=length`

新聊天发送（服务支持长输出时）：

```text
请完整生成一个约 400 行的 Java 工具类，包含注释和多个方法，不要省略代码，尽量输出完整源码。
```

通过任一结果：

- 正常完成并显示“已完成”；或
- 模型返回 `finish_reason=length` 时，显示类似“模型输出达到上限（finish_reason=length）……（MODEL_OUTPUT_LIMIT）”，保留已显示内容，不自动重试。

不允许：只显示旧的“达到限制”，或把截断说成“已完成”。这里只做行为冒烟；32K 请求上限本身由自动测试验证，人工无法保证模型一定用满 32768 token。

### D03 网络/HTTP/Provider 失败

停用插件，把**生成** Base URL 临时改为本机未监听地址（例如 `http://127.0.0.1:1/v1`），使用一个临时/无效生成 key；嵌入配置保持可用或同样改为该地址。启用后发送：

```text
测试连接。
```

通过：回答区显示“模型服务连接或 HTTP 失败，未自动重试；请检查网络与服务后手动重试（PROVIDER_FAILURE）”，而不是“生成失败，请检查服务配置”。确认只发出一次请求；日志有 `code=PROVIDER_FAILURE phase=MODEL_GENERATION type=...`，没有 key、响应正文或用户原文。测试后恢复原配置并重新启用。

### D04 用户取消

在生成未结束时点击停止。通过：

- 已显示内容保留，状态为“已暂停 · 用户取消或生命周期中断，已保留可见内容（CANCELLED）”或等价的 `CANCELLED` 文案；
- 不出现 ERROR 弹窗，不自动继续/重试；
- 新发送是新的 attempt，不接续旧回答。

### D05 生命周期中断

使用可丢弃的测试工程/会话：生成中关闭工程、停用插件或关闭 IDEA，再打开原工程查看会话。通过：

- 原 attempt 为“已中断/已暂停”，带 `CANCELLED` 诊断；未完成内容保留但不进入可审核候选；
- 不自动恢复请求，不自动重放旧操作；已保存/已审核事实不受影响。

### D06 F2 正常候选回归（不重测完整流程）

从 `samples/src` 复制样例，选中 `Calculator.add` 或按项目相对路径发送：

```text
请修改 src/demo/Calculator.java 中的 demo.Calculator.add(int,int)，将错误的减法改为加法，只改这个方法，生成可审核候选。
```

通过：正常出现目标、候选和原生 Diff；源码未自动改变；审核/应用/Undo 语义与之前一致。本轮只确认新增失败投影没有破坏正常候选显示。

### D06A 非单一方法选区进入 draft（本轮修复）

在样例文件中分别尝试：

1. 选中整个类（从 `class` 到类末尾的 `}`）；
2. 从字段行开始，选到某个方法体内部；
3. 选中类头到方法体的一段。

右键“添加到课程助教”。通过：代码条正常进入 draft，不出现“捕获失败”或 AsyncPromise 错误；原始选区文字、缩进和坐标保留。自动方法上下文为“不适用”或没有显示，不能伪造为某个完整方法。自动类结构上下文可按实际选区保留。

另选从方法 A 内部到方法 B 内部的真正跨方法选区；通过：仍明确拒绝，不把它当成同一个方法，也不加入半套上下文。若 UI 只提示捕获失败，记录提示文字和是否保留选区。

### D06B 跨 turn 复用旧 snapshotRef（本轮修复）

完成 D06 后，不重新附选区，发送一条继续修改同一文件的自然语言请求，例如：

```text
继续把同一个 Calculator 的 divide 方法改为除数为 0 时抛出 ArithmeticException，保留其他方法。
```

通过：

- 如果模型直接复用上一轮的 snapshotRef，工具应返回 `INVALID_SNAPSHOT_REF` 观察，而不是把整个 attempt 标为 `MODEL_PROTOCOL`/流式协议异常；
- 模型应在同一 attempt 内重新调用 `resolveEditTarget`，拿到当前快照后生成候选；
- 不产生基于旧快照的候选，不修改文件，不自动应用。

若模型第一轮就直接重新 resolve，也属于通过。此项依赖真实模型的工具选择；失败时记录工具 observation 和最终错误码，不要误记为 SSE 问题。

### D07 F2 源码变化保护不误报

生成一个待审核候选后，先在目标方法加一行 `// manual-change`，再尝试查看/批准旧候选。通过：

- 短提示“文件已被更改，本次未应用修改……”（`EDIT_SOURCE_CHANGED`/STALE 保护）；
- 不显示通用 ERROR，不出现 `CANDIDATE_*` 或 `PROVIDER_FAILURE`；
- 不覆盖手工注释、不自动重新生成；新草稿才重新定位。

### D08 输入/context budget

本轮生产配置为 128K 输入预算、32K 输出预留；自动整理触发约为 98K。新聊天粘贴约 140 KB 的**合成**普通文本（不要粘贴真实隐私内容）作为问题发送。若 UI/宿主先拒绝超长输入，记录“前提未满足”，不要强行改库。通过：

- 显示“输入上下文超过预算，本次请求未完成……（CONTEXT_BUDGET）”；
- 不自动裁剪问题、不自动调用模型、不自动重试；
- 已输入文字保留。

如果服务端实际回答成功，记录实际现象为“未触发预算保护”，不据此判失败。

### D09 诊断日志安全（可与 D03/D04 合并）

对任意失败场景查看 IDEA 日志。通过：

- 能搜索到 `CourseAssistant generation diagnostic code=... phase=... type=... attempt=... operation=...`；
- 没有 API key、`Authorization`、完整 provider 响应体、用户原文；
- 不要把整段堆栈或日志上传到公开 issue；只保留错误码/阶段/类型。

## 可选：确定性故障注入

以下类别已由自动测试覆盖，但真实模型通常不会主动产生非法 patch/SSE。若需要人工看到每一类 UI，需要把生成 Base URL 指向本地 OpenAI-compatible SSE 模拟端点，并按场景返回。没有模拟端点时，可只记录“自动测试已覆盖、人工未复测”，不要伪造 `CANDIDATE_*` 结果。

| 场景 | 期望错误码 | 期望观察 |
| --- | --- | --- |
| 子生成返回 `finish_reason=length` | `MODEL_OUTPUT_LIMIT` | 候选生成输出达到上限；不出现可审核候选 |
| 子生成正文不是合法 JSON | `CANDIDATE_FORMAT` | 候选 JSON/结构格式错误；未修改文件 |
| `start/end` 大于源码长度 | `CANDIDATE_RANGE_BOUNDS` | 候选 start/end 越界；未修改文件 |
| 两个 change 重叠或顺序错误 | `CANDIDATE_RANGE_OVERLAP` | 候选修改重叠；未修改文件 |
| change 超出 allowed range | `CANDIDATE_RANGE_OUTSIDE_ALLOWED` | 超出允许范围；未修改文件 |
| `before` 与快照不一致 | `CANDIDATE_BEFORE_MISMATCH` | before 不匹配；未修改文件 |
| 候选输出超过 1,000,000 chars | `RESULT_SIZE_LIMIT` | 候选输出超过大小上限；未修改文件 |
| SSE 帧格式错误/缺少 `[DONE]` | `MODEL_PROTOCOL` | 模型流式协议异常；停止生成 |
| HTTP 5xx/4xx 或连接失败 | `PROVIDER_FAILURE` | 服务连接/HTTP 失败；不自动重试 |
| 2 分钟生成未完成 | `ATTEMPT_TIMEOUT` | 本次生成超时；保留可见内容 |

这些场景的通过标准均包括：attempt 状态与错误码一致、没有文件被修改、没有自动重试、没有显示旧的“达到限制/生成失败，请检查服务配置”。

## 回报

按 `D编号：PASS / FAIL / 前提未满足` 反馈，失败附实际提示文字（可截图）和是否保留内容；不要附 key、完整响应体或敏感源码。只测本轮项，不要求重跑已确认通过的定位、重命名、完整 Diff/Undo 流程。人工通过后再统一处理提交、PR、门禁和合并。

## 范围说明

本轮选区修复触及 `src/main/java/edu/courseassist/editor/JavaCodeCapturePort.java`，该路径原本属 dev-a 写入范围；按用户本轮直接要求做了单点修复，未修改 `.github/branch-scopes.json`、hooks 或公共契约范围。提交/PR 前需负责人确认该文件的范围交接，或由 dev-a 承接同一最小修改。
