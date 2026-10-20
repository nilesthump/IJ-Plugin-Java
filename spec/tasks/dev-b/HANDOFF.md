# dev-b PDF 解析交接

日期：2026-10-20（Asia/Singapore）。整体 `IN_PROGRESS`；B1–B3 已实现和模块补充验证，尚未生产集成。

## 工作区

- 既有 dev-b，跟踪 origin/dev-b；已同步 main 基线为 `2976118fb4e8a8980da70b59041ed29f77d524fc`。本轮 fetch 后 main 未变化。
- 上轮 PLAN/STATUS/HANDOFF 内容已延续。本次按用户要求将源码、测试及任务记录作为本地提交交付；实际实现提交号执行 `git log -1 --format=%h -- src/main/java/edu/courseassist/pdf/PdfBoxPdfReader.java` 查询。未推送或创建 PR；后续固定 dev-b→main，保留分支。
- 已按根入口阅读公共基线、治理、任务与 Java 契约；用户已授权开始实现，并补充九份课件到本地 local-course-pdfs。

## 实际成果和证据

`edu.courseassist.pdf.PdfBoxPdfReader` 有 public 无参构造，无共享可变状态。只读托管路径，透传原请求，完整物理页保留空页，独立标签，不 trim，LF/内容流顺序。显式预解码直接内容流，防止坏页被 PDFBox 容错跳过；IOException 保留，不返回部分成功；文件/文档/内容流关闭，无线程/事务/模型操作。

同包 PdfBoxPdfReaderTest 共 12 项合成 PDF 测试。Java25 编译及 JUnit 最终退出 0；九份真实课件共 199 页的临时托管副本探针退出 0，独立 pypdf 页数一致，原件字节未变，副本可重命名/删除。没有记录讲义正文。完整规则、限制、英文报告素材和补充命令见 [IMPLEMENTATION](IMPLEMENTATION.md)。

日志在忽略的 tmp/dev-b-validation：javac.log、junit-final.log、junit/TEST-junit-jupiter.xml、course-pdfs.log。初期临时目录沙箱失败和坏流回归失败已保留并修正，不假称从未失败。公开精简汇总见 [evidence/local-validation.json](evidence/local-validation.json)。

统一 All 首次因沙箱 socket 拒绝退出 1（baseline.log）；获准联网重试同一入口最终 PASS/0（baseline-network.log）。目标 SDK 2026.2.3/262.10968.63，主工程/测试编译、FixtureSmoke、88 项 JUnit（含 PDF 12 项，15 个套件，0 失败/错误/跳过）及 buildPlugin 均通过，ZIP 位于 build/distributions/IJ-Plugin-Java-0.1.0-dev.zip。结构及八份文件暂存范围检查退出 0，检查后恢复空暂存区，日志 repository.log/scope.log。生产扩展加载/实际 IDE/真实模型仍 NOT_RUN；SDK 编译不等于实际 IDE 验证。

## 接入与下一步

[PDF-REG-001](DEPENDENCY_REQUEST.md) 为 PROPOSED：组长将唯一实现 `edu.courseassist.pdf.PdfBoxPdfReader` 注册到现有 edu.courseassist.f0.pdfReader 扩展点。无需新增库、DTO、注入或释放钩子。本轮未改公共注册，也未向他人发工具消息；不假称已获组长接受。

下一步公共注册合入 main 后同步，执行三页样例的真实入库/页号/固定来源和失败不发布场景。独立审核及 main 合入仍待完成；不导入 manager 内部实现绕过公共入口，不自行改 plugin.xml。

两份原始 PDF 和 local-course-pdfs 全目录均保持 Git 忽略；tmp 中 JAR/SDK/探针/副本不暂存。Git 通过进程级 safe.directory 处理当前目录所有权提示，hooks 保持开启；构建使用进程级 DataGrip JBR25.0.3，不改系统 Java。目标 SDK 是仓库 2026.2.3，本机旧 IDEA 不冒充目标运行。
