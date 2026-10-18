# 第一组：公共骨架与运行边界

日期：2026-10-18。对应 README 五组工作中的第 1 组；不表示第 2–5 组或完整 P0 已完成。架构仍为实施方案，未冻结。

## 交付范围

- 注册惰性应用/项目服务。应用拥有共享 H2 入口、后台执行器、运行状态与 epoch；项目只拥有打开实例身份和任务 scope。关闭一个项目不释放应用存储。构造服务不执行磁盘或网络操作。
- `RuntimeGate` 保留 OFF → STARTING → ON → STOPPING → OFF；开始停用先失效旧 epoch。配置与凭据二次校验、停止后保存协调、公共控制器接线仍属第 4 组，不把内部 gate 当已完成的用户开停功能。
- `TaskScope` 关闭时拒绝排队任务和迟到结果，不中断 H2 I/O；它不是数据库写权限栅栏。平台取消原异常通过异步阶段传播，不转换成检索失败。
- `H2Store` 在单后台写入线程运行短事务，成功提交、失败 rollback 并传播原 cause，关闭后拒绝新请求且排空已接受短事务。应用目录为 IDEA config 下的 `courseassist-f0`，不置于可清理的 system 缓存。这里只提供连接边界，无业务 schema、项目迁移、会话恢复或资料持久化。
- 增加类型化 codeCapture/pdfReader 扩展点和唯一适配器查找；缺少或多重注册明确失败。保留既有公共 DTO、合成 fixtures 与占位 Tool Window，成员范围没有改动。
- 保留 Java25 / Spring AI2.0.1 / H2 / JVector 组合。固定既定 JUnit5 BOM，避免 Boot 版本约束把首次实际测试升级到 JUnit6。测试使用 Spring AI 2.0.1 实际 HTTP 构造，未引入 Boot launcher、独立服务或生产网络调用。
- `SpringAiTransport` 显式提供 JSON/text/SSE 消息转换器，避免 Spring 自动探测 IDE 内不兼容的 YAML 类；`ModelClassLoading` 限定调用的 TCCL 并恢复原线程。Spring AI JacksonUtils 自行指定其类加载器，所以还显式加入与 BOM 一致的 Jackson3 Kotlin 模块（不是业务 Kotlin 源码）；排除重复 SLF4J API，沿用 IDE 日志桥。修复依据为真实插件内的 ServiceConfigurationError / VerifyError，不是更换框架或吞错。

类加载边界参考 [JetBrains 类加载器说明](https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html)；日志归属参考 [JetBrains SLF4J 说明](https://blog.jetbrains.com/platform/2022/02/removing-log4j-from-the-intellij-platform/)。手动 Spring AI 构造参考 [官方文档](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html)，准确 2.0.1 签名以本机依赖字节码与实际编译为准。

## 验证与限制

准确命令、结果与 main/PR 状态记录在 STATUS/HANDOFF。JUnit 验证任务范围撤销、原异常、应用 epoch 失效、H2 提交/回滚/重开与不打断已接受 I/O，以及 JVector/Spring AI 合成响应。

IDE 验证入口 `scripts/validate.ps1 -Scope All -IdeSmoke` 使用独立测试插件启动隔离 IDEA 命令。报告在 `build/reports/ide-smoke/result.json`；检查服务归属、Swing content 释放、后台 PSI、插件类加载和合成依赖调用。headless 验证与可视人工验收分开；本地合成 HTTP 与真实平台验证分开。合成向量/图参数仅供三节点 API 检查，不能当生产检索参数或召回证明。

第 2 组继续资料版本/托管 PDF/嵌入空间/代次与检索；第 3 组继续 Spring AI 生产调用和唯一有限 ReAct/流式；第 4 组继续耐久项目身份、session/草稿/删除/恢复/凭据与完整开停协调；第 5 组继续跨模块集成和最终交付。公共控制器业务仍未实现，不以资源骨架代替这些功能。
