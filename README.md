# Course Assistant F0

IntelliJ IDEA 内嵌课程助教：课程 PDF RAG、来源与物理页引用、多 session 聊天及选中代码上下文。纯 Java，保留 Spring AI、H2、JVector。当前是可并行开发的初始化基线，业务功能尚待各任务实现。

## 开始开发

先读 [AGENTS.md](AGENTS.md)，再读 [当前进度](spec/progress/CURRENT.md) 和 [公共契约](spec/contracts/API.md)。

| 负责人/成员 | 固定分支 | agent 任务入口 |
| --- | --- | --- |
| 负责人，5份 | `dev-manager` | [公共工程与核心](spec/tasks/dev-manager/README.md) |
| 甲，1份 | `dev-a` | [IDE 选区/方法/类捕获](spec/tasks/dev-a/README.md) |
| 乙，1份 | `dev-b` | [固定 PDF 物理分页](spec/tasks/dev-b/README.md) |
| 丙，1份 | `dev-c` | [Tool Window 与 Markdown/代码高亮](spec/tasks/dev-c/README.md) |

四个开发分支独立，**各自 PR → main**，合并后保留分支。`main` 是主分支；禁止创建其他分支。GitHub 服务端规则、本地 hooks 和实际生效状态见 [分支规则](spec/governance/BRANCH_POLICY.md) 与 [当前进度](spec/progress/CURRENT.md)。

成员用自己的目录克隆（按成员替换 dev-a）：

```powershell
git clone --branch dev-a https://github.com/nilesthump/IJ-Plugin-Java.git IJ-Plugin-Java-a
cd IJ-Plugin-Java-a
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/git/Configure-Git.ps1
```

将本任务 README 中的“可复制给 agent 的提示”交给 agent 即可开始；不需要复述本次聊天。不要在另一个人的工作区切分支，也不要使用自动创建分支的工作树工具。

## 构建与验证

需要 JDK 25。Gradle Wrapper 固定 9.1.0；默认 SDK 为本机对应的 IDEA 2026.2.3（build 262.10968.63）。首次运行下载构建依赖与 SDK；用户已要求始终只验证本机版本。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1
```

使用已安装的 IDE 可传 `-LocalIdePath <绝对目录>`；CI下载与本机相同的固定版本，不做其他IDE版本的验证。`-Scope Repository` 仅检查仓库结构。参数、CI、模拟状态和运行验证的证明边界见 [验证契约](spec/governance/VALIDATION.md)。插件产物位于 `build/distributions/`。

目前 Tool Window 只有开发入口。公共接口与合成夹具用于成员并行，不模拟真实发送、保存、删除或模型成功。未配置任何真实 API key，不会因构建而调用模型。

## 设计依据

[F0 综合开发方案](spec/architecture/F0_DEVELOPMENT_PLAN.md) 与 [实施基线](spec/architecture/BASELINE.md) 区分了已确认要求、实施提案和未验证项。初始化不等于正式架构冻结或全部 P0 验证通过。任务需要新增公共字段、依赖或注册时，写各自 `DEPENDENCY_REQUEST.md` 由负责人接入。
