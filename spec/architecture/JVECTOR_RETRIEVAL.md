# F0 JVector 检索与索引代次设计

更新：2026-10-16（Asia/Shanghai）。用户在 `evidence/user-turn-028.md` 指定向量检索使用 JVector，后在 user-turn-029 选定 H2；取代先前自定义精确全扫描的代理提案。本文的具体版本、存储结构与代次算法仍为讨论提案，权威状态为 `WORKING.json`。

## 1. 最小职责

| 组件 | 职责与当前状态 |
| --- | --- |
| Spring AI EmbeddingModel | 调用远程嵌入 API，生成文档/问题向量；具体网关协议仍待验证 |
| H2 业务存储 | 用户已确认采用 H2；耐久保存片段、原始向量、嵌入空间、代次、来源及页码，具体版本/结构仍待验证 |
| JVector | 已确认的向量检索引擎，插件内构建/查询图索引；不新增独立服务 |
| CourseRetriever | 项目端口与适配：固定代次、调用 JVector、校验结果映射及分数，返回真实课程证据 |
| 托管 PDF 目录 | 保存固定导入版本；历史引用不依赖图索引持续存在 |

JVector 的官方 Maven 坐标为 `io.github.jbellis:jvector`；初始化构建采用4.0.1，真实IDE/完整数据链仍待验证。见 [官方仓库](https://github.com/datastax/jvector)、[发布构件](https://central.sonatype.com/artifact/io.github.jbellis/jvector/4.0.1)。按用户最新更正，始终只验证本机当前IDEA版本，构建及CI与其一致。

## 2. MVP 优先提案：耐久向量＋内存派生图

H2 已由用户选定。优先实现提案将 float32 原始向量、片段和索引配置保存到 H2，JVector 内存图由固定代次数据派生；首版不同时引入持久图文件的发布/回收流程。重启从本地已有向量后台恢复图，不重新请求文档嵌入；每次新问题的查询向量仍按正常流程取得。向量编码及图恢复策略尚未实测或定版。

一份不可变 GenerationHandle 同时拥有 generationId、embeddingSpace/config 指纹、JVector 图、供评分的原始向量和 ordinal→chunkId 映射。ordinal 只在本代次有效；不能以新图的 ordinal 读取旧正文，也不能让图脱离向量/映射生命周期。官方内存图不自行拥有外部原始向量，见 [内存图教程](https://github.com/datastax/jvector/blob/main/docs/tutorials/1-intro-tutorial.md)。教程作为功能依据，签名需按最终版本核对。

代次配置记录模型身份/维度/编码、向量数据及来源指纹、距离与分数语义、JVector 版本和构建参数。构建前校验数量/维度、有效有限值、非零范数及映射完整性；损坏数据不能成为正常无命中。

新导入先完整保存暂存代次并构建/验证新图，再由应用服务统一激活耐久代次与对应内存引用。查询通过统一服务取得匹配 handle，不分别读取数据库新指针和内存旧图。未完成/提交失败不报告激活成功；旧就绪代次依既定规则保留。在途请求固定旧 handle，后续切换不改写其正文与证据。

旧 handle 在没有在途使用者后才释放，session 删除只结束自己的查询使用权，不删除共享图。应用退出/卸载处理全部图与查询资源；生命周期实现仍需验证。

## 3. 恢复、等待与检索失败

每个目标代次共享一个应用级后台构建 future，成功或失败都有明确终点；避免多个项目/session 重复构建同一图。数据库保存的激活代次恢复时仍使用原 generationId，不因为重建内存图而换业务身份。

已有库但图仍在恢复时，查询固定目标代次并可取消地等待，UI 显示“课程索引恢复中”。这不是未建库或正常无命中；等待不占用 EDT、数据库事务或 PSI 读锁。某个 session 取消等待不取消共享构建，取消/删除后也不能继续写回该请求。

恢复/构建异常在开发时暴露；实际使用按已确认检索故障路径提示，再自动通识回答，标明检索失败和没有本轮课程证据。不自动切回全扫描、其他检索库或外部向量服务。正常未建立任何激活库与已就绪索引的正常无命中继续按各自已确认标签处理。

## 4. ANN、度量与查询资源

JVector 图搜索是 ANN；评分使用 exact provider 只说明所评分候选的分数计算，不保证全库精确 Top-K。检索质量需与离线精确基准比较，不能沿用旧扫描方案的完整召回承诺。

固定 4.0.1 的 COSINE 返回 `(1 + cosine) / 2`，同向/正交/反向约为 1/0.5/0。阈值必须绑定度量/分数语义，不能把原始 cosine 的阈值直接套入；若端口转换为原始 cosine，要明确转换并保留语义版本。见 [固定度量源码](https://github.com/datastax/jvector/blob/4.0.1/jvector-base/src/main/java/io/github/jbellis/jvector/vector/VectorSimilarityFunction.java)。

图及向量可按不可变代次共享，但每次查询使用独立 GraphSearcher，避免多 session 共用其可变队列/visited 状态；资源随查询结束释放。依据 [固定查询器源码](https://github.com/datastax/jvector/blob/4.0.1/jvector-base/src/main/java/io/github/jbellis/jvector/graph/GraphSearcher.java)。Top-K、构建/搜索预算、阈值与缓存由负责成员基于真实资料测量，不填预设数字；首版先评估完整浮点向量，不默认追加 PQ/量化。

## 5. 打包与 P0 证据边界

优先验证无需用户修改 IDE VM options、不开启实验 native 路径的 JVector 运行线。向量化 provider 的能力选择与业务检索故障分开：标量实现仍是 JVector 图搜索，不是回到自制全库扫描。固定版本、multi-release jar、JBR 模块和插件类加载都需核查，不能从主分支或命令行 JDK 成功外推 IDE 运行结果。

固定 4.0.1 在向量模块不可用时有 DefaultVectorizationProvider 路径，实验 native 为显式启用；不要求插件擅自设置 JVM 全局属性。消费端基线与构建 JDK 分开理解，具体依赖和 Java25/JBR25 行为仍需实测。依据 [固定 README](https://raw.githubusercontent.com/datastax/jvector/4.0.1/README.md)、[固定 provider](https://raw.githubusercontent.com/datastax/jvector/4.0.1/jvector-base/src/main/java/io/github/jbellis/jvector/vector/VectorizationProvider.java)。4.0.1 新增 JMX/全局构建配置，P0 还需检查依赖冲突、后台线程、单例及插件卸载，见 [发布说明](https://github.com/datastax/jvector/releases/tag/4.0.1)。

官方也提供 GraphIndexWriter / OnDiskGraphIndex 的持久图路线，见 [磁盘图教程](https://github.com/datastax/jvector/blob/main/docs/tutorials/2-disk-tutorial.md)。若启动时间/内存测量要求改用它，应补齐文件版本、暂存/发布、读取句柄与回收设计；本轮不同时实现两套方案。

P0 需要分别留证：依赖/Java25无额外模块标志的构建查询探针；目标 JBR/插件类加载；内存图恢复和共享等待；ordinal/来源一致；并发独立查询器；新旧代次切换；无效向量/恢复故障；真实规模的构建/启动/内存、延迟和召回效果。任何小型探针都不替代这些验证。

本轮已完成隔离命令行探针：JVector 4.0.1 在 Oracle Java25.0.2 下，无额外 JVM 参数、无向量模块/native 请求，实际选用 DefaultVectorizationProvider，成功构建八个四维合成向量的内存图并查询得到预期 ordinal 0/1/2。证据见 `evidence/jvector-command-line-probe.json`。它仅验证这条小型 API 运行线，不证明目标 JBR/IDE 类加载、真实召回/性能、H2 恢复或卸载行为，参数仍是探针专用。

依赖来源附记见 `evidence/jvector-probe-repository-note.json`：本次 Maven 解析实际使用继承的 Aliyun 镜像，不宣称 Central-only 隔离解析；构件 hash 与运行日志已保存。后续受控构建需同时隔离全局和用户 Maven 设置，本轮不为此重复运行图查询。

分工仍按 5:1:1:1：负责人-2承接代次/向量存储及图构建，负责人-3承接查询/映射/证据；丙只展示准备/检索状态，甲乙边界不扩大。具体参数与版本由负责成员实现验证后回填。

## 6. 第二组实施回填（2026-10-18）

`CourseLibrary` / `GenerationHandle` 已落地 H2 float32-le 原始向量、带指纹与 ordinal 的耐久代次、每查询独立 GraphSearcher、旧 lease 保留和每代次共享恢复 future。查询结果包含固定 generation/chunk/document/source/物理页/页内范围；同维不同空间拒绝查询或复用，空间/处理变化通过显式全库 reindex 发布。没有全扫描回退。实际插件内已运行合成入库、查询、关闭重开后的本地向量恢复与旧 attempt 移除后检索；仍不能据此证明真实课程召回或服务配置。

schema v1 固定编码 float32 little-endian、JVector4.0.1 与 COSINE [0,1] 分数语义；恢复校验配置、向量/正文 SHA-256、数量和 ordinal。分块/图/Top-K/阈值没有生产默认值，当前合成参数仅供边界验证。详情、准确运行证据和未执行项见 [WORK_GROUP_2](../tasks/dev-manager/WORK_GROUP_2.md) / STATUS。
