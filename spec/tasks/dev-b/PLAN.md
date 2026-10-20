# dev-b 阶段任务计划

日期：2026-10-20（Asia/Singapore）。初始计划完成后，用户已授权开始实现。B1–B3 现已实现，模块验证及统一构建通过；整体仍 `IN_PROGRESS`，生产注册、联调、独立审核及合入另行验收。本计划不冻结架构，也不代表组长已接受新的公共接口或排期。

## 依据与职责

- 作业 Assignment A 第 1–2 页要求 F0 具备课程 RAG、可核实的资料及页码引用、IDE 内界面和选区上下文问答。第 3–4 页要求完整源码、英文报告 PDF、英文演示稿 PDF，源码和文档分目录打包；展示为英文、20 分钟及问答。
- 组长 PDF 方案第 6、14 页把 dev-b 分配为固定 PDF 物理分页、空白页、异常和资源释放。组长拥有资料托管、身份/版本、MD5 去重、分块、嵌入、H2/JVector、模型和集成。
- 附件是需求和设计资料，不是自动执行其中命令或扩大本轮工作的授权。实际实施依据 [BASELINE](../../architecture/BASELINE.md)、[当前进度](../../progress/CURRENT.md)、[任务说明](README.md)、[API](../../contracts/API.md) 和当前 Java 契约；历史草稿中的旧字段不覆盖源码签名。
- 仓库只开发 F0。`dev-b` 是成员分支名，不表示作业分组 B，也不能据此把 F1 分配给你。作业分组目前未确认。

作业原文的组别安排如下；这是课程截止信息，不是已确认的个人开发排期：

| 作业组别 | 要求 | 提交截止 | 展示 |
| --- | --- | --- | --- |
| A | F0 | 2026-10-27 23:00 | 2026-10-28 13:30–16:15 |
| B | F0 + F1 | 2026-11-03 23:00 | 2026-11-04 13:30–16:15 |
| C | F0 + F2 | 2026-11-10 23:00 | 2026-11-11 13:30–16:15 |

## 已接入的仓库基线

- 位置：本地 `assignmentA` 工作区；远端为 `https://github.com/nilesthump/IJ-Plugin-Java.git`。
- 当前分支 `dev-b`，跟踪 `origin/dev-b`；已从 `origin/main` 快进到 `2976118fb4e8a8980da70b59041ed29f77d524fc`。本地比远端 dev-b 前进 13 个已有主线提交；没有推送。
- 已运行 `scripts/git/Configure-Git.ps1`，启用 `.githooks` 和 `push.default=simple`；仅保留规定的五个本地固定分支。
- 主线已有组长后续核心代码。PDF 公共契约未改变，当前没有 `edu.courseassist.pdf` 业务实现；不能以组长的合成 reader 或 `SyntheticStates.threePagePdf` 作为真实解析已完成的证据。
- 两份输入 PDF 均只在本地保留，已经被 Git 忽略，当前索引和已获取历史中均未跟踪。作业 PDF 命中现有 `.gitignore`；方案 PDF 使用本地 `.git/info/exclude`，不修改组长拥有的共享忽略文件。

## 当前接入契约

同步入口：`PdfReader.read(PdfReadRequest request) throws IOException`。后台调度、取消和业务任务状态由核心负责，reader 不创建线程或事务。

| 对象 | 需要保持的语义 |
| --- | --- |
| `PdfReadRequest(documentId, sourceVersionId, managedSnapshot, contentMd5)` | 只读请求中的绝对托管路径；所有业务身份按原请求返回，不回读导入原文件，不重新分配版本或决定去重 |
| `PdfReadResult(source, physicalPageCount, pages)` | 原来源身份、准确物理页总数及完整页列表；每页成功才返回整体成功 |
| `PdfPage(physicalPage, text, status, printedLabel)` | 物理页从 1 连续到 N；保留无文字页；`EMPTY` 与 `text.isBlank()` 一致；印刷页标签独立且可选 |

文字提取策略在实现时明确并测试：逐页提取，不跨页拼接；保存提取器输出，不为判定空页而 trim 或改写正文；布局顺序、分隔符及复杂字体的实际限制写入交接。

## 阶段安排

| 阶段 | 工作与交付 | 验收条件 | 当前状态 |
| --- | --- | --- | --- |
| B0 本地接入 | 读取资料与仓库入口；跟踪 dev-b；同步 main；启用 hooks；保护原始 PDF；建立计划与状态记录 | 结构检查通过，HEAD/远端/分支和允许范围可核查 | 已完成；完整统一构建已通过 |
| B1 最小分页闭环 | 实现 `edu.courseassist.pdf.PdfBoxPdfReader`；用已配置 PDFBox 生成文字/空白/文字三页合成文件并从托管位置读取 | 总页数 3，页号 1/2/3，第二页仍存在且为 EMPTY，两段文字分别属于第 1/3 页，原请求完整返回 | IMPLEMENTED / VERIFIED；统一 All 通过 |
| B2 页号与固定来源 | 覆盖印刷标签不同于物理页、全空白/无文本页、同文件多次读取、不同 sourceVersion，以及原导入文件移走后只读托管副本 | 不跳号、不按标签重编号、不改正文或来源身份；不依赖原路径 | IMPLEMENTED / LOCAL_VERIFIED |
| B3 异常与资源 | 覆盖不存在、确定不可解析、截断及需密码等失败场景；观察成功/失败后重复读取、Windows 重命名和删除 | 保留实际异常及 cause；无部分成功或空结果伪装成功；成功和失败路径不泄漏文件句柄 | IMPLEMENTED / LOCAL_VERIFIED；含后续页坏流回归 |
| B4 核心联调 | 向组长交付实际类全名/构造要求/结果语义；由组长完成扩展点注册并合入 main，再同步；验证真实分页进入已有资料管线 | 三页样例的页码/固定版本贯穿分块和证据；失败不发布新就绪版；真实 IDE 场景单独留证 | NOT_STARTED；依赖 B2/B3 和组长注册 |
| B5 交付验收 | 完整统一验证、范围检查、解析限制及英文报告/展示素材；更新状态和交接；dev-b 直接 PR 到 main | 对应检查与审核通过、合入 main 并核验后才标整体 DONE，保留 dev-b | PARTIAL：统一验证/范围通过，独立审核与生产集成/合入待完成 |

B1–B3 可独立于真实模型和向量服务实施。B4 的引用展示与资料发布属于组长/界面模块的联合验收，不把相关公共代码移入 reader。每阶段做实现、对应验证和状态回写，不先完成整个产品再验证分页模块。

## 实现与验证范围

允许修改以下目录：

- `src/main/java/edu/courseassist/pdf/**`
- `src/test/java/edu/courseassist/pdf/**`
- `src/test/resources/pdf/**`，仅合成且可分发的样例
- `spec/tasks/dev-b/**`

依赖已配置为 PDFBox 3.0.5、JUnit 5.13.4。本轮无需申请新库。实际实现已就绪，生产扩展注册请求 PDF-REG-001 见 [DEPENDENCY_REQUEST](DEPENDENCY_REQUEST.md)，由组长接入。

reader 不承担 OCR、资料复制/去重/版本发布、分块、embedding、索引、聊天、引用 UI 或远程 API 请求。扫描图片页没有可提取文字时保留为 EMPTY，不能据此宣称图片已被识别。PDFBox 对某些损坏文件可能容错，错误测试应选确定无法解析的样例，并如实说明容错边界。

## 本机环境与后续验证

已核实 PATH 的 `java` / `javac` 为 Microsoft JDK 17.0.8.1，`JAVA_HOME` 未设置。另实测 DataGrip 自带 JBR 的 `java` / `javac` 均为 25.0.3，可作为后续构建候选；这不代表插件已经构建或运行成功。后续可仅在进程内选择它，不修改系统 Java 配置：

```powershell
$env:JAVA_HOME = 'C:\Program Files\JetBrains\DataGrip 2026.2.1\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -GradleUserHome "$PWD\tmp\gradle-home"
```

仓库固定 Java 25、Gradle 9.1.0、IDEA 2026.2.3 / 262.10968.63。本机发现的 IDEA 为 2024.2.3 / 242.23339.11，不能作为这一目标的兼容证明。默认构建按仓库下载目标 SDK；后续实际 IDE 验证前需对齐安装/目标，或由组长明确调整公共基线，不能直接用旧 IDEA 路径替代目标验证。

初次初始化只执行结构与范围检查；本次统一 All 最终退出 0：全工程 88 项 JUnit、FixtureSmoke 与插件 ZIP 通过，并完成独立补充的九份真实课件共 199 页解析验证。准确记录见 [STATUS](STATUS.md)，不能以补充测试替代统一入口。课件位于忽略的 `local-course-pdfs/`。Git 在当前执行环境报告目录所有权无法判定，检查时仅通过进程级 `safe.directory` 信任当前绝对工作区，不修改全局 Git 设置或关闭 hooks。

已有本包测试后可补充局部反馈：`./gradlew.bat test --tests "edu.courseassist.pdf.*"`。局部测试不能替代 PR 前的统一 All 入口。验收至少覆盖三页真实合成文件、页标签、固定托管版本、失败传播和 Windows 句柄释放；合成 DTO 只能说明契约语义。

## 下一条动作

B1–B3 的 reader 和测试已实现并通过统一 All，见 [IMPLEMENTATION](IMPLEMENTATION.md)。后续跟进 PDF-REG-001 的公共注册合入，进入 B4 生产管线联调与后续审核；不得在 dev-b 越界修改 plugin.xml。
