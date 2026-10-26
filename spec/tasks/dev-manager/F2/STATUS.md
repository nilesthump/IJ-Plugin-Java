# 当前 G2/G3 单 PR 交付（2026-10-26）

唯一负责人：用户本人；分支 dev-manager，基线 main `1ec4b9412d8c70b20a404da6c9a499d82bae1654`。G0/G1 已经 PR #18/#19 合入核验；此前待合入记录为历史。用户授权下两组 G2/G3，只提交一个 PR；G4–G6 不展开，架构未冻结。

G2/G3 实现和本地验证完成，当前 LOCAL_VERIFIED / AWAITING_CI；唯一交付 [PR #20](https://github.com/nilesthump/IJ-Plugin-Java/pull/20)，dev-manager → main，尚未合并。最终 All + IdeSmoke 退出 0，34 suites / 196 tests，失败/错误/跳过均 0；本机 IU-262.10968.63 / JBR25.0.4 的 result.json 为 PASS。行为、有限参数、失败修复及未执行项见 [G23_IMPLEMENTATION](G23_IMPLEMENTATION.md)。不得提前记 main DONE。

命令：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'。

原始日志保留在 tmp/f2-g23-baseline.log、tmp/f2-g23-first.log、tmp/f2-g23-tests.log、tmp/f2-g23-second.log、tmp/f2-g23-third.log、tmp/f2-g23-fourth.log；最终运行 tmp/f2-g23-final.log。既有基线和每轮 JUnit/打包结果与单独 IDE 失败分开记录。独立代码复核原三项（跨降级重复生成、稳定文件身份、零参数重载）已修复并闭合；最终运行证据已核验，独立增量复核无阻断遗留。

下一条：最终 All/IDE、独立检查及28文件暂存范围检查已闭合；实现提交 52fd92f 已推送并创建唯一 PR #20。下一步核验最新 head 的 validation/scope；本轮只提交 PR，不合并。新 PR 未合入前保持待集成。真实付费 API、人工 F2、Diff/审核 UI、Undo/源码应用未执行。

## 以前的记录（原时点状态）

# 当前 F2 状态（2026-10-26）

唯一负责人：用户本人。分支：dev-manager。G0 已完成并在 main 核验；G1 已实现，最终统一验证及独立检查均通过，尚未交付 main；G2–G6 未开始。架构未冻结。

| 组 | 实现/交付 | 验证 |
| --- | --- | --- |
| G0 | DONE；PR #18，main 0eecc0e | validation/scope成功；170 tests、本地范围22项/CI一致20项、Git策略37项、独立检查通过 |
| G1 | LOCAL_VERIFIED / AWAITING_PR | 187项测试及本机IDE PASS；两路独立检查闭合 |
| G2–G6 | NOT_STARTED | NOT_RUN |

范围与具体行为见 [G1_IMPLEMENTATION.md](G1_IMPLEMENTATION.md)。下一步：按既有dev-manager→main PR交付G1，门禁及合入核验后才能标DONE。没有真实付费调用；原生Diff/Undo/实际源码应用尚未实现。

## 历史记录（保留原时点状态）

# F2 状态

日期：2026-10-25。唯一负责人：用户本人。文档包已生成，产品实现NOT_STARTED；架构未正式冻结。本地基线dev-manager/origin-main为8fac788。

| 组 | 实现 | 自动/IDE验证 | 依赖 |
| --- | --- | --- | --- |
| G0治理与边界 | NOT_STARTED | NOT_RUN | 可开始治理准备 |
| G1契约与存储 | NOT_STARTED | NOT_RUN | G0通过 |
| G2目标定位 | NOT_STARTED | NOT_RUN | G1通过 |
| G3候选生成 | NOT_STARTED | NOT_RUN | G2通过 |
| G4Diff审核 | NOT_STARTED | NOT_RUN | G3通过 |
| G5安全应用 | NOT_STARTED | NOT_RUN | G4通过 |
| G6回归交付 | NOT_STARTED | NOT_RUN | G5通过 |

## 本轮实际范围

只生成开发方案、契约提案、七组任务和验证矩阵，补入口导航与讨论证据；不改业务、实际API、构建依赖、hooks或范围政策。现有负责人补记保留；F0验收/合入沿用历史证据，不重做完成工作。

推荐dev-manager集中交付先经G0精确治理，现有managerExcluded未自动放开。单文件/整份批准等继续是提案，不据文档把全部细节记为用户已确认。两按钮本地零模型调用、draft唯一重入是明确要求。

## 下一条动作

进入GROUP_0，先形成拟写文件/范围清单与实施边界，落独立治理PR并通过main可信政策；之后才开始G1源码。不要直接修改editor/ui或绕过hooks。

## 本轮文档验证与独立检查

统一scripts/validate.ps1 -Scope Repository退出0；13份新文档内容/范围、17个本地链接、4处入口导航检查通过。产品、构建、DevOps、现行权限规则与WORKING保持不变。

两路独立文档检查闭合：修正了Diff需存在REVIEWABLE候选、审核不依赖已释放父operation、同逻辑请求换callId/失败后不重复生成三处歧义。首次BASELINE新增段落换行空白失败及一次只读核验脚本解析失败，修正后的最终检查通过；原始检查结果保存在本地证据，未隐去失败。

所有F2产品、All、IDE/API及人工验收仍NOT_RUN，本轮结果只证明文档交付。

## F2方案文档PR发布准备（2026-10-25）

用户授权提交推送本批19份公开规划/入口/任务状态文档，使用既有dev-manager→main，保留固定分支。原始答复、DISCUSSION、WORKING及本地evidence继续忽略，不入提交。

本轮PR前统一验证命令：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'，退出0；30 suites / 169 tests，失败/错误/跳过均0，check/buildPlugin通过。仅现有工程回归，F2实现仍NOT_STARTED；本轮IdeSmoke、真实API与F2产品场景NOT_RUN。

发布独立核对无阻断；提交前机检核验精确19文件、范围、空白与敏感模式。当前正在提交推送/创建PR，远端CI和实际URL由本地发布回执另记；本轮不合并、不修改门禁或业务。

## G0 实施与验证（2026-10-26）

用户已明确授权精确治理提案。实施范围见 G0_DECISIONS.md：四个F2子目录与三个UI接线文件；保留原managerExcluded、hooks、固定五分支和main可信检查。当前阶段 LOCAL_VERIFIED / AWAITING_PR，不标记DONE；G1仍NOT_STARTED。

node scripts/testing/scope-policy.test.cjs：22项本地检查通过、20项可信工作流表达式一致；包含相似名称拒绝、其他成员目录拒绝、main拒绝及重命名旧端拒绝。原始结果 tmp/f2-scope-figVz7/results.json。
统一 powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home' 退出0，日志 tmp/f2-g0/validation.log。F0 test任务UP-TO-DATE，既有169项报告不作为本轮重新执行。F2产品/IDE/API未执行。
下一步：独立检查闭合后独立治理PR，经最新head validation/scope成功、main合入核验并同步，才能开始G1。
独立只读检查：g0_review 核对精确授权、排除项保留、本地/CI一致、重命名双端及可信main来源，无阻断；git diff --check通过。

### G0 CI 测试观察边界修复
首次CI run 40287327107：169 tests中1项失败，ContextPlannerTest重复摘要在SessionTestEnvironment.dispatch的10秒get超时，没有业务断言失败。保留失败，不推定远端变慢的唯一根因。
新增真实H2写线程延迟11秒后执行UpdateDraftText的回归，原夹具确定性在同一位置TimeoutException（tmp/f2-g0/observation-red.log）。仅将夹具dispatch观察窗口对齐既有idle的30秒；模型业务10秒时限不变。修复后统一All退出0，31 suites/170 tests，失败错误跳过0（tmp/f2-g0/observation-green-all.log）。独立检查无阻断。
37项既有Git策略操作回归通过（tmp/bootstrap-git-tests/run-7c64bef6b75c450aa67be8dc5576ba83/results.json）。G0仍待最新head门禁；G1未开始。
## 2026-10-26：G0 完成，G1 实施

G0 DONE：PR #18 最新受检 head 7f77ab5e2bba235ab8c75a73068df0696df6dfa0 的 validation/scope 均成功；已合入 main 0eecc0efd60895e297a250291be04cc58cf25887，并 fetch/快进/祖先核验。固定分支保留。首次CI夹具超时、确定性红测和修复均在上文保留；最终170项测试通过。

G1 实际实现见 [G1_IMPLEMENTATION.md](G1_IMPLEMENTATION.md)。两路独立检查已闭合；首轮存储/工具/摘要定向测试通过。最终 All + IdeSmoke 正在执行，远端交付尚未开始；当前 IN_PROGRESS，不提前标 DONE。G2–G6未开始。

## G1 最终本地验证与交付条件（2026-10-26）

阶段 LOCAL_VERIFIED / AWAITING_PR；仅当本交付 PR 最新 head 的 validation/scope 成功、合入 main 并 fetch 核验后，G1 才为 DONE。此前不提前标记已集成。

最终命令：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'

退出0；187 tests / 33 suites，失败/错误/跳过均0。check、FixtureSmoke、buildPlugin通过；本机 IDEA IU-262.10968.63 / JBR25.0.4，build/reports/ide-smoke/result.json 为 PASS。日志 tmp/f2-g1/delivery-validation.log；预期跨方法拒绝异常与IDE环境警告保留，不改变最终PASS含义。

新增17项G1回归（实际JUnit报告：EditFoundationTest 13、ToolBoundaryTest 4），另G0观察回归1项；F0既有169项继续通过。G1定向日志 foundation-tests.log、refined-tests.log；完整集成及后续去重/会话隔离增量分别在 first-all.log、final-validation.log、final-replay-validation.log、delivery-validation.log，最终结论使用最后一份。

独立存储/生命周期/上下文检查及契约/工具检查均0遗留。修正并补测：未知应用结果的新commandId去重；同一事实快照验证checkpoint投影；重复批准命令固定内容；单会话恢复不失效另一session预览；真实H2重开原文保留且不恢复批准。

实际行为与后续边界见 G1_IMPLEMENTATION.md。此次只交付G0/G1，G2–G6 NOT_STARTED。真实API、原生Diff/Undo、实际源码应用、人工F2验收 NOT_RUN；无付费调用，不宣称F2产品可用或架构冻结。
