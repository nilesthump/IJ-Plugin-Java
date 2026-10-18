# 当前开发进度

更新：2026-10-18。阶段：用户授权初始化实施基线；架构尚未正式冻结。主方案：[F0_DEVELOPMENT_PLAN.md](../architecture/F0_DEVELOPMENT_PLAN.md)，公开仓库：https://github.com/nilesthump/IJ-Plugin-Java。

本记录随收尾PR交付，只有对应validation/scope通过并合入main后才作为初始化完成记录；开发分支上的未合入版本不能据此宣称验收完成。

## 当前基线

- 固定分支：main、dev-manager、dev-a、dev-b、dev-c；四个 dev 各自 PR 到 main，manager 独立。负责人工作区 `H:/IJ-Plugin-Java` 最终保持 dev-manager，成员独立克隆。
- 单 Gradle Groovy 工程，Java 25；公共接口已落为源码，合成夹具不调用网络。Spring AI/H2/JVector/PDFBox/commonmark 是初始化依赖组合，不代表真实运行已验证。
- 三个成员任务保持READY / NOT_STARTED。负责人第1–3组及PR #6收尾已合入main；第四组已实现/已验证，本交付PR门禁与main合入核验后DONE；第五组NOT_STARTED，整体IN_PROGRESS。详见[负责人状态](../tasks/dev-manager/STATUS.md)。公共进度归负责人。
- 正式架构状态仍见 WORKING.json，不伪造候选/冻结版本。

## 初始化验证状态

| 项目 | 状态 |
| --- | --- |
| 纯 Java 公共契约 + 合成夹具，无第三方 javac | PASS（JDK25.0.2，release17；最终工程release25） |
| FixtureSmoke 观察释放/状态/物理空页/凭据脱敏 | PASS（不是产品核心） |
| 本地 Git hooks 37 次隔离 Git 操作 | PASS |
| 生产范围检查与独立审核 | PASS：PS7 93场景、PS5 96场景；同步main三方合并PS5/PS7各16场景。目录检查的协作边界见VALIDATION |
| Gradle Wrapper | PASS，官方JAR/ZIP校验通过并实际运行；本机网络下载慢时使用已校验的同一官方ZIP供Wrapper安装 |
| 主插件编译 / check / ZIP | PASS，本机IDE 262.10968.63；按用户更正始终只验证本机版本，实际IDE运行仍NOT_RUN |
| 公开远端仓库 | 已创建，额外分支创建禁令与固定分支删除/强推保护已active（25332001/25332002） |
| 五分支推送 / 服务端规则 | PASS，五分支均已推送；main为默认。规则25332001禁止额外创建，25332002禁止固定分支删除/强推，25332264要求main经PR且validation与scope通过；已读回有效规则 |
| 公开内容 | 仅工程/方案/任务文件，新根历史无原始附件/需求答复/内部状态；本地原件和私有历史保留，不公开 |
| GitHub CI | PR #1的真实validation构建PASS并已按门禁合入main；本收尾PR进入main必须同时满足validation/scope，实际结果以PR检查记录为准 |
| 目标IDE实际运行 / 真实模型与嵌入 / 完整持久化与检索 | NOT_RUN，属后续任务 |

## 下一步

在main读取到本版本后，各成员以公共契约/依赖为基线，按所属任务的第一个场景开始实现；负责人接入模型/存储/检索及核心。四个固定dev分支各自直接PR main，持续从origin/main同步。完成必须有实际验证、审核、main合入与进度回写；本次只完成初始化，不宣称F0产品完成。原始附件/需求答复与WORKING状态只在负责人本地保留，公开任务无需这些文件即可开始。

## 负责人第二组：资料与检索（2026-10-18）

第 1 组已由 PR #3 合入 main（50f50f8）。本轮第 2 组 manager 核心已实现并通过本地完整验证：托管副本/MD5、独立来源版本、页内分块、嵌入空间、H2 耐久 float32/映射/指纹、JVector 代次与共享恢复、CAS 发布、固定 attempt/历史引用和显式清理。31 项 JUnit、FixtureSmoke、插件 ZIP 与本机 IDEA headless 核心场景通过，两路独立检查发现的问题已修复。行为、准确命令和证据见 [负责人状态](../tasks/dev-manager/STATUS.md) / [第二组范围](../tasks/dev-manager/WORK_GROUP_2.md)。

本节随本交付 PR 的 validation/scope 通过并合入 main 后生效，满足后第 2 组 DONE；此前为待集成。整体仍 IN_PROGRESS，第 3–5 组未开始。真实乙 PDF/服务联调、生产界面/配置/会话/ReAct 与真实课程规模仍有明确未执行边界，不据合成验证宣称完整 F0 或架构冻结。开工前方案/PDF改动保留且不纳入本组提交。

第二组交付入口：[PR #4](https://github.com/nilesthump/IJ-Plugin-Java/pull/4)，实现提交 `8b41aba`；只有最新 head 的 validation/scope 与 main 合入核验共同满足后，上述第二组 DONE 条件生效。合并保留固定来源分支。

## 负责人第三组：Spring AI、有限 ReAct 与流式（2026-10-18）

第 1、2 组已通过 PR #3 / #4 合入 main。第三组已实现固定代次首检/课程工具、单所有者有限步骤、严格 Spring AI 请求/SSE、正文/thinking/工具及终止分离、原子累计档案/来源保留、最终实际引用、故障分支隔离和统一上下文资格。最终统一 All + IdeSmoke 退出 0，51 项 JUnit、FixtureSmoke、插件打包和本机 IU-262.10968.63 / JBR25.0.4 场景通过；两路独立检查无未闭合项。准确范围、命令与证据见 [第三组记录](../tasks/dev-manager/WORK_GROUP_3.md) 和 [负责人状态](../tasks/dev-manager/STATUS.md)。

本节随本交付 PR 最新 head 的 validation/scope 通过并合入 main、核验后生效，满足后第三组 DONE；此前为已实现/已验证待集成。第 4–5 组未开始，整体仍 IN_PROGRESS，不开展第四组。真实 API/生产思考模式/实际 tokenizer、真实 PDF/可视 UI/规模、完整 session 与压缩仍待证；没有付费调用或架构冻结。开工前方案/PDF改动保留且不纳入本组提交。

## 第三组主线核验完成（2026-10-18）

第三组 `DONE`：[PR #5](https://github.com/nilesthump/IJ-Plugin-Java/pull/5) 最新 head `d959b0b329629cc75ba329c8b6315cb1613bba1b` 的 validation 与 scope 均 SUCCESS，已合入 main 并 fetch 核验 merge commit `14d465ea761da45d982691270e782e9a006d96d0`。固定 dev-manager 保留；主工作区同步 main，未触及成员分支。远端证据见 [交付记录](../tasks/dev-manager/evidence/work-group-3-delivery.json)。第 4–5 组仍 NOT_STARTED，整体 IN_PROGRESS；本轮不开展第四组，原有方案/PDF改动继续保留。

### 第三组收尾：终止查询许可（2026-10-18）

在 PR #5 完成基础交付后，收尾复核将查询许可收紧为仅 Decision.RUNNING，避免取消转入终态保存时、lease 尚未释放的排队查询重新获得嵌入资格。可控执行器/延迟终态保存回归验证旧查询的嵌入与模型调用均为 0，两路独立增量检查均闭合。最新统一 All + IdeSmoke 退出 0，共 52 项 JUnit（本组新增 21 项）；原始日志 `tmp/manager-task3/terminal-fence-validation.log`。最终证据见原 work-group-3-validation.json / runtime.json / review.md。该修复和完成记录经 [PR #6](https://github.com/nilesthump/IJ-Plugin-Java/pull/6) 最新 head 的 validation/scope 并合入 main 后生效；未扩展至第四组，历史 51 项记录属于 PR #5 的真实交付批次。

## 负责人第四组：会话、提交、恢复、压缩与删除（2026-10-18）

在PR #6 main基线 `8a68daa` 上完成真实H2项目/session根、原始typed代码/草稿/标签、原子提交去重和单槽生命周期、捕获撤销、手动/自动摘要及checkpoint资格、根删除全清/继续清理、目录身份移动/复制隔离与显式关联、PasswordSafe/全应用epoch和未保存阻塞恢复。行为与候选参数见 [第四组记录](../tasks/dev-manager/WORK_GROUP_4.md)，准确命令/测试/独立检查见 [负责人状态](../tasks/dev-manager/STATUS.md) 与 evidence/work-group-4-*。

最终统一All + IdeSmoke退出0，实际JUnit计数见验证汇总；FixtureSmoke/插件打包、本机IU-262.10968.63/JBR25.0.4的真实服务/H2恢复/硬删除和Windows同卷目录移动/复制通过。两路独立检查未闭合均0，实施失败保留后已修复重验。没有真实付费请求；真实平台/摘要语义/tokenizer、成员可视联调、人工PDF页定位/退出与规模仍NOT_RUN，架构未冻结。

本记录仅在本交付PR最新head的validation/scope通过、合入main并fetch核验后作为第四组DONE；满足前为已实现/已验证待集成。第五组NOT_STARTED、整体IN_PROGRESS，不扩展第五组。固定dev-manager保留，开工前方案/PDF差异未纳入本组交付。
