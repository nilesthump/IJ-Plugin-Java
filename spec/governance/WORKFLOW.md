# 并行开发工作流

本次用户授权直接初始化工程与任务包；此前架构稿仍是讨论和实施依据，并未因此正式冻结。已确认的行为必须实现；模型平台能力、目标 IDE 兼容性、依赖组合和参数等未验证项仍由负责人验证，不能把初始化或孤立探针描述成产品验收通过。

## 开始一个任务

1. 读取根 `AGENTS.md` → [CURRENT.md](../progress/CURRENT.md) → [BASELINE.md](../architecture/BASELINE.md) / [开发计划](../architecture/F0_DEVELOPMENT_PLAN.md)，再读 [WRITE_SCOPE.md](WRITE_SCOPE.md)、本文件、[BRANCH_POLICY.md](BRANCH_POLICY.md)、[VALIDATION.md](VALIDATION.md)。
2. 按 [任务入口](../tasks/README.md) 进入自己的 README、`STATUS.md` / `HANDOFF.md`，再读 [公共 API](../contracts/API.md)、实际 Java 契约及任务要求的细则。使用当前仓库代码签名，不凭旧聊天猜接口。
3. 确认当前分支、工作区已有改动和自己的写入范围。保留现有改动，不执行清理目录、硬重置、强推或覆盖他人文件。缺少公共基线时写依赖请求并继续可独立完成的部分。
4. 从 `main` 同步已合入的公共基线。首先运行统一验证入口，记录真实结果；随后选择任务 README 的第一个可观察场景实施。不要把所有产品功能都完成作为成员开工前提，合成 fixtures 可支持独立开发。

## 固定分支与工作区

只使用以下五个名称，不另开功能分支、修复分支或由工具自动命名的分支：

| 分支 | 工作区 / 用途 | PR 目标 |
| --- | --- | --- |
| `main` | 已集成公共基线与发布入口 | 不作为日常开发分支 |
| `dev-manager` | 负责人；主工作区 `H:/IJ-Plugin-Java` | `main` |
| `dev-a` | 甲；独立克隆或已有分支的工作树 | `main` |
| `dev-b` | 乙；独立克隆或已有分支的工作树 | `main` |
| `dev-c` | 丙；独立克隆或已有分支的工作树 | `main` |

成员可用 `git clone --branch dev-a <已配置仓库URL> <本地目录>` 检出远端已有同名分支（乙、丙替换名称），进入后执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/git/Configure-Git.ps1
```

也可先查看工作树占用，再用 `git worktree add <本地目录> dev-a` 检出已有分支。不使用 `-b` / `-c`、`git switch -c` 或自动创建分支的工作树工具。同一分支不能同时被两个工作区检出；主工作区保持 `dev-manager`。固定远端分支或公共文件缺失时报告仓库状态，不自造替代分支。

`dev-manager` 是负责人的开发分支，**不是甲乙丙的集成中转**。公共变更由负责人从 `dev-manager` PR 到 `main`；三位成员同步 `main` 后继续，分别从各自开发分支直接 PR 到 `main`。同步采用普通获取和合并，保留自己的已提交工作；合并冲突按写入归属协调，不以强推或整目录复制解决。合并后保留来源分支，下次继续复用。

本地钩子、CI 和远端规则的实际生效边界见 [BRANCH_POLICY.md](BRANCH_POLICY.md)。规则文件存在不能证明 GitHub 已启用服务器规则；不要伪报远端保护状态。

## 实施、依赖和检查

- 按任务的一个小场景完成“实现 → 有意义的验证 → 更新状态”。相关测试应验证归属、线程、状态转换或数据证据等真实约束，不以只检查 getter 或镜像实现的测试充数。
- 三位成员不改构建、插件注册和公共契约。公共阻塞写入本任务 `DEPENDENCY_REQUEST.md`：触发场景、最小字段/依赖、理由、候选及证据、受影响文件。负责人评估后在公共范围落实，通过 `main` 提供基线。无需为任务范围内可逆实现再次询问用户许可。
- 工程统一使用 Java 25、Gradle Groovy 与 Wrapper。唯一日常验证入口为下列脚本；局部测试可补充，不能代替入口或绕开失败：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All
```

- 本地与 CI 使用同一入口。`-Scope Repository` 只检查仓库结构，不能替代默认/显式 `-Scope All`。本机可按脚本使用 `-LocalIdePath <绝对IDE路径>`、`-GradleUserHome <绝对缓存路径>`；IDEA始终只验证本机当前版本，升级后同步构建/CI，不再增加旧版本验证任务。日常可用 `./gradlew.bat test --tests "edu.courseassist.editor.*"` 等当前任务包过滤器缩短局部反馈，PR 前仍须执行 All。入口的具体检查和参数以脚本为准，不另建“等价”脚本；负责人负责修改入口。构建版本是当前工程候选组合，只有对应证据才支持兼容性结论。
- 网络、PDF、数据库及长耗时计算不运行在 EDT。PSI 访问使用平台允许的后台读机制，取得不可变快照后退出读作用域；网络或数据库事务不跨持有 PSI/IDE 读锁。
- 自动验证使用合成 PDF、向量、模型输出和控制器。真实模型、嵌入平台、真实目标 IDE 的检查分别列证据；没有权限、额度、目标安装或能力数据时保持待验证，不把假实现标成真实通过。

## PR、交接和继续

提交前运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-scope.ps1 -Staged`，检查差异范围、测试退出码和密钥/私有数据。PR 描述说明具体问题、结果行为、实际验证及限制；所有开发 PR 均目标 `main`。主分支合入依据仓库实际规则和 `validation` 结果，不绕过失败。创建 PR 后将其附到当前任务，合并时保留开发分支。

每次结束更新本任务 `STATUS.md`，需要换 agent 时填 `HANDOFF.md`。至少记录当前分支/提交、已改文件、真实实现范围、准确验证命令与退出码、证据链接、未决项、下一条具体动作。`READY` / `IN_PROGRESS` / `BLOCKED` / `DONE` 是任务记录状态，`DONE` 必须有范围内实现和对应验证；仅完成说明文档不能标成功能完成。

涉及行为、持久化或上下文资格变化时，负责人更新对应公共设计与契约；成员在依赖请求中给出需要同步的条目。已中断/失败输出、删除竞争、跨项目身份、资料版本和代次固定等约束不能通过 UI 隐藏或假实现消除。下一位 agent 从当前状态继续，不重做已验收工作，也不把未执行检查变成通过。
