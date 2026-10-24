# 第五组白盒联调结果

2026-10-24；基线 `fb425c9`（已包含成员 PR #8/#9/#10）；分支 dev-manager。最终统一 All + IdeSmoke 退出 **0**，**19 suites / 100 tests / 0 failures / 0 errors / 0 skipped**；另有 FixtureSmoke 与真实本机 IDEA 命令断言。没有远端付费模型调用。

准确命令、逐 suite 数量、安装包大小/哈希见 [验证 JSON](evidence/work-group-5-validation.json)，本机 IU-262.10968.63 / JBR25.0.4 运行清单见 [运行 JSON](evidence/work-group-5-runtime.json)。原始日志在本地 `tmp/manager-task5/final-validation.log`。

| 覆盖链路/不变量 | 实际检查与证据边界 |
| --- | --- |
| 注册与生产接线 | 真正加载发布插件；唯一 PdfBoxPdfReader / JavaCodeCapturePort；注册 CaptureEntryAction；真实 Tool Window 绑定生产控制器 |
| 首次右键/无会话 | 隔离 IDEA 打开 Java 项目，未准备绑定时动作引导身份选择且不排队；随后真实注册动作 → Java PSI → 核心 ticket → H2 READY 捕获，断言选区/完整方法/类字段且排除无关方法正文 |
| 真实界面与耐久草稿 | EDT 创建真实 CourseAssistantView；核心创建会话，点击界面保存草稿，检查 H2 状态；关闭/重开真实窗口恢复原文。命名弹窗没有人工执行，不宣称弹窗通过 |
| PDF → H2 → JVector → 固定页证据 | 注册解析器读取真实生成的三页 PDF（中间空页），查询命中物理页 3；删除用户选择源仍能打开托管文件；重开仅恢复本地耐久向量；旧 lease 在移除后保留固定来源 |
| 资料发布与并发 | CourseLibraryTest / LifecycleTest 覆盖 MD5、source/blob 分离、空间不兼容、CAS、固定 attempt、恢复等待/取消及旧版本清理；不是千页性能/召回结论 |
| Spring AI/有限 ReAct | DependencyRuntimeTest、SpringAiCourseModelTest、ReactEngineTest：本地 loopback HTTP/SSE、一次订阅、工具聚合配对、真实结束/断流、固定证据与分支故障隔离；不是真实供应商协议证明 |
| 提交与代码 | SessionCoordinatorTest：提交去重在 busy/revision 前、草稿只消费确切版本、捕获原目标/迟到移除、typed 原文、标签关闭不停止与重开不重复调用 |
| 中断输入排除 | ReactEngineTest / ContextPlannerTest / SessionFailureTest：停止/失效/断流生成保留档案，但不进入未来输入/摘要/checkpoint；取消激活竞争、保存失败阻塞与恢复 |
| 项目和删除 | 本机目录移动/复制身份；原会话恢复、删除全清；ProjectAssociationTest 验证候选不含占用项目、显式关联恢复原草稿且零模型调用 |
| 成员逻辑回归 | editor 3、PDF 12、UI 6、Markdown 2 项测试均通过；真实 IDE 多色高亮资源能构造/释放，人工配色视觉质量仍待用户 |
| 测试源码隔离 | ideSmoke 已迁到独立 `src/ideSmoke/java`；JUnit 仅在 test 依赖。test 复用已编译 probe 输出，不共享 Java 源根；全量构建通过。VS Code/JDT 手工重新导入后的诊断面板未执行 |
| 发布 ZIP | 79,788,041 字节，60 条目；实际检查不含 JUnit、fixtures、smoke jar 或 smoke/test class。具体 SHA-256 见验证 JSON |

## 失败与修复记录

- 初始普通执行工具无法启动，随后获得提升执行的读写通道；没有改变仓库 hooks。
- 普通 Git fetch 先遇证书错误，Windows 证书库直连遇网络失败。使用系统既有代理 127.0.0.1:7897 和 schannel 的单次 Git 配置后成功；未关闭 TLS 验证、未修改全局代理。
- 开工 All 验证通过，日志 `baseline-validation.log`。
- `integration-validation-1.log`：All 通过，IDE 检查因 headless 环境点击命名弹窗抛 HeadlessException 而失败；测试改为核心真实创建会话后检查非弹窗界面操作，未修改产品行为来掩盖问题。
- `integration-validation-2.log`：All + IdeSmoke 通过。独立检查指出首次右键未初始化、界面构造异常无提示及重试语义问题；已在公共装配修复并独立复核闭合。
- 最终 `final-validation.log`：修正后 All + IdeSmoke 退出 0。IDE 安装自身的 Ultimate/服务 override 诊断和 JVector Vector API 性能提示仍保留；不把其他插件的警告描述为所有插件兼容通过。

## 未执行

[人工黑盒方案](BLACKBOX_TEST_PLAN.md)的 47 项均 NOT_RUN；真实服务认证、生成/嵌入/摘要语义、人工安装/交互/配色/PDF阅读器跳页、真实规模性能/召回均待用户。最终英文报告/演示 PDF 是另行交付材料，不属于本轮黑盒 Markdown，也未制作。架构未冻结。
