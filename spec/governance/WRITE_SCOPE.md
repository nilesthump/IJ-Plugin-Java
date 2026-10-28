# 写入范围与公共接入点

本文件用于本次仓库初始化后的实现分工。负责人和三位成员按 `5:1:1:1` 分配职责；该比例不表示已承诺的小时数。四个开发分支各自向 `main` 提 PR，`dev-manager` 与其他三个开发分支并列。操作顺序见 [WORKFLOW.md](WORKFLOW.md)，分支约束见 [BRANCH_POLICY.md](BRANCH_POLICY.md)。

所有业务代码使用 Java，包名前缀为 `edu.courseassist`。一个 Gradle Groovy 工程、一个插件产物；逻辑包不是四个独立服务或四份插件。下表是源文件的写入归属，读取其他范围不受此表限制。

| 分支 / 任务 | 可写实现与测试 | 可写任务记录 |
| --- | --- | --- |
| `dev-a` / [甲：编辑器采集](../tasks/dev-a/README.md) | `src/main/java/edu/courseassist/editor/**`；`src/test/java/edu/courseassist/editor/**` | `spec/tasks/dev-a/**` |
| `dev-b` / [乙：PDF 解析](../tasks/dev-b/README.md) | `src/main/java/edu/courseassist/pdf/**`；`src/test/java/edu/courseassist/pdf/**`；`src/test/resources/pdf/**` | `spec/tasks/dev-b/**` |
| `dev-c` / [丙：界面](../tasks/dev-c/README.md) | `src/main/java/edu/courseassist/ui/**`；`src/test/java/edu/courseassist/ui/**`；`src/test/resources/ui/**` | `spec/tasks/dev-c/**` |
| `dev-manager` / [负责人：公共与核心](../tasks/dev-manager/README.md) | `contract/**`、`core/**`、`model/**`、`storage/**`、`bootstrap/**`（均位于 `src/main/java/edu/courseassist/`）及对应测试；公共 `src/testFixtures/java/edu/courseassist/fixtures/**` | `spec/tasks/dev-manager/**`；公共架构、契约、治理与交付文档 |

负责人还负责 `build.gradle`、`settings.gradle`、`gradle.properties`、Gradle Wrapper、`src/main/resources/META-INF/plugin.xml`、`scripts/**`、`.github/**`、`.githooks/**` 和根级工程/治理文件。负责人的公共职责不包含并发覆盖甲、乙、丙的实现、测试、资源与各自任务记录。若确需跨范围修复，先与归属人协调清楚具体文件和交接时点，再记录范围调整；不得借集成之名整目录覆盖。

## 公共边界

- 三位成员不直接修改构建、插件注册、公共契约或 fixtures。需要新依赖、接口字段、动作注册、插件依赖或 fixtures 时，先写各自的 `DEPENDENCY_REQUEST.md`，给出用例和最小变更；负责人落实公共变更并独立 PR 到 `main`，成员从 `main` 同步。已有 PDFBox、commonmark 配置以当前构建为准，无需各自重复引入。
- 对外类型以 [API.md](../contracts/API.md) 和 `src/main/java/edu/courseassist/contract/` 为准。架构文档中的示意签名不能覆盖已落地签名。成员可自由重构自己包内的私有实现，不把 Swing、PSI、PDFBox、JDBC 或模型 SDK 类型泄漏进公共 DTO。
- `editor` 只产生固定代码快照；`pdf` 只解析已经托管的固定 PDF；`ui` 只渲染状态和发送事件。持久化、身份分配、幂等、代码组归属、会话删除、资料版本、检索、模型调用和后续上下文资格由负责人负责。
- `SessionController` 面向项目/会话；`ApplicationController` 面向应用状态、配置和共享资料。跨项目共享资料事件不附会话目标。视图订阅关闭不等于停止生成，删除会话必须走独立命令。
- 密钥不进入仓库、日志、fixture 或视图状态。界面按契约使用短期凭据交付入口，不自行增加字符串密钥字段。通常验证使用合成数据/假控制器；不得为让检查变绿而调用真实课程平台或模型 API。

## 生成物与冲突

运行统一验证入口可能产生 `build/`、`.gradle/` 等忽略文件，这不扩展源文件归属；不提交这些缓存、测试账号配置或真实课程/学生数据。测试资源只能使用可分发的合成样例。

发现范围外错误时先在本任务 `STATUS.md` 记录重现和影响，再向归属人说明；在自己的范围继续独立工作。需要契约变更不能用反射、重复定义 DTO、直接访问其他模块内部字段、增加静默兼容分支或复制实现绕过。规则与已授权的用户新要求冲突时，记录并同步治理，不要求用户重复授权常规实现。

## 完成与证据

任务的 `READY` 只表示任务说明可开始，不表示功能已经实现。每个任务须区分实现状态、自动验证、真实 IDE 验证、真实 API 验证及尚未覆盖的场景；未执行写 `NOT_RUN`，环境阻塞写出具体原因。fixtures 能证明契约接入或渲染，不能证明数据库恢复、模型协议或产品端到端成功。

提交前检查差异均在分配范围内；执行 [统一验证入口](../../scripts/validate.ps1)，并把命令、退出码、真实测试范围和证据位置写进自己的 `STATUS.md` / `HANDOFF.md`。所有开发 PR 目标是 `main`，合并保留固定来源分支。

# F2 G0 实施决定与精确范围

日期：2026-10-26。负责人：用户本人；执行分支 dev-manager。用户要求完成前两组，即 G0、G1，并明确授权精确治理变更提案。不是架构冻结。

- D0：既有 dev-manager 集中交付，独立治理 PR 先合入 main；不改其他分支 HEAD。
- D1：现有单 Java 文件、完整候选整体审核；不新增/删除/移动文件，不做多文件或逐块批准。方法外改动须属于快照明确允许范围。
- D4：应用到 Document，遵循 IDE 正常保存行为，不额外强制保存；磁盘状态独立报告。
- D6：仅明确按钮决定；draft 中“同意”等文字不授予写权限。
- D2/D3/D5/D7：G1/G3 按最小本地 Java 能力边界、无自动重放及有界生成落实，准确参数和证据在实现组记录。未实施能力不返回成功。

## 精确路径

既有 manager 的 contract/core/model/storage/bootstrap、对应测试、公共 fixtures、IDE smoke、构建/注册/脚本/治理权限保持。

额外允许四个目录，必须包括末尾斜线：

- src/main/java/edu/courseassist/editor/f2/
- src/test/java/edu/courseassist/editor/f2/
- src/main/java/edu/courseassist/ui/f2/
- src/test/java/edu/courseassist/ui/f2/

旧 UI 仅列三个必要接线文件，精确匹配：

- src/main/java/edu/courseassist/ui/CourseAssistantView.java
- src/main/java/edu/courseassist/ui/ChatTimeline.java
- src/test/java/edu/courseassist/ui/CourseAssistantViewTest.java

其他 editor/ui/PDF 文件、成员任务记录及 UI/PDF 资源保持排除。现有 MixedDraftEditor 不在例外内。新 adapter 测试放对应 f2 包。例外只支持 F2 接线，不授权改写 F0 功能。

本地 PowerShell 与 main 可信 scope 工作流读取相同 managerExceptions.prefixes/files，保留全部 managerExcluded。重命名按旧/新两端逐一检查。固定五分支、hooks、强推/删除禁令、PR 目标和必需检查不变。G0 只改治理/文档/验证文件，不含业务代码；main 实际合入前 G1 NOT_STARTED。

## 2026-10-27 人工反馈精确更正

用户明确报告F0成功完成后仍显示重试，要求修复；同时要求F2编辑器/保存/Undo状态在代码内部自动校验。本轮沿用dev-manager，允许既有精确例外 CourseAssistantView.java 与 CourseAssistantViewTest.java 修正完成态按钮映射；ChatTimeline.java 移除常规手动核验按钮。仅此反馈覆盖上文“例外只支持F2接线”的语义限制，已有机检路径无需改变。其他旧UI/editor/PDF路径仍排除，不修改hooks或机器策略。原文在负责人忽略evidence中记录并登记哈希；用户人工通过后才统一PR合并。

## 2026-10-27 会话与定位范围更正

用户明确授权当前项目目录内现有Java文件定位/应用；无前台会话保留空新会话界面并在发送时创建继续；历史/搜索显示首条消息摘要并可右键改名。既有精确UI例外 CourseAssistantView.java、ChatTimeline.java及对应测试可实现上述F0交互更正；manager契约/core/storage与editor/f2原权限不变。不改MixedDraftEditor或机器范围策略。原文见本地忽略evidence。

## 2026-10-28 最终体验优化更正

用户授权移除draft上方常驻ERROR区并改为短弹窗，准确提示F2源码/选区已更改。沿用既有CourseAssistantView.java及测试精确例外，辅助通知类放ui/f2；不改MixedDraftEditor，不扩大机检目录。保留失败档案、保存阻塞和审核保护。

## 2026-10-28 讨论公开与交付授权

用户要求将所有未入Git的讨论文件一并上传。负责人可将spec/architecture下DISCUSSION.md、WORKING.json、FINAL_CHECK.md及evidence纳入Git，原始附件和凭据除外；本更正优先于此前“本地忽略/不公开”规定，不改变成员目录归属或架构冻结状态。用户已授权先清包、补README、PR检查通过后合并，再创建R05待验证issue；R05人工结果不据此补记通过。
