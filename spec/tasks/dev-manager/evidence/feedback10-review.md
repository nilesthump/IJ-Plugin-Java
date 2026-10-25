# 第十轮独立检查

2026-10-25，dev-manager / HEAD 5ec16c4；用户原文见本地user-turn-069。

Spec：0。实际component叶与继承CAPTURE的IME预编辑文字区分，文本读取/offset/order/caret/删除保护一致；原文不转换。

Standards：0。未增加线程切换、公共协议或强制分行；所有身份读取统一captureAt。两路均只读，未重复构建。

红测tmp/feedback10-red.log复现同一行IME：组合文字真实存在于文档，但被getText误排除。诊断探针tmp/feedback10-probe.log/XML仅合成文字，已从测试源码移除。本机JDK DefaultStyledDocument.insertUpdate明确将前run属性合入composition并命名content。修复后真实Swing InputMethodEvent、实际控制器/H2自动保存回执、两个代码条之间输入、取消、正常换行和保护代码条回归通过；最终All+IdeSmoke见feedback10-validation.json。

尚未实际驱动Windows输入法候选窗，用户人工IJ复测NOT_RUN。无提交/PR。
