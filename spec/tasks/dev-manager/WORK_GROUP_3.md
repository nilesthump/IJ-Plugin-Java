# 第三组：Spring AI、有限 ReAct 与流式

日期：2026-10-18。基线 `5b945fa`（第二组 PR #4 已合入 main）。本文件描述第三组实现和接入边界，不表示架构冻结或完整 F0 验收。验证/交付状态以 STATUS 与实际 PR 为准。

## 核心与接入

- `core.react.ReactEngine` 为每 attempt 提供一个后台循环所有者；调用者先完成用户提交/单任务槽，再传入冻结 Identity、Input、同 session 历史、已取得的 CourseTools lease、显式 Limits、ErrorPolicy、运行资格和 Journal。核心首次检索不可跳过，所有课程工具沿用原 lease。重试必须新 attemptId，固定原问题和 typed CodePart，不另存用户消息。
- `CourseTools` 只有 searchCourse 和 readCourseEvidence；完整 JSON、白名单、引用资格、批内所有参数及 ID 先验证，之后串行执行。read 只允许本分支已经提供的 chunk。工具调用/结果成对，工具步骤的预览与最终步骤正文分开。
- `model.SpringAiCourseModel` 使用固定 Spring AI 2.0.1 DeepSeek 请求/消息/工具类型和已有 SpringAiTransport，严格解码原始 SSE。此明确边界避开高层转换吞错/结束枚举丢失；无框架循环、memory、advisor 或重试。每 step 只订阅一次，正文/thinking/tool 参数/finish/usage 分离；未知结束原样交核心并失败，EOF/[DONE] 都不能单独证明回答完成。凭据只在应用构造 WebClient 时提供，不进入请求档案或 DTO。
- 仅明确配置的 DeepSeek 风格 SSE 协议实现；model、path、thinking、includeUsage、HTTP 超时由调用方显式提供。本轮不选择生产 alias、不推断课程网关兼容，不调用真实/付费服务。准确接口与思考模式仍需服务证据。
- 每 step 重新核算包含规则、工具 schema、历史、代码、证据及完整配对协议的 Request；预算不足立即停止，无截断代码或递归压缩。Budget 可接实际 tokenizer；`utf8Budget()` 是含消息/工具封装余量的本地保守估算，不是已验证模型 tokenizer。上下文窗口/输出预留必须显式配置；压缩与生产预算选择属第四组。
- Limits 显式限制步骤、工具总数、单工具参数/结果字符、attempt 生成字符、总时限、partial 保存间隔。测试候选为 6 steps / 8 calls / 16000 tool chars / 32000 generated chars / 200000 context estimate / 1024 output reserve / 15 s / 12 chars；仅为可控小场景，不作为真实服务默认值。队列最多 256 个未处理流事件，超出明确超限。字符按 Java UTF-16 计；正文/代码不 trim。
- 最终成功要求真实 stop、非空正文、无待执行工具、合法课程引用及原子耐久保存。引用语法 `[[course:chunkId]]`；核对最终步骤实际输入证据后转 Citation，带固定文档/sourceVersion/资料名/物理页。未知标记失败，普通 Markdown 链接不是可信课程引用。

## 失败、档案与资格

DEVELOPMENT 将原始检索异常交任务边界；PRODUCT 有明确提示并自动通识。补查故障先使旧 branch 失效，至多一次从冻结原输入/合法历史重建无本轮证据/工具轨迹/旧 thinking 的分支并禁用课程工具；最终证据为空。正常无命中不清已有证据。平台取消、运行 epoch 失效和生成故障不触发检索降级。原始故障由 Job.retrievalFailures 暴露，不全量记录私有输入/凭据。

累计 Snapshot 保留每 branch/step 原文、thinking、工具、usage、身份和 attempt 内递增序号。ReactViews 只投影公共 AttemptView，视图观察/重开不重新订阅。cancel() 以原子决定返回是否接受停止：停止先赢则立即撤资格并取消句柄，最终保存先取得资格则返回 false（终态保存已开始），调用方不能报告已接受停止。该决定不等待数据库；epoch/session 撤销仍在短事务 guard 中复验。后台不等待远端确认，可由协调方条件保存已接受前缀。保存失败仍保留未保存/后续发送阻塞边界。所有迟到帧只留在已失去消费者的本地队列，不能改终止快照。观察者抛出的运行异常通过 Job.observerFailures 暴露，不回滚已提交回答或重订阅模型。

`ReactContext.history` 是本轮请求及未来检索改写/摘要/checkpoint 接入的共同资格入口：保留已提交用户输入，仅投影耐久成功最终有效分支的配对协议，排除中断/超限/失败/失效分支生成内容。无任意旧摘要/cache 注入入口；第四组实现 checkpoint 时必须携带 POLICY 与来源资格，不能绕过此规则。本轮不实现摘要模型或完整 checkpoint。

`storage.ReactRepository` 在应用共享 H2 保存版本化累计档案，单条记录含全部分支/步骤和协议；较旧 revision、终止后覆盖、目标不符和不存在记录的晚到写入均拒绝。保存正文/证据/状态与 ca_source_ref 来源保留同一短事务；失效过程引用也保留至 session 删除。保存失败保留内存原文、saved=false、不可用于未来上下文，异常交调用方阻塞后续发送。

Repository 必须传入事务内 Guard（CREATE/LIVE_WRITE/COORDINATOR_TERMINAL/READ/DELETE），先初始化 corpus schema；没有生产 no-op guard。第四组把它接到实际 project/session LIVE、instance/operation/epoch 与删除根栅栏。显式 interrupt 仅将已保存 RUNNING 前缀标中断，不启动网络；deleteSession 在删除 guard 内清档案/引用，保留共享 PDF/索引。本轮的 SQL 根仅是测试夹具，不是生产 SessionController、项目身份/草稿/完整删除实现。

## 验证边界

自动测试使用真实 H2/JVector 和合成分页/嵌入，模型为可控帧或 127.0.0.1 SSE。另在本机 IDEA 的真实插件类加载器运行两步 ReAct、Spring AI 序列化、流式工具聚合、末帧 usage、固定物理页引用和耐久档案。结果待 STATUS 实际回填；可视界面、真实乙解析器、真实服务/生产思考模式、完整第四组会话/压缩/删除及实际规模均 NOT_RUN。

第三组收尾复核：查询许可仅在 Decision.RUNNING 时成立，进入 FINISHING 或 TERMINAL 后不再允许排队查询启动嵌入。回归用受控执行器与 STOPPED 保存栅栏复现“取消→协调保存→lease 尚未释放→释放旧查询”，断言嵌入与模型调用均为 0。52 项 JUnit（新增 21 项）、All + IdeSmoke 最终退出 0；两路独立增量复核闭合。该修正与真实完成记录通过 [PR #6](https://github.com/nilesthump/IJ-Plugin-Java/pull/6) 门禁交付，不开展第四组。
