# 目标平台与 PDF 追溯核查

查证日期：2026-10-15（Asia/Shanghai）。本记录为一手资料核查与工程推断，不是用户选择的版本清单，也不是运行验证报告。

## 目标 IDEA

用户报告 Windows、构建号 262.9437.185。JetBrains 官方发布索引的可检索条目将该完整构建号对应为 IntelliJ IDEA 2026.2.1。该索引为动态页面，直接正文读取为空，本次依据搜索引擎对官方索引条目的抓取，未读取用户本机安装信息。[官方 2026.2 发布索引](https://youtrack.jetbrains.com/projects/RSRP/articles/IDEA-A-2100662674/IntelliJ-IDEA-2026.2-Latest-Builds)

JetBrains 的 2026 专项兼容性文档明确 2026.2+ 使用 Java 25；官方发布前公告也确认这一点。[专项兼容性文档](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html)、[JetBrains 公告](https://platform.jetbrains.com/t/2026-2-is-coming-time-to-check-your-plugin-compatibility/4618)

采用此平台开发时须按 Java 25 基线规划；用户实际 JBR 补丁版本、CPU 架构尚未检查。插件开发 JDK 与用户被辅助的 Java 工程 JDK 分开记录。

来源注意：Build Number Ranges 总表当前把 2026.2 的 branch 列成 261，与该页编号规则、官方公告的 262.* 及完整构建发布记录不一致。本次采用专项文档及完整发布记录，不复制该单元格。[总表](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html)

## Spring AI 内嵌的证据范围

Spring AI v2.0.1 的固定版本 POM 将 Java 字节码目标设为 17，核心非 starter 模块约束避免 Boot 依赖；对应文档支持手动装配。它是可供评估的版本样本，不代表项目已选定 v2.0.1。[固定 POM](https://github.com/spring-projects/spring-ai/blob/v2.0.1/pom.xml)、[固定版 ChatClient 文档](https://github.com/spring-projects/spring-ai/blob/v2.0.1/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chatclient.adoc)

工程推断：仅从字节码版本看，没有 Java 17 字节码高于 Java 25 运行时的障碍。完整依赖、反射、资源发现和插件类加载仍须实测。官方机制支持内嵌的讨论方向，不能据此标记兼容性已经通过。[插件类加载](https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html)

## PDF 分页与切块

核对 Spring AI v2.0.1 的 PagePdfDocumentReader：默认每个输出 Document 对应一个物理页，元数据含 page_number；多页组才有 end_page_number；可获取时有 file_name。无文字页可能不产生输出文本对象，因此不能按结果列表顺序重新编号。该类持有 PDFBox 的 PDDocument；集成时还需明确关闭及释放责任。[固定版 reader 实现](https://raw.githubusercontent.com/spring-projects/spring-ai/v2.0.1/document-readers/spring-ai-pdf-document-reader/src/main/java/org/springframework/ai/reader/pdf/PagePdfDocumentReader.java)

同版本 TextSplitter 将非空父 metadata 复制给子块，并补充父文档 ID、块序号和块总数；它没有重新定位子块的页码。先合并多页再切块，会使子块继承父组范围，而非自动获得精确页码。[固定版 TextSplitter 实现](https://raw.githubusercontent.com/spring-projects/spring-ai/v2.0.1/spring-ai-commons/src/main/java/org/springframework/ai/transformer/splitter/TextSplitter.java)

工程建议：优先讨论按物理页提取后在页内分块，使用业务维护的 documentId、源文件指纹、处理配置版本、chunkId 和物理页码建立追溯链。默认随机 Document ID 不能充当重复导入时稳定的业务标识。[固定版 Document 实现](https://raw.githubusercontent.com/spring-projects/spring-ai/v2.0.1/spring-ai-commons/src/main/java/org/springframework/ai/document/Document.java)

PDF 第几页、PDF page label 与页面上印刷的页码可能不同；page label 也不能未经核验等同于印刷页码。引用展示优先讨论“资料名，PDF 第 N 页”，印刷页码映射单独处理。[PDFBox page labels](https://raw.githubusercontent.com/apache/pdfbox/3.0/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/common/PDPageLabels.java)

可复制文字不保证代码缩进、表格和阅读顺序正确；需要真实课程样本验证提取效果。PDFBox 文档要求关闭文档，实施验证应包括反复导入、取消后文件及内存资源能否释放。[PDFBox FAQ](https://pdfbox.apache.org/3.0/faq.html#text-extraction)

## 后续最小运行验证（尚未执行）

1. 在用户目标 Windows 构建及实际 JBR 下验证插件归档与二进制兼容；Plugin Verifier 只覆盖其能检查的兼容范围，不替代业务运行。
2. 验证无 Boot 手动装配的 Spring AI 模块可加载、请求可取消，网络和索引工作不阻塞界面。
3. 用真实课程 PDF 核对文字、代码及物理页码；重复导入和失败后能释放 PDF/网络资源。
4. 使用已确认能力的远程嵌入服务完成分块向量化与问题向量化，保持模型和维度一致，再走检索、生成、引用显示闭环。
5. 检查索引更新失败保留旧代次，项目关闭不继续回写 UI；新环境满足只安装插件并配置资料与 API 的目标体验。

检查工具依据：[Plugin Verifier](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)、[线程模型](https://plugins.jetbrains.com/docs/intellij/threading-model.html)。本轮未构建或启动插件，也未发送模型 API 请求。
