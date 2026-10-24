# 丙：Tool Window 与状态驱动界面：公共依赖与接口请求

当前：两项 `PROPOSED`，均未由负责人处理。此文件用于提出公共变更，不授权成员修改 build、plugin.xml、contract 或 fixtures。

## DEV-C-UI-WIRING-20261024

- 状态：`PROPOSED`；归属：dev-c 提出，dev-manager 处理。
- 触发场景：`CourseAssistantView` 已在 `ui` 包消费 `SessionController`、`ApplicationController`、`CredentialUpdateSink` 和 IDEA `Project`，但生产 `BootstrapToolWindowFactory` 仍只显示开发占位文本；真实 Tool Window 无法到达成员界面。
- 最小公共变更：负责人在现有 `BootstrapToolWindowFactory` 中异步取得 `CourseAssistantProject.sessionController()` 与 `CourseAssistantApplication.controllers()`；就绪后在 EDT 构造 `new CourseAssistantView(projectInstanceId, sessionController, applicationController, credentialSink, project)` 并安装其 `component()`，在内容/项目释放时调用 `close()`。初始化/项目身份失败须显示明确状态和恢复入口，不构造成功空状态。此请求不要求修改公共 DTO 或依赖。
- 理由：`bootstrap/**` 和 `plugin.xml` 属负责人写入范围；成员只交付可注入的视图类。合成控制器不能接生产问答。
- 受影响公共文件：`src/main/java/edu/courseassist/bootstrap/BootstrapToolWindowFactory.java`；必要生命周期接线仍由负责人确认。
- 可独立继续：完成 UI 范围的组件测试、Markdown/高亮、资料及凭据事件与可视验证准备。
- 负责人处理结论 / PR / 合入 main：`NOT_RUN`。成员同步后接入验证：`NOT_RUN`。

## DEV-C-LOCAL-IDE-20261024

- 状态：`PROPOSED`；归属：dev-c 提出，dev-manager 判定目标 SDK。
- 触发场景：当前可见本机 IDEA 安装的 `product-info.json` 为 2026.2.2 / build 262.10315.125，仓库 `gradle.properties` / `build.gradle` 固定 2026.2.3 / build 262.10968.63；任务要求始终以实际本机版本验证。
- 最小公共处理：负责人确认本次协作的实际目标 IDE 安装位置和版本，并按既定治理规则协调构建/CI 与记录；成员不在 dev-c 改构建或宣称 2026.2.2 测试证明 2026.2.3 兼容。
- 可独立继续：合成逻辑与现有本机 IDE SDK 的局部编译；真实目标 IDE 可视测试保持 `NOT_RUN`，直到版本口径一致。
- 负责人处理结论 / PR / 合入 main：`NOT_RUN`。

负责人在公共范围评估并从 `dev-manager` PR 到 `main`，成员同步 `main` 后接入。公共签名以 [API.md](../../contracts/API.md) 与 Java 定义为准；PDFBox/commonmark 等已预配依赖先检查现有构建，避免重复申请。

## 请求模板（每项复制一份）

- 请求 ID / 提出日期 / 归属人：待填。
- 状态：PROPOSED / ACCEPTED / IMPLEMENTED / REJECTED（按事实填写）。
- 触发场景及当前不能完成的行为：待填。
- 需要的最小公开字段/端口/动作注册/fixture 或依赖：待填。
- 若为依赖：Maven 坐标、候选准确版本、官方来源、Java25/目标IDE兼容证据、许可及插件包体积影响：待填。
- 现有能力为何不足 / 可行替代与取舍：待填；不要以静默 fallback 绕过行为要求。
- 预计影响的公共文件及消费者：待填。
- 可独立继续的本任务工作：待填。
- 负责人处理结论、公共 PR 和合入 main 提交：待填。
- 本任务同步基线后实际验证：待填，未执行写 NOT_RUN。

不要在此记录 API key、凭据 ref 或真实课程内容。模型/平台的非秘密能力和未验证参数可列为问题，不能凭别名推断已支持。
