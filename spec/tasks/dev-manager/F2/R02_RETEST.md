> 最新：首次选区定位在现场已成功。后续同会话定位失败的本轮方案见 [R02_CONTINUATION_RETEST](R02_CONTINUATION_RETEST.md)，下面保留前次修复证据，不要求重复执行。

# R02选区定位修复与复测

2026-10-28，dev-manager。本地验证通过，等待人工复测；R02原FAIL记录保留，未提交/推送/PR/合并。

## 现场与原因

用户按选区入口发送clamp修改请求，回复INCOMPLETE并要求项目相对路径。只读保存轮次确认有单个冻结SELECTION，捕获文件和版本完整；模型把其中file://H:/...转换为H:/...传给resolveEditTarget。定位器先拒绝冒号/前导斜杠，后续过滤也只认可relative/basename，导致有效绑定选区被拒。该选区还比PSI方法多了3字符前导缩进，严格包含检查会再返回NOT_FOUND。

## 修复

- 优先读取绑定选区并校验原项目canonical归属。模型路径只与该选区原VFS文件比较，同一文件的本机绝对路径、反斜杠或file URL表示转成原项目相对路径；不会按该提示重新搜索或选另一文件。
- 方法归属判断只裁去选区两端Character.isWhitespace。冻结文本、文档版本、原坐标和allowedRanges保持，不扩大为整个方法；相邻符号/跨方法内容/全空白不匹配。
- 无选区文字入口仍使用项目相对路径；冲突文件、项目外目标、过期选区仍拒绝。工具说明提示模型在用户未明确给路径时留空，不复制fileRef；正确性不依赖模型遵循这一提示。

## 最短复测

1. 安装修复包并重启IDE后，沿用当前工程，打开 `src/main/java/demo/Calculator.java`。已有 `// snapshot-test` 注释不用重复添加。
2. 在新草稿中重新采集整个clamp方法，允许一起选择行首缩进和行末换行；旧失败轮次保持原样，不复用过期代码条。
3. 发送原prompt：

```text
将选中的 clamp(int) 方法改为把 value 限制在 0 到 100 之间。只改该方法，不改变签名，并保留现有注释。生成可审核的代码修改候选。
```

预期：不要求手填路径，能定位当前选区并生成待审核候选；保留注释。随后继续 [剩余黑测R02](BLACKBOX_REMAINING.md) 的首次Diff计时、关闭后留草稿、重开并拒绝部分。其他已通过项不重跑。

## 验证证据

统一All+IdeSmoke红测 `tmp/r02-selection-red.log`：222项JUnit通过，真实IDE的整方法选区分别返回 `NOT_FOUND / INCOMPLETE / INCOMPLETE`（相对/绝对/URL），准确复现两处缺陷。

最终统一All+IdeSmoke退出0，日志 `tmp/r02-selection-green.log`：222 tests / 35 suites，失败/错误/跳过0；check、FixtureSmoke、buildPlugin通过。本机IU-262.10968.63 / JBR25.0.4 headless运行PASS，报告2026-10-28T06:41:46.783307500Z。两路独立Standards/Spec检查无阻断，源码哈希复验一致，产品ZIP排除IDE smoke/fixtures。IDE定位用例覆盖路径别名、原允许范围、跨边界/全空白/冲突目标/项目外/STALE；完整链路采用真实F0捕获带缩进和注释的整方法，合成父模型发送绝对路径，再走实际候选/原生Diff/拒绝。模型合成且不调用付费服务；人工复测独立记录。

## 安装包

`H:/IJ-Plugin-Java/build/distributions/IJ-Plugin-Java-0.1.0-dev-f2-selection.zip`

大小79986990 bytes；SHA-256：`9e993e931bce04f2ddc3dfdf732aed9fec82cf842d71a644140ffbc9850e414b`。该包包含先前已通过的重命名修复，不要求重跑其验收。
