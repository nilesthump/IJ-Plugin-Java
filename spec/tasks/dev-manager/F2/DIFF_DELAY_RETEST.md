# 首次 Diff 延迟修复与复测

2026-10-27，dev-manager。状态：LOCAL_VERIFIED / AWAITING_USER；尚未用户验收，无提交/推送/PR/合并。

## 发现与修复

用户报告首次点击“查看 Diff”后窗口一两分钟才出现，出现后再次点击无长等待。

真实 SessionCoordinator + ReactEngine + H2 回归复现：40个连续输出片段在首次预览前造成42次会话刷新；每次都会重新读取持久化会话/历史/候选。模型生成完成或候选已经可见，不意味着旧刷新队列已排空。

现在每个运行最多保留一个尚未开始的流式刷新；刷新执行期间的新片段可排入一个后继刷新，避免遗漏最新内容。合并的是视图通知，不改变每帧正文、存储、终态、取消或审核身份。预览仍显示只读快照，同意按钮的身份与漂移校验保持。

该回归证明一个真实积压缺陷；未采到用户长等待期间的线程栈，不能声称已证明现场唯一原因。IDE测试测量从预览命令到首次原生 SnapshotDiff 面板就绪的耗时（15秒上限）。统一测试为headless，不能证明可见窗口弹出时间。尝试生产Dialog的失败原因已用本机平台字节码确认：HeadlessDialog.show()直接调用wrapper.close(0)，因此审核按钮不会启用；恢复面板测试，不修改产品逻辑绕开这一测试限制。

## 验证记录

- `tmp/diff-delay-red.log`：首次测试代码类型编译错误，已修正；不算产品复现。
- `tmp/diff-delay-red2.log` / `tmp/diff-delay-red.xml`：215 tests中1项失败，明确报告首次预览前42次刷新，其余214项通过。
- `tmp/diff-delay-green.log`：修复后215项测试通过；尝试真实Dialog的headless IDE测试因上述自动关闭而失败，600ms仅为初始化观测，不是桌面弹窗实测。
- 最终 `tmp/diff-delay-final.log`：统一 All + IdeSmoke 退出0，215 tests / 35 suites，失败/错误/跳过0；本机IU-262.10968.63/JBR25.0.4 PASS（2026-10-27T06:05:16.688311500Z），首次原生面板就绪718ms。实际可见窗口首次速度待人工确认。
- 两路独立增量检查无阻断；源码哈希与验证输入一致，产品ZIP包含refreshQueued且不含IDE smoke/fixtures；临时索引scope通过38路径，真实暂存区未改。
- 独立 Spec 检查：无阻断，要求保留现场根因和人工验收边界；Standards检查也无阻断，检查均为只读，不代替运行验证。

## 安装包

从磁盘安装 `H:\IJ-Plugin-Java\build\distributions\IJ-Plugin-Java-0.1.0-dev-f2-diff.zip` 后按IDE提示重启，复用当前配置。大小79978245 bytes，SHA256 `ff735793a31698f0ee452b729fb865a00c6c2d80dee0885df1560b0500a6883e`。包含前轮完成按钮和自动状态核验修复。

## 用户复测

安装本轮新包并重启IDE后，在原来的测试工程生成一个新候选。候选出现后立即首次点击“查看 Diff”，记录从点击到窗口出现是否仍需长等待；关闭后再打开同一候选作对比。预期首次窗口及时出现、无一分钟级等待，仍显示两侧只读快照和同意/拒绝按钮。

此复测不要求额外手动核对编辑器、保存或Undo状态；代码继续自动观察这些事实。不需要通过重复生成来打开已有候选。

源码根原因见 [SOURCE_ROOT_ANALYSIS](SOURCE_ROOT_ANALYSIS.md)。完整功能黑盒仍见 [BLACKBOX_TEST_PLAN](BLACKBOX_TEST_PLAN.md)；本轮结果不能代替未执行的人工场景。用户整体通过后才统一PR合并。
