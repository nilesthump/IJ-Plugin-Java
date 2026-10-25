# Course Assistant F0

IntelliJ IDEA 内嵌 Java 课程助教：PDF 检索问答、文件及物理页引用、多会话聊天、编辑器代码上下文。业务使用纯 Java，保留 Spring AI、H2、JVector，无需额外启动服务进程。

**阶段状态：F0 已于 2026-10-25 通过用户验收；F2 尚未开工。** F0 验收不代表 F2 完成或架构正式冻结。代码交付与合并状态见 [当前进度](spec/progress/CURRENT.md)。历史测试包已按要求清理，安装包从源码构建，不随源码仓库提交。

## 构建方案

### 环境

- Windows、JDK 25，确认 `java -version` 指向 JDK 25；可配置 `JAVA_HOME` 指向本机 JDK。
- 使用仓库内 Gradle Wrapper（9.1.0），无需另装 Gradle。
- 当前构建与已验证目标为 IntelliJ IDEA 2026.2.3 / build 262.10968.63，启用 Java 插件。只验证这一版本；更换 IDEA 版本需重新验证。
- 首次构建需要联网下载 Maven、Gradle 和 IntelliJ SDK 依赖；构建和自动测试不需要真实 API key，不调用付费模型。

### 获取源码与构建

```powershell
git clone https://github.com/nilesthump/IJ-Plugin-Java.git
cd IJ-Plugin-Java
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/git/Configure-Git.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All
```

统一入口执行仓库检查、编译、JUnit、契约夹具检查和 `buildPlugin`。成功后生成：

```text
build/distributions/IJ-Plugin-Java-0.1.0-dev.zip
```

版本名取自 `gradle.properties` 的 `pluginVersion`。仅构建可在 main 上执行；开发修改必须使用已有开发分支，规则见下方。

使用本机已安装的上述 IDEA，减少 SDK 下载，并运行独立 IDEA 启动检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3"
```

将路径替换为本机实际 IDEA 安装目录。可选 `-GradleUserHome "D:\gradle-cache"` 指定缓存目录。`-Scope Repository` 只验证仓库结构，不生成安装包，也不替代全量验证。

JUnit 报告位于 `build/reports/tests/test/index.html`；IDE 启动检查结果位于 `build/reports/ide-smoke/result.json`。自动检查使用合成模型/本地响应，不能代替真实服务配置和人工界面验收。更多说明见 [验证契约](spec/governance/VALIDATION.md)。

## 安装

1. 构建成功后打开 IDEA → Settings（设置）→ Plugins（插件）。
2. 点击齿轮 → Install Plugin from Disk（从磁盘安装插件），选择上述 ZIP，**不要解压**。
3. 按提示重启 IDEA，打开 Java 项目，在右侧工具窗口或 View → Tool Windows 打开 **Course Assistant**。
4. 更新时安装新构建的 ZIP 并重启。无需清空 H2，也无需重新填写已有凭据。若从不保存启停状态的旧版本升级，手动启用一次后即可记住选择。

## 服务配置与启用

右上角齿轮打开设置；配置只能在停用状态修改。点击“服务配置”，分别填写生成服务与嵌入服务：

| 字段 | 填写方式 |
| --- | --- |
| 配置 ID、提供方 | 标识当前服务的非空名称 |
| Base URL | 服务商提供的 API 基地址；不要附加 `/chat/completions` 或 `/embeddings`，不要在 URL 中放密钥 |
| 模型 ID | 服务端实际允许调用的完整模型名 |
| 嵌入维度 | 与嵌入模型实际输出一致的正整数；生成服务可留空 |
| 新 API key | 对应服务的凭据；留空且不勾选移除时保留已有 key |

生成服务走当前实现的 DeepSeek 风格聊天接口；嵌入服务走 OpenAI 兼容 Embeddings 接口。PDF 检索需要**嵌入模型**，不能用普通聊天模型替代。服务和模型是否兼容，以其实际接口和测试结果为准。

保存后点击“启用”。启用成功表示本地配置及运行资源初始化成功，不代表远端认证已验证。API key 通过 IDEA 凭据服务保存，不进入聊天或业务 H2。更换 key 直接填写新值；“移除现有凭据”用于删除已有 key。

插件记住最后一次成功启用或明确停用的选择，重启恢复；不会自动重发上次未完成的生成或资料任务。恢复启用失败会回到停用并显示诊断，可重新检查配置。

## 使用

### PDF 资料

1. 启用后打开齿轮设置 → “导入 PDF”。在原生文件选择器中用 Ctrl/Shift 多选可提取文字的 PDF。
2. 插件逐份处理，等待资料状态变为 READY 后再提问。扫描图片 PDF 不提供 OCR。
3. 批量中某份失败或主动停用时，剩余文件停止，已完成资料保留。排除问题后重新选择剩余文件。
4. “更换所选”一次替换一份 PDF；“移除所选”彻底删除托管资料及派生数据，不删除原始文件。旧聊天文字保留，旧引用提示“资料已删除”。
5. 课程库在同一 IDEA 进程的项目间共享，聊天与代码仍按项目和会话隔离。批量导入期间其他资料变更会被拒绝，等待批量结束再操作。

### 聊天与代码

- 右上角依次为新建会话、历史会话、搜索会话、设置。历史可滚动选择，搜索窗口可点击外部关闭。
- 在底部 draft 输入问题；草稿自动保存，无需先手动保存再发送。
- 在 Java 编辑器中选中一个方法或其内部代码，右键“添加到课程助教”。可按“代码条 → 文字 → 代码条 → 文字”混排；所在方法、类结构由插件隐式补充，不重复展示为选中代码。
- 发送后按混排顺序显示，代码条保持紧凑。回答使用 Markdown 渲染；“过程与思考”可展开或收起。
- draft 右下角按钮按当前状态负责发送、重试、暂停或继续生成；拖动 draft 上边缘调整高度。
- 生成时向上滚动可阅读已有内容，点击聊天区底部的 ↓ 回到最新内容。
- 引用显示文件名与 PDF 物理页码，可打开对应来源；物理页码可能与书内印刷页码不同。

### 常见问题

- HTTP 401/403：停用后核对嵌入凭据、模型权限及当前网络策略。VPN/代理可能影响服务端判断；更换 PDF 不能解决权限拒绝。
- TLS 握手失败：检查网络、代理和证书，不能据此判断 key 无效。
- PDF 导入失败：查看错误提示中的任务/诊断编号，在 IDEA 日志中查找对应记录；不要因此直接清空资料库。
- 保存或本地资料清理失败：保留错误编号，排除文件占用后按提示重试或重启。不要把 API key 粘贴到日志、聊天、Issue 或 PR。

人工逐项测试见 [黑盒方案](spec/tasks/dev-manager/BLACKBOX_TEST_PLAN.md) 和 [启动状态/批量导入复测](spec/tasks/dev-manager/FEEDBACK_RETEST_11.md)。

## 开始开发

先读 [AGENTS.md](AGENTS.md)，再读 [当前进度](spec/progress/CURRENT.md) 和 [公共契约](spec/contracts/API.md)。

| 负责人/成员 | 固定分支 | agent 任务入口 |
| --- | --- | --- |
| 负责人，5份 | `dev-manager` | [公共工程与核心](spec/tasks/dev-manager/README.md) |
| 甲，1份 | `dev-a` | [IDE 选区/方法/类捕获](spec/tasks/dev-a/README.md) |
| 乙，1份 | `dev-b` | [固定 PDF 物理分页](spec/tasks/dev-b/README.md) |
| 丙，1份 | `dev-c` | [Tool Window 与 Markdown/代码高亮](spec/tasks/dev-c/README.md) |

四个开发分支独立，**各自 PR → main**，合并后保留分支。`main` 是主分支；禁止创建其他分支。GitHub 服务端规则、本地 hooks 和实际生效状态见 [分支规则](spec/governance/BRANCH_POLICY.md) 与 [当前进度](spec/progress/CURRENT.md)。

成员用自己的目录克隆（按成员替换 dev-a）：

```powershell
git clone --branch dev-a https://github.com/nilesthump/IJ-Plugin-Java.git IJ-Plugin-Java-a
cd IJ-Plugin-Java-a
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/git/Configure-Git.ps1
```

将本任务 README 中的“可复制给 agent 的提示”交给 agent 即可开始；不需要复述本次聊天。不要在另一个人的工作区切分支，也不要使用自动创建分支的工作树工具。

## 设计依据

[F0 综合开发方案](spec/architecture/F0_DEVELOPMENT_PLAN.md) 与 [实施基线](spec/architecture/BASELINE.md) 区分已确认要求、实现方案和验证范围。F0 用户验收已通过；F2 尚未启动。任务需要新增公共字段、依赖或注册时，写本分支 `DEPENDENCY_REQUEST.md` 由负责人协调。
