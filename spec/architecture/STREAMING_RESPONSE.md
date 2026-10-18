# F0 流式回答与增量 Markdown 设计

更新：2026-10-16（Asia/Shanghai）。状态：讨论提案，权威状态为 `WORKING.json`。用户在 `evidence/user-turn-027.md` 确认第一版回答流式显示、生成中逐步更新。本文定义最小契约与验证边界，尚未接入真实流或创建主插件工程。

## 1. 所有权与一次模型调用

每次已持久化提交建立一个活跃 attempt；已采纳的 ReAct 允许其串行包含多个模型步骤，每个 ModelStep 由核心持有唯一订阅。UI 和存储消费核心的累计状态，不能各自订阅模型流。关闭标签仅解除视图订阅；重开从核心最新状态显示，不重新请求模型。停止、删除、关闭项目分别执行既定的失效/取消规则。

Spring AI 的 StreamingChatModel.stream(Prompt) 返回 Flux<ChatResponse>；DeepSeek 手动客户端可使用该接口。适配器应保留正文、结束与用量元数据，不只消费丢失元数据的便捷字符串流。依据 [Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)、[DeepSeek 手动流式示例](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html#_manual_configuration)。Flux 及供应商对象留在 model 适配层，不要求 UI 契约依赖 Reactor。

课程网关的流式支持、字段格式、结束原因和 usage 仍需真实契约验证。未验证不能作为“与 DeepSeek 相同”的已知能力，也不自动换服务或整请求重发。

## 2. 最小事件与终止（提案）

复用 projectId / projectInstanceId / sessionId / turnId / requestId / attemptId，增加 branchId、stepId、toolCallId 及 attempt 内持续单调 seq；不以当前标签或供应商同一个 completion id 分配归属。分支/步骤变化不复用事件身份。

| 事件 | 最小内容 | 核心处理 |
| --- | --- | --- |
| delta | seq、回答正文增量 | 按序追加累计 Markdown，保留空格/换行，不 trim；不按相同文字去重 |
| model-ended | 正文末序号、finishReason、可选 finalUsage | 表示适配器取得结束证据；不表示已保存完成 |
| failed | 原因/分类、最后正文序号、可得 usage | 保留部分正文和原异常，按任务边界提示；不能冒充空回答成功 |
| canceled | 核心明确取消及对应 operation | 不依赖取消订阅后还会收到 onComplete，核心完成本地终止 |

核心对同一 attempt 的已接收 seq 幂等处理；同序号不同正文是契约错误，缺口不能直接跳过拼接。seq 是本地流事件身份，不代表远程服务已提供续传/去重能力。UI 只需消费累计视图快照，不重放网络增量。

DeepSeek 的 content、reasoning_content、工具调用及角色元数据分别处理，回答正文只消费 content。F0 已采纳 ReAct：完整结构化工具请求由核心校验后执行，不能把正文字符串当工具指令。推理内容与工具过程采用独立可折叠小字区，不混入最终回答/普通聊天正文；协议续接字段单独处理。后续提问只允许成功有效分支的合法协议状态，中断分支字段不得回注。DeepSeek 当前协议的结束原因与 usage 规则不能外推到课程网关，见 [官方流协议](https://api-docs.deepseek.com/api/create-chat-completion/)。

适配器必须验证结束证据，再由核心提交终止结果：不能仅凭 Flux onComplete / 网络 EOF 判定答案完整。正常 stop、输出上限、过滤、未知结束原因、合法工具请求、无效工具请求和只有元数据/空正文需分类；合法 tool_calls 只结束当前模型步骤并进入工具阶段，最终步骤无待执行工具且正常结束才可能完成回答；上限或断流保留未完成标记。usage 缺失用 unavailable/null，不填 0，不累计重复出现的整次统计。

固定 v2.0.1 的 DeepSeek 适配器会过滤原始结束标记，且有转换异常后返回空 ChatResponse 的路径；当前 DeepSeek 协议还列有该版本枚举未覆盖的结束值。P0 应用可控响应验证这些情况，不能因没有 onError 就宣告完成。依据 [固定模型源码](https://github.com/spring-projects/spring-ai/blob/v2.0.1/models/spring-ai-deepseek/src/main/java/org/springframework/ai/deepseek/DeepSeekChatModel.java)、[固定 API 源码](https://github.com/spring-projects/spring-ai/blob/v2.0.1/models/spring-ai-deepseek/src/main/java/org/springframework/ai/deepseek/api/DeepSeekApi.java)。这不是切换版本或放宽错误策略的理由。

## 3. 部分保存与最后提交（提案）

1. delta 先经过 session、项目实例、operation/attempt/branch/step 有效性检查，追加本 attempt 的输出缓冲。它不是一条新用户消息，也不是每个 token 一条助手消息。
2. 建议将按 attempt/branch/step 区分的累计 partialBody + throughSeq + incomplete 按短批次持久化，不新增逐 token 日志。保存频率留实现测量；旧前缀不得覆盖新前缀或终止记录。UI 可显示较新的内存前缀，但已保存状态只承诺确实提交的版本。
3. 正常结束时，完整正文、实际证据、usage（如有）和终止状态共同提交，才显示完成并保存。失败/取消则冻结可得的部分正文为未完成结果并继续显示；其生成内容不得进入后续模型请求、检索查询或摘要输入，原用户问题/代码仍保留。
4. 运行中输出只属于 attempt，终止时形成该尝试的固定助手结果；历史压缩只覆盖已终止轮次，不覆盖正在增长的输出。重启保留最后完整保存前缀并标记中断，不自动重新订阅。
5. 所有部分保存、完成、用量、诊断和渲染缓存都遵守删除栅栏；取消或删除后的 delta 不能获得写回权。删除成功不保留该 session 的部分答案或恢复副本。

断流不自动重发整条模型请求。用户明确重试才建立新 attempt、关联原问题并恢复其固定输入；新回答不拼接旧部分前缀，旧未完成结果保留到用户删除 session；停止、重试、超限和中断不清空已渲染内容，旧结果也不进入新尝试的输入。Reactor 的 retry 会重新订阅上游，不能当作续传，见 [官方重试语义](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html#_retrying)。具体必要失败参数继续由实施成员验证，不叠加隐藏重试。

检索故障自动通识仍发生在同一回答任务内；每个 ModelStep 固定其输入证据快照，后续补查可为下一步追加证据，最终引用以实际提供给最终步骤的有效证据为准。检索故障转通识时按 REACT_ORCHESTRATION.md 新 branch 重建输入并失效旧结果，不沿用旧证据/正文。生成断流属于生成故障，不再次触发通识请求；已有真实依据说明与未完成标记同时保留。

## 4. 增量 Markdown 与配色（提案）

核心持续保留累计原始 Markdown。UI 每次只重新解析变化中的这条助手消息，已终止历史可复用缓存；不按 token 重建全部聊天。围栏语言未输完时按暂时未知处理，围栏未闭合时显示已有代码，不在档案中补结束符，不丢弃尾部或暂停到全文结束才显示。

本地 CommonMark 前缀探针显示：语言标记可能从 ja 增长到 java，未闭合 fence 也能形成代码节点；解析后的 code literal 可能附带规范化尾换行。因而 AST/HTML 不能替代原 Markdown 或固定 CodeSnapshot。复制原消息取原文，typed 代码取快照；不得从高亮 HTML 反解为耐久正文。

建议标签内轻量维护 RenderToken(viewInstanceId, originKey, sourceRevision, colorSchemeRevision, renderEpoch)。originKey 含 session/message/attempt/branch/step/part 或 block 身份；整消息版本覆盖有序 parts。配色版本由监听变化推进，不以配色名字代替版本，也不依赖实验性 SDK 计数器。

- 每消息最多一个渲染任务、一个最新 pending 完整快照；新 delta 仅替换 pending，不堆积 token 任务。任务退出后无论应用/过期都检查 pending，且按 jobId 更新槽。
- 同一活跃 attempt、同一 epoch 的可靠纯追加前缀，可在 EDT 接受“已显示 revision < 结果 revision ≤ 最新接收 revision”，避免快流导致每次结果都被拒绝。不能只凭序号推断前缀关系。
- 改写正文/parts、影响呈现的引用映射改变、新 attempt/branch/step 及任何终止状态都推进 epoch，安排精确最新快照。终止后拒绝旧流式前缀，即使正文没变也要收敛状态。
- 在 EDT 真正应用前，再检查视图未销毁、project/session 仍有效、viewInstanceId、attempt、epoch 与配色版本匹配；不能只在排入 EDT 前检查。旧配色、旧标签和更低已显示版本都不能覆盖新结果。
- HTML 回调只更新正文显示，不回写 role、完成状态、证据可信度或其他业务状态。关闭标签先失效视图再解除订阅/取消渲染/清缓存，重开分配新 viewInstanceId；session 模型流继续归核心。

这些字段放在现有 UI 适配器，不新增通用渲染框架。IDE 多色高亮、只读代码组、受控 HTML 及未知语言规则继续见 `ENGINEERING_BLUEPRINT.md` 第 8 节。

## 5. 已有证据与未验证项

| 层次 | 本轮结果 | 能证明的范围 |
| --- | --- | --- |
| 本机 SDK | 八个相关 class/公开签名/注解已静态检查 | 当前安装262.10968.63；用户后续更正始终仅验证本机，不另验旧build |
| 独立 Swing | Markdown 嵌套代码/表格/转义原生 HTML 与四种合成颜色离屏显示成功，PNG 已目检 | 单个离屏 Java 组件；不是 IDEA 高亮器或产品界面 |
| CommonMark 增量前缀 | 五个累计前缀解析成功，观察到语言标记与未闭合 fence 行为 | 本地解析器行为；不是网络、核心调度或 IDE 增量更新 |
| 平台与真实流 | 未执行 | IDE 实际高亮/配色/资源/长历史、目标构建、供应商协议、取消与保存竞争仍需验证 |

证据：`evidence/ui-sdk-inspection.json`、`ui-markdown-swing-probe.json`、`ui-markdown-swing-probe.png`、`ui-streaming-markdown-probe.json`。探针的库版本不等于项目锁定版本。

下一步在主工程 P0 验证：一次订阅；content 与元数据分离；完整/截断/未知结束；断流及 usage 缺失；快速 delta 下持续可见；配色切换与旧回调；关闭/重开/删除；部分保存/最终提交竞争；长历史与资源释放。当前用例为计划，不能标记通过。

负责人负责模型流、拼接/终止、持久化与公共身份；丙负责累计视图与 Markdown/高亮调度。甲乙边界不扩大，仍按 5:1:1:1，实际成本需重估；不追加新的后台通用框架。

## 6. ReAct 多步骤补充

现行主流程见 `REACT_ORCHESTRATION.md`。每模型步骤独立暂存内容；流式显示先标为当前步骤预览，若该步结束于工具调用则归入可折叠小字过程区，不拼接成最终答案；若停止/超限/中断则继续保留已渲染预览并标明未完成。最终答案正文、证据及完成提交仅属于最终步骤。工具参数的流式片段必须聚合完整并通过校验后才执行；换 branch/step 同时使旧回包、旧保存和旧渲染失效，不能仅靠 UI epoch。

DeepSeek 带工具思考模式有必要协议字段回传要求，见 `evidence/react-protocol-research.md`；该状态不作为 Markdown 正文但也不能直接丢弃。usage 按 ModelStep 保存已报告/不可得状态，attempt 总量仅在范围明确、没有重复统计时汇总。以上是未运行验证的契约，现有本地 Markdown 探针不覆盖工具循环。

## 7. 保留渲染与排除输入

user-turn-032 已明确不同渲染及中断排除规则，详见 `F0_BEHAVIOR.md`。重试新增 attempt 视图块，先前正文/工具/思考继续可回看，不复用同一缓冲覆盖。核心停止命令冻结已接收累计快照，由有权限的协调方提交终止状态；网络回调继续失效。停用的配置 epoch 保护、删除优先级和保存失败提示同样适用。

历史是否可供模型使用由耐久成功状态和最终有效分支决定，不能按“已经画到界面”决定。所有摘要和记忆缓存共用该过滤，丢弃不合资格的派生缓存而保留原始档案；详细验证未执行。
