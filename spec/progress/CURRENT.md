# 当前开发进度

更新：2026-10-18。阶段：用户授权初始化实施基线；架构尚未正式冻结。主方案：[F0_DEVELOPMENT_PLAN.md](../architecture/F0_DEVELOPMENT_PLAN.md)，公开仓库：https://github.com/nilesthump/IJ-Plugin-Java。

本记录随收尾PR交付，只有对应validation/scope通过并合入main后才作为初始化完成记录；开发分支上的未合入版本不能据此宣称验收完成。

## 当前基线

- 固定分支：main、dev-manager、dev-a、dev-b、dev-c；四个 dev 各自 PR 到 main，manager 独立。负责人工作区 `H:/IJ-Plugin-Java` 最终保持 dev-manager，成员独立克隆。
- 单 Gradle Groovy 工程，Java 25；公共接口已落为源码，合成夹具不调用网络。Spring AI/H2/JVector/PDFBox/commonmark 是初始化依赖组合，不代表真实运行已验证。
- 三个成员任务保持 READY / NOT_STARTED；负责人第 1 组公共骨架已实施，整体 IN_PROGRESS。实际范围和验证以 [负责人状态](../tasks/dev-manager/STATUS.md) 为准，其余四组尚未实现。各自记录更新仅在自己的任务目录，公共进度归负责人。
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
