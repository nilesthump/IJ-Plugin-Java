# 用户反馈（2026-10-25）

用户原文：
> 尝试代码块 文本 代码块 文本 时发生如下错误

用户附带堆栈摘录（非完整堆栈）：
> java.lang.IllegalArgumentException: Selection crosses a method boundary
> at edu.courseassist.editor.JavaCodeCapturePort.methodContext(JavaCodeCapturePort.java:110)
> at edu.courseassist.editor.JavaCodeCapturePort.extract(JavaCodeCapturePort.java:96)

选区澄清原文：
> 一个方法或方法内部，可能带缩进、换行

代理实施范围：修正选区边缘空白导致的方法/类归属误判，原始SelectionStart不变，真正跨边界仍拒绝。不扩大为跨多个方法捕获。修改已集成editor/JavaCodeCapturePort单文件及负责人真实IDE测试；不覆盖成员任务记录，无并发成员编辑，用户当前修复请求授权该集成修复。未授权PR。

用户后续原文：
> 先检查最新pr有没有这个处理

核对结果：GitHub REST按created desc得到最新PR12（open，head f35ca6ce82f9ae24b19a43f25334c867c146ae7c），连接器fetch_pr和固定head文件验证其JavaCodeCapturePort仍直接使用未去空白端点；diff仅冻结路径和SourceRanges测试及任务文档，不含此修复。连接器search_prs首次漏列12，已用实时REST补核，不据搜索列表断言最新。未合并、未提交评论。
