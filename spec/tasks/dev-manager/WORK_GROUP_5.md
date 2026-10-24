# 第五组：主线接入、联调与人工验收准备

2026-10-24。状态：IMPLEMENTED / VERIFIED；仅在本 PR 最新 head 的 validation/scope 成功、独立检查闭合并合入 main 核验后，开发交付记 DONE。人工黑盒、真实 API 与产品验收仍 NOT_RUN。

## 行为

- 从 origin/main 普通快进同步 `2976118 → fb425c9`，保留 dev-manager 及既有方案/PDF/讨论文档改动，不改成员 HEAD。
- 注册已由成员直达 main 的 JavaCodeCapturePort 与 PdfBoxPdfReader。公共 CaptureEntryAction 同步委托已有采集动作；未初始化时启动恢复并显示侧栏/提示，不异步排队后改绑目标。
- BootstrapToolWindowFactory 异步等待真实应用/项目服务，EDT 构造真实 CourseAssistantView，并随内容释放订阅/高亮资源。恢复失败不伪装空会话；身份不明提供新项目或显式关联旧记录，保留版本/占用/空目标校验。
- 视图初始化故障在明确边界保留原始原因；应用存储初始化失败提示排障后重启；关联失败保持重选入口。项目释放后拒绝新 openSessions。
- ideSmoke 源码迁至独立根，JUnit 源码保持 src/test/java。test 复用已编译的内部测试辅助类，不再让 IDE 把同一 Java 源目录映射成两个源集。
- 提供 [47 项人工黑盒步骤](BLACKBOX_TEST_PLAN.md)、三份 3 页合成 PDF、坏 PDF 以及 Java 示例，覆盖安装、两类服务/key、PDF 导入与行为验收。

## 成员公共请求处理

| 请求 | 本轮结论 |
| --- | --- |
| DEV-A-001 | 端口注册完成；菜单通过 manager readiness 包装调用原成员动作，防止首次使用未初始化异常 |
| DEV-A-002 | 独立 ideSmoke 源根与 test 编译输出依赖完成；All/IDE运行通过，VS Code/JDT 人工重导入诊断未执行 |
| DEV-A-003 | 历史运行期 ClassNotFoundException 在本次全量验证未复现；非空/unused/deprecation 提示不在本轮擅自重构公共语义，未认定所有编辑器静态提示消除 |
| PDF-REG-001 | 唯一注册解析器；真实三页 PDF 经注册端口走核心并验证物理页、托管与恢复 |
| DEV-C-UI-WIRING-20261024 | 真实视图、控制器、凭据入口、释放与身份处理接线完成 |
| DEV-C-LOCAL-IDE-20261024 | 负责人本机 product-info 实测 2026.2.3 / 262.10968.63，与现有构建/CI一致；成员机器的 2026.2.2 不作为本机兼容证明 |

未修改成员 editor/pdf/ui 实现、测试或任务目录。成员静态诊断中的可选维护并不等于本轮集成失败；新的实现问题仍按归属协调。

## 验证与交付

[白盒证据](WHITEBOX_TESTS.md)：最终 All + IdeSmoke 退出 0，100 项 JUnit 全通过；真实注册动作/PSI/H2、真实界面草稿保存重开、注册 PDF/存储/检索与本地合成 HTTP 通过。两路 [独立检查](evidence/work-group-5-review.md) 原问题修正后均 0 遗留。

公开方案仍为未冻结状态；不扩展 F1、不执行付费模型调用。用户操作的黑盒方案不预填 PASS；最终合入信息见交付记录。
