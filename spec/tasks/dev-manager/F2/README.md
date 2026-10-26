# F2 个人任务组入口

2026-10-25。全部任务唯一负责人：用户本人。顺序开发，不分派给F0成员；开发分支推荐沿用dev-manager，先由G0落实精确F2归属与门禁，再写产品代码。不创建新分支，不把代码放错包规避规则。

方案：[F2_DEVELOPMENT_PLAN](../../../architecture/F2_DEVELOPMENT_PLAN.md)；契约：[F2_API_PROPOSAL](../../../contracts/F2_API_PROPOSAL.md)；[状态](STATUS.md)、[交接](HANDOFF.md)、[验证矩阵](VALIDATION_MATRIX.md)。

| 组 | 内容 | 前置 | 负责人 | 初始实现状态 |
| --- | --- | --- | --- | --- |
| [G0](GROUP_0.md) | 治理、实施范围及交付路径 | 阅读当前基线 | 用户本人 | NOT_STARTED；可先做治理准备 |
| [G1](GROUP_1.md) | 契约、状态/存储、工具能力基础 | G0通过 | 用户本人 | NOT_STARTED |
| [G2](GROUP_2.md) | 可选选区/文字定位与同版快照 | G1通过 | 用户本人 | NOT_STARTED |
| [G3](GROUP_3.md) | 受控候选生成与ReAct接入 | G2通过 | 用户本人 | NOT_STARTED |
| [G4](GROUP_4.md) | 原生Diff与两个本地审核按钮 | G3通过 | 用户本人 | NOT_STARTED |
| [G5](GROUP_5.md) | 获批应用、Undo、回执与恢复 | G4通过 | 用户本人 | NOT_STARTED |
| [G6](GROUP_6.md) | 端到端回归、人工验收与交付 | G5通过 | 用户本人 | NOT_STARTED |

## 每组执行方式

先读根AGENTS、CURRENT、BASELINE/F0与F2方案、治理、当前本组及实际API/Java。记录当前分支/工作区和上一组真实证据；不改写F0历史，不假定新组已授权改全部旧目录。

每组按一个可观察场景实施、验证、记录；只在本组退出条件满足后推进。阶段LOCAL_VERIFIED不同于main交付DONE；最终完成要求统一验证、独立检查、PR门禁、main合入与核验，人工验收独立记录。未完成组不提前标DONE。

统一入口：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All；真实IDE必要场景增加-IdeSmoke及本机路径。只生成本轮文档时用-Scope Repository，不能替代产品验证。不调用付费模型证明本地接口。

本轮仅创建方案与任务定义，没有修改实际契约、业务、构建、权限策略或hooks。原始讨论与来源哈希仅在负责人本地忽略证据保存，公开任务不要求读取它们。

## 可直接交给后续agent的提示

```text
F2所有任务由用户本人负责。先读F2/STATUS和HANDOFF，从GROUP_0的下一条动作开始；不要跳过现有AGENTS的F0-only和branch-scopes限制，不分派甲乙丙实施，不创建分支。
严格区分已确认R01–R10与D0–D7技术提案。两个审核按钮本地处理，不调用模型、不进入ReAct；只有用户重新发送draft才能再次进入。
任何候选必须完整、父有效分支正常且耐久后才可审核。批准绑定Diff展示版本；应用复验当前Document并支持Undo，Document与磁盘保存分开报告。
保持F0混排输入、身份/单槽、课程降级、停止/中断资格、摘要与删除语义。统一验证，记录真实通过/失败/未执行。
```

## 当前执行更正（2026-10-26）

G0/G1 已通过 PR #18/#19 合入并核验；旧 NOT_STARTED 表为规划初始状态。用户授权下两组 G2/G3 合在一个 dev-manager→main PR。最终 All+IdeSmoke 196 项/34套件及本机 IDEA 通过，独立检查闭合，当前 LOCAL_VERIFIED。实施与边界见 [G23_IMPLEMENTATION](G23_IMPLEMENTATION.md)，准确交付状态见 STATUS/HANDOFF。G4–G6 不在本次授权范围。

## G4/G5 当前执行（2026-10-26）

G2/G3 已通过 PR #20 合入 main 4de5417并核验。用户本轮授权 G4/G5 统一一个 PR 提交并合并；早前“G4–G6 不在本轮范围”属于上一轮状态。实现与验证见 [G45_IMPLEMENTATION](G45_IMPLEMENTATION.md)，以 STATUS/HANDOFF 的最新记录为准；G6 仍未开始，架构未冻结。
