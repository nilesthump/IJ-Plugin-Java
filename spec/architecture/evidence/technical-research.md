# 官方技术资料核查

查证日期：2026-10-15（Asia/Shanghai）。本记录区分官方能力与工程推断；不构成用户决策或已经完成的兼容测试。在线 `reference` / `current` 页面会变化；后续选定版本时应重新绑定版本化文档。

## Spring AI 与进程边界

官方允许以编程方式创建 `ChatClient`，提供不使用 Spring Boot 的工具调用配置；Spring AI 因而不强制 Spring Boot 或独立服务进程。[ChatClient 官方文档](https://docs.spring.io/spring-ai/reference/api/chatclient.html)

工程推断：可讨论由 IntelliJ 插件服务装配 Spring AI。它是否适合本项目，仍需目标 IDEA/JBR、依赖、类加载和资源释放的验证。JetBrains 说明插件使用独立类加载器，部分 `ServiceLoader` 用法需留意线程上下文类加载器。[插件类加载](https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html)

官方要求耗时工作在后台运行，并遵守 IDE 数据的线程访问规则；服务与 Disposable 用于管理生命周期。内嵌并不意味着可以在界面线程调用模型或建立索引。[线程模型](https://plugins.jetbrains.com/docs/intellij/threading-model.html)、[插件服务](https://plugins.jetbrains.com/docs/intellij/plugin-services.html)、[资源释放](https://plugins.jetbrains.com/docs/intellij/disposers.html)

工程推断：独立本地 Agent 可以隔离模型和索引的部分资源与依赖，但必须增加上下文快照契约、进程就绪、通信、取消及退出处理。不能跨进程直接传递 IDEA 的 Project/PSI 对象。

## 嵌入与网络边界

Spring AI 提供 ONNX 本地嵌入及手动配置方式，可从本地资源加载模型。向量检索的文档嵌入与查询嵌入须使用兼容的模型和向量空间；离线预处理本身不保证提问阶段不调用嵌入服务。[ONNX Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html)

若选择远程嵌入，图片中的“只有 LLM 使用 HTTP”须修订。若选择本地嵌入，模型文件分发、首次加载、内存及实际课程检索效果成为待验证事项；本轮不预选模型。

若插件通过本机 HTTP 与独立 Agent 通信，则“只允许生成模型访问外网”和“只有生成模型使用 HTTP”含义不同。传输方式尚未选择。

## 工具调用与 MCP

Spring AI 支持 Java 工具回调；接入工具不以 MCP 为前提。若由模型选择工具，完整时序包括模型提出工具调用、应用执行工具、将结果交回模型。若工具在首次 LLM 请求前执行，应说明触发它的是应用规则或用户选择。[Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)

MCP stdio 通过本地子进程的标准输入输出传递 JSON-RPC，不需要 HTTP，但需要进程管理；协议输出与日志需分开。[MCP transports](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)、[Spring AI MCP client](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)

作业 F0 未要求 MCP；是否保留取决于实际工具需求，不由技术支持能力反向推导需求。

## 存储和引用

Spring AI Chroma 集成面向可访问的服务，本地容器也会增加服务依赖，不能把它当成直接嵌入插件的普通文件存储。[Chroma 官方集成](https://docs.spring.io/spring-ai/reference/api/vectordbs/chroma.html)

`SimpleVectorStore` 可用于内存存储及 JSON 保存/加载，但官方定位是测试和演示。是否适用于本课程数据规模需验证，不能据此承诺生产能力。[SimpleVectorStore API](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/vectorstore/SimpleVectorStore.html)

Spring AI 的 PDF 分页读取器提供文件与起止页元数据。工程设计仍需保证解析、分块、存储、检索、提示词及结果展示全过程保留来源，不能只让模型自由生成页码。[PagePdfDocumentReader API](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/reader/pdf/PagePdfDocumentReader.html)

## 未作出的选择

Spring AI 版本、IDE/JBR/JDK 版本、Spring Boot、独立进程、传输协议、模型、嵌入、向量库、MCP 和工具清单均未因本次查证而确认。版本组合与运行行为需在后续目标环境明确后核实。
