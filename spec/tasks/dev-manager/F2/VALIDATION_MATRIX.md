# F2 验证与验收矩阵

日期：2026-10-25。负责人：用户本人。所有产品场景初始NOT_RUN；本轮Repository文档检查不改变该表。按组执行后填真实命令/退出码/日志与限制。

| ID | 场景/最小可观察结果 | 关联目标/组 | 层次 | 初始状态 |
| --- | --- | --- | --- | --- |
| T01 | F2精确路径可交付，无关成员路径仍拒绝，可信main政策一致 | R10/G0 | 治理行为 | NOT_RUN |
| T02 | 旧库/旧JSON重开不丢F0原文；不支持版本明确失败 | 全部/G1 | H2/JUnit | NOT_RUN |
| T03 | 两入口固定原session；唯一目标直接继续，重名/重载不猜 | R01/R02/G2 | JUnit+IDE | NOT_RUN |
| T04 | 未找到/未就绪/查询截断分开；截断一个候选不伪称唯一 | R02/G2 | JUnit+IDE | NOT_RUN |
| T05 | 同版完整before含未保存编辑，采集中变更拒绝半套快照 | R02/R05/G2 | 真实IDE | NOT_RUN |
| T06 | 无课程库仍有F2；课程故障不把F2错误变通识成功 | R01/G3 | 合成ReAct | NOT_RUN |
| T07 | 同逻辑请求子生成至多一次；换callId/并发/失败后重复不再订阅，tools为空且预算前拒绝 | R01/G3 | 计数/协议 | NOT_RUN |
| T08 | 越界/重叠/原文不匹配候选明确失败；无实际项目写入 | R04/G3 | JUnit | NOT_RUN |
| T09 | 子候选完整但父停止/失效/保存失败，不可批准或进未来模型 | R04/R05/G1/G3 | 故障注入 | NOT_RUN |
| T10 | 内置侧对比、before/after准确、预览零Document/磁盘修改 | R03/R04/G4 | 真实IDE+人工 | NOT_RUN |
| T11 | 两按钮准确标签，模型/嵌入/首检/ReAct增量0，draft不变 | R08/R09/G4/G5 | UI+调用计数 | NOT_RUN |
| T12 | 拒绝/关闭/过期/失败不自动重生成；新draft才新ReAct | R08/R09/G4 | UI+核心 | NOT_RUN |
| T13 | 源码/候选/项目/文件身份变化使旧批准失效，不自动覆盖 | R05/G5 | 真实IDE | NOT_RUN |
| T14 | 重复同意和同意/拒绝竞争不重复写，Undo后不重放批准 | R06/R08/G5 | 并发+IDE | NOT_RUN |
| T15 | 已展示after准确应用且可Undo；Document/磁盘结果分开 | R06/R07/G5 | 真实IDE+人工 | NOT_RUN |
| T16 | 意图失败不写；已改回执失败保留事实；未知结果只核验 | R05/R07/G5 | H2故障+IDE | NOT_RUN |
| T17 | 停用/关闭/删除及晚回调不能复活候选/槽；已改文件不自动回滚 | R04/R05/G5 | 竞争/生命周期 | NOT_RUN |
| T18 | 审核事实投影/checkpoint更新，不提升失败正文/协议资格 | R05/R09/G1/G5 | ContextPlanner | NOT_RUN |
| T19 | F0课程/PDF硬删除、混排/IME、草稿与滚动、上下文/删除全回归 | 继承/G6 | All+IDE | NOT_RUN |
| T20 | 打包无测试/凭据；独立检查、最新CI及main核验分开记录 | R10/G6 | 交付 | NOT_RUN |

## 统一入口与证据口径

文档：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope Repository。

实施：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All。必要真实IDE再加-IdeSmoke -LocalIdePath <本机绝对路径> -GradleUserHome <明确缓存路径>；先读取实际product-info，不复制旧版本假设。

JUnit/本地合成HTTP证明规则和协议，真实IDE证明Diff/PSI/Document/Undo，人工证明鼠标交互/视觉/实际体验，真实API质量须用户自行按配置验收；四者不得互相冒充。没有付费调用许可或额度时不发请求。

每组记录首次失败、修复、最终退出码、测试数量/实际IDEbuild和未执行项。PR前范围与All必做；门禁/合入只有读到真实结果才记完成。不要用空测试或镜像实现的断言充数。

## G0/G1 实际结果（2026-10-26）

T01 PASS：22项本地范围/20项CI一致及37项Git策略，PR18合入核验。
T02 PASS：旧Input JSON缺字段、旧F0库加F2表、未知schema/format拒绝、实际H2关闭重开保留原文。
T09 G1基础PASS：父终态保存故障整体回滚，停止/未证明成功工具不能批准；真实G3子生成未实施。
T18 G1基础PASS：拒绝事实改变指纹，摘要调用前固定事实、激活复验、投影剔除失效摘要；不含候选原文。G5实际应用事实仍待证。
本地事件零模型/嵌入、不同session/instance、APPLY意图去重/未知恢复阻塞、删除迟到写通过。最终统一All+IdeSmoke：187项测试/33套件，退出0，本机IDE PASS。上述合成回执不代表T14–T16真实IDE写入完成；其他后续F2场景仍NOT_RUN。

## G2/G3 实际结果（2026-10-26）

最终 All+IdeSmoke 退出0：196 tests / 34 suites，无失败错误跳过，本机 IU-262.10968.63 / JBR25.0.4 PASS，独立检查闭合。

T03/T04：真实IDE验证两模块同名、重载含明确零参、嵌套类、未找到/不完整、真实dumb模式和查找截断。T05：完整未保存Document、同版PSI、旧选区过期与文字冲突通过；采集中写竞争/会话切换/真正项目关闭竞争未做确定性复现。
T06–T09：无库候选、F2故障不降级、单子请求、分片参数、不同callId/并发/失败重复、父预算前拒绝/共享输出限制、严格替换/无变化、父失败/子流停止与上下文隔离通过。既有G1父保存回滚、课程降级能力隔离回归继续通过；新增降级分支禁止再定位测试。真实Spring AI loopback在JUnit与IDE均验证父/子工具定义、配对观察与H2候选提升。未调用真实服务。
T19：既有F0全部自动测试继续通过，不代表重新完成用户人工验收。T10–T17涉及原生Diff/审核/应用/Undo部分仍留G4/G5；单PR远端门禁/合入按交付实况另记。

## G4/G5 实际验证（2026-10-26）

最终统一All+IdeSmoke退出0，209项/35套件失败、错误、跳过均0，本机IU-262.10968.63/JBR25.0.4 PASS。准确命令、日志和历史失败见 [G45_IMPLEMENTATION](G45_IMPLEMENTATION.md) / [STATUS](STATUS.md)。

- T10/T13/T15：真实原生双侧只读viewer、左右完整快照、脏Document和磁盘零预览修改；准确应用、命名Undo/Redo、正常IDE保存与原换行符、漂移/重命名/只读拒绝通过。人工视觉仍NOT_RUN。
- T11/T12/T14：本地按钮计数、draft/turn保持、关闭/拒绝/过期、重复command/applyId、新实例重审核均通过；真实IDE同意后紧接拒绝、Undo后重复applyId及已提交后StopOperation通过。任意并发交错采用核心/H2可控夹具，人工窗口竞争NOT_RUN。
- T16/T17：意图/回执故障、未知不重放、核对按钮补记、停用释放旧槽后恢复、旧终态目标失效不阻塞新回执、删除/停用与迟到预览成功或失败隔离通过。原生adapter的撤销许可保证真实Document零写；未声称H2/Document原子。
- T18/T19：既有资格/checkpoint与F0自动回归全部通过；不把本轮自动结果改写为用户人工验收。
- T20：安装包已构建、独立检查闭合；最新远端CI和main合入核验在完成后留回执。G6仍未开始。

## G6 本轮证据与剩余人工边界（2026-10-26）

实际链路与日志见 [G6_IMPLEMENTATION](G6_IMPLEMENTATION.md)，人工步骤见 [BLACKBOX_TEST_PLAN](BLACKBOX_TEST_PLAN.md)。先前表格为初始状态，不覆盖逐组实证。

| ID | 本地结论与证据 | 尚未执行 |
| --- | --- | --- |
| T01 | G0既有真实范围/可信策略证据保留；本轮未改治理 | 无新增治理变更 |
| T02 | EditFoundationTest / SessionRecoveryTest 等真实H2与兼容回归 | 真实用户库异常样本 |
| T03 | F2TargetChecks两入口/重名重载；G6完整生成链路及原session切换隔离 | 人工定位语义质量 |
| T04 | F2TargetChecks未找到/不完整/dumb/截断 | 人工索引现场 |
| T05 | F2TargetChecks及G6同版PSI/未保存全文/旧选区过期 | 采集中写竞争确定性注入 |
| T06–T09 | CodeEditToolsTest / ToolBoundaryTest / EditFoundationTest；真实SpringAI loopback；G6实际链路 | 真实服务、任意网络异常组合 |
| T10 | F2ReviewApplyChecks与G6真实原生只读双侧Diff，完整前后像、零预览写入 | B02人工窗口外观 |
| T11–T12 | EditReviewApplyTest与G6真实按钮、零请求增量、draft/turn保持、新draft事实 | B03/B07/B08/B19人工 |
| T13–T16 | EditReviewApplyTest、F2ReviewApplyChecks漂移/身份/只读/去重/Undo/磁盘/故障回执；G6真实生成后应用Undo | B12–B18人工、真实崩溃现场 |
| T17 | 核心停用/删除/迟到预览/许可守卫、真实IDE提交前许可撤销及提交后Stop | 实际项目关闭瞬间与IDE提交竞争确定性端到端注入 |
| T18 | 资格/checkpoint原有回归；G6新draft收到REJECTED/UNDONE事实 | 真实模型对事实的语义理解 |
| T19 | 209项既有JUnit与本机IDE回归；IME/滚动/PDF/恢复测试保留 | B27–B31用户实际体验 |
| T20 | 本地ZIP内容与哈希、独立Standards/Spec检查；G45 main回执已有 | 本轮PR/CI/main按用户要求等待人工通过 |

上表“本地”仅指指定实际用例，不把一行所有可能并发时序标成已穷尽；人工与真实API仍NOT_RUN。

## B01人工反馈（2026-10-26）
B01 FAIL，原prompt及回复见BLACKBOX_TEST_PLAN。T03/T04补充大源码树精确路径与源码根外诊断回归，红测失败/绿色IDE通过；最终统一结果和用户复测见B01_RETEST。人工失败未被自动PASS覆盖。
