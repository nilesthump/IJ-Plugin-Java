# 第二组：资料与检索

日期：2026-10-18。对应负责人 README 第 2 组。这里记录已实现的 manager 核心及合成验证边界，不把实施提案改称正式冻结，不包含第 3–5 组。

## 已实现行为

- `CourseAssistantApplication.courses()` 惰性持有唯一 `CourseLibrary`；后台初始化/资料作业/查询与 H2 单写入入口分离。构造服务不访问磁盘/网络。应用释放撤销作业和 attempt lease；单个项目关闭不销毁共享库。
- `ManagedPdfStore` 先复制、强制写出，再对固定副本计算 MD5。相同字节只安装一个托管 blob；解析、引用和恢复验证只使用托管路径。blob、document、sourceVersion、derivation、generation 分别有身份。重复导入区分就绪、处理中/失败/已移除及需要重新索引；不将“有 MD5”当作可检索。
- 更换固定 `MaterialTarget` 与 `CorpusBase`，保留 documentId。历史同源字节可复用该资料的 sourceVersion 和完全兼容的派生结果；另一资料只共享 blob，来源、显示名和 chunk 映射独立。页原文及空白页原样耐久保存；按物理页分块，记录 UTF-16 开闭区间且不切开代理对。
- `EmbeddingSpace` 包含 provider/endpoint/精确 model/维度/document 与 query 前缀/部署修订。只有整个空间和 reader/chunk 配置兼容才能复用向量。`CourseEmbeddings.springAi` 通过 Spring AI 显式提交 model、维度与不同前缀；校验响应数量/index/维度、有限值和非零范数。不探测账号，不替用户选真实 alias，不在适配器重试或换模型。
- H2 corpus schema v1 保存原始 float32 little-endian 向量、页、片段、来源、配置、代次成员/ordinal、任务状态与来源引用。派生结果和代次保存 SHA-256 指纹及数量，读取校验正文/向量/映射/配置一致性。未知 schema、缺失根或活动目录不匹配报错，不清库后冒充首次使用。
- 先保存完整候选数据、构建 JVector 图，再在同一短事务中复验全库基准、资料修订/源版本和任务资格，共同发布资料目录和代次。失败、取消、过期基准不发布；不同资料并发发布不会丢失已成功更新。图构建/远程嵌入不持数据库事务。显式 `reindex` 重建整个活动集合，普通单项更换拒绝混用处理/嵌入空间。
- `GenerationHandle` 同寿命持有不可变图、原始向量及 ordinal→chunk/source/page 映射；每次搜索独立 GraphSearcher。固定 JVector 4.0.1 COSINE 分数为 `(1 + cosine) / 2`，阈值使用 [0,1]。这是 ANN，不承诺全库精确 Top-K；没有全扫描或外部服务兜底。
- 每个 attempt 通过 `openAttempt()` 固定代次或明确无库状态。首次和后续课程检索/证据读取均使用该 lease；新 attempt 才看到更换/移除结果。重启每代次共享一个后台恢复 future，从 H2 原始向量重建图，不解析 PDF 或重嵌入文档。RESTORING、FAILED、EMPTY、READY 分开；一个查询取消等待不取消共享恢复。
- 旧在途 lease 和耐久 owner/source 引用阻止清理。`previewCleanup` 展示源版本及 blob 集合；`cleanup` 再校验活动集合/引用/占用，只按预览删除无引用旧版本。共享 blob 最后一个关联释放后才删。跨介质失败保留 deleting blob 身份供显式重试；不会静默按容量淘汰。会话删除释放哪些 owner 由第 4 组接线，不能据此宣称 session 全清已完成。

## 接入和参数

生产入口在应用服务上，PDF 仍经唯一 `PdfReader` extension point 获取。当前 main 尚无乙的解析器注册；未注册会明确失败，核心没有自建成功空 reader。现有公共 ApplicationController/SessionController 的业务和配置/凭据/epoch 接线仍属第 4 组，UI 合成控制器保持原来的 NOT_SUPPORTED，不把本组核心伪装成已完成的产品交互。

所有 index/chunk/search 数值由调用者显式传入，没有生产默认值。自动场景采用三维合成向量、512 UTF-16 字符/20 重叠、图 degree=2/beam=8/overflow=1.5/alpha=1.2、Top-K=3/分数阈值0.9；IDE 场景用 200/10、Top-K=1。参数仅验证 API/归属/代次规则，未对真实资料选择或测量。并行图构建使用本库拥有的单 worker pool，不设置 IDE/JVector 全局属性，不启用 Vector API/native 参数。

调用方须提供冻结 runtimeEpoch 的 `BooleanSupplier`，在每次远程嵌入前后及发布/交付前复验。`Job.cancel()` 撤销本地资格；`completion` 与 `settled` 分别表示结果终点和后台资源/任务状态已收尾，不宣称远端物理请求已取消。取消不打断 JDBC；平台取消原异常沿 stage 传播并记录为 CANCELLED，不能成为检索故障/通识降级。

## 验证与未执行

准确命令、退出码、独立检查和交付门禁见 STATUS/HANDOFF。核心场景包含固定副本/空白物理页/ordinal 映射、当前/历史/跨资料 MD5、CAS 冲突/移除竞争、失败/取消、同维异模型/全库重建、共享恢复/并发查询、损坏持久数据、耐久引用与清理失败重试、最终指针写失败时整个发布事务回滚。

真实 IDEA headless 使用相同生产核心，验证插件类加载器内 H2 入库/JVector 证据/本地向量恢复/旧 attempt 保留。PDF 端口与嵌入值为显式合成输入；Spring AI HTTP 仅访问进程内 127.0.0.1。真实乙 PDF 解析联调、课程平台模型/维度/批量限制/编码策略、真实课程召回/内存/时延/千页规模、可视 UI、生产会话/ReAct/凭据开停均为 NOT_RUN 或所属后续组。没有调用付费模型。
