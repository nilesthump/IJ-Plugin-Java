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
