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
- 状态：`PROPOSED`。
- 触发场景及当前不能完成的行为：`JavaCodeCapturePort` 已实现 `CodeCapturePort`，`CaptureSelectionAction` 已固定 Java 编辑器中的原选区和来源项目；没有生产 extension 注册和 action 注册时，核心无法解析采集器，用户也没有右键入口。
- 需要的最小公开动作注册：在 `plugin.xml` 的 `edu.courseassist.f0` 扩展命名空间注册 `edu.courseassist.editor.JavaCodeCapturePort` 为唯一 `codeCapture` 实现；注册 action `edu.courseassist.editor.CaptureSelectionAction`，仅在 Java 文件且非空选区时显示于编辑器右键菜单。action ID、菜单组和展示文案由负责人按现有插件布局确定。
- 现有能力为何不足：当前 `plugin.xml` 只声明 `codeCapture` extension point；`CourseAssistantProject.codeCapture()` 要求恰有一个实现，缺失或重复均明确失败。成员无权修改插件注册文件。
- 预计影响的公共文件及消费者：`src/main/resources/META-INF/plugin.xml`；消费者是 `CourseAssistantProject.codeCapture()` 和编辑器用户。无公共 DTO、构建依赖或 fixture 变更。
- 可独立继续的本任务工作：完成端口的 PSI 行为测试、版本变化和跨边界错误测试；注册前不伪造生产动作成功。
- 负责人处理结论、公共 PR 和合入 main 提交：待填。
- 本任务同步基线后实际验证：`NOT_RUN`；等待本任务编译和负责人注册后执行目标 IDEA 场景。


## DEV-A-002：分离 ideSmoke 与 test 源码根

- 日期 / 状态：2026-10-24 / PROPOSED，负责人公共构建范围。
- 触发：重启并导入 Gradle 后，JUnit import、Test、TempDir 和断言方法仍无法解析。
- 实测：VS Code Gradle Build Server 将 src/test/java 映射到 ideSmoke 输出且无 test=true；已下载并导入的 JUnit 依赖均标记 test=true。详见本任务 STATUS 的 2026-10-24 诊断。
- 建议：将 bootstrap/ide smoke 源码迁移到独立 src/ideSmoke/java，移除共享 src/test/java 的 ideSmoke 源目录配置，同步脚本/文档/范围规则中实际受影响的路径引用。保留 JUnit 为 testImplementation，不将其提升为生产依赖。
- 验证：重新导入后确认 src/test/java 的 test=true 和 JUnit 可见性；统一 All 与 IdeSmoke 尚未运行，方案尚未实施。原测试运行期 ClassNotFoundException 需独立验证。
