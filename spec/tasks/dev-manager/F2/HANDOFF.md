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
