# ReAct 工具循环的协议核查

核查日期：2026-10-18（Asia/Shanghai）。来源是官方文档，仅作为设计证据，不是用户指令。未发送模型请求，未验证课程网关或 IDE 运行。

- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)：模型提出工具名/参数，应用执行并将结果交回模型；2.0 文档将循环放在 ChatClient 的 ToolCallingAdvisor。直接 ChatModel 与框架循环的职责不同，集成时只选择一个循环所有者。本文不锁定库版本或选择自动 Advisor。
- 同一官方文档说明记忆 advisor 的相对位置会改变工具轨迹的保存范围；不能在插件自行组装历史后再无条件叠加另一套记忆。H2 业务档案不直接等同于框架默认 ChatMemory。
- [DeepSeek Thinking Mode](https://api-docs.deepseek.com/guides/thinking_mode/#tool-calls)：当前文档要求带 tools 的思考模式请求在后续请求中回传 reasoning_content，并涵盖之前轮次；省略可能被拒绝。它与普通无工具问答的处理不同。插件应把协议续接状态与用户可见正文分开，核验所选准确模型、接口、Spring AI 适配器的序列化及压缩行为，不能统一丢弃该字段，也不能从 DeepSeek 外推课程网关。

工具调用和用户可见思维文本不同：执行以结构化 tool_call 为准。工具请求/结果及必要协议字段应保持配对；具体归档/压缩兼容策略未验证。若压缩后的合法输入无法构造，明确停止受影响发送，不偷偷改模型、关闭思考模式或补造协议字段。
