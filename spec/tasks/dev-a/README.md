# 甲：编辑器代码采集

固定分支 `dev-a`，直接 PR 到 `main`。本任务实现用户在 IDEA 中选中代码后捕获“原始选区 + 所在方法全文 + 所属类结构”，把只读代码组送回原会话草稿。无需等待数据库、检索或模型完成，先使用公共契约与合成接入点开发。

## 可复制给 agent 的提示

```text
你负责 dev-a 编辑器任务。先按本 README 顺序读文件，再核对当前分支为已有 dev-a。
仅修改 editor 实现/测试及 spec/tasks/dev-a/**；包名前缀 edu.courseassist。
从 main 同步公共基线，使用实际 contract 签名，不修改 build、plugin.xml、contract 或 fixtures。
实现右键采集固定原始选区、方法全文与类结构，遵守同文档版本、来源项目和原会话绑定。
代码组只读、整组移除后重新采集；不做模型、数据库、跨文件调用图或项目扫描。
用有意义场景验证，更新 STATUS/HANDOFF；未跑真实 IDEA 的检查写 NOT_RUN。
缺少公共入口写 DEPENDENCY_REQUEST，不复制 DTO 或绕过核心；PR 直接到 main，保留 dev-a。
```

## 按序阅读

1. 根 `AGENTS.md` → [CURRENT](../../progress/CURRENT.md) → [BASELINE](../../architecture/BASELINE.md) / 开发计划，再依根入口读治理、当前任务 README / [STATUS](STATUS.md) / [HANDOFF](HANDOFF.md)。治理包括 [写入范围](../../governance/WRITE_SCOPE.md)、[工作流](../../governance/WORKFLOW.md)、[分支规则](../../governance/BRANCH_POLICY.md)、[验证规则](../../governance/VALIDATION.md)。
2. [开发计划](../../architecture/F0_DEVELOPMENT_PLAN.md)、[并行接入](../../architecture/PARALLEL_START.md)、[分工](../../architecture/TASK_ALLOCATION.md)。
3. [公共 API](../../contracts/API.md) 以及 `CaptureContracts`、`SelectionSink`、`CodeCapturePort` 的当前 Java 定义。
4. [行为](../../architecture/F0_BEHAVIOR.md)、[工程蓝图](../../architecture/ENGINEERING_BLUEPRINT.md)、[会话上下文](../../architecture/SESSION_CONTEXT.md) 中代码捕获、草稿归属、提交和删除相关规则。
5. [STATUS.md](STATUS.md)、[HANDOFF.md](HANDOFF.md)、[DEPENDENCY_REQUEST.md](DEPENDENCY_REQUEST.md)。

## 第一步与范围

先运行统一验证并记录基线结果；确认右键动作由负责人的插件注册接入。以一个含选区、方法、字段和另一个方法的合成 Java 文件为首个场景：捕获结果应包含选区原文、所在方法完整原文及所属类声明/字段结构，且不包含另一个方法正文。

可写：`src/main/java/edu/courseassist/editor/**`、`src/test/java/edu/courseassist/editor/**`、`spec/tasks/dev-a/**`。不得改构建、插件 XML、公共类型或其他成员目录；Java 插件依赖与动作注册需求写依赖请求，由负责人合入 `main`。

## 实施步骤

1. 实现轻量动作入口，从动作的来源项目、编辑器和文档取得选区信息。动作开始使用 `SelectionSink.beginCapture(SelectionStart)` 获取绑定票据；会话/代码组/快照等业务身份由核心分配，不能由 editor 新建会话或持久化。无活动标签时由核心决定新建会话；不要读取全局“最近活动项目”作为替代。
2. 按 `CodeCapturePort.capture` 的异步契约采集。PSI 访问在平台允许的读作用域内，避免 EDT 长任务；取得不可变文本和范围后退出读作用域。不能把 PSI、Document 或 Editor 对象作为结果长期保存，也不把读锁带入网络或数据库工作。
3. 所有片段来自同一文档版本。保留原始选区，定位 enclosing method / class，提供方法全文与所属类声明/字段结构；构造器、嵌套类等按实际归属给结果。类结构不包含其他方法实现、继承成员正文、跨文件调用图或项目扫描。范围单位、开闭区间、文件/版本标识以公共契约为准。
4. 明确区分无选区、无所在方法、无所属类、不支持语言、语法不完整和文档已变化。可接受的缺项用契约状态表达，不能伪造空方法成功；空选区不自动扩成全文件。若无法取得一致版本，明确结束为失效/失败，不能混合两次文档内容。
5. 回传到票据绑定的原代码组。切换标签不重定向；重复采集相同代码仍是新捕获身份。核心负责条件接纳，editor 不整份覆盖草稿、不清除用户新输入。迟到结果不能复活已移除代码组、已删除会话或已关闭的项目实例。
6. 完成范围内测试与实际 IDEA 接入验证后写交接。不要用假的成功回调遮掩未注册动作或未完成的核心。

## 有意义的验证

| 场景 | 需要观察的结果 | 证明边界 |
| --- | --- | --- |
| 含两个方法、字段、嵌套类的 Java 文件 | 选区不变；只取实际方法全文及所属类结构；不泄露其他方法正文 | 纯逻辑测试可测文本规则，PSI 行为须平台测试/IDE |
| 捕获中把活动标签 A 切到 B | 结果仍交给 A 的原捕获票据 | 假 sink 可验证路由，真实核心接纳另验 |
| 两个项目各有编辑器 | 从源项目发起，不能落到另一项目会话 | 真实动作上下文需 IDEA |
| 采集中改文档；无方法/无类/非 Java/空选区/不完整语法 | 明确状态或失效，不混版本、不自动全文件、不静默空成功 | 区分契约测试与平台证据 |
| 相同代码采两次；移除后迟到回调；会话删除/项目关闭 | 新捕获身份独立；失效票据不被 editor 重试成新组 | 回调测试不等于 H2 删除已验证 |
| 采集中用户继续改草稿文字 | 不因旧回调整份替换草稿 | 核心条件写是负责人验收点 |

线程/生命周期检查记录所用读机制、调用线程和释放位置；不能只因测试很快便宣称 EDT 安全。

已有本包测试后可用 `./gradlew.bat test --tests "edu.courseassist.editor.*"` 获得局部反馈；PR 前仍执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All`，记录退出码和实际测试范围。

## 接入点与退出条件

当前主要入口：`CaptureContracts`、`SelectionSink.beginCapture`、`CodeCapturePort.capture`，以 [API.md](../../contracts/API.md) 为准。负责人负责注入、动作注册、票据初始化与接纳、草稿持久化和删除栅栏；甲交付采集器，不实现模型/资料服务。

退出条件：范围内采集行为已实现；一致版本、原目标会话、缺项/错误和生命周期场景有实际证据；统一验证入口通过，或清楚记录不属于本任务的具体阻塞；状态与交接可供下一人直接复现。IDEA 始终只验证本机当前版本，目前为 `262.10968.63`，不额外验证旧 build；未跑本机IDE场景时写 NOT_RUN。PR 直接 `dev-a → main`，不删除分支。
