# F2 交接

日期：2026-10-25。负责人：用户本人。分支：现有dev-manager；本地基线8fac788，与本地origin/main一致。当前任务是方案/任务包交付，未实施F2。

先读F2 README、STATUS和开发方案，再从GROUP_0继续。根AGENTS仍F0-only，manager范围仍排除editor/ui；用户个人负责不等于机器策略已改。推荐G0先独立治理PR明确精确F2路径/必要接线，不新开分支、不把实现挪到bootstrap、不分配甲乙丙。

已确认：可选选区/文字定位；原生Diff先审核且预览零写入；批准准确版本；应用已展示内容、支持Undo，Document与磁盘保存分开；“同意并应用”“拒绝”均本地事件，只有用户再次draft发送才重新进入ReAct。

技术提案：单文件/整份批准、本地工具边界/受控子生成、独立F2仓储、具体幂等和恢复字段。先按G0记录实际实施边界，不能把生成文档说成所有技术决定已确认或正式冻结。

真实接点：ReactData.Input有question/code/captureOffsets/captureOrder；ReactEngine仍课程工具总开关；真实UI已是MixedDraftEditor/ChatTimeline；终态ReactRepository Snapshot不可回写。F2需独立审核/应用记录及checkpoint事实投影，不破坏F0。

七组顺序执行，各组负责人都是用户本人。当前所有实现/产品测试NOT_STARTED/NOT_RUN；本轮Repository退出0，13份新文档/17个本地链接/4处入口导航与范围检查通过；两路独立文档检查已闭合，修正三处候选准入/审核守卫/逻辑生成去重歧义。初次换行空白及核验脚本解析失败均已修正并保留本地记录；产品与DevOps未改。不得拿F0历史169项测试作为F2已验证证据，不发付费模型请求证明本地接口。

保留本轮之前STATUS/HANDOFF未提交记录，不自行提交/推送/合并。公开任务不依赖本地忽略原文；后续每轮继续维护负责人evidence与来源哈希。

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