# 甲：编辑器代码采集：状态

- 任务定义：`READY`。
- 功能实现：`NOT_STARTED`。此文件由初始化生成，不表示任务功能已经实现。
- 当前分支：`dev-a`；PR 目标：`main`，合并保留来源分支。
- 记录日期：2026-10-18。
- 当前 HEAD / 公共 main 基线：首次开工填写，未核实不填提交号。
- 公共基线：按根 AGENTS、[API](../../contracts/API.md) 和本任务 [README](README.md) 核对；契约/fixtures 与工程骨架不等于本任务实现。

## 当前可观察结果

尚无本任务实现或验证结果。第一步按 README 的合成场景开始；若公共入口缺失，记录到 [依赖请求](DEPENDENCY_REQUEST.md)，继续不依赖该入口的范围内工作。

## 验证记录

| 检查 | 状态 | 命令 / 退出码 / 证据 |
| --- | --- | --- |
| 差异写入范围 | NOT_RUN | 待填；提交前使用 `scripts/check-scope.ps1 -Staged` |
| 仓库结构检查 | NOT_RUN | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope Repository`；不替代 All |
| 任务相关测试 | NOT_RUN | 已有测试后可用 `./gradlew.bat test --tests "edu.courseassist.editor.*"`；未执行 |
| 完整统一入口 | NOT_RUN | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All`；PR 前必须完成并记录真实结果 |
| 场景验收 | NOT_RUN | 按 README 逐项记录实际输入与观察结果 |
| 真实环境边界 | NOT_RUN | 真实 PSI/动作上下文、后台读机制与目标 IDEA 兼容性 |

记录局部测试、模拟控制器、真实 IDE 和真实 API 的证据边界。没有运行的项目保持 NOT_RUN；失败保留原因和退出码，不只改状态文字。真实 API 不因本地任务验证而自动调用。

## 阻塞与下一步

- 当前阻塞：尚未开展环境/实现核对，不预设有或没有阻塞。
- 下一步：确认固定分支及已有改动，按 README 阅读并运行基线检查，然后实现首个可观察场景。
- 公共改动请求：见 [DEPENDENCY_REQUEST.md](DEPENDENCY_REQUEST.md)。
- 结束/换 agent 前：更新本文件并填写 [HANDOFF.md](HANDOFF.md)，记录真实提交、测试和下一条动作。
