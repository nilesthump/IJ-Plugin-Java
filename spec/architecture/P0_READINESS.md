# P0 最小工程技术提案与核查边界

更新：2026-10-16（Asia/Shanghai）。状态：讨论提案，不是正式候选/冻结版本；权威状态为 `WORKING.json`。本轮核查环境与依赖，尚未建立主插件工程。

## 1. 已确认的工程约束

- 业务源码纯 Java，保留 Spring AI；用户只安装插件，优先评估内嵌路线。
- 首版预计几十份文字 PDF，总计约 1000 页以内；这不是已经测量的分块量或硬容量上限。
- 共享范围为同一 IDEA 进程下的多个项目和 session，不要求多个独立进程同时读写同一课程库。
- session 隔离、完整保存/恢复、标签删除全清、方法/类上下文、开发暴露错误与实际使用提示/降级等既有要求继续适用。

新增用户依据见 `evidence/user-turn-023.md`、024；业务流程与并行边界分别见 `DISCUSSION.md`、`PARALLEL_START.md`。

## 2. 本机观察与兼容目标分开

| 项目 | 本轮观察 | 能说明的范围 |
| --- | --- | --- |
| 当前验证范围（用户后续更正） | 只验证本机IDEA；当前262.10968.63 | 不再单独验证旧build；本机升级时同步构建及CI |
| 实际安装文件 | IDEA 2026.2.3，build 262.10968.63，Windows amd64 | product-info.json 已读取；不是插件运行验证 |
| IDE 内置 JBR | Java 25.0.4，25.0.4+1-b508.27，x86_64 | release 元数据与 java -version 已核对 |
| PATH 中 JDK | Oracle Java 25.0.2，25.0.2+10-LTS-69 | 编译工具链候选；不等同 IDE 内置 JBR |
| 构建现状 | 有 Maven；本轮 PATH 未找到 Gradle，工作区没有主插件构建/Wrapper | Maven 仅用于隔离依赖探针，不据此选择 Maven 为插件构建工具 |

安装目录名称和卸载注册信息仍为“2026.1.1”，目录内的产品文件已经更新。核查使用实际 product-info/JBR，不仅依据目录名。不可变观察记录见 `evidence/local-environment-2026-10-16.json`。

## 3. 第一条评估路线（代理提案）

| 责任 | 优先评估路线 | 尚需证明 |
| --- | --- | --- |
| 语言/平台 | Java 25；插件业务源码不引入 Kotlin | 目标 SDK 编译、API 与线程约束 |
| 构建 | IntelliJ Platform Gradle Plugin 2.x；建议 Groovy DSL、Wrapper 和固定版本 | 具体版本组合、依赖解析与插件构建 |
| 界面 | Java Swing Tool Window，渲染已有 typed parts/状态 | 交互、资源释放、长历史显示与取消 |
| 选区上下文 | IDE Java PSI 的本地方法/类结构捕获 | 同版本快照、边界状态、文档修改和项目关闭 |
| 模型 | Spring AI 非 starter 模块，手动构造客户端 | IDE 类加载器中的初始化、HTTP、错误/取消及关闭 |
| 数据 | 用户已确认 H2；优先以嵌入式 JDBC＋托管 PDF 文件装配 | 具体版本、持久化事务、全记录删除、项目移动/复制隔离、关闭/重开与包体 |
| 检索 | 用户已指定 JVector；优先评估固定代次的内存图及本地向量恢复 | 版本/类加载、构建/恢复、并发、延迟、内存、ANN 召回与引用质量 |

Gradle 官方对 Java 25 的 toolchain 和运行支持从 9.1.0 开始；当前 IntelliJ 插件文档示例为 2.19.0、通用最低 Gradle 9.0，不能直接把该最低值用作 Java 25 组合。P0 可先评估明确支持 Java 25 的固定 Gradle 9.x 与插件 2.19.0；具体版本尚未锁定。见 [Gradle 兼容表](https://docs.gradle.org/current/userguide/compatibility.html#java_runtime)、[IntelliJ 构建插件要求](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)。

纯 Java 是本项目业务源码约束，不代表 IDE 或第三方库内部没有 Kotlin。构建工具/第三方的实际运行依赖仍需检查，不能把 IDE 已提供的 Kotlin/协程库重复打包；见 [平台 Kotlin 库说明](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html)。

## 4. Spring AI 装配与已执行的依赖探针

当前官方发布与文档指向 Spring AI 2.0.1，可作为第一条评估版本线；1.1.8 作为另一条稳定线的比较记录，不在失败时无提示切换。暂未选为项目最终版本。见 [官方发布](https://github.com/spring-projects/spring-ai/releases/latest)。

最小手动装配优先评估 `spring-ai-deepseek` 与 Spring AI 的 ChatModel/EmbeddingModel 契约；确需 ChatClient 时再引入对应模块。无需因库名而调用 SpringApplication 或启动本地 HTTP 服务，见 [DeepSeek 手动配置](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html#_manual_configuration)。

课程网关嵌入有两个候选：协议匹配后使用现成 OpenAI 嵌入适配器，或实现 Spring AI EmbeddingModel 网关适配。前者减少自写协议代码，后者可能减少专用客户端依赖但增加适配/验证工作；两者的包体和实际成本都要测量，不能仅凭模型名字选择。bge-m3 / qwen3-embedding 尚不能证明接口是 OpenAI 兼容格式。

隔离 Maven 探针位于 `tmp/architecture/probes/spring-ai-dependencies`，只解析依赖树，不编译主插件、不启动 IDEA、不访问模型 API。原始树及结果见 `evidence/p0-dependency-probe.json`。仅 Spring AI BOM 的两组树暴露了第三方版本选择差异，因此补做官方依赖基线的对齐解析；依赖树解析成功不证明实际存在或不存在类加载/API 冲突。

Spring AI v2.0.1 父工程使用 Boot dependencies BOM 4.1.1。P0 对齐探针只导入其版本约束，不引入 Boot starter 或启动 Boot；Spring AI BOM 负责 AI 模块，第三方版本仍需一致的约束来源。依据 [固定 tag POM](https://github.com/spring-projects/spring-ai/blob/v2.0.1/pom.xml)、[单独使用依赖管理](https://docs.spring.io/spring-boot/gradle-plugin/managing-dependencies.html#managing-dependencies.dependency-management-plugin.using-in-isolation)。

2.0.1 的 OpenAI 模块使用官方 openai-java/OkHttp，而 DeepSeek 仍有 Spring HTTP 栈。最终方案需分别验证 HTTP/代理/超时/流式/取消/重试及资源释放，并检查 Spring、Jackson、Reactor、Kotlin和日志绑定；第一版流式显示已确认，当前没有模型网络运行验证结果。

## 5. 小型课程库的本地存储/检索提案

H2 已在 user-turn-029 被用户选定。它可在同一 JVM 中通过 JDBC 提供磁盘存储和事务；与用户确认的单 IDEA 进程共享范围相容，仍需针对本插件类加载器验证。建议由应用级服务拥有数据库与课程库，项目服务通过统一接口管理自己的 session。应用级装配仍为提案，未选版本或最终目录。项目移动保留、复制不继承的身份契约见 `PERSISTENCE.md` 第 5.1 节。见 [H2 特性与连接模式](https://h2database.github.io/html/features.html#connection_modes)。

业务记录、片段、向量、嵌入空间及代次元数据统一持久化，PDF 大文件旁存；短事务处理草稿提交、回答/证据、激活指针和 session 记录删除。会话删除仍必须处理旁存文件、缓存、诊断和布局引用，不能把数据库级联删除当作全部完成。

用户在 user-turn-028 指定使用 JVector，先前应用侧精确扫描提案被替代。JVector 图与原始向量/映射固定到同一激活代次，模型/维度/编码/度量一致；无效数据不能伪装成正常无命中。实际模型协议仍是前置条件。

CourseRetriever 保留项目端口，通过 Spring AI EmbeddingModel 取得查询向量，调用 JVector 查询固定代次图，将 ordinal 映射回 chunkId 及 document/sourceVersion/physicalPage 证据。H2 保存原始向量/片段/配置，首版优先评估后台恢复内存图，不重新请求文档嵌入。JVector 的 ANN、分数映射、独立查询器及恢复中等待规则见 `JVECTOR_RETRIEVAL.md`。版本、编码、K、搜索/构建参数、阈值及性能未定，不保留自动切回全扫描的路径。

若 N 个片段、维度 d、采用 float32 编码，`4 × N × d` 只表示向量原始载荷字节数，不含文本、对象、数据库、缓存和暂存代次开销。约 1000 页不能推导 N 或 d。应实测冷/热查询延迟、内存、启动、入库耗时及引用效果，远程嵌入耗时单独计量。

比较记录：SQLite JDBC 需要考虑原生库提取/加载；SimpleVectorStore 的 JSON 保存不替代本项目业务事务；Qdrant/PGvector 会增加实例/连接边界。向量检索与业务数据库现按用户指定的 JVector、H2 收敛；版本及运行兼容仍待验证，见 [SQLite JDBC](https://github.com/xerial/sqlite-jdbc/blob/master/README.adoc)、[SimpleVectorStore API](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/vectorstore/SimpleVectorStore.html)。

当前 H2 嵌入式路线下，取消不能用中断正在执行数据库 I/O 的线程实现。操作失效、HTTP 取消与短数据库提交分开；session 存在、操作有效及未删除等条件必须在短事务的条件写入/提交时校验，提交后的检查只控制界面回包，不能代替落盘前的写入权判断。不通过关闭文件锁或开启自动服务端模式绕过另一个进程的占用。该边界依据同一 H2 连接模式文档和现有删除/终止竞争规则，需要纳入实现验证。

## 6. P0 通过条件（尚未全部执行）

| 项目 | 当前证据/需要的结果 |
| --- | --- |
| 环境 | 本机 IDE/JBR/JDK/架构已观察；按用户更正仅验证本机当前版本 |
| 依赖 | 已执行隔离树解析，结果文件逐项记录；尚未编译/构造客户端/测量插件包 |
| 最小插件 | Java 编译、描述文件、Java 插件依赖、构建/安装包验证；未执行 |
| Sandbox | Tool Window 创建/切换/关闭、后台任务与服务释放；未执行 |
| Markdown / 高亮 | 本机八个 class 静态检查、独立 Swing 合成颜色与本地前缀解析探针成功；IDE 实际高亮/配色/资源/长历史未验证，范围见 STREAMING_RESPONSE.md 第 5 节 |
| PSI | 本地选区/方法/类结构、文档版本、正常缺失与错误边界；未执行 |
| 模型构造 | 插件类加载器内构造并用可控响应验证，核对重试/取消；未执行 |
| 流式协议 | 每 ModelStep 唯一订阅、branch/step 正文隔离、tool_calls 与最终结束、部分保存/断流/取消/重试及围栏；设计见 STREAMING_RESPONSE.md，尚未运行真实流 |
| 本地存储 | 事务、重启恢复、删除全清、取消与迟到写入；未执行 |
| JVector | Java25.0.2 无额外 VM 参数的小型内存构建/查询探针成功；目标 JBR/插件类加载、H2 恢复/代次/并发与真实规模召回/性能仍未验证 |
| 单一本机基线 | 构建及CI对齐本机当前版本，不额外验证旧build；Plugin Verifier不替代IDE业务运行测试 |
| 真实 API | 课程网关准确路径/模型/维度/批次限制及账户连通；未验证 |

P0 不以“程序启动过”或“依赖能下载”作为全套通过。完成后才能把运行兼容性作为四人并行接入的可靠基线；正式候选/冻结仍走架构流程。摘要模型、Token 预算与失败参数的实施决策继续交给对应成员，本文件不填具体值。

## 7. 已采纳 ReAct 的新增验证

主流程见 `REACT_ORCHESTRATION.md`。P0 需用可控响应验证：工具定义/参数聚合、零/一/多次往返、只执行已完整校验请求、唯一循环所有者、每步骤一次网络请求、结果配对与真实结束、有限控制、步骤预览及旧分支写入失效。DeepSeek 带工具思考模式的协议字段与历史压缩兼容、课程网关准确工具能力仍需分别验证。未支持工具的服务不自动退回旧单次 RAG 流程并宣称满足新主流程。

## 8. 最新确认带来的验证

user-turn-032 增加开停/API key 变更门槛、MD5/资料视图选择后更换和移除（user-turn-033 更正）、过程折叠和中断输出保留显示/排除模型输入。可控验证需覆盖全部网络入口的配置 epoch、旧回包拒绝、所选资料版本及索引原子激活、跨资料字节复用/切换选中项不改变旧操作目标、前后版本证据、重试不擦旧显示，以及摘要/缓存/协议不得带回中断内容。具体方案见 F0_BEHAVIOR.md、COURSE_LIBRARY.md；这些不是已通过测试，详细验收和排期继续待定。
