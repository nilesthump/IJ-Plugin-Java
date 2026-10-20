# dev-b：固定 PDF 解析状态

更新：2026-10-20（Asia/Singapore）。整体 `IN_PROGRESS`，不是 DONE。

- 分支 `dev-b`，跟踪 origin/dev-b；同步的 main 基线 `2976118fb4e8a8980da70b59041ed29f77d524fc`。用户已授权本地提交本次实现；实际实现提交号可用 `git log -1 --format=%h -- src/main/java/edu/courseassist/pdf/PdfBoxPdfReader.java` 查询。未推送或创建 PR。
- B0 已完成；B1–B3 已实现并完成模块补充验证。B4 生产注册/核心联调及 B5 统一验收/审核/合入待完成。
- 实现：`src/main/java/edu/courseassist/pdf/PdfBoxPdfReader.java`；12 项测试：`src/test/java/edu/courseassist/pdf/PdfBoxPdfReaderTest.java`。
- 规则、限制及英文报告素材见 [IMPLEMENTATION](IMPLEMENTATION.md)；[阶段计划](PLAN.md)；[接入请求 PDF-REG-001](DEPENDENCY_REQUEST.md)；[验证汇总](evidence/local-validation.json)。

## 行为

只读固定托管快照，原请求透传，完整保留 1..N 物理页，空页/图片无文本页为 EMPTY，印刷标签独立。使用内容流顺序和 LF 分隔符，保留提取字符串，不 trim。直接内容流逐页预解码，避免 PDFBox 跳过坏流后伪装空白页；失败使整次读取失败。文件源、文档及验证流均在成功/失败路径释放，无业务线程、事务或远程调用。

## 验证记录

| 检查 | 实际结果 | 证据与边界 |
| --- | --- | --- |
| Java25 模块编译 | PASS / 0 | JBR25.0.3，javac --release 25；补充命令见 IMPLEMENTATION；tmp/dev-b-validation/javac.log；不等于完整插件 |
| 模块 JUnit | PASS / 0，12/12 | JUnit Console execute；tmp/dev-b-validation/junit-final.log 及 junit/TEST-junit-jupiter.xml；运行时生成合成 PDF |
| 真实课件探针 | PASS / 0，9 份 / 199 页 | tmp/dev-b-validation/course-pdfs.log；独立 pypdf 页数一致，连续页号/原请求、原件字节未变、副本重命名/删除通过；不证明所有布局/字形语义 |
| 统一 All 默认沙箱 | FAIL / 1 | Gradle 下载 socket 被沙箱拒绝；tmp/dev-b-validation/baseline.log |
| 统一 All 联网重试 | PASS / 0，88/88 | 同一入口获准联网，目标 SDK 2026.2.3/262.10968.63；主工程/测试编译、FixtureSmoke、全工程 88 项 JUnit（含 PDF 12 项）及 buildPlugin 通过；tmp/dev-b-validation/baseline-network.log；15 个测试套件，0 失败/错误/跳过 |
| 结构/写入范围 | PASS / 0，8 paths | 统一 Repository 和 check-scope -Staged；tmp/dev-b-validation/repository.log / scope.log；仅暂存八份明确文件检查后恢复空暂存区 |
| 生产注册/IDE 联调 | NOT_RUN | PDF-REG-001 为 PROPOSED，未注册实际实现；本机旧 IDEA 不当作目标验证；未调用付费模型 |

12 项场景：三页文字/空白/文字、独立标签、图片/空白页、托管副本/身份/字节、空白保留/LF、重复成功后的句柄、缺文件、坏文件/截断、后续坏页禁止部分成功、原密码异常、权限拒绝、并发独立读取。

失败历史保留：初次 JUnit 默认用户临时目录清理受沙箱拒绝，改用工作区临时目录后通过；后续坏流回归最初发现 PDFBox 容错跳过，已显式解码并重验。早期日志留在 tmp/dev-b-validation，不把失败尝试说成通过。

## 仓库边界和下一步

仅改 dev-b 源码/测试/任务记录，未改 contract/build/plugin.xml/fixtures。两份原始 PDF 和 local-course-pdfs 全目录被 Git 忽略；讲义正文、原件、临时副本、JAR/SDK及测试产物不提交。Git 使用进程级 safe.directory，hooks 保持启用；Java25 使用进程级 JAVA_HOME，不改系统配置。

统一命令：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -GradleUserHome "$PWD\tmp\gradle-home"`；进程 JAVA_HOME 为已核实的 DataGrip JBR25.0.3。仓库目标 IDEA2026.2.3/262.10968.63，本机发现 IDEA2024.2.3，实际 IDE 场景待对齐。

插件 ZIP 已生成：`build/distributions/IJ-Plugin-Java-0.1.0-dev.zip`。主 JAR 含真实 reader，未打入 PDF 文档；这仍不是全部 F0 功能或生产集成完成的声明。

下一步：组长处理 PDF-REG-001 注册合入 main 后同步，验证真实资料管线、物理页/固定来源证据及坏页不发布新版本。完整统一验证已通过；独立审核、生产集成及 PR→main 核验仍待完成，不能标整体 DONE。
