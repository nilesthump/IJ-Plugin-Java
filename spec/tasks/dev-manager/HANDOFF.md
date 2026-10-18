# 负责人：公共骨架、核心与主线验收：交接模板

本轮交接为五组任务的第 1 组；后续组和整体产品未完成。历史 PDF 工作补充保持原文，属于本轮开始前已有改动。

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
| 统一 All 入口 | STATUS 中准确命令 | 0 / PASS，9 项 JUnit、FixtureSmoke、check/buildPlugin | tmp/manager-task1/final-validation.log；build/test-results/test | 完整业务功能 |
| 真实 IDEA 骨架 | 同一命令增加 -IdeSmoke | 0 / PASS；IU-262.10968.63、JBR25.0.4 | [运行报告](evidence/work-group-1-runtime.json) | headless；无可视交互或真实 API |
| 发布 ZIP | .NET ZipArchive 枚举条目 | PASS，82,652,477 bytes；测试插件/重复 Kotlin/SLF4J API 均无 | build/distributions/IJ-Plugin-Java-0.1.0-dev.zip | 最终产品安装、课程/模型能力与业务恢复 |

未执行的写 NOT_RUN，失败附原因，不能用 fixture 代替真实能力结论。证据不得含 key、凭据 ref、私有课程/学生数据或完整秘密命令 dump。

## 继续动作

- 当前状态：第 1 组实现与本地 All/IDE 验证完成，PR 门禁待完成；准确结果及已修复失败见 STATUS。
- 下一条可执行动作：第 1 组交付后，第 2 组先实现固定托管副本与 blob/sourceVersion 身份边界；仍在已有 dev-manager。
- 成员接入：待成员提供实际适配器类名后由负责人注册；当前无未处理依赖请求。
- PR：目标 main，来源 dev-manager，合并保留分支；创建/合入结果随后补录。
- 本任务 [STATUS.md](STATUS.md) 已同步，整体保持 IN_PROGRESS。
