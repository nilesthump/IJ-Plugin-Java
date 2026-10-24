# 甲：编辑器代码采集：状态

- 任务定义：`IN_PROGRESS`。
- 功能实现：`IN_PROGRESS`。已实现 Java 编辑器动作、固定选区坐标转换和 `CodeCapturePort` 的后台 PSI 采集；动作及扩展注册等待负责人处理 `DEV-A-001`。
- 当前分支：`dev-a`；PR 目标：`main`，合并保留来源分支。
- 记录日期：2026-10-23。
- 当前 HEAD / 公共 main 基线：`9cf52ca`；`dev-a` 与 `origin/main` 同步后开始实现。
- 公共基线：按根 AGENTS、[API](../../contracts/API.md) 和本任务 [README](README.md) 核对；契约/fixtures 与工程骨架不等于本任务实现。

## 当前可观察结果

`CaptureSelectionAction` 在动作开始固定源项目、文件 URL、document modification stamp、原始选区和一基 UTF-16 坐标，并调用项目绑定的 `SelectionSink`。`JavaCodeCapturePort` 按冻结的 project instance 与文件版本查回 PSI，在后台读作用域中返回 METHOD 和 CLASS_STRUCTURE 两个结果；跨方法/类边界、文档变化、未提交 PSI、文件或源项目不可用均异常完成，不伪装为正常缺项。

类结构只保留当前类声明、字段和嵌套类结构，不包含方法实现。入口和端口尚未注册进生产 `plugin.xml`，见 [依赖请求](DEPENDENCY_REQUEST.md)。

## 验证记录

| 检查 | 状态 | 命令 / 退出码 / 证据 |
| --- | --- | --- |
| 差异写入范围 | NOT_RUN | 待填；提交前使用 `scripts/check-scope.ps1 -Staged` |
| 仓库结构检查 | PASS | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All` 的 repository 阶段已通过；该阶段不证明编译或 IDEA 运行 |
| 任务相关测试编译 | PASS | `./gradlew.bat --no-daemon --console=plain --gradle-user-home "C:\\Users\\Lenovo\\.gradle" cleanTest compileTestJava`；退出码 0，2026-10-23 |
| 任务相关断言 | BLOCKED | `--stacktrace --info` 重跑 `SourceRangesTest`，退出码 1；测试类存在于普通与插桩输出，worker classpath 也含插桩目录，但 IntelliJ `UrlClassLoader` 报 `ClassNotFoundException`。完整证据见 [TEST_SANDBOX_DIAGNOSIS.md](evidence/TEST_SANDBOX_DIAGNOSIS.md) |
| 基线测试对照 | BLOCKED | 相同参数重跑未修改的 `TaskScopeTest`，退出码 1；使用与任务测试完全相同的 runtime classpath，得到同构 `ClassNotFoundException`。完整证据见 [TEST_SANDBOX_DIAGNOSIS.md](evidence/TEST_SANDBOX_DIAGNOSIS.md) |
| 完整统一入口 | INCOMPLETE | 初始默认缓存解析为 `C:\\.gradle`，无法创建锁文件；改用已存在的 `C:\\Users\\Lenovo\\.gradle` 后 Wrapper 与编译可运行，但 test sandbox 基线阻塞，PR 前必须重跑 All |
| 场景验收 | NOT_RUN | 按 README 逐项记录实际输入与观察结果 |
| 真实环境边界 | NOT_RUN | 真实 PSI/动作上下文、后台读机制与目标 IDEA 兼容性 |

记录局部测试、模拟控制器、真实 IDE 和真实 API 的证据边界。没有运行的项目保持 NOT_RUN；失败保留原因和退出码，不只改状态文字。真实 API 不因本地任务验证而自动调用。

## 阻塞与下一步

- 当前阻塞：IntelliJ 测试 JVM 在测试类已编译、插桩目录已进入 worker classpath 的情况下仍无法通过 `UrlClassLoader` 加载任务测试和未修改的基线测试；根因还需在共享构建或路径对照环境中进一步隔离。生产注册另等待负责人处理 `DEV-A-001`。
- 下一步：确认固定分支及已有改动，按 README 阅读并运行基线检查，然后实现首个可观察场景。
- 公共改动请求：见 [DEPENDENCY_REQUEST.md](DEPENDENCY_REQUEST.md)。
- 结束/换 agent 前：更新本文件并填写 [HANDOFF.md](HANDOFF.md)，记录真实提交、测试和下一条动作。


## 2026-10-24：VS Code Gradle 导入诊断

- 已确认当前为 dev-a，保留已有暂存修改。此次只诊断并补充任务记录，未改公共构建或编辑器配置。
- 重启后 SourceRangesTest 仍有 13 条 JUnit 相关诊断。VS Code Java 工作区元数据表明项目已通过 Gradle Build Server 导入，并在 18:26:02 完成 build jobs。
- 根因证据：导入 classpath 将 src/test/java 映射为 build/classes/java/ideSmoke，未带 test=true；junit-jupiter-api 5.13.4 等依赖已导入、磁盘文件存在，但均带 test=true。因此该源码目录中的普通测试无法访问测试专用 JUnit 依赖。
- 公共 build.gradle 的 ideSmoke 与 test 共用 src/test/java，是此映射冲突的来源。重装 Gradle 或再次清理缓存不会消除源集重叠。
- 建议负责人将 bootstrap/ide 的 smoke 源码迁移到独立 src/ideSmoke/java，移除 ideSmoke 对 src/test/java 的共享配置，并同步相关路径引用；重新导入后检查 src/test/java 恢复 test=true，再运行统一 validate.ps1 -Scope All 与必要 IdeSmoke。
- 修复及统一验证：NOT_RUN；本次没有执行构建，不把编辑器诊断与已有测试 JVM ClassNotFoundException 认定为同一根因。


## 2026-10-24：修复前检查点准备

- 本轮仅准备提交，未执行 commit/push，未修复业务代码或共享构建。
- 分支 dev-a；本地 core.hooksPath=.githooks。git diff --cached --check 与 git diff --check 均无输出。
- scripts/check-scope.ps1 -Staged：退出 0，14 个路径均符合 dev-a 范围。
- .vscode/ 已加入本仓库 .git/info/exclude；settings.json 未跟踪、未暂存，仅本地保留。
- 14 个暂存文件的常见凭据模式扫描仅命中 task-scope-test-result.xml / task-scope-worker-classpath.txt 文件名子串，已人工核对；未发现实际密钥。这不是完整秘密审计。
- 本轮统一命令：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -GradleUserHome C:\Users\Lenovo\.gradle。日志：tmp/precommit/validation.log（本地忽略文件）。
- 当前结果：RUNNING / INCOMPLETE，尚无退出码。repository 通过，compileJava / compileTestJava 等为 UP-TO-DATE，FixtureSmoke 实际 PASS；停留在 :test，尚未产生 JUnit XML，不能声称测试或插件打包通过。此次未执行 IdeSmoke。历史失败证据保持原日期，不作为本次运行结果。
- 只有验证获得终态后才能更新本轮 PASS/FAIL；当前只可视为带未完成验证的检查点候选。DEV-A-001/002、两处空值诊断和历史测试类加载问题仍未关闭。


## 2026-10-24：检查点后的显式判空修复

- 基线：095d290，分支 dev-a；本轮未提交或推送。
- JavaCodeCapturePort：在已处理起止均空分支后，为方法/类的跨边界判断增加 startMethod == null / startClass == null。其余行为不变：均空仍为 NOT_APPLICABLE，单端空或对象不同仍抛出原异常，同一非空对象正常捕获。
- 编辑器诊断：修复前本文件 2 处 potential null pointer access；修复后 problems 明确返回 No errors found。未关闭 null analysis。git diff --check 通过。
- 本轮统一验证命令：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -GradleUserHome C:\Users\Lenovo\.gradle。日志 tmp/precommit/fix-validation.log。repository PASS、compileJava 实际执行成功、FixtureSmoke PASS；compileTestJava UP-TO-DATE。当前 :test 仍运行，无 JUnit XML 或最终退出码，完整结果 RUNNING / INCOMPLETE；插件打包与 IDE 场景不记通过。
- 上次 tmp/precommit/validation.log 停在 :test，但原进程已不存在、执行会话无法恢复；终态 UNKNOWN，不能继续标为运行中或声称成功。
- 公共阻塞分别见 DEV-A-001、DEV-A-002、DEV-A-003；未修改共享构建、公共源码或插件注册。下一步先获取本轮测试终态，再由负责人修复源集并重验运行阻塞。


### 本轮最终验证结果（替代上方运行中状态）

统一 All 验证退出 0，BUILD SUCCESSFUL in 4m 2s。JUnit XML 汇总：16 个 suite，91 项测试，失败 0，错误 0，跳过 0。SourceRangesTest 包含在本次报告中。FixtureSmoke、check、buildPlugin 均通过；实际 IDEA/PSI 运行 NOT_RUN（未使用 -IdeSmoke）。历史类加载失败本轮未复现，不证明此前根因已修复；VS Code JUnit 源集映射仍待 DEV-A-002。原日志 tmp/precommit/fix-validation.log，本次 XML 位于 build/test-results/test/。
