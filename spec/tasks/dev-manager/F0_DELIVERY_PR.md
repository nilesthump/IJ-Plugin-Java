# F0 交付 PR 待提交内容

## 行为

完成用户验收后的 F0 反馈修复：统一聊天及代码/文本混排草稿、中文输入法和流式滚动、用户气泡及引用页码、生成生命周期、PDF 导入诊断及资料硬删除、重启启停偏好、批量 PDF 导入和设置精简。补充根 README 的构建、安装、配置和使用步骤。

F0 已获用户验收；F2 尚未开工。本轮不宣称架构正式冻结，不增加 F2 功能。

## 依赖与验证

PR #12 已先检查无冲突，独立 Standards/Spec 均无阻断，CI validation/scope 通过后合并到 main（913d21d017e60fb1444f68a11fcff13e8d3cc68b）；dev-manager 已快进同步。其选区冻结和 UTF-16 边界测试与本轮 PSI 空白边缘修复互补。

完整验证：scripts/validate.ps1 -Scope All -IdeSmoke，结果见 evidence/f0-delivery-validation.json。合成模型与本机 IDEA smoke 不代表新增真实服务兼容承诺；用户明确本轮 F0 验收通过。

历史安装 ZIP 已清理；最终验证重新生成的安装包也清理，不提交产物。API key 和本地原始讨论证据不发布。

## 发布状态

用户已明确批准按dev-a/dev-c/dev-manager拆分：以下10个文件分别移交成员分支，本PR只包含负责人归属内容。目录门禁保持不变；UI依赖本轮公共契约，等待负责人PR合入main后同步。

- `src/main/java/edu/courseassist/editor/JavaCodeCapturePort.java`
- `src/main/java/edu/courseassist/ui/ApplicationWorkspace.java`
- `src/main/java/edu/courseassist/ui/ChatTimeline.java`
- `src/main/java/edu/courseassist/ui/CourseAssistantView.java`
- `src/main/java/edu/courseassist/ui/IdeCodeViewer.java`
- `src/main/java/edu/courseassist/ui/MixedDraftEditor.java`
- `src/test/java/edu/courseassist/ui/CourseAssistantViewTest.java`
- `src/test/java/edu/courseassist/ui/FeedbackInteractionTest.java`
- `src/test/java/edu/courseassist/ui/FeedbackPreviewTest.java`
- `src/test/java/edu/courseassist/ui/MixedDraftTest.java`

拆分后负责人分支独立 All 退出0：146项测试，失败/错误/跳过0。独立 IdeSmoke 尚未执行：新增EditorIntegrationChecks依赖dev-a边缘空白修复，需其先合入main后同步验证。合入顺序dev-a → dev-manager → dev-c；均各自PR到main。验证生成的ZIP随后删除。

## 分拆 PR 已发起（2026-10-25）

用户明确 approve 后，使用三个既有分支的独立克隆分拆提交，hooks和目录规则未修改。PR #13（dev-a，067deb7）为编辑器边界修复；PR #14（dev-manager）为核心与文档草稿；PR #15（dev-c，c668d4f）为界面草稿，均目标main且保留分支。

- https://github.com/nilesthump/IJ-Plugin-Java/pull/13
- https://github.com/nilesthump/IJ-Plugin-Java/pull/14
- https://github.com/nilesthump/IJ-Plugin-Java/pull/15

独立All：dev-a 106 tests通过，dev-manager 146 tests通过；dev-c编译因main尚无ImportCoursePdfs/captureOffsets/captureOrder等契约而失败，按依赖阻塞如实保留草稿。完整集成树169 tests+IDE PASS与用户F0验收仍有效，但不冒充拆分分支单独验证。顺序：#13合入main→#14同步并补独立IdeSmoke→#14合入main→#15同步并全量验证。此轮只创建PR，未合并三份新PR；F2仍未开工。验证生成的安装ZIP已再次清理。
