# F2-G1：契约、状态/存储与能力基础

负责人：用户本人。依赖：G0通过并同步main。状态：LOCAL_VERIFIED / AWAITING_PR（2026-10-26）；范围见 G1_IMPLEMENTATION.md，最终交付以STATUS为准。

## 工作项

1. 依据F2_API_PROPOSAL落实纯Java请求/目标/快照/候选/审核/应用回执DTO和平台端口；保留既有Input四字段及混排顺序，旧JSON/构造入口有明确兼容策略。
2. 新增独立本地Approve/Reject/OpenPreview事件与APPLY操作分派骨架。批准/拒绝不得落入SubmitDraft/Retry/Compact调度；没有真实实现时明确不支持，不能返回假成功。
3. 建立独立F2仓储/schema迁移与session根守卫：不可变产物、审核状态、应用意图/事实分开；父有效终态、候选资格提升与槽收口设计共同事务协调。
4. 抽出最小工具执行/能力边界，使课程、定位、候选可用性与错误分类独立；不是引入泛化任意文件/执行注册器。旧课程实现保持。
5. 明确checkpoint指纹/事实投影、session删除、停用/关闭及未知应用结果恢复票据的处理；不把本地事件附回已终态ReactSnapshot。

## 落点与验证

拟落点：contract、core/react、core/edit、core/session、storage及对应测试；实际文件需服从G0清单。同步实际API.md，提案文档不冒充源码。

验证旧库/旧F0 JSON重开、无字段兼容与不支持版本明确失败、跨项目/session/instance守卫、重复命令先返回原记录、父终态保存失败不能批准、删除/迟到写不能复活。新增本地按钮事件路径对模型/嵌入/ReAct调度计数均0。

## 退出条件

契约可编译、兼容/守卫测试有实际证据、存储事务与资格联动设计可验证；仅骨架不宣称F2可用。统一All通过及独立检查闭合后推进；阶段交付按既有PR规则。
