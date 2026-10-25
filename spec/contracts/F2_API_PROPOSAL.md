# F2 公共边界提案

2026-10-25。与[开发方案](../architecture/F2_DEVELOPMENT_PLAN.md)配套；这是拟新增契约，不覆盖[实际F0 API](API.md)，没有对应源码实现。负责人：用户本人。G0治理落地后，G1才同步实际DTO/端口/API。

## 纯Java数据边界

| 示意类型 | 必要信息与约束 |
| --- | --- |
| EditRequest | 宿主稳定requestId、原project/instance/session/turn/attempt、用户指令引用与修订、可选capture引用、定位线索；不以任意路径作授权 |
| TargetQuery | 用户提供的路径/模块/类/方法/参数线索及请求来源；限制原项目源码范围 |
| TargetResolution | UNIQUE/AMBIGUOUS/NOT_FOUND/NOT_READY/INCOMPLETE/STALE；候选带稳定身份、路径/模块/签名；INCOMPLETE不能伪称唯一 |
| EditSnapshot | 宿主snapshotRef、真实文件/类/方法、完整Document before、stamp、Document文本哈希、允许范围、采集来源 |
| EditProposal | proposalId/revision、snapshotRef、instructionRef、不可变after/哈希、范围、来源branch/toolCall、完整性与父资格 |
| ProposalView | 展示概要、准确版本、是否可审核、已展示标识、本地审核/应用事实；SDK/PSI/Document对象不进DTO |
| ReviewDecision | commandId、明确proposalId/revision及before/after哈希、来源实例/展示版本，APPROVE或REJECT；不接模型自报批准 |
| ApplyReceipt | applyId、精确候选绑定、Document实际结果、磁盘保存状态、后续Undo/漂移事实、可得文件引用与恢复状态 |

公共坐标沿用SourceRange的一基行列/UTF-16规则；内部offset起含终不含，转换有测试。Document前像/after使用同一文本表示计算哈希，保留所有正文；磁盘编码/换行及保存hash另算，不能混同。任何预览前的合法表示转换必须先固化到展示版本，批准后不再改变内容。

## 示意端口，不是已落地签名

- EditTargetPort.resolveAndCapture(context, query)：只读、有界、项目作用域，返回完整结果或明确异常；已有选区仍须有完整同版before。
- EditProposalService.propose(context, snapshotRef, instructionRef)：受控子生成，返回有界观察和不可变产物引用，不写项目。
- EditPreviewPort.open(context, proposalRef)：原生只读副本Diff；记录准确展示版本；关闭仅释放视图。
- EditApplyPort.apply(context, applyId)：只读取宿主保存的批准产物，在IDE写命令复验并应用，返回真实Document结果。它不是ReAct模型工具。
- EditRepository：独立候选/审核/应用记录，复用H2守卫与协调事务，不覆盖已终态Snapshot。

只有目标解析/候选工具可列入模型schema；本地批准/拒绝/应用/恢复不暴露给模型。MCP适配仅包裹同一语义边界，不能获得额外读写范围。

## 本地事件与操作提案

新增ApproveEditProposal、RejectEditProposal、OpenEditPreview、RecoverEditOperation等明确事件，均携带EventMeta与精确候选引用。APPLY为独立OperationKind；拒绝/打开预览只做有界本地处理，是否占短期本地操作资源由G1明确，不能调起生成槽。

两个用户按钮准确为“同意并应用”“拒绝”。本地事件分支不能进入SessionCoordinator的SubmitDraft/RetryAttempt/ManualCompact调度分支。事件不清空draft、不生成用户消息，不伪造assistant/tool协议。支持文字替代按钮的应用协议不在当前已确认范围。

## 存储、迁移及资格

建议新增F2目标/产物、review、apply_intent/receipt表或同等独立结构，身份与session根守卫一致。各记录包含schema/policy版本；旧F0 JSON缺F2集合按兼容规则读取，未知版本明确限制，不删除数据库后伪装首次启动。

候选“可审核”的提升与父有效终态、持久提交及槽释放协调；子模型结束不等于父成功。审核/应用后只更新独立记录与投影视图，不能再次save已终态ReactSnapshot。

生成读写检查根LIVE、原instance及各自生成operation；预览/审核检查根LIVE、原instance、候选来源/修订及父耐久资格，不要求已释放的父生成operation仍活跃。新的APPLY写入校验它自己的operation/epoch/提交资格，不把旧生成operation复用为应用权限。应用副作用单独回执，不升级失败生成历史资格；checkpoint指纹纳入准确审核/应用事实版本。删除清理范围、未知结果恢复票据与根生命周期竞争在G1/G5明确。

## 必须区分的结果

正常业务结果包括需定位、需澄清、未找到、候选无变化、候选过期、busy、拒绝。平台取消、IO/SQL/解析及开发故障保留原cause到明确任务边界，不变成空成功或课程检索降级。

应用结果至少区分未应用、Document已应用、结果未知、回执待恢复；磁盘UNKNOWN/UNSAVED/SAVED单列。重复approve返回原applyId/状态，Undo后不重新应用。新draft发送才允许再次进入ReAct。

逻辑生成请求由宿主按原提交、目标/源码修订、指令修订及范围绑定。toolCallId仅作协议配对，不能作唯一业务去重键；更换callId或模型自造requestId不能重复生成。同请求并发/成功后/失败后重复propose返回既有状态/结果，内部生成订阅不增加；真正重生成需用户新draft提交。
