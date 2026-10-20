# 乙：固定 PDF 快照解析：公共依赖与接口请求

当前：`PROPOSED`，见 PDF-REG-001。此文件用于提出公共变更，不授权成员修改 build、plugin.xml、contract 或 fixtures。

负责人在公共范围评估并从 `dev-manager` PR 到 `main`，成员同步 `main` 后接入。公共签名以 [API.md](../../contracts/API.md) 与 Java 定义为准；PDFBox/commonmark 等已预配依赖先检查现有构建，避免重复申请。

## PDF-REG-001：注册已实现的 PDF reader

- 提出日期 / 归属人：2026-10-20 / dev-b；状态 `PROPOSED`，尚未获负责人接入或合入确认。
- 触发场景：`CourseAssistantApplication.pdfReader()` 要求扩展点恰有一个实现。当前主线只有 extension point 声明，没有生产解析器注册，真实资料导入尚不能通过该入口取得 reader。
- 最小公共变更：负责人在 `src/main/resources/META-INF/plugin.xml` 的 `edu.courseassist.f0` 命名空间注册 `pdfReader implementation="edu.courseassist.pdf.PdfBoxPdfReader"`。沿用既有 `PdfReader` 契约，不新增字段、依赖、服务或 fixtures。

  建议负责人在现有 plugin.xml 的 idea-plugin 下添加：

  ```xml
  <extensions defaultExtensionNs="edu.courseassist.f0">
      <pdfReader implementation="edu.courseassist.pdf.PdfBoxPdfReader"/>
  </extensions>
  ```

- 实现要求：类为 public/final，public 无参构造；无实例可变状态；同步读取并关闭本次文件资源，线程/取消/托管生命周期仍归核心。
- 已有证据：Java25 编译与 12 项真实合成 PDF JUnit 通过；九份真实课件共 199 页的固定副本读取、页数独立核对、原件未变和副本重命名/删除通过。另已运行统一 All，退出 0，全工程 88 项 JUnit/FixtureSmoke/buildPlugin 均通过；见 [IMPLEMENTATION](IMPLEMENTATION.md)。这些证据仍不替代生产扩展点和实际 IDEA 联调。
- 可独立继续：B1–B3 已实现及补充验证；组长注册前可完善范围内测试/文档，不能自行修改公共注册或直接绕过 application 服务。
- 联合验收：负责人注册合入 main 后，dev-b 同步；真实插件中取得 reader，用三页合成文件走入库管线，验证来源/物理页引用以及坏页不发布新版本。真实模型效果单独验证。
- 负责人结论 / 公共 PR / main 提交：`PENDING`。未通过工具向他人发送消息，本文是仓库内的待评估接入记录。

## 后续请求模板

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
