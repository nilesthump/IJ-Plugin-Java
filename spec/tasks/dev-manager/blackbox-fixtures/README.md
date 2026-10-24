# 人工黑盒合成样例

所有内容均为本任务生成的虚构测试资料，可分发；不是真实课件/API 输出。

三个合法 PDF 各有 3 个物理页，第 2 页空白。第 3 页：A-v1 的 F0-ALPHA 为 17 minutes；A-v2 的 F0-BETA 为 23 minutes；B 的 F0-GAMMA 为 31 minutes。broken.pdf 故意无效。CaptureSample.java 复制到测试项目后选 return 行。

生成器：`scripts/testing/GenerateBlackBoxFixtures.java`。在仓库已完成统一 All 构建后可用 Java25 与发布插件 lib 中的 PDFBox 依赖运行；只生成本目录的合成 PDF，不访问网络。PDFBox 保存时可能生成不同文件 ID，重新生成后字节哈希可能变化。当前交付字节哈希见 fixture-sha256.json。

使用步骤见上级 BLACKBOX_TEST_PLAN.md；不要更改插件托管 PDF 目录。
