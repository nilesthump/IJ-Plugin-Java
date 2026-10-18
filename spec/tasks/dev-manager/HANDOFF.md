# 负责人：公共骨架、核心与主线验收：交接模板

本页保留前两组历史交接，最新交接见后续“第三组交接”。第四、第五组及整体产品未完成。历史 PDF 工作补充保持原文，属于本轮开始前已有改动。

## 身份与读取

- 交接日期 / agent：2026-10-18 / Codex。
- 分支：`dev-manager`；开工 HEAD：`36452814d58dfda228511bd17556dfa5df69094a`。
- 同步的 `main` 基线提交：同上，fetch 后无增量；未创建分支或改其他工作区 HEAD。
- 已读取根入口、CURRENT/BASELINE/开发方案、治理四件、当前任务三件、API/实际契约、并行接入/分工/蓝图/P0 与成员 README/依赖请求。第 2 组开工继续深入 COURSE_LIBRARY/JVECTOR_RETRIEVAL/PERSISTENCE 的业务规则。
- 工作区已有 PDF/方案导出改动均保留；本轮只提交第一组代码/验证和对应记录，未提交这些历史文档产物。

## 实际结果

- 本次具体实现的行为：见 [WORK_GROUP_1.md](WORK_GROUP_1.md)。应用/项目服务、TaskScope/RuntimeGate、H2Store、SpringAiTransport/ModelClassLoading；生命周期与真实插件依赖检查。
- 改动文件：上述 manager 包和对应测试、plugin.xml、build.gradle、统一验证入口及 test-only IDE 命令；API/验证文档与本任务记录。未改 editor/pdf/ui 或成员任务目录。
- 未实现：业务 schema、耐久 projectId、生产控制器、凭据服务接线、资料/检索、ReAct 与完整恢复/删除。现有占位 Tool Window 和合成 fixtures 保留；运行检查的所有 HTTP 响应来自进程内 loopback。
- 公共接入：API 第 7 节；codeCapture/pdfReader 端口要求负责人显式注册且恰有一份，无成员实现时明确失败。

## 验证与证据

| 检查 / 场景 | 准确命令或操作 | 退出码 / 实际结果 | 原始记录位置 | 尚不能证明 |
| --- | --- | --- | --- | --- |
| 统一 All 入口 | STATUS 中准确命令 | 0 / PASS，9 项 JUnit、FixtureSmoke、check/buildPlugin | tmp/manager-task1/final-validation-reflect.log；build/test-results/test | 完整业务功能 |
| 真实 IDEA 骨架 | 同一命令增加 -IdeSmoke | 0 / PASS；IU-262.10968.63、JBR25.0.4 | [运行报告](evidence/work-group-1-runtime.json) | headless；无可视交互或真实 API |
| 发布 ZIP | .NET ZipArchive 枚举条目 | PASS，79,474,195 bytes / 60 entries；测试插件/重复 Kotlin stdlib、reflect/SLF4J API 均无 | build/distributions/IJ-Plugin-Java-0.1.0-dev.zip | 最终产品安装、课程/模型能力与业务恢复 |

未执行的写 NOT_RUN，失败附原因，不能用 fixture 代替真实能力结论。证据不得含 key、凭据 ref、私有课程/学生数据或完整秘密命令 dump。

## 继续动作

- 当前状态：第 1 组实现与本地 All/IDE 验证完成；本交付记录在 PR #3 的 validation/scope 通过并合入 main 后生效，满足后第 1 组 DONE。不得提前声明门禁成功；准确结果及已修复失败见 STATUS。
- 下一条可执行动作：第 1 组交付后，第 2 组先实现固定托管副本与 blob/sourceVersion 身份边界；仍在已有 dev-manager。
- 成员接入：待成员提供实际适配器类名后由负责人注册；当前无未处理依赖请求。
- PR：[PR #3](https://github.com/nilesthump/IJ-Plugin-Java/pull/3)，目标 main，来源 dev-manager；代码首提交 `11f7c9e`，后续提交去重 kotlin-reflect 并更新本记录；检查/合入以该 PR 实际状态为准，保留分支。
- 本任务 [STATUS.md](STATUS.md) 已同步，整体保持 IN_PROGRESS。

## 第二组交接（2026-10-18）

- 分支 dev-manager；开工 HEAD/main 为 `50f50f8b77daa81cbbfbfb2bcacf564109e7f7f0`。第一组 PR #3 已合入。保留开工前方案/PDF工作区差异，第二组提交不带入这些产物；不触及 editor/pdf/ui 或成员任务。
- 完整行为/显式参数/接入限制见 [WORK_GROUP_2](WORK_GROUP_2.md)，准确验证见 [STATUS](STATUS.md)。应用服务新增惰性 courses()；后台初始化后，每 attempt 取得独立 Lease 固定代次，关闭时释放。Job.cancel 撤销结果资格，settled 表示后台收尾；settled 失败不能当作已保存终止状态。
- H2 corpus schema v1 与托管 blob 在应用 config/courseassist-f0 下；未知/损坏版本不自动清空或迁移。重启只从本地原始向量构图，不请求文档嵌入。持久 owner/source 引用和在途 lease 是旧版本清理栅栏；未来会话提交/删除仍须由第 4 组接线，当前不宣称已有回答原子提交或 session 全清。
- 最终 All + IdeSmoke 退出 0，31 项 JUnit、FixtureSmoke、ZIP、本机 IU-262.10968.63 / JBR25.0.4 通过。原日志 tmp/manager-task2/delivery-validation.log，公开 evidence/work-group-2-runtime.json、work-group-2-validation.json；独立 Standards/Spec 修复记录见 work-group-2-review.md。
- 未实现/未执行：生产 ApplicationController 配置/凭据/epoch 与 UI 业务装配、乙解析器注册/真实 PDF 联调、真实网关与真实规模；前者属后续组或成员，绝不注入成功空适配器。Spring AI 桥接仅对进程内合成 HTTP 验证，不选择真实 alias/维度，也不自动联网测试。
- 交付条件仍为本次 PR validation/scope、独立检查、main 合入并核验；满足后第二组 DONE，整体仍 IN_PROGRESS。保留固定来源分支，主工作区仍 dev-manager；下一项为第三组但本轮不开始。实际 PR/提交/合入信息附后。

### 第二组 PR 与继续位置

第二组 [PR #4](https://github.com/nilesthump/IJ-Plugin-Java/pull/4) 已创建，目标 main，来源 dev-manager；代码提交 `8b41abae4b11e24459d9903778e967b3d55d4d47`。实际门禁和合入以 PR 最新 head 为准；规则满足后合入并核验，保留开发分支。后续 agent 先核对该 PR 状态及 origin/main，勿重做已通过场景；第三组另行实施。开工前 PDF/方案差异仍独立留在工作区。

## 第三组交接（2026-10-18）

- 主工作区保持 dev-manager；开工 HEAD/main 为 `5b945fa936b7c5c7d86aea1e2fc01fe04f32609b`（第二组 PR #4 已合入）。既有方案/PDF差异保留且排除本次提交，成员实现/任务未改，没有新建分支或修改其他人的 HEAD。
- 第三组行为、显式参数及真实边界见 [WORK_GROUP_3](WORK_GROUP_3.md)，公共接入见 API 第 9 节。ReactEngine 的 Model/Journal/运行资格由调用方明确提供；CourseTools 在 attempt 创建时固定一次 lease，不能由模型步骤重新 openAttempt。一次 step 一订阅，工具仅两个，正常完成/引用/耐久提交共同成功。
- cancel 返回 true 才表示停止已接受；最终保存先取得资格时为 false，不能显示已停止。runtime/session guard 仍在事务内；Cancelled 不能当作保存故障或检索通识，真实保存失败维持未保存/后续发送阻塞。completion 是隔离取消的观察 future，不用取消它代替 Job.cancel。
- H2 ReactRepository 要求先初始化 corpus schema，并传入真实 session/project/instance/operation/epoch guard。测试 SQL 根不是生产实现。保存正文/证据/状态与 ca_source_ref 同事务；interrupt 不启动网络；deleteSession 在 DELETING guard 下清私有档案/引用。第四组接完整根生命周期时复用，不能提供生产 no-op guard 或 upsert 复活。
- ReactContext.history / POLICY 是未来模型/检索改写/摘要/checkpoint 共同资格入口；仅耐久成功最终有效分支生成内容合格。停止/中断/超限/失效生成输出仍在 Snapshot 中显示；重试新 attempt、原输入只投一次。不得绕过资格入口注入任意旧 summary/cache。
- 最终 All + IdeSmoke 退出 0：51 项 JUnit、FixtureSmoke、插件 ZIP、本机 IDEA/JBR 的严格 SSE 两步 ReAct/固定页证据/H2 档案通过。日志 `tmp/manager-task3/final-validation.log`；公开 evidence/work-group-3-runtime.json / validation.json / review.md。两路独立检查均无未闭合项。
- 真实/付费模型与嵌入、真实 PDF/可视交互/课程规模 NOT_RUN。模型/预算/限额仍为显式配置与合成候选，完整第四组会话/摘要/项目身份未实施。下一条动作：先核对本第三组 PR 最新 head 的门禁与 main 合入；满足后第三组 DONE，再按用户下一轮请求开始第四组。本轮停止于第三组。
