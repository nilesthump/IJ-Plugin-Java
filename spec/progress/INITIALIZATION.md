# 初始化执行记录

日期：2026-10-18。用户已授权直接初始化，并明确仅公开工程、方案和任务；原始请求记录留本地。本次不是正式架构冻结。

## 已完成的本地交付

- Java25 单 Gradle 工程、可注册的最小 Tool Window、源码公共契约、独立 testFixtures 和可重复夹具检查。
- 负责人/甲/乙/丙四个可开工任务，具备读取顺序、首个场景、允许路径、明确依赖请求、验证及交接入口；功能实现均未开始。
- 仓库本地三种 hooks、范围检查、统一验证入口、GitHub validation 工作流及三份远程规则输入。PR 从四个 dev 分支各自进入 main，manager 独立。
- 本地架构原始来源的工作区/初始暂存 SHA-256 已核对一致；后按用户要求排除原始附件、来源记录、需求答复和内部状态，并重建无其历史的公开提交。本地原件和历史另留，不公开。常见 key/token/私钥模式未命中；这不是全类型凭据审计。

## 实际验证

| 验证 | 结果与范围 |
| --- | --- |
| 契约与夹具 javac | PASS，JDK25.0.2 / release17，无第三方依赖；最终工程编译release25 |
| Gradle check + buildPlugin | PASS，本机 SDK 262.10968.63，17项执行；test NO-SOURCE，FixtureSmoke 实际执行并通过；没有成员测试则不能宣称成员行为已验证。用户后续更正始终仅验证本机版本 |
| 插件 ZIP | 已构建 `IJ-Plugin-Java-0.1.0-dev.zip`，82,503,738字节；未运行目标IDE/未验证客户端与真实依赖类加载，包体仍需负责人后续评估 |
| 隔离Git行为 | [37次操作结果](evidence/git-policy-tests.json)全部通过；含引用创建/删除/改名、detached工作树、fetch、push及pre-commit调用边界 |
| 独立范围检查 | [93场景结果](evidence/scope-tests.json)全部符合预期，退出0；覆盖4分支路径矩阵、rename两端、main/detached拒绝、merge-base和篡改规则被可信checker拒绝 |
| Wrapper | 官方生成、JAR SHA-256与官方一致；ZIP固定官方SHA-256；实际下载/使用状态看CURRENT |

独立只读检查由 `/root/bootstrap_review` 在 fresh context 执行；初始CI直接使用PR中的checker会被修改绕过，已拆为只读PR构建与仅从main读取策略/文件元数据的范围状态工作流。范围逻辑复核未发现尚未修复的P1/P2，远端接线待实际运行核验。隔离执行器一度无法启动，测试使用已授权环境重跑；没有把环境失败记成测试通过。

## 远程与运行边界

公开仓库已创建为 `nilesthump/IJ-Plugin-Java`；五分支已推送，服务端创建禁令/删除与强推保护/main PR门禁均active并读回。最终CI结果以 [CURRENT.md](CURRENT.md) 为准。本地 hooks 需要各克隆执行安装，GitHub 规则只约束远程，不能阻止用户在未安装 hooks 的任意本地克隆创建分支。协作 agent 仍须遵守固定分支规范；组员从同仓库固定分支推送需由负责人授予仓库写权限，未配置具体成员账号。

仅本机 262.10968.63 的 IDE 运行、实际生成/嵌入、H2与JVector组合、完整业务流程及千页性能均待任务实施；不再增加旧版本验证任务。初始化不发送真实模型请求，也不承诺全部P0或产品验收通过。
