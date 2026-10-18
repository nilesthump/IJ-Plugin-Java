# 当前开发进度

更新：2026-10-18。阶段：用户授权初始化实施基线；架构尚未正式冻结。主方案：[F0_DEVELOPMENT_PLAN.md](../architecture/F0_DEVELOPMENT_PLAN.md)，公开仓库：https://github.com/nilesthump/IJ-Plugin-Java。

## 当前基线

- 固定分支：main、dev-manager、dev-a、dev-b、dev-c；四个 dev 各自 PR 到 main，manager 独立。负责人工作区 `H:/IJ-Plugin-Java` 最终保持 dev-manager，成员独立克隆。
- 单 Gradle Groovy 工程，Java 25；公共接口已落为源码，合成夹具不调用网络。Spring AI/H2/JVector/PDFBox/commonmark 是初始化依赖组合，不代表真实运行已验证。
- 四份任务均 READY / NOT_STARTED。按 [任务入口](../tasks/README.md) 开始。各自记录更新仅在自己的任务目录，公共进度归负责人。
- 正式架构状态仍见 WORKING.json，不伪造候选/冻结版本。

## 初始化验证状态

| 项目 | 状态 |
| --- | --- |
| 纯 Java 公共契约 + 合成夹具，无第三方 javac | PASS（JDK25.0.2，release17；最终工程release25） |
| FixtureSmoke 观察释放/状态/物理空页/凭据脱敏 | PASS（不是产品核心） |
| 本地 Git hooks 37 次隔离 Git 操作 | PASS |
| 生产范围检查与独立审核 | PASS，93场景，未发现未修复P1/P2；见INITIALIZATION.md |
| Gradle Wrapper | PASS，官方JAR/ZIP校验通过并实际运行；本机网络下载慢时使用已校验的同一官方ZIP供Wrapper安装 |
| 主插件编译 / check / ZIP | PASS，本机IDE 262.10968.63；按用户更正始终只验证本机版本，实际IDE运行仍NOT_RUN |
| 公开远端仓库 | 已创建，额外分支创建禁令与固定分支删除/强推保护已active（25332001/25332002） |
| 五分支推送 / 服务端规则 | PASS，五分支均已推送；main为默认。规则25332001禁止额外创建，25332002禁止固定分支删除/强推，25332264要求main经PR且validation通过；已读回有效规则 |
| 公开内容 | 88份工程/方案/任务文件，新根历史无原始附件/需求答复/内部状态；本地原件和私有历史保留，不公开 |
| GitHub CI | 首次工作流已识别active，运行门禁正在通过负责人PR核验 |
| 目标IDE实际运行 / 真实模型与嵌入 / 完整持久化与检索 | NOT_RUN，属后续任务 |

## 下一步

负责人完成当前PR的CI门禁核验并回写结果；各成员以 main 的公共契约/依赖为基线，按所属任务的第一个场景实现。未来变更通过四个固定 dev 分支直接 PR main。完成必须有实际验证、审核、main 合入与进度回写；现阶段不宣称 F0 产品完成。原始附件/需求答复与WORKING状态只在负责人本地保留，公开任务无需这些文件即可开始。
