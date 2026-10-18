# 固定五分支协作规则

本规则依据负责人最新确认的并行开发方式：仓库只保留 `main`、`dev-manager`、`dev-a`、`dev-b`、`dev-c`。`main` 是默认主分支；四个 `dev-*` 是长期开发分支，各自通过 PR 直接合入 `main`。`dev-manager` 不作为其他分支的汇聚分支。

| 分支 | 使用人/用途 | 合入方式 |
| --- | --- | --- |
| `main` | 已集成基线 | 通过 PR，`validation` 通过后合入 |
| `dev-manager` | 负责人；公共契约、构建、核心编排和集成 | `dev-manager` → `main` |
| `dev-a` | 成员 A；任务包定义的范围 | `dev-a` → `main` |
| `dev-b` | 成员 B；任务包定义的范围 | `dev-b` → `main` |
| `dev-c` | 成员 C；任务包定义的范围 | `dev-c` → `main` |

不得创建额外分支名、删除或改名固定分支、强制改写已推送历史。合并 PR 时保留来源分支；仓库设置 `delete_branch_on_merge=false`。任务继续在原分支上进行；同步 `main` 使用普通合并，不能通过强制推送解决分叉。共享契约和构建修改先由负责人完成并合入 `main`，成员同步后继续。

## 每次新克隆的启用步骤

按分配分支克隆，例如 `git clone --branch dev-a <仓库 URL> <本地目录>`。这是检出远端已经存在的固定分支。进入该目录后执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/git/Configure-Git.ps1
```

脚本只修改当前仓库的本地 Git 配置，设置 `core.hooksPath` 和 `push.default=simple`。缺少本地固定分支时，只从已存在的 `origin/<固定分支>` 建立跟踪引用；不创建任何额外名称，不自动联网，也不删除现有引用。若发现额外本地分支或缺少远端固定引用，报错并要求先检查仓库状态。可以先 `git fetch origin`，再重试。

同一克隆使用工作树时只检出已有分支，例如 `git worktree add <目录> dev-a`。不使用 `-b`、`-c` 或自动创建分支的工作树工具。工作树不能同时检出已经被其他工作区占用的同一分支。

## 本地保护

- `.githooks/reference-transaction` 在 `prepared` 阶段拒绝非白名单 `refs/heads/*` 的写入，以及五个固定分支的删除。改名需要删除原引用，因此也被拒绝。
- `.githooks/pre-push` 拒绝推送额外分支名、删除固定远端分支和非快进更新。旧远端提交本地未知时要求先获取远端状态。
- `.githooks/pre-commit` 调用 `scripts/check-scope.ps1 -Staged`，按共享分支范围检查已暂存内容；`main` 不接受本地开发提交。初始仓库首提交须在启用钩子前完成。该钩子优先使用 Windows PowerShell，找不到时使用 `pwsh`，缺少校验器或运行环境则明确失败。
- 远程跟踪引用、`HEAD`、标签和 detached 工作树不属于分支白名单检查范围；普通获取远端与 detached 检查操作正常工作。

Git 的参考事务钩子可在 `prepared` 阶段以非零退出中止引用更新；推送前钩子可拒绝整次推送。参见 [Git hooks 官方说明](https://git-scm.com/docs/githooks)。本地钩子需要每个克隆启用，具有本机配置写权限的人可以关闭它们，因此服务器规则才是远程仓库的强制边界。

## GitHub 规则配置

规则文件位于 `.github/rulesets/`；JSON 是配置输入，文件存在不代表远端已经启用。实际远端能力、规则 ID 与读回验证结果记录在初始化交付记录中。

1. `01-deny-additional-branches.json`：对所有分支应用创建限制，排除五个允许名称；无绕过主体。
2. `02-preserve-fixed-branches.json`：五个固定分支禁止删除和非快进更新；无绕过主体。
3. `03-main-pr-and-validation.json`：`main` 必须经过 PR，要求 `validation` 状态通过及讨论已解决；无管理员直推绕过。初始不强制独立人员审批，不要求尚未建立的团队/CODEOWNERS 身份。

四个开发分支允许其使用人普通推送，不要求向开发分支提 PR。所有开发 PR 目标均为 `main`，CI 同时校验来源为固定开发分支。规则禁止额外远端分支名称，并不限制只读获取或建立远程跟踪引用。

GitHub 规则接口需要仓库 Administration 写权限；参见 [规则 REST API](https://docs.github.com/en/rest/repos/rules)。GitHub Free 的私有仓库可能没有规则集能力，实际以接口结果为准；官方支持范围见 [规则集说明](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets)。若远端返回套餐限制，必须记录为 `UNAVAILABLE`，不能把本地钩子和 CI 描述成已启用的服务器分支创建禁令，也不能擅自把私有仓库公开或购买套餐。

## 行为验证

在 PowerShell 7 中执行 `./scripts/git/Test-GitPolicy.ps1`。测试只在 `tmp/bootstrap-git-tests/run-<随机 ID>/` 新建隔离仓库和本地裸远端，不操作产品仓库引用，不修改全局 Git 设置，不联系 GitHub。成功后保留完整 `results.json`。

2026-10-18 本机 Git `2.52.0.windows.1` 已验证 37 次 Git 操作全部符合预期，包括：额外分支创建/切换/直接引用写入被拒绝；固定分支删除和改名被拒绝；detached HEAD/工作树、远程跟踪更新和真实 fetch 被允许；五分支首次推送和快进推送被允许；额外远端分支、删除与强制回退被拒绝；新成员克隆后能建立五个固定本地跟踪分支。`pre-commit` 使用隔离的范围校验器，验证主分支拒绝、范围失败传播和开发分支合法提交；实际路径范围由产品仓库共享校验器另行验证。原始记录：`tmp/bootstrap-git-tests/run-9f2f7e18f56344a79866b88e3844e8b8/results.json`。这项验证不证明 GitHub 规则已经生效。
