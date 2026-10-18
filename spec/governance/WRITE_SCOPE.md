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
