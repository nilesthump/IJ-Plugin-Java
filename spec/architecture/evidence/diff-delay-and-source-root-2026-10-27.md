# 用户原文与诊断范围（2026-10-27）

1.点击查看diff要长等待，检查并修复长等待问题

2.找出当前实现F2只能修改Sources Root目录下代码文件的原因

追问答复原文：diff窗口迟迟不出现，一两分钟

代理范围：定位并修复Diff长等待；追溯源码根限制，不将“查原因”解释为授权扩大修改范围。保留此前所有未提交修改，用户整体通过后才统一PR合并。

初步证据：JavaEditTargetPort定位（选区、精确路径、枚举、canonical复查）及JavaEditApplyPort应用均显式调用isInSourceContent。G2/G3实现提交52fd92f及G23_IMPLEMENTATION描述仅查Java source roots；F2已确认R01–R10和G0决定未明确逐字要求IDE Sources Root标记。精确目录是实施选择，未找到用户直接要求该限制的原文，不伪造其理由为用户授权。

Diff诊断候选（尚待证明）：单线程命令队列/数据库事务积压；invokeLater模态状态延迟；原生Diff冷初始化。首次现场线程栈在空闲时取得，不能当复现。自动点击工具node_repl因windows sandbox setup refresh失败不可用，已请用户重新点已有候选以取现场等待栈，不发模型请求。

用户补充原文：当弹出后再次点击不会发生长等待问题

代理分析：该现象同时符合首次原生初始化和生成结束后积压排空；不能据此单独认定任何一个根因。准备对流式刷新队列做真实协调器/H2回归，并将IDE测试升级为实际Dialog打开而非仅创建Diff面板。

代理实施与验证进展：真实协调器/ReactEngine/H2回归旧实现40帧在首次预览前42次刷新（tmp/diff-delay-red2.log，215 tests仅此1项失败）。新增Run级待刷新合并；仅合并投影通知，保留存储、正文、终态和身份检查。生产NativeEditPreviewPort用于真实IDE首次Dialog测试。两路独立Standards/Spec审查均无阻断，指出不能认定现场唯一根因。完整绿色验证进行中。源码根限制本轮不改。

验证更正：tmp/diff-delay-green.log中215项JUnit通过，首次生产Dialog初始化600ms后按钮等待超时。实际平台HeadlessDialog.show()字节码调用wrapper.close(0)，因此该值不证明可见桌面窗口。已恢复原SnapshotDiff面板路径并加入面板首次就绪15秒界限；不改产品Dialog来迎合测试。DIFF_DELAY_RETEST记录失败和人工边界，重新All+IdeSmoke验证。

最终交付：tmp/diff-delay-final.log退出0，215 tests/35 suites零失败错误跳过；本机IDE PASS 2026-10-27T06:05:16.688311500Z。首次SnapshotDiff面板718ms(headless)，可见窗口待用户验证。新包f2-diff.zip SHA256 ff735793a31698f0ee452b729fb865a00c6c2d80dee0885df1560b0500a6883e；无提交/推送/PR/合并。
