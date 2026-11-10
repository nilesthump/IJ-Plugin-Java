# 完成按钮与自动核验复测

日期：2026-10-27。本地实现及最终验证通过，等待人工复测；用户通过前不提交、推送或合并。

## 安装

从磁盘安装 `H:\IJ-Plugin-Java\build\distributions\IJ-Plugin-Java-0.1.0-dev-f2-autostate.zip` 后按IDE提示重启；复用当前配置。包大小79,978,126 bytes，SHA256 `9665a9a9c533076984644ddf3882b8df55ea8545baf6f57d93321af8326761d9`。

## 行为

- 成功生成后，空输入框显示禁用的发送箭头；输入新问题后可发送。正常F0停止/中断仍可继续生成，失败/超限可重试；F2使用过编辑工具后仍要求新draft。
- 批准后自动核验并更新应用事实；后续保存、Undo、Redo、手动修改自动更新。不需要“核对编辑器/保存/Undo”按钮。
- 自动核验只读，不替用户执行保存或Undo，不生成新候选或模型请求。已应用历史与当前内容、磁盘事实仍分别显示。
- 回执保存失败或核验异常时保留“重试状态恢复”；禁止把未知结果说成成功，禁止重放写入。当前目标不可读取时保存未知事实；核验结果保存失败才阻止新发送，正在运行的生成仍可停止；成功重验清除对应错误。

## 最短复测

1. 用普通问题完成一次F0问答：空输入框不再显示“重试”；填入下一问题可正常发送。生成中停止，确认仍有适用的继续入口。
2. 先把临时 Calculator 样例的 add 恢复为 `return a - b;` 并保存，再请求改为加法，查看Diff并同意应用。当前用户Maven样例路径为 `src/main/java/demo/Calculator.java`。确认正常卡片无手动核验按钮，应用状态自动出现。
3. 回到编辑器Undo，再Redo，再正常保存，再加一行注释：聊天自动分别更新撤销、应用、磁盘保存、后续编辑事实，无额外模型回复，不覆盖注释。关闭/重开工程后自动读取已应用回执并核验当前状态。

完整F2验收继续按 [BLACKBOX_TEST_PLAN](BLACKBOX_TEST_PLAN.md)。截图中曾出现候选及应用事实，不等于全量人工通过。

## 验证记录

最终 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath "H:\IntelliJ IDEA 2026.1.1" -GradleUserHome "H:\IJ-Plugin-Java\tmp\gradle-user-home"` 退出0，日志 `tmp/auto-observation-delivery.log`。214 tests/35 suites，失败、错误、跳过均0；本机IU-262.10968.63/JBR25.0.4 PASS，IDE报告2026-10-27T05:14:29.672650100Z。验证真实Undo/Redo、保存、手工修改自动更新，零额外模型请求；包含错误观察恢复、历史目标隔离及UI恢复入口。两路独立检查闭合，源码哈希一致，ZIP不含测试类。没有代理付费调用或人工验收通过声明。

此前完整验证 `tmp/auto-observation-green2.log` 退出0，后续独立检查修正与新增测试须另跑。定向红测 `tmp/auto-observation-red-focused2.log`：2项均失败，分别复现成功态错误按钮及错误RETRY许可。

最终前失败如实保留：auto-observation-final.log 中旧目标隔离及无效UI回执夹具失败；修正夹具与UNKNOWN持久化后定向通过。auto-observation-final2.log 的214项通过，但新增IDE no-op夹具被既有 Unchanged candidate 契约拒绝；删除无效测试前提，保留真实有修改候选Undo/Redo场景后最终通过。以上不是隐藏失败或放宽产品契约。
