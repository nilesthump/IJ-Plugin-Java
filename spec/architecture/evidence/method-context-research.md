# 方法与类结构捕获的官方资料核查

日期：2026-10-16。用于工作量和接口边界评估；尚未在目标构建 262.9437.185 执行，未选定实现版本或语言覆盖。

- 官方 [PSI 导航](https://plugins.jetbrains.com/docs/intellij/navigating-psi.html) 展示从 PsiFile 定位元素，再向父元素查找 PsiMethod/PsiClass 的路径。选区首尾与父节点范围需一起验证，不能只凭起点认定整段属于一个方法。
- Java PSI 与普通平台 PSI 有依赖区别；工程需要按拟用构建方式声明 Java 插件依赖，见 [插件依赖](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html)。此处不据此选择具体 Gradle 插件版本或证明目标平台已兼容。
- [线程模型](https://plugins.jetbrains.com/docs/intellij/threading-model.html) 约束模型读取。项目建议在受控短读操作中保证文档/PSI版本一致并复制不可变文本，再释放读锁做保存/网络工作；排队后重新检查有效性，避免使用过期偏移。
- [PsiMethod 源码](https://github.com/JetBrains/intellij-community/blob/master/java/java-psi-api/src/com/intellij/psi/PsiMethod.java)、[PsiClass 源码](https://github.com/JetBrains/intellij-community/blob/master/java/java-psi-api/src/com/intellij/psi/PsiClass.java)、[PsiDocumentManager 源码](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/psi/PsiDocumentManager.java) 可供实施核实。方法体/类名/语法节点可能缺失，类结构需要区分本类成员与继承成员；源码链接是 master，不能替代针对目标 SDK 的验证。

MVP 评估优先在选区所在源文件内取得方法全文及类声明/字段结构，不需要跨文件调用图。无方法、跨边界、字段/初始化块、局部/匿名类、语法不完整和不支持语言须给出明确捕获状态。正常缺失不是捕获异常后伪造空方法，实际解析错误仍交给开发/实际使用边界分别处理。

上述技术路线和异常边界是代理提案；用户确认的正文组合见 user-turn-022.md。
