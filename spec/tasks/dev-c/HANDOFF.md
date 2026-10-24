# 丙：Tool Window 与状态驱动界面：交接

## 当前分支与范围

- 日期：2026-10-24；固定分支 `dev-c`，同步的本地 `origin/main` 基线 `9cf52ca`。功能提交 `3674cbe` 已推至 `origin/dev-c`；草稿 [PR #10](https://github.com/nilesthump/IJ-Plugin-Java/pull/10) 目标 `main`。未创建新分支。
- 本次改动仅位于 `src/main/java/edu/courseassist/ui/**`、`src/test/java/edu/courseassist/ui/**`、`spec/tasks/dev-c/**`。未编辑 `bootstrap`、公共契约、构建或插件 XML。
- 本地 hooks 已安装。工作区中的 `tmp/` 缓存和合成冒烟脚本被忽略，不作为交付文件。

## 交付内容

- 入口 `CourseAssistantView(projectInstanceId, sessions, application, credentials, project)` 必须在 EDT 构造，`component()` 供负责人装入 Tool Window，内容释放时调用 `close()`。不带 `Project` 的重载供合成组件测试；真实运行应传入 `Project` 以启用 IDEA Java 高亮。
- `ApplicationWorkspace` 消费应用快照和事件；资料操作固定目标，配置仅 OFF 可编辑，凭据经短期字符载体交给 `CredentialUpdateSink`。`SafeMarkdown` 和 `MarkdownRenderQueue` 生成受控的异步 HTML 预览，`IdeCodeViewer` 负责编辑器及配色资源。
- 组件测试覆盖双会话/停止与引用、旧 revision、未确认草稿、资料固定目标、关闭与删除、重开、中断显示；Markdown 测试覆盖表格、列表、HTML/script/远程图及未闭合 Java 围栏。合成控制器返回 `NOT_SUPPORTED`，不模拟核心持久化或网络。

## 验证与限制

- 完整统一入口已通过：设置 `TEMP`/`TMP` 为 `D:\IJ-Plugin-Java\tmp`、`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:\IJ-Plugin-Java\tmp`，然后运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -GradleUserHome 'D:\IJ-Plugin-Java\tmp\gradle-user-home' -LocalIdePath 'D:\IntelliJ IDEA 2026.2.2'`；退出码 0，96 tests / 0 failures，`buildPlugin` 成功。
- 前两轮 All 失败分别来自 UI CommonMark API 编译错误（已修复）和沙箱外 JUnit 临时目录权限（已用进程内环境变量规避）；初始默认 `C:\.gradle` 同样无写权限。详见 [状态](STATUS.md)。
- 本机 IDEA 是 2026.2.2 build 262.10315.125；仓库配置目标是 2026.2.3 build 262.10968.63。当前编译/打包使用 2026.2.2，不等于目标版本运行验收。实际 IDEA 多色高亮、主题切换和 UI 生命周期仍需在目标安装中观察。
- 生产 `BootstrapToolWindowFactory` 仍是占位界面。负责人需按 [依赖请求](DEPENDENCY_REQUEST.md) 接入控制器、凭据 sink 和项目身份，再做真实工作流验证。付费模型未调用，不能宣称 API 或端到端通过。

## 后续

1. staged 范围检查已通过（10 路径，退出码 0）；功能已提交、推送并创建草稿 PR。后续在 PR #10 完成独立检查并保留上述限制。
2. 负责人完成公共接线、IDE 版本协调，成员同步后在真实插件中核验显示、Java 高亮、配色和关闭资源。
3. 独立检查、main 合入及核验后更新任务状态；合入后不删除 dev-c 分支。
