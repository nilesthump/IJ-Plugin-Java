# 丙：Tool Window 与状态驱动界面：状态

- 任务状态：`IN_PROGRESS`。成员允许范围内的视图与事件适配已实现，统一验证已通过；生产 Tool Window 接线、目标 IDE 可视验证、独立检查和合入仍未完成。
- 记录日期：2026-10-24；分支：`dev-c`；开始时工作区干净，基线为 `9cf52ca`。功能提交 `3674cbe` 已推至 `origin/dev-c`。本次仅改 `ui/**` 和 `spec/tasks/dev-c/**`。
- 仓库本地 hooks 已通过 `scripts/git/Configure-Git.ps1` 安装；未新建或切换分支。

## 已实现

- `CourseAssistantView` 订阅项目和应用快照，切至 EDT 后按 revision 显示多会话标签、全部会话、独立草稿、捕获代码组、历史消息、attempt/branch/step、引用和通知。旧快照不覆盖新状态，未确认的本地草稿不会被旧正文清掉。
- 会话命令包括新建、选择/重开、关闭标签、删除、保存草稿、发送、移除代码组、停止、重试、整理、恢复及打开固定来源引用；按钮按核心提供的 `allowedActions` 和活动 operation 控制。关闭和删除为不同事件。
- 回答使用核心累计正文；过程与最终正文分区。旧 attempt 与中断正文留在显示档案；复制最近回答原文取模型步骤中的原始 Markdown。CommonMark/GFM 表格渲染转义 HTML、净化 URL、移除远程图片；原始代码快照及历史代码另行显示，不反解 Markdown 为附件。IDEA 编辑器用于 Java 高亮，配色切换时重建 highlighter；关闭时释放编辑器、监听和观察订阅。
- 应用区显示工作状态、资料状态及物理页引用；导入、替换、移除使用点击时固定的资料身份、源版本和 revision。仅 OFF 可打开服务配置；密钥只经 `CredentialUpdateSink.stage` 暂存并清理字符数组；异步回调复核视图存活及应用版本。

## 验证记录

| 检查 | 结果 | 证据边界 |
| --- | --- | --- |
| 首轮 `scripts/validate.ps1 -Scope All` | FAIL（环境） | 默认 `C:\.gradle` 无写权限，未进入 Java 测试 |
| 首轮仓库内 Gradle 缓存 + 本机 IDE | FAIL（编译） | CommonMark `Node.replaceWith` 不存在；已在 `SafeMarkdown` 改用 `insertBefore` / `unlink` |
| 第二轮仓库内缓存 + 本机 IDE | FAIL（环境） | 96 个测试中 78 个因 JUnit 在 `C:\Users\A\AppData\Local\Temp` 创建目录被拒；当轮 8 个 dev-c UI 测试通过 |
| 统一 All：`TEMP`、`TMP`、`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:\IJ-Plugin-Java\tmp`，`-GradleUserHome D:\IJ-Plugin-Java\tmp\gradle-user-home -LocalIdePath 'D:\IntelliJ IDEA 2026.2.2'` | PASS | `scripts/validate.ps1 -Scope All` 退出码 0；96 tests、0 failures；`buildPlugin` 成功。使用本机 2026.2.2 SDK 编译/打包，不证明仓库目标 2026.2.3 的运行兼容 |
| 仓库结构 | PASS | `scripts/validate.ps1 -Scope Repository` 退出码 0 |
| 真实 IDEA UI、高亮及配色动态行为、付费模型 | NOT_RUN | 尚未运行目标 IDEA 可视界面或实际付费模型；合成测试仅证明视图/契约行为 |
| 生产 Tool Window 接线与端到端 | NOT_RUN | `BootstrapToolWindowFactory` 仍为负责人占位实现；见 [依赖请求](DEPENDENCY_REQUEST.md) |
| staged 写入范围与差异检查 | PASS | `scripts/check-scope.ps1 -Staged`：10 路径、退出码 0；`git diff --cached --check`：退出码 0 |
| `dev-c` 推送 | PASS | `git -c credential.helper=manager -c credential.interactive=never push origin dev-c` 退出码 0；无强推 |
| `dev-c → main` PR | DRAFT | [#10](https://github.com/nilesthump/IJ-Plugin-Java/pull/10) 已创建，描述含行为、验证和限制；待独立检查及公共接线 |
| main 合入与核验 | NOT_RUN | 合入由负责人/审核流程完成，合入后核验并保留 dev-c |

## 继续动作

1. 在 [PR #10](https://github.com/nilesthump/IJ-Plugin-Java/pull/10) 完成独立检查，跟踪负责人公共接线与目标 IDE 验证。
2. 负责人从其公共范围接 `BootstrapToolWindowFactory`，并协调目标 IDEA 2026.2.3 的实际运行验证；成员同步后复验和交接。
3. 经独立检查、`main` 合入及核验后才能将任务标为完成；当前不把合成 fixture 当真实核心/API 成功。
