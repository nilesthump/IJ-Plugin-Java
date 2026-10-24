# 甲：编辑器代码采集：公共依赖与接口请求

当前：`PROPOSED`。尚未核查的需求不等于已确认无需依赖。此文件用于提出公共变更，不授权成员修改 build、plugin.xml、contract 或 fixtures。

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

## DEV-A-001：注册编辑器采集端口与右键动作

- 请求 ID / 提出日期 / 归属人：`DEV-A-001` / 2026-10-23 / dev-a。
- 状态：`IMPLEMENTED`。负责人已通过 main 的 PR #11 注册唯一 `JavaCodeCapturePort` 和公共 `CaptureEntryAction`，并由真实 IDE smoke 验证注册动作、端口与耐久三段捕获。
- 触发场景及当前不能完成的行为：`JavaCodeCapturePort` 已实现 `CodeCapturePort`，`CaptureSelectionAction` 已固定 Java 编辑器中的原选区和来源项目；没有生产 extension 注册和 action 注册时，核心无法解析采集器，用户也没有右键入口。
- 需要的最小公开动作注册：在 `plugin.xml` 的 `edu.courseassist.f0` 扩展命名空间注册 `edu.courseassist.editor.JavaCodeCapturePort` 为唯一 `codeCapture` 实现；注册 action `edu.courseassist.editor.CaptureSelectionAction`，仅在 Java 文件且非空选区时显示于编辑器右键菜单。action ID、菜单组和展示文案由负责人按现有插件布局确定。
- 现有能力为何不足：当前 `plugin.xml` 只声明 `codeCapture` extension point；`CourseAssistantProject.codeCapture()` 要求恰有一个实现，缺失或重复均明确失败。成员无权修改插件注册文件。
- 预计影响的公共文件及消费者：`src/main/resources/META-INF/plugin.xml`；消费者是 `CourseAssistantProject.codeCapture()` 和编辑器用户。无公共 DTO、构建依赖或 fixture 变更。
- 可独立继续的本任务工作：完成端口的 PSI 行为测试、版本变化和跨边界错误测试；注册前不伪造生产动作成功。
- 负责人处理结论、公共 PR 和合入 main 提交：待填。
- 本任务同步基线后实际验证：`NOT_RUN`；等待本任务编译和负责人注册后执行目标 IDEA 场景。


## DEV-A-002：分离 ideSmoke 与 test 源码根

- 日期 / 状态：2026-10-24 / `IMPLEMENTED`。负责人已通过 main 的 PR #11 将 `ideSmoke` 迁移到独立 `src/ideSmoke/java`；重新加载后 VS Code editor 目录无 JUnit 诊断。
- 触发：重启并导入 Gradle 后，JUnit import、Test、TempDir 和断言方法仍无法解析。
- 实测：VS Code Gradle Build Server 将 src/test/java 映射到 ideSmoke 输出且无 test=true；已下载并导入的 JUnit 依赖均标记 test=true。详见本任务 STATUS 的 2026-10-24 诊断。
- 建议：将 bootstrap/ide smoke 源码迁移到独立 src/ideSmoke/java，移除共享 src/test/java 的 ideSmoke 源目录配置，同步脚本/文档/范围规则中实际受影响的路径引用。保留 JUnit 为 testImplementation，不将其提升为生产依赖。
- 验证：重新导入后确认 src/test/java 的 test=true 和 JUnit 可见性；统一 All 与 IdeSmoke 尚未运行，方案尚未实施。原测试运行期 ClassNotFoundException 需独立验证。


## DEV-A-003：统一测试运行阻塞与公共诊断收尾

- 日期 / 状态：2026-10-24 / PROPOSED，归属 dev-manager。
- 测试运行：历史 UrlClassLoader ClassNotFoundException 证据见 evidence/TEST_SANDBOX_DIAGNOSIS.md；2026-10-24 检查点验证日志停止在 :test，后续检查原进程已不存在且无法恢复退出码，结果为 INCOMPLETE / UNKNOWN，不认定为相同异常或通过。源集修复后应独立重验测试运行。
- 公共代码仍有方法引用的 null type safety 诊断、未使用 import，以及 SpringAiTransport.messageConverters 的弃用提示。请按实际依赖版本核对非空注解/泛型约束，清理未使用 import；迁移 configureMessageConverters 时保留显式转换器集合及避免不兼容格式探测的行为，用本地合成 HTTP 场景验证。不要把 JUnit 提升为生产依赖或关闭空值检查代替修复。
- 本成员已经用显式起点判空消除 JavaCodeCapturePort 的两处诊断，未改公共源码、构建、版本或注册。生产接入仍依赖 DEV-A-001，编辑器测试源码映射仍依赖 DEV-A-002。


### 本轮最终验证结果（替代上方运行中状态）

统一 All 验证退出 0，BUILD SUCCESSFUL in 4m 2s。JUnit XML 汇总：16 个 suite，91 项测试，失败 0，错误 0，跳过 0。SourceRangesTest 包含在本次报告中。FixtureSmoke、check、buildPlugin 均通过；实际 IDEA/PSI 运行 NOT_RUN（未使用 -IdeSmoke）。历史类加载失败本轮未复现，不证明此前根因已修复；VS Code JUnit 源集映射仍待 DEV-A-002。原日志 tmp/precommit/fix-validation.log，本次 XML 位于 build/test-results/test/。

## DEV-A-004：提供 IntelliJ 平台测试框架依赖

- 请求 ID / 提出日期 / 归属人：`DEV-A-004` / 2026-10-24 / dev-a。
- 状态：`PROPOSED`，属于 `dev-manager` 公共构建范围。
- 触发场景：甲需要用真实 Java PSI 覆盖方法/类结构、构造器、嵌套类、文档版本变化、跨边界和项目关闭场景。当前 `test` classpath 可编译 IntelliJ 产品 API，但不包含 `com.intellij.testFramework.fixtures.BasePlatformTestCase`。
- 实际证据：新增平台测试后执行 `.\\gradlew.bat --no-daemon --console=plain --gradle-user-home C:\\Users\\Lenovo\\.gradle test --tests "edu.courseassist.editor.JavaCodeCapturePortTest"`，`:compileTestJava` 因 `com.intellij.testFramework.fixtures` 不存在而退出 1；未保留该不可编译测试文件。
- 最小公共变更：按当前 IntelliJ Platform Gradle Plugin 2.19.0 的官方方式，仅为 `test` source set 增加匹配目标 IDE 版本的 IntelliJ Platform test framework 依赖；不要把测试框架加入生产插件或运行时包。若公共测试约定选择其他已有平台测试入口，也需允许 `dev-a` 在自身测试目录运行真实 PSI fixture。
- 验证要求：平台测试编译并实际运行；统一 `scripts/validate.ps1 -Scope All` 与必要 `-IdeSmoke` 仍需通过，发布 ZIP 不包含测试框架。准确依赖写法和版本由负责人按现有插件版本落实。
- 可独立继续：坐标转换边界、纯值冻结逻辑、任务记录整理；真实 PSI、动作事件和项目生命周期测试等待该请求。

## 2026-10-24：合并 main 后复核

- `DEV-A-001`：`IMPLEMENTED`。生产 `plugin.xml` 已注册 `JavaCodeCapturePort` 与公共 `CaptureEntryAction`。
- `DEV-A-002`：`IMPLEMENTED`。`ideSmoke` 已迁到独立源码根；VS Code 当前对 editor 主源码和测试均无诊断。
- `DEV-A-004`：仍为 `PROPOSED`。公共构建没有加入 `BasePlatformTestCase` 所需 test framework；负责人改以 `src/ideSmoke` 的 `EditorIntegrationChecks` 覆盖一个真实注册动作/Java PSI/耐久捕获场景，但甲仍不能在自己的测试目录扩展构造器、跨边界、文档变化和取消测试。
- 合并后验证：首次 `All + IdeSmoke` 因 Gradle Test Executor 120 秒连接超时退出 1；JUnit XML 中 105 项业务测试实际均通过，唯一失败为 Gradle 测试运行基础设施。随后 editor focused 9 项退出 0，再次执行统一 `All + IdeSmoke` 退出 0。
- 最终结果：20 个 JUnit suite、106 项测试，失败/错误/跳过均 0；`build/reports/ide-smoke/result.json` 为 `PASS`，IDE build `IU-262.10968.63` / JBR `25.0.4`，包含 `registered-editor-action-port` 与 `registered-editor-action-Java-PSI-durable-three-part-capture`。
