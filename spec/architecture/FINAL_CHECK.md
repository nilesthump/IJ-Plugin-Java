# F0 方案生成前终检记录

日期：2026-10-18（Asia/Shanghai）。基准：WORKING revision 27 / contentRevision 28。

## 结论

两名从空白上下文启动的独立审阅者均未发现必须再向用户确认的新增 F0 功能/架构决策。可以形成明确区分“已确认、实现提案、待验证、待定”的综合开发方案。

本记录是讨论完整性终检，不是 architecture-review 的正式 candidate/package/report，不满足或替代冻结门槛。没有执行主插件、IDE、模型 API 或存储运行测试，也不据此宣称按期可行。

## 审阅输入与独立性

- 原始材料副本：`tmp/architecture/final-audit-r27/manifest.json`，51 个文件，manifest SHA-256 为 `5a0d4b72a77297ee6f169f6d116e07b289b25242e8a71f3228c3de7b26b75e87`。用户原始答复/作业作为需求证据，代理设计文件为 ADVISORY。
- 修订包：`tmp/architecture/final-audit-r27/amendment/manifest.json`，8 个修订文件及综合方案预稿；原包保留未修改。
- 独立审阅任务：`/root/final_requirements_audit`、`/root/final_contracts_audit`；两次初始调用均为 fork_turns=none，只读取包内原始材料。两者各自核验原包 51 项及修订包 8 项哈希，并回报匹配。
- 审阅者未改文件、未执行产品代码、未联网，不将代理文字当作已确认事实。以上是本次协作工具实际调用和返回的摘要，不是虚构的正式 hostTrace 或候选绑定。

## 发现与处理

| 发现 | 处理 |
| --- | --- |
| 早期讨论仍把数据库/检索产品列为未选 | 对齐 H2/JVector 已确认，剩余仅版本/结构/兼容验证 |
| 旧批次激活问题及未要求的“固定关键材料”界面仍在队列 | 给出单资料任务发布的 MVP 提案，未要求的界面不加入已确认范围 |
| 应用开停/配置/共享资料命令未与 session 事件充分分开 | 补 ApplicationController/ApplicationViewState 和明确资料目标/预期版本命令 |
| “停用完成”门槛不够明确 | 补 OFF/STARTING/ON/STOPPING、旧权撤销、本地收尾/活跃槽与保存失败阻塞；不无限等待远端取消 |
| “优先固定库快照”有歧义 | attempt 建立时固定，包括无库状态，所有课程工具沿用；新 attempt 才读新代次 |
| 主备/session 切换旧措辞 | 对齐应用级当前 profile 提案，准确服务配置继续验证，不制造新的需求问答 |
| 方案预稿来源显示和最终交付检查不够直白 | 补“资料名＋PDF页码/范围”，以及作业原要求的源码、英文报告/演示 PDF、分目录 ZIP |

前五项经修订包再次复核，未发现新增矛盾；最后两处文字完整性修正按审阅原始证据直接补齐。代理提案仍未标成用户已接受。

## 保留的未完成状态

- 用户明确待定：详细验收及排期；实际截止、有效工时和额度仍未知。
- 已委托实施：摘要模型、Token 预算、压缩失败参数。
- 可随实施收敛：准确模型/API/工具协议、内嵌目标兼容、版本组合、检索/分页/事务/恢复/渲染与真实规模验证。
- 仍为提案：具体应用级开停/profile范围、资料移除后历史保留/显式清理、本机迁移、循环与事务字段/算法等。

综合阅读入口：[F0_DEVELOPMENT_PLAN.md](F0_DEVELOPMENT_PLAN.md)。权威讨论状态继续在 WORKING.json；不因生成本文关闭未解决节点、不生成正式候选或修改 CURRENT。
