# F0 开发任务入口

四个任务可并行推进；每个任务使用一个固定开发分支，各自直接 PR 到 `main`，公共基线从 `main` 同步。`dev-manager` 与三个成员开发分支并列，不接收成员 PR 作为中转。工作流见 [WORKFLOW.md](../governance/WORKFLOW.md)，文件归属见 [WRITE_SCOPE.md](../governance/WRITE_SCOPE.md)。

| 任务 | 固定分支 | 主要交付 | 当前定义状态 |
| --- | --- | --- | --- |
| [甲：编辑器采集](dev-a/README.md) | `dev-a` | 当前选区、方法全文与所属类结构的固定快照 | [READY；未实现](dev-a/STATUS.md) |
| [乙：PDF 解析](dev-b/README.md) | `dev-b` | 托管 PDF 的物理页文本及稳定身份回传 | [READY；未实现](dev-b/STATUS.md) |
| [丙：界面](dev-c/README.md) | `dev-c` | 会话、草稿代码组、流式回答、资料与应用配置界面 | [READY；未实现](dev-c/STATUS.md) |
| [负责人：公共与核心](dev-manager/README.md) | `dev-manager` | 公共骨架、资料/检索、ReAct/流式、会话/存储、主线验收 | [READY；未实现](dev-manager/STATUS.md) |

任务比例 `5:1:1:1` 表示负责人承担更多公共与核心职责，不是工期估算。公共 Java 类型及合成接入点见 [API.md](../contracts/API.md)；任务包不另造一份接口。各自目录还包含状态、交接和依赖请求模板，可直接把 README 的 agent 提示交给成员使用。

这些任务根据用户确认的 F0 行为初始化，未宣称架构正式冻结、真实 API/目标 IDE 已验证或成员功能已存在。

## F2：用户本人负责的顺序任务

新增[F2个人任务入口](dev-manager/F2/README.md)，所有组唯一负责人为用户本人，不按上方历史F0四人分工分派。顺序为治理/范围、契约/存储、目标定位、候选生成、Diff审核、安全应用、联调交付。当前仅任务定义已生成，F2实现NOT_STARTED；先G0对齐现行写入规则，不创建新分支。
