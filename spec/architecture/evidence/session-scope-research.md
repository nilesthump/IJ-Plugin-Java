# Session、记忆与平台作用域核查

查证日期：2026-10-15（Asia/Shanghai）。本记录为公开官方资料与设计推论；没有选定 Spring AI 版本或执行插件运行测试。

Spring AI 的 ChatMemory 使用 conversation ID 分区。当前文档要求每次显式传入 ChatMemory.CONVERSATION_ID；本轮还核对 v1.1.8 和 v2.0.1 源码中的非空校验。应用应使用独立 sessionId 作为记忆命名空间，不能把项目 ID 当作所有会话共用的 conversation ID。[Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)、[v1.1.8 advisor](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/api/BaseChatMemoryAdvisor.java)、[v2.0.1 advisor](https://github.com/spring-projects/spring-ai/blob/v2.0.1/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/api/BaseChatMemoryAdvisor.java)

官方区分提供给模型的 Chat Memory 与完整 Chat History。完整聊天档案、代码附件及请求状态应由应用持久化；模型窗口淘汰策略不等于用户聊天记录删除策略。重启后恢复完整历史，也不意味着每次把全部历史都发送给模型，实际上下文预算仍待设计。依据同上。

IntelliJ application-level Service 为 IDE 应用范围单例，project-level Service 在每个项目中各有实例。工程映射提案为：共享课程库由应用服务管理，项目服务管理多个 session，session 自身管理对话、代码快照及请求。项目服务不是单个 session；应用服务单例也不意味着多个独立 IDE 进程自动共享内存。[Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html)

实现 Disposable 的应用/项目服务分别随应用/项目生命周期释放。工程推论：项目关闭可以释放属于该次打开实例的请求和 UI 引用，同时保留磁盘会话档案及应用级课程库；关闭视图、取消请求、删除会话和清除持久化数据应是不同操作。[Disposer](https://plugins.jetbrains.com/docs/intellij/disposers.html)

平台提供状态持久化机制，但本轮不据此指定聊天记录存储格式或数据库。小型 UI 状态与持续增长的消息/代码档案应分别评估写入、恢复、容量及迁移方式。[Persisting State](https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html)

查证时尚未确定的容器形式，后续已由 `user-turn-010.md` 确认为 Tool Window 内标签页切换；`user-turn-011.md` 已确认右键进入当前项目当前会话、缺省新建。仍未确定：具体持久化介质、项目重定位后的身份映射、会话删除策略、并发请求策略、上下文预算、独立 IDE 进程的课程库并发写入，以及标签关闭和布局恢复规则。
