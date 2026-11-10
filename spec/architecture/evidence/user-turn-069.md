# 用户反馈原文（2026-10-25）

> 发生如图错误
> 输入中文、选择候选字时出现乱序或拼音残留
> 关掉代码块后恢复
> 换行输入也无乱序

截图为draft代码条后同一行拼音残留，状态“草稿已接收，等待新状态确认”。这是截图描述，不补造用户原文。

代理定位：真实Swing InputMethodEvent本地合成复现，原始文档含U+FFFC+jie但getText返回空；DefaultStyledDocument.insertUpdate把前一run属性合入组合文字并将叶类型设为content，旧逻辑仅按CAPTURE属性识别而误挡组合文字删除。仅真实component叶具备代码捕获身份，原始代码/文字和混排顺序规则不变。不强制换行，无PR授权。
