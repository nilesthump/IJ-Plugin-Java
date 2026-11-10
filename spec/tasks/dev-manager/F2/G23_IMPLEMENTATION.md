# G2/G3：固定目标与受控候选

2026-10-26。用户本人负责，既有 dev-manager；基线为 PR #19 合入后的 main `1ec4b9412d8c70b20a404da6c9a499d82bae1654`。本轮授权完成接下来的 G2/G3，统一一个 PR。G4–G6、Diff/审核 UI、真实应用/Undo 不在本轮范围；架构未冻结。

## G2 实现

- `editor/f2/JavaEditTargetPort` 固定原项目/实例与已提交选区；不读取完成时当前编辑器或会话。仅查项目目录内的 Java source roots，同时检查 canonical 文件的项目归属；线索不授权任意路径访问。
- 非空选区从已冻结 CodePart 取 fileRef、stamp、原文与 UTF-16 坐标，补采同版完整 Document。文字与选区冲突不改绑。多选区或缺少修改指令明确要求新 draft 补充。
- 无选区按文件名/项目相对路径、简单或限定类名、方法名、规范参数类型查找。`f()` 明确零参数；`f` 配空参数列表表示参数未知，不能假称重载唯一。嵌套类单独定位。仅路径时允许整个文件，仅类/方法时限制到相应 PSI 范围；选区存在时不扩大到方法外。
- `UNIQUE/AMBIGUOUS/NOT_FOUND/NOT_READY/INCOMPLETE/STALE` 分开返回。查找最多访问 10,000 个内容节点、256 个 Java 源文件、20 个目标，超过即 INCOMPLETE。Document 上限沿用 1,000,000 UTF-16 单元；不是实际规模验收结论。
- 后台非阻塞读作用域校验真实 Document/已提交 PSI 的文本与 stamp；保留未保存编辑，不强制保存/格式化。完整 before、允许范围、URL、独立 VFS id、模块/签名和请求来源固定到快照。Target 新增可缺的 fileIdentity 保持旧 G1 JSON/构造兼容；旧缺身份记录不构成未来应用授权。
- 完成读操作后在异步工作线程向 `ca_edit_snapshot` 保存完整快照；H2 等待不进入平台读锁。根 LIVE、原实例/operation、父 ACTIVE 分支同事务检查；不修改原提交。表随 session 级联硬删除。

## G3 实现

- 生产 Project 服务在打开 session 时注入真实目标适配器；SessionCoordinator 为原始 SubmitDraft 创建独立 CodeEditTools，现有唯一 ReactEngine 执行目标/候选工具。无课程库仍开放 F2；错误不进入课程降级。默认无适配器的 F0 测试/构造继续支持。
- 宿主冻结整次 attempt 的第一次解析及其分支。重复 resolve 不改目标/范围；课程降级后只报告需要新 draft，不在新分支重新采集或重新生成，旧 snapshotRef 不能跨分支使用。
- 逻辑 requestId 来自原 project/session/turn、固定目标/VFS身份/stamp/before hash、范围和原始指令；模型只能交定位线索或宿主 snapshotRef，不能自造 requestId、替换指令或批准写入。
- `ca_edit_generation` 在子模型订阅前耐久预留；并发/成功/失败后的相同请求共用旧 future/记录，换 toolCallId 不重复生成，各观察仍分别配对。崩溃遗留 RESERVED_OR_INTERRUPTED 不自动重放；该表也随 session 删除。
- 至多一次 tools=[] 子请求，由父 ReAct 所有者同步驱动；使用同一模型端口、deadline、取消句柄、步骤数、累计输出上限，调用前重新测量完整子输入预算。源范围总长上限 24,000 UTF-16 单元，最多 64 个替换块。参数是本轮有限保护值，不宣称真实生成质量或规模。
- 严格 JSON 解析（重复字段/尾随内容拒绝），只接受按绝对 UTF-16 offset 排序的 start/end/before/after。前像精确一致、非重叠且位于明确范围内，构造完整 after 后固定哈希；无 fuzzy patch、imports 优化或额外格式化。无变化不建候选；无效结构、超限、取消与开发异常分别保留。
- 子流正文/thinking 作为不可批准的 Step 档案保存，不进入 branch.protocol。工具仅返回状态/候选引用；父有效分支正常耐久终态才沿 G1 同事务规则提升 REVIEWABLE。父停止、失效或保存失败不提升，不把候选原文带进后续历史/检索/摘要。
- 已调用 F2 工具的旧 attempt 不可 RetryAttempt；尚未调用 F2 的旧 F0 重试保留，但不开放 F2 能力。只有新 SubmitDraft 才能重新定位/生成。G1 的本地审核事件和未实现 Diff/apply 行为不变。

## 验证与限制

统一入口及最终结果见 STATUS/HANDOFF。本轮首轮既有回归通过；新增真实 IDE 检查的模块命名冲突、取消异常包装断言失败均保留在原始日志并修正。第三/四轮同名目标断言实际返回 INCOMPLETE：测试复用目录中保留了上轮截断测试的 257 个文件。已改为每轮独立 UUID 项目目录，不删除原失败证据、不放宽生产查找上限。

新增 JUnit 验证真实 H2 资格、无库生成、分片参数、不同 callId/并发/失败重复、无变化/非法替换、预算前拒绝、共享输出限制、父失败、子流停止、旧 retry 隔离。Spring AI 使用 127.0.0.1 合成 SSE，核验空工具子请求与协议配对，不访问付费服务。

真实 IDEA 检查覆盖源码模块归属、重名/重载/嵌套类、完整前像、未保存 Document、旧选区失效、文字冲突、生命周期撤销、真实 DumbModeTask 索引未就绪，以及 257 个源文件触发的截断结果。没有确定性复现采集中写竞争、会话切换或真正项目关闭竞争；不把旧选区版本测试扩大成这些结论。真实服务、人工 F2 交互、原生 Diff/Undo/源码应用仍 NOT_RUN。

最终统一 All + IdeSmoke 退出 0，34 suites / 196 tests，失败/错误/跳过均 0；check/FixtureSmoke/buildPlugin 通过，真实本机 IU-262.10968.63 / JBR25.0.4 result.json 为 PASS（日志 tmp/f2-g23-final.log）。独立检查及增量复核无阻断遗留。G2/G3 为 LOCAL_VERIFIED；单 PR 最新 validation/scope、main 合入核验仍是交付 DONE 条件。

## PR #20 首次 CI 失败与取消测试隔离

CI run 40309275683 在 head acf3203 上运行196项，仅 stopDuringChildRetainsArchiveButExcludesPrefixFromFutureContext 预期 STOPPED、实际 LIMITED；完整失败日志 tmp/f2-g23-ci-failed.log 保留。原用例等待耐久前缀后才发停止，没有断言停止是否被接受，与夹具10秒生成期限存在竞争。

确定性红测在已保存前缀后延迟11秒，再断言停止APPLIED，实际返回REJECTED（tmp/f2-g23-cancel-red.log）。这证明与CI结果一致的期限竞争路径，不宣称原日志足以确定唯一根因。

仅测试夹具增加可配置 generationTimeout，默认仍10秒；该单个显式取消用例在启用前设2分钟，保留11秒延迟、原STOPPED/单订阅/取消句柄/无候选/档案保留/未来上下文隔离断言，并要求停止命令APPLIED。生产期限、实现和门禁不变。独立增量检查无阻断；修复后统一All结果见状态/交接，原IDE通过证据与本次未重跑分别记录。
