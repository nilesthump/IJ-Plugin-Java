# 乙：固定 PDF 快照解析

固定分支 `dev-b`，直接 PR 到 `main`。本任务把核心交来的托管 PDF 快照解析成逐物理页文本。保留空页与固定来源身份，使后续引用能够准确指向原版本和 PDF 物理页；不实现资料库、分块、向量化或模型调用。

## 可复制给 agent 的提示

```text
你负责 dev-b PDF 解析任务。先按本 README 顺序读文件，核对已有 dev-b 分支。
仅修改 pdf 实现/测试、src/test/resources/pdf/**、spec/tasks/dev-b/**；使用 Java/edu.courseassist。
从 main 同步公共基线，按 PdfReader/PdfContracts 解析托管固定快照；不改 build、plugin.xml、contract、fixtures。
保留物理页总数和每一页（含空白页），透传核心分配的来源身份，不再读外部原文件。
解析失败保留原因并失败，不跳页或以空文本伪装成功，不做 OCR/分块/embedding。
用合成 PDF 验证页号、资源关闭和损坏文件；记录真实退出码、未执行项及交接。
缺少依赖/签名写 DEPENDENCY_REQUEST；PR 直接 main，保留 dev-b。
```

## 按序阅读

1. 根 `AGENTS.md` → [CURRENT](../../progress/CURRENT.md) → [BASELINE](../../architecture/BASELINE.md) / 开发计划，再依根入口读治理、当前任务 README / [STATUS](STATUS.md) / [HANDOFF](HANDOFF.md)。治理包括 [写入范围](../../governance/WRITE_SCOPE.md)、[工作流](../../governance/WORKFLOW.md)、[分支规则](../../governance/BRANCH_POLICY.md)、[验证规则](../../governance/VALIDATION.md)。
2. [开发计划](../../architecture/F0_DEVELOPMENT_PLAN.md)、[并行接入](../../architecture/PARALLEL_START.md)、[分工](../../architecture/TASK_ALLOCATION.md)。
3. [公共 API](../../contracts/API.md)、`PdfContracts` 与 `PdfReader` 当前定义、`SyntheticStates.threePagePdf(request)` 的合成结果语义。
4. [资料库](../../architecture/COURSE_LIBRARY.md)、[行为](../../architecture/F0_BEHAVIOR.md)、[工程蓝图](../../architecture/ENGINEERING_BLUEPRINT.md) 中托管快照、来源版本和物理页规则。
5. [STATUS.md](STATUS.md)、[HANDOFF.md](HANDOFF.md)、[DEPENDENCY_REQUEST.md](DEPENDENCY_REQUEST.md)。

## 第一步与范围

运行统一验证并记录基线。用 PDFBox 生成可分发的三页合成 PDF：第一页已知文字、第二页空白、第三页另一段已知文字；在临时托管位置读取，断言总页数为三、页号为 1/2/3、空页仍有记录、来源身份原样回传。当前工程已预配 PDFBox；如需调整版本先提案，不自行改构建。

可写：`src/main/java/edu/courseassist/pdf/**`、`src/test/java/edu/courseassist/pdf/**`、`src/test/resources/pdf/**`、`spec/tasks/dev-b/**`。测试资源只放合成/可分发样例，不提交用户课程资料或其他私有文件。

## 实施步骤

1. 实现 `PdfReader.read(PdfReadRequest)`，同步解析并以 `IOException` 传播读取/解析失败；线程调度归核心。不得在 reader 内自行启动常驻线程、UI 或数据库事务。
2. 只使用请求的托管快照路径。`documentId`、`sourceVersionId`、`contentHash` 等业务身份由核心分配，按契约透传；不生成资料版本、不计算业务去重决策、不重新定位用户导入的原文件。
3. 返回准确 `physicalPageCount` 和逐页结果。页号从 1 开始，覆盖所有物理页，含空白或无可提取文本页。可显示的印刷页标签仅作标签，不能替代物理页。文字提取顺序和规范化策略应明确且可测，不默默丢弃页或跨页拼接。
4. 使用正常的资源关闭结构；关闭后不持有 PDF 文件句柄。损坏、截断、加密/不可读等情况按真实库行为给失败或契约明确状态，保留原因；不能 catch 后返回部分结果/空结果表示成功，也不自动 OCR。
5. 提供有意义测试和解析限制说明。核心以后负责分块与跨页引用、MD5 字节去重、资料替换/删除、向量、JVector 和引用呈现，乙不得把这些逻辑塞入 reader。

## 有意义的验证

| 场景 | 需要观察的结果 |
| --- | --- |
| 文本/空白/文本三页真实合成 PDF | 总数 3；页号不跳；空页记录存在；第一页/第三页文本分别匹配 |
| 可见印刷页标签与物理页不同 | 输出引用仍使用物理页 1..N；标签若支持则独立保存 |
| 托管副本存在，导入外部原文件被移走 | 仍只从托管路径成功读取；不依赖原路径 |
| 请求中固定的版本/内容身份 | 输出逐项透传，不把文件名或 MD5 当业务身份 |
| 损坏/截断/不存在或不可读文件 | 报真实失败并保留原因，无“成功空页列表”或跳页成功 |
| 连续重复读取、读取后在 Windows 重命名/删除测试副本 | 资源已关闭；不残留 reader 自己的文件句柄 |

对文字布局复杂 PDF 的可读顺序、字体等限制按实际测试记录；不把三页样例成功扩展成所有 PDF 都可正确重排。若库对某种损坏文件容错，测试应选择确定无法解析的样例，不编造错误。

已有本包测试后可用 `./gradlew.bat test --tests "edu.courseassist.pdf.*"` 获得局部反馈；PR 前仍执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All`，记录退出码和实际测试范围。

## 接入点与退出条件

入口为 [API.md](../../contracts/API.md) 中 `PdfReader` / `PdfContracts`。负责人创建托管快照并在后台调用，负责取消/作业状态和持久化；乙的同步结果不能直接发布新资料 generation。合成 `threePagePdf` 只帮助其他任务接入，不证明乙已真正解析二进制 PDF。

退出条件：真实文件读取、所有物理页与身份语义已实现；损坏文件和资源关闭有实际测试；统一验证通过并保存准确结果；交接说明提取规则、限制和未测格式。PR 直接 `dev-b → main`，保留分支，不等待向量库/API 完成再提交独立解析能力。
