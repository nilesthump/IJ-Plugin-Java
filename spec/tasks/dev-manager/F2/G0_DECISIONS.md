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
