## 当前：用户授权发布与合并（2026-10-28）

本轮用户要求顺序完成清包→README→PR合并→发布R05 issue，并补充将所有未入Git的讨论文件上传。此授权覆盖旧“等待整体通过后发PR”和讨论保持本地的规定；不覆盖凭据保护或CI门禁。11个本地插件ZIP已清空；README补齐F2使用、构建/安装、会话和Undo说明，讨论档案及证据已纳入发布范围。旧安装包路径/哈希为历史交付记录，当前不再保留这些安装包。

源码沿用229项/36套件与本机IDE已通过版本，127份Java文件哈希一致。本轮Repository检查、README本地链接、241路径scope、暂存内容凭据模式检查和Git内容来源哈希均通过，文档独立复查无阻断。远端PR仍须validation/scope成功。R05独立人工验证转交组员，PR合并后创建issue；#22继续暂缓，弹窗可视效果不补造人工通过。本段随包含它的PR最新head通过门禁并合入main后生效，满足后F2 G6开发交付DONE；开发分支上合入前仍为RELEASE_IN_PROGRESS。

## 当前：最终用户体验优化（2026-10-28）

用户确认R03/R04保护行为和R06–R08通过；R05已批准但不理解编辑器Undo/Redo步骤，保留这些子项人工待验证。进入最终体验优化，不据此宣称整体交付完成。

已移除draft上方常驻通知区；改为5秒非阻塞短弹窗，不请求输入焦点，刷新去重，后台提示带会话名称，隐藏侧栏期间的一次性命令反馈独立保留。底栏不再显示原始异常类/枚举或含混的“操作已应用”。文件漂移使用typed SourceChanged并提示文件已更改；选区STALE由宿主直接提示原选区或所在文件已更改，无候选/自动重生成/自动应用。真实异常和保存阻塞保留，通知刷新异常传播至任务边界。

LOCAL_VERIFIED / AWAITING_USER：最终统一All+IdeSmoke退出0（tmp/ux-notice-release-validation.log），229项/36套件零失败错误跳过；本机IU-262.10968.63 / JBR 25.0.4+1-b508.27 PASS（2026-10-28T09:49:48.666476400Z）。独立Standards/Spec发现并修正后台提示归属、异步错误传播、隐藏反馈丢失，复查无阻断。源码哈希复验和产品ZIP检查通过，56路径scope通过；首两轮验证日志保留，不替代最终证据。

新包build/distributions/IJ-Plugin-Java-0.1.0-dev-f2-ux.zip，79999432 bytes，SHA256 fa08737ac285295b160a282fd5a0dcbc06e4806373df9e44c5439a16e89c5f0a。交付证据tmp/ux-notice-delivery.json。仅后续提示视觉及R05编辑器操作补测，见spec/tasks/dev-manager/F2/UX_FINAL_RETEST.md；headless检查不证明可见弹窗效果。#22继续等待成员复现，不修复；未提交/推送/PR/合并，最终通过后再统一处理。

## 2026-10-28 新一轮黑测入口

用户表示最新方案只测了R01/R02；现改用BLACKBOX_R03_R08.md执行六项未测场景，不重跑两项，不把已测补记通过。Issue #22继续等待其他成员复现，暂不修复。只更新方案，未运行人工用例，无产品包变化。

## 2026-10-28 DSML问题暂缓处理

用户要求先由其他成员独立复现。已发布 [issue #22](https://github.com/nilesthump/IJ-Plugin-Java/issues/22)，bug/question，暂不实施修复、不擅自推进开发；待复现反馈后再处理。此前产品交付状态保持。

## 当前交付：Diff卡片归属与成功后ERROR分类（2026-10-28）

用户已确认同会话可定位原文件方法。新增反馈：最早Diff堆到末尾、仅发送prompt后出现ERROR。本轮按持久turn/attempt/branch将卡片插回原回答；自动摘要预算内降级独立WARNING，真实失败仍ERROR，新请求只清对应旧生成故障，原失败档案保留。现场本次attempt/tools均SUCCEEDED；运行时原始异常未持久，未取得唯一错误栈，不声称确知现场ERROR唯一根因。

LOCAL_VERIFIED / AWAITING_USER：统一All+IdeSmoke退出0，tmp/diff-owner-notice-validation.log，225项/35套件零失败错误跳过；本机IU-262.10968.63/JBR25.0.4 PASS（2026-10-28T08:00:34.559523400Z）。两路独立Standards/Spec无阻断，源码哈希/ZIP复验及52路径scope通过。

最新包build/distributions/IJ-Plugin-Java-0.1.0-dev-f2-card-owner.zip。仅两项复测见spec/tasks/dev-manager/F2/DIFF_OWNER_RETEST.md：原会话观察已有卡片位置，再发一条prompt观察新卡片/通知；不重跑已通过定位与重命名。未提交/推送/PR/合并，等待整体人工通过。

## 当前交付：R02同会话续改修复（2026-10-28）

LOCAL_VERIFIED / AWAITING_USER。实际会话首轮选区UNIQUE并生成候选；后续“1”“3”未换文件却空路径全项目重查而AMBIGUOUS。现从同会话合格宿主定位结果延续目标线索，当前明确目标优先，重新读取Document并核对文件身份；旧选区/旧快照不复活。全省略恢复方法或类范围，未知范围不扩大为整文件，泛型参数保持。候选子请求复用父规划历史解释数字选项，不绕过压缩或输出资格。

最终统一All+IdeSmoke退出0，tmp/r02-continuation-release.log；223项/35套件，失败/错误/跳过0。本机IU-262.10968.63/JBR25.0.4 PASS（2026-10-28T07:41:14.428434400Z），真实捕获/同名文件/拒绝/多轮短回复/最新未保存源码/新候选全链路通过。独立Standards和Spec发现的边界均修复复查；源码哈希及产品ZIP复验通过。初轮测试错误断言失败保留在tmp/r02-continuation-green.log，不当作产品通过证据。

最新包build/distributions/IJ-Plugin-Java-0.1.0-dev-f2-continuation.zip。最短复测见spec/tasks/dev-manager/F2/R02_CONTINUATION_RETEST.md：保留原失败会话，不附选区、不补路径，继续原方法。首次选区现场成功及已通过的重命名/两项定位不重跑；冷启动Diff计时没有明确人工结论仍保留待测。未提交/推送/PR/合并，等待用户整体通过。

## 当前交付：R02选区定位修复（2026-10-28）

LOCAL_VERIFIED / AWAITING_USER。R02原人工FAIL保留：实际冻结选区正确，模型发送原文件H:/绝对路径被早期拒绝；选区还带有方法前缩进。已修复为仅识别绑定文件的路径别名，并仅为方法归属判断裁两端空白，原allowedRanges、原文/版本及项目边界不变。无选区文字入口仍要求项目相对路径。

红测真实IDE返回NOT_FOUND/INCOMPLETE/INCOMPLETE，准确复现。最终统一All+IdeSmoke退出0（tmp/r02-selection-green.log），222项/35套件零失败错误跳过，本机IU-262.10968.63/JBR25.0.4 PASS（2026-10-28T06:41:46.783307500Z）。含实际F0整方法捕获、模型绝对路径参数、候选与Diff全链路及范围/冲突/外部/过期拒绝。两路独立检查无阻断，源码哈希与产品ZIP复验通过。

最新包build/distributions/IJ-Plugin-Java-0.1.0-dev-f2-selection.zip。复测见spec/tasks/dev-manager/F2/R02_RETEST.md，剩余清单见BLACKBOX_REMAINING.md；原prompt不变，重开后新草稿重新采集。已有两项定位及重命名通过记录保留，不要求重跑。未提交/推送/PR/合并，仍等待用户整体验收。

## 最新人工验收与剩余清单（2026-10-28）

用户确认“重命名成功”；此前两项定位测试通过。普通Diff打开、基础应用已有现场执行证据，移出重复清单，但不补造所有细项PASS。当前执行入口为spec/tasks/dev-manager/F2/BLACKBOX_REMAINING.md：8项合并后的修复复测/新边界，另6项按需补测保留自动验证与人工未测边界。已验收F0不重跑整套；沿用现有notes/examples样例，不复位add。

本轮只更新验收记录与方案，无业务代码或包变化；最新安装包仍为f2-rename.zip。222项及本机IDE验证证据保持。用户未确认整体完成，尚无提交/推送/PR/合并。

## 当前交付：右键重命名菜单修复（2026-10-28）

LOCAL_VERIFIED / AWAITING_USER。用户报告前述两项定位测试通过；右键重命名按钮灰色FAIL已修复。原因是Swing全局菜单显示早于列表MouseListener，冻结了空目标；现于getPopupLocation先命中目标，空白禁用且打开菜单后不被刷新改向其他会话。

真实dispatchEvent红测准确复现（222项中仅新增1项失败）；修复后统一All+IdeSmoke退出0，222项/35套件零失败错误跳过，本机IU-262.10968.63/JBR25.0.4 PASS（2026-10-28T05:48:20.905575600Z）。日志tmp/rename-menu-red.log、tmp/rename-menu-green.log。两路独立检查无阻断；源码哈希、产品ZIP和45路径scope通过。桌面工具路径错误不可用，真实桌面点击仍由用户复测。

安装包build/distributions/IJ-Plugin-Java-0.1.0-dev-f2-rename.zip，菜单两步复测见spec/tasks/dev-manager/F2/RENAME_RETEST.md。既有样例保留。未提交/推送/PR/合并，继续用户整体通过后统一PR合并；下文为历史记录。

## 当前交付：项目目录与会话交互修复（2026-10-28）

LOCAL_VERIFIED / AWAITING_USER。当前项目目录内现有Java文件无需Sources Root；歧义要求明确。空前台可输入，发送自动创建会话；历史/搜索采用首条消息短摘要并支持右键持久改名。保留此前完成态、自动观察及Diff刷新积压修复。

统一All+IdeSmoke退出0：221项/35套件，失败/错误/跳过0；本机IU-262.10968.63/JBR25.0.4 PASS（2026-10-28T05:17:54.956565600Z），headless首次Diff面板442ms。日志tmp/project-conversation-delivery.log。两路独立检查无阻断，源码哈希、产品包和44路径scope通过；失败过程见PROJECT_CHAT_RETEST。桌面窗口速度/真实模型/人工验收不由此证明。

安装包 `build/distributions/IJ-Plugin-Java-0.1.0-dev-f2-project-chat.zip`；最短黑测见 `spec/tasks/dev-manager/F2/PROJECT_CHAT_RETEST.md`，完整方案见BLACKBOX_TEST_PLAN。用户通过后再统一PR合并；未提交/推送/PR/合并，dev-manager保留。下文旧时点不覆盖本条。

# 当前：首次 Diff 刷新积压修复本地验证通过（2026-10-27）

LOCAL_VERIFIED / AWAITING_USER。真实协调器/H2回归复现40帧在首次预览前42次刷新；合并待处理视图刷新后通过，保留完整正文、终态与审核检查。最终统一All+IdeSmoke退出0，215项/35套件零失败错误跳过，本机IU-262.10968.63/JBR25.0.4 PASS，原生面板首次718ms（headless，非可见窗口速度）。日志tmp/diff-delay-final.log，报告2026-10-27T06:05:16.688311500Z。首次真实Dialog测试因HeadlessDialog自动关闭失败的记录已保留并更正；独立检查无阻断，产品ZIP/源码哈希/scope通过。

安装及最短复测见 [DIFF_DELAY_RETEST](DIFF_DELAY_RETEST.md)。源码根限制来自G2/G3实施时的显式收窄，定位/应用两道检查，本轮未扩大范围；见 [SOURCE_ROOT_ANALYSIS](SOURCE_ROOT_ANALYSIS.md)。实际桌面首次点击速度与整体人工验收仍待用户确认，无提交/推送/PR/合并。

## 历史记录

# 当前：完成态与自动状态修复本地验证通过（2026-10-27）

LOCAL_VERIFIED / AWAITING_USER。成功回答后恢复发送，正常F2编辑器/保存/Undo/Redo事实自动核验，无常规手动核验按钮；异常仍可恢复，历史应用与当前未知状态分开。详细行为、包和三步复测见 [AUTO_STATE_RETEST](AUTO_STATE_RETEST.md)。

最终统一All+IdeSmoke退出0：214 tests / 35 suites，失败/错误/跳过0；本机IU-262.10968.63/JBR25.0.4通过，报告2026-10-27T05:14:29.672650100Z。日志tmp/auto-observation-delivery.log。源码哈希复验一致，产品ZIP不含IDE smoke/fixtures。两路独立增量检查闭合；此前失败日志和无效no-op测试前提更正保留在本地证据。用户整体人工验收仍待确认，无提交/推送/PR/merge，固定dev-manager保留。

## 历史记录

# 当前：人工反馈修复中（2026-10-27）

用户新截图已显示唯一目标、候选、查看Diff及应用/保存状态；这只证明现场进展，不代替B01–B32整体验收。新增两项明确反馈：成功回答后底部仍为重试；应用后的编辑器/保存/Undo核验应内部自动完成。本轮已实现候选，正在完整All+IdeSmoke和独立检查，未通过前不交付新包。原文及代理解释已分列保存于本地忽略evidence，哈希登记。继续保留人工通过后统一PR合并门槛，无提交/推送/PR/merge。

新增定向红测2项稳定失败（tmp/auto-observation-red-focused2.log）；首轮脚本编码与测试字段/局部变量编译问题已修正，失败日志保留。本轮成功结论以最终验证记录为准。

## 之前记录

# 当前：B01 人工失败修复，本地验证通过，等待人工复测（2026-10-26）

用户在BLACKBOX_TEST_PLAN记录B01失败，实际prompt指向spec中的样例，定位返回INCOMPLETE/candidates=[]。原文与文件快照已在本地忽略evidence保存、来源哈希登记；旧G6自动通过不代表B01人工通过。

真实IDE红测证明精确路径错误地受全工程扫描上限影响。已改为项目相对路径单点定位，继续守住原项目/源码根/canonical/同版PSI；不存在路径不fallback，非源码根明确OUTSIDE_SOURCE_ROOT。绿色IDE probe通过，最终统一All+IdeSmoke退出0：210 tests / 35 suites，失败/错误/跳过0，本机IU-262.10968.63/JBR25.0.4 PASS；日志 `tmp/f2-b01-final.log`，IDE报告2026-10-26T14:47:56Z。 本轮不读取凭据、不发付费请求、不提交/推送/PR/合并。

用户现场触发哪条扫描上限和实际源码根配置未取得运行证据，不能称唯一根因已证实。spec样例若不在源码根，新版本应明确拒绝；成功复测应先复制样例到临时Java工程src。步骤与新包入口见[B01_RETEST](B01_RETEST.md)。B01保留FAIL / AWAITING_RETEST，其余人工未执行；不能标用户通过或G6 main DONE。

## 此前记录

# 当前：G5 已合入核验，G6 本地收尾待人工验收（2026-10-26）

负责人：用户本人；分支 dev-manager，HEAD/main `9ac4448ec649890646979215fa35601314168761`。G0–G5 开发交付已合入；G4/G5 PR #21 受检head `053aa37` 的 validation/scope成功，main提交与本地祖先再次核验通过。此前 AWAITING_CI 段落保留为历史。

本轮用户原文要求完成最后任务、给出黑盒方案，人工通过后再统一PR合并。按实际剩余任务执行G5补记和G6，不增加不存在的组。本轮未提交/推送/创建PR/合并，不修改生产或治理；G6 LOCAL_VERIFIED / AWAITING_USER，不能标main DONE或人工PASS。

新增真实两入口完整链路及生成期间切换聊天隔离、原生按钮零模型/draft保持、Undo和新draft状态投影。首次统一All+IdeSmoke退出0，209 tests / 35 suites零失败错误跳过；本机IU-262.10968.63/JBR25.0.4 PASS。最终固定源码复核 `tmp/f2-g6-final.log` 退出0：209项/35套件零失败错误跳过，JUnit实际重执行，本机IDE PASS。实际细节、修正、日志、包哈希与未执行边界见 [G6_IMPLEMENTATION](G6_IMPLEMENTATION.md)。

交付：[32项黑盒方案](BLACKBOX_TEST_PLAN.md)，准备 [Java样例](samples/src/demo/Calculator.java)及[重名样例](samples/src/other/Calculator.java)。人工/真实API均NOT_RUN。下一条：由用户安装 `build/distributions/IJ-Plugin-Java-0.1.0-dev-f2-g6.zip` 并反馈B编号；明确通过后才统一PR、门禁与main核验。保留固定分支，架构未冻结。

## 历史记录

# 当前 G4/G5 单 PR 交付（2026-10-26）

唯一负责人：用户本人；既有 dev-manager，基线 main `4de5417ef81cd3e409c5721b1eb0a8d508ff3443`。G0–G3 已合入核验（G2/G3 为 PR #20）。用户授权 G4/G5 统一一个 PR 提交并合并；G6 未开始，架构未冻结。

阶段 LOCAL_VERIFIED / AWAITING_CI（本PR成功合入并核验后G4/G5 DONE）。最终统一 All+IdeSmoke 退出0：35 suites / 209 tests，失败/错误/跳过均0；本机 IU-262.10968.63 / JBR25.0.4 的 result.json 为 PASS。新增13项审核/应用/恢复JUnit及真实IDE原生Diff、准确应用/Undo/保存/拒写场景。两路独立Standards/Spec检查及最后恢复增量检查均无遗留。具体行为、各轮失败/修复与层次限制见 [G45_IMPLEMENTATION](G45_IMPLEMENTATION.md)。

准确命令：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'。最后日志 `tmp/f2-g45-delivery-final.log`；本地原始证据 `spec/architecture/evidence/f2-g45-local-validation-2026-10-26.json`。先前成功批次不覆盖其后的修复，结论仅以最后交付运行计。

安装包 `build/distributions/IJ-Plugin-Java-0.1.0-dev.zip`，79967508 bytes，SHA256 `17defe73bb9977fd576e16a4fcba64e1ae8adc86aeb3311051b397189b144772`。

交付入口：[PR #21](https://github.com/nilesthump/IJ-Plugin-Java/pull/21)，dev-manager → main；实现提交1578f23已推送，工作区干净。仅此一个PR。下一条：核验最新head的validation/scope，成功后合并并fetch/快进核验，保留固定分支。本PR满足这些条件后G4/G5为DONE；否则保持待集成。G6未开始，人工F2/真实API继续NOT_RUN。

## 此前记录（保留原时点状态）
# 当前 G2/G3 单 PR 交付（2026-10-26）

唯一负责人：用户本人；分支 dev-manager，基线 main `1ec4b9412d8c70b20a404da6c9a499d82bae1654`。G0/G1 已经 PR #18/#19 合入核验；此前待合入记录为历史。用户授权下两组 G2/G3，只提交一个 PR；G4–G6 不展开，架构未冻结。

G2/G3 实现和本地验证完成，当前 LOCAL_VERIFIED / AWAITING_CI；唯一交付 [PR #20](https://github.com/nilesthump/IJ-Plugin-Java/pull/20)，dev-manager → main，尚未合并。最终 All + IdeSmoke 退出 0，34 suites / 196 tests，失败/错误/跳过均 0；本机 IU-262.10968.63 / JBR25.0.4 的 result.json 为 PASS。行为、有限参数、失败修复及未执行项见 [G23_IMPLEMENTATION](G23_IMPLEMENTATION.md)。不得提前记 main DONE。

命令：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'。

原始日志保留在 tmp/f2-g23-baseline.log、tmp/f2-g23-first.log、tmp/f2-g23-tests.log、tmp/f2-g23-second.log、tmp/f2-g23-third.log、tmp/f2-g23-fourth.log；最终运行 tmp/f2-g23-final.log。既有基线和每轮 JUnit/打包结果与单独 IDE 失败分开记录。独立代码复核原三项（跨降级重复生成、稳定文件身份、零参数重载）已修复并闭合；最终运行证据已核验，独立增量复核无阻断遗留。

下一条：最终 All/IDE、独立检查及28文件暂存范围检查已闭合；实现提交 52fd92f 已推送并创建唯一 PR #20。下一步核验最新 head 的 validation/scope；本轮只提交 PR，不合并。新 PR 未合入前保持待集成。真实付费 API、人工 F2、Diff/审核 UI、Undo/源码应用未执行。

## 以前的记录（原时点状态）

# 当前 F2 状态（2026-10-26）

唯一负责人：用户本人。分支：dev-manager。G0 已完成并在 main 核验；G1 已实现，最终统一验证及独立检查均通过，尚未交付 main；G2–G6 未开始。架构未冻结。

| 组 | 实现/交付 | 验证 |
| --- | --- | --- |
| G0 | DONE；PR #18，main 0eecc0e | validation/scope成功；170 tests、本地范围22项/CI一致20项、Git策略37项、独立检查通过 |
| G1 | LOCAL_VERIFIED / AWAITING_PR | 187项测试及本机IDE PASS；两路独立检查闭合 |
| G2–G6 | NOT_STARTED | NOT_RUN |

范围与具体行为见 [G1_IMPLEMENTATION.md](G1_IMPLEMENTATION.md)。下一步：按既有dev-manager→main PR交付G1，门禁及合入核验后才能标DONE。没有真实付费调用；原生Diff/Undo/实际源码应用尚未实现。

## 历史记录（保留原时点状态）

# F2 交接

日期：2026-10-25。负责人：用户本人。分支：现有dev-manager；本地基线8fac788，与本地origin/main一致。当前任务是方案/任务包交付，未实施F2。

先读F2 README、STATUS和开发方案，再从GROUP_0继续。根AGENTS仍F0-only，manager范围仍排除editor/ui；用户个人负责不等于机器策略已改。推荐G0先独立治理PR明确精确F2路径/必要接线，不新开分支、不把实现挪到bootstrap、不分配甲乙丙。

已确认：可选选区/文字定位；原生Diff先审核且预览零写入；批准准确版本；应用已展示内容、支持Undo，Document与磁盘保存分开；“同意并应用”“拒绝”均本地事件，只有用户再次draft发送才重新进入ReAct。

技术提案：单文件/整份批准、本地工具边界/受控子生成、独立F2仓储、具体幂等和恢复字段。先按G0记录实际实施边界，不能把生成文档说成所有技术决定已确认或正式冻结。

真实接点：ReactData.Input有question/code/captureOffsets/captureOrder；ReactEngine仍课程工具总开关；真实UI已是MixedDraftEditor/ChatTimeline；终态ReactRepository Snapshot不可回写。F2需独立审核/应用记录及checkpoint事实投影，不破坏F0。

七组顺序执行，各组负责人都是用户本人。当前所有实现/产品测试NOT_STARTED/NOT_RUN；本轮Repository退出0，13份新文档/17个本地链接/4处入口导航与范围检查通过；两路独立文档检查已闭合，修正三处候选准入/审核守卫/逻辑生成去重歧义。初次换行空白及核验脚本解析失败均已修正并保留本地记录；产品与DevOps未改。不得拿F0历史169项测试作为F2已验证证据，不发付费模型请求证明本地接口。

保留本轮之前STATUS/HANDOFF未提交记录，不自行提交/推送/合并。公开任务不依赖本地忽略原文；后续每轮继续维护负责人evidence与来源哈希。

## F2方案文档PR发布准备（2026-10-25）

用户授权提交推送本批19份公开规划/入口/任务状态文档，使用既有dev-manager→main，保留固定分支。原始答复、DISCUSSION、WORKING及本地evidence继续忽略，不入提交。

本轮PR前统一验证命令：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'，退出0；30 suites / 169 tests，失败/错误/跳过均0，check/buildPlugin通过。仅现有工程回归，F2实现仍NOT_STARTED；本轮IdeSmoke、真实API与F2产品场景NOT_RUN。

发布独立核对无阻断；提交前机检核验精确19文件、范围、空白与敏感模式。当前正在提交推送/创建PR，远端CI和实际URL由本地发布回执另记；本轮不合并、不修改门禁或业务。

## G0 实施与验证（2026-10-26）

用户已明确授权精确治理提案。实施范围见 G0_DECISIONS.md：四个F2子目录与三个UI接线文件；保留原managerExcluded、hooks、固定五分支和main可信检查。当前阶段 LOCAL_VERIFIED / AWAITING_PR，不标记DONE；G1仍NOT_STARTED。

node scripts/testing/scope-policy.test.cjs：22项本地检查通过、20项可信工作流表达式一致；包含相似名称拒绝、其他成员目录拒绝、main拒绝及重命名旧端拒绝。原始结果 tmp/f2-scope-figVz7/results.json。
统一 powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home' 退出0，日志 tmp/f2-g0/validation.log。F0 test任务UP-TO-DATE，既有169项报告不作为本轮重新执行。F2产品/IDE/API未执行。
下一步：独立检查闭合后独立治理PR，经最新head validation/scope成功、main合入核验并同步，才能开始G1。
独立只读检查：g0_review 核对精确授权、排除项保留、本地/CI一致、重命名双端及可信main来源，无阻断；git diff --check通过。

### G0 CI 测试观察边界修复
首次CI run 40287327107：169 tests中1项失败，ContextPlannerTest重复摘要在SessionTestEnvironment.dispatch的10秒get超时，没有业务断言失败。保留失败，不推定远端变慢的唯一根因。
新增真实H2写线程延迟11秒后执行UpdateDraftText的回归，原夹具确定性在同一位置TimeoutException（tmp/f2-g0/observation-red.log）。仅将夹具dispatch观察窗口对齐既有idle的30秒；模型业务10秒时限不变。修复后统一All退出0，31 suites/170 tests，失败错误跳过0（tmp/f2-g0/observation-green-all.log）。独立检查无阻断。
37项既有Git策略操作回归通过（tmp/bootstrap-git-tests/run-7c64bef6b75c450aa67be8dc5576ba83/results.json）。G0仍待最新head门禁；G1未开始。
## 2026-10-26：G0 完成，G1 实施

G0 DONE：PR #18 最新受检 head 7f77ab5e2bba235ab8c75a73068df0696df6dfa0 的 validation/scope 均成功；已合入 main 0eecc0efd60895e297a250291be04cc58cf25887，并 fetch/快进/祖先核验。固定分支保留。首次CI夹具超时、确定性红测和修复均在上文保留；最终170项测试通过。

G1 实际实现见 [G1_IMPLEMENTATION.md](G1_IMPLEMENTATION.md)。两路独立检查已闭合；首轮存储/工具/摘要定向测试通过。最终 All + IdeSmoke 正在执行，远端交付尚未开始；当前 IN_PROGRESS，不提前标 DONE。G2–G6未开始。

## G1 最终本地验证与交付条件（2026-10-26）

阶段 LOCAL_VERIFIED / AWAITING_PR；仅当本交付 PR 最新 head 的 validation/scope 成功、合入 main 并 fetch 核验后，G1 才为 DONE。此前不提前标记已集成。

最终命令：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'

退出0；187 tests / 33 suites，失败/错误/跳过均0。check、FixtureSmoke、buildPlugin通过；本机 IDEA IU-262.10968.63 / JBR25.0.4，build/reports/ide-smoke/result.json 为 PASS。日志 tmp/f2-g1/delivery-validation.log；预期跨方法拒绝异常与IDE环境警告保留，不改变最终PASS含义。

新增17项G1回归（实际JUnit报告：EditFoundationTest 13、ToolBoundaryTest 4），另G0观察回归1项；F0既有169项继续通过。G1定向日志 foundation-tests.log、refined-tests.log；完整集成及后续去重/会话隔离增量分别在 first-all.log、final-validation.log、final-replay-validation.log、delivery-validation.log，最终结论使用最后一份。

独立存储/生命周期/上下文检查及契约/工具检查均0遗留。修正并补测：未知应用结果的新commandId去重；同一事实快照验证checkpoint投影；重复批准命令固定内容；单会话恢复不失效另一session预览；真实H2重开原文保留且不恢复批准。

实际行为与后续边界见 G1_IMPLEMENTATION.md。此次只交付G0/G1，G2–G6 NOT_STARTED。真实API、原生Diff/Undo、实际源码应用、人工F2验收 NOT_RUN；无付费调用，不宣称F2产品可用或架构冻结。

## PR #20 合并门禁修复（2026-10-26）

用户已授权“本次合并”，覆盖此前只提交不合并的当轮限制。首次远端run40309275683失败：196项中取消测试预期STOPPED、实际LIMITED。保留完整日志，已用11秒观察延迟确定性证明期限先结束导致停止被拒绝的路径；不据原日志宣称唯一根因。

只修改测试夹具可配置期限（默认10秒不变），该单个取消用例设2分钟，保留11秒延迟和全部原断言，新增停止必须APPLIED。生产代码、业务期限及门禁不变。修复后统一All退出0，34 suites / 196 tests，失败/错误/跳过0；日志tmp/f2-g23-cancel-green-all.log。独立增量检查无阻断。此次纯测试修复未重跑IdeSmoke；先前同生产代码本机IDE PASS证据仍独立保留。

修复继续推送同一PR #20，待最新head validation/scope成功后合并并fetch核验；保留dev-manager，不新增PR或开展G4–G6。
