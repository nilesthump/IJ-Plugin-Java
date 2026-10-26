# G1 契约、存储和能力基础

2026-10-26。负责人：用户本人。基线为 G0 PR #18 合入 main 的 0eecc0e；既有 dev-manager 集中交付。此组提供可验证基础，不宣称 G2–G6 或完整 F2 产品可用，架构未冻结。

## 实际行为

- 新增纯 Java EditContracts、EditTargetPort、EditPreviewPort、EditApplyPort。请求固定 project/instance/session/turn/attempt/operation/epoch 与指令引用；目标线索不授予路径权限。Document before/after 保留原文，哈希按精确 UTF-16BE code unit 计算，磁盘编码/保存状态单列。范围是起含终不含 UTF-16 offset；重复/交叉/越界范围拒绝。
- 旧 ReactData.Input 四字段、旧构造器和缺 captureOffsets/captureOrder 的 JSON 不变。F2 使用独立 schema/payload format=1，未知版本明确失败。1,000,000 Java chars 是内存防护上限，不代表已验证实际编辑规模；超出报错，不截断原文。
- ca_edit_artifact 保存不可变产物，ca_edit_review 保存资格/本地决定，ca_edit_preview 保存实际展示凭证，ca_edit_apply 分离新 APPLY 意图/回执和批准时的准确凭证，ca_edit_command 保存本地命令结果。各表受真实 session 根约束，删除根时级联清除私有内容。
- stage 要求当前 GENERATE 操作/实例/epoch、LIVE 根及有效父分支。同一逻辑请求只能对应同一产物，不能覆盖。完整产物暂存后仍不可审核。
- ReactRepository 的 afterSave 在同一 H2 Connection 中处理父成功资格、实际成功 proposeCodeEdit call 来源、候选 REVIEWABLE 与父槽释放。任一步失败整体回滚。父停止、失败或失效不提升；终态 Snapshot 不因审核再次保存。
- OpenEditPreview / ApproveEditProposal 明确走独立本地分派；G1 没有真实适配器，因此返回 NOT_SUPPORTED，不占槽、不消费 draft、不创建 turn、不读取生成资源。RejectEditProposal 仅在准确展示凭证及耐久父资格成立后记录拒绝，不进入 ReAct。
- 仓储提供 G5 将消费的 beginApply/applying/outcome 基础。新 APPLY 使用新身份，意图耐久后才能进入 APPLYING；相同命令或同一候选的新命令返回原意图，不重复占槽。原批准凭证独立保留，未知结果查询不依赖已失效预览记录。应用结果与磁盘状态分开，存储回归中的 APPLIED 回执是合成夹具，不是实际 IDE 编辑证明。
- 停用/关闭/重启撤销预览、失效未批准候选；未收口 APPLY 转为 OUTCOME_UNKNOWN，保留可见 blocked 和原事实。不自动编辑；普通 RecoverSession 不得清除此阻塞。真实结果核验/补记适配由 G5 完成，G1 不伪造“恢复成功”。
- 删除先 LIVE→DELETING，再删除 session 和全部 F2 私有行/恢复票据。迟到 stage/apply/outcome 同事务检查拒绝；没有独立旁存或自动回滚用户源文件。
- 未来上下文只投影有合格父来源的本地状态事实，不复制候选 before/after、失败生成正文/thinking/protocol。事实快照与指纹在摘要调用前共同固定，摘要激活重验；普通历史投影也用同一事实快照剔除旧摘要。无 F2 事实时保持原 F0 指纹。
- ToolBoundary 固定 COURSE/TARGET/PROPOSAL 三类能力，只有安装的能力进入模型 schema。默认 F0 构造器不装 F2；可选宿主适配器用于后续 G2/G3。无课程不关闭独立 F2 能力；F2 错误/取消不落课程降级。模型不获得 apply、任意文件或命令执行入口。

## 实现选择与后续边界

G0 的单文件/整份审核/不强制保存边界继续有效。生成模型子调用、失败逻辑请求的预留/去重、源码范围及补丁验证由 G3 落实；G1 的 stage 唯一键不是已实现完整生成重试协议。真实项目定位/同版采集是 G2；原生 Diff/按钮是 G4；写命令、Undo、实际磁盘观察及未知结果核验是 G5。

G1 只提供平台接口，不注入假的生产 Diff/apply adapter。旧实例原始候选可通过受当前根守卫的 load 回看，不能直接恢复旧批准；重新验证/展示策略由 G4/G5 接入。

## 验证

EditFoundationTest 使用真实 H2、真实 SessionRepository/SessionCoordinator 验证资格事务、回滚、版本/归属、重复命令、本地事件零网络、未知结果阻塞、删除/迟到、旧格式迁移、实际重开、摘要事实竞争。ToolBoundaryTest 使用真实 ReactEngine 和合成适配器验证能力独立、错误分流及固定工具拒绝。F0 原有回归继续执行。

两路独立只读检查已闭合：修复未知结果的新 commandId 去重，以及摘要投影与事实之间的竞争；新增对应回归。最终统一验证、PR head/门禁/main 核验见 STATUS/HANDOFF。真实付费 API、原生 Diff/Undo 和人工 F2 验收仍 NOT_RUN。
