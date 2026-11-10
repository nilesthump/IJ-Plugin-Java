# 历史/搜索右键重命名复测

2026-10-28，dev-manager；本地验证通过，用户已确认“重命名成功”，未提交/推送/PR/合并。

## 人工反馈

用户报告前述两项测试通过，右键重命名按钮灰色不能点击。前述定位/消歧反馈保留为用户通过，不推定整体F2验收完成。重命名已于2026-10-28由用户确认成功，旧失败记录保留。

## 原因与修复

Swing全局右键处理先显示JPopupMenu，后调用列表MouseListener。旧代码在后者才选择行，菜单先冻结空目标并禁用；随后选择也不能补回菜单目标。现由JList.getPopupLocation在显示前命中行，空白处清空；菜单显示时冻结目标，流式刷新/选择变更不重定向改名。父弹窗关闭规则、持久化和手动标题优先逻辑保持。

## 最短复测

1. 打开历史，不先左键选中，直接右键某个会话：重命名应可点击；点击弹出输入框，输入“重命名测试”并确认，列表显示新名称。
2. 打开搜索，输入“重命名测试”，右键结果并再次改名；确认正常弹框和改名。关闭/重开工程后名称保留。

不需重新生成候选或重跑已经通过的两项定位测试。

## 验证

统一All红测 tmp/rename-menu-red.log：222项中仅新增菜单回归失败，期望会话ID但菜单显示disabled，其余221项通过。回归真实dispatchEvent→Swing全局菜单处理→生产菜单可见回调→doClick→RenameSession；仅以记录型show替代窗口放置、注入输入框结果，覆盖历史/搜索首次无选择、切换行、空白禁用、刷新后目标固定。

最终统一All+IdeSmoke退出0，日志tmp/rename-menu-green.log：222 tests / 35 suites，失败/错误/跳过0；check、FixtureSmoke、buildPlugin通过。本机IU-262.10968.63/JBR25.0.4 headless运行PASS（2026-10-28T05:48:20.905575600Z）。两路独立Standards/Spec增量检查无阻断，源码哈希一致，产品ZIP排除IDE smoke/fixtures，45路径scope通过、真实暂存区为空。桌面工具因kernel assets路径错误未能启动，不将无窗口测试当人工可视点击验收。

## 安装包

`H:/IJ-Plugin-Java/build/distributions/IJ-Plugin-Java-0.1.0-dev-f2-rename.zip`

大小：79986418 bytes；SHA-256：`52bcf07b731e464f0a0b726c5f1c13606f3acea90fac14838de27548ac5dcca2`。从磁盘安装后按IDE提示重启，复测上面两步。
