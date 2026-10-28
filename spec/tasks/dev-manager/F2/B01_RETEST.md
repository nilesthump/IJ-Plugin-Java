# B01 失败修复与复测

2026-10-26。B01用户实际结果：FAIL；本轮修复后人工仍待复测，不发PR或合并。

## 现象与已证明的原因

用户MD记录中的实际prompt指向 `spec/tasks/dev-manager/F2/samples/src/demo/Calculator.java`，响应为INCOMPLETE/candidates=[]，没有候选。原文保留在 [黑盒方案](BLACKBOX_TEST_PLAN.md) 和本地忽略证据，未改写为PASS。

真实IDE红测确定性复现：工程有超过256个Java源文件时，给定准确 `a/X.java` 仍先全工程枚举，因此被截断误拒绝。日志 `tmp/f2-b01-red-ide.log`，退出1，断言 `Exact project-relative path must not scan unrelated Java files` 失败。它证明该实现缺陷，不证明用户现场究竟命中文件数还是访问数上限，也未读取用户真实模型工具参数或运行时源码根配置。

修复：含目录的项目相对路径直接查找，再做原项目/canonical/Java源码根及同版本PSI校验；缺失路径不回退同名文件，basename模糊搜索仍有界。非源码根明确OUTSIDE_SOURCE_ROOT并给出操作说明，不扩大文件写权限、不自动改项目配置。

本仓库spec中的样例是供复制的资料，按现有Gradle源码布局不属于产品Java源码根。原黑盒准备步骤要求先放入临时Java工程，本次实际prompt没有使用该准备后的 `src/demo/...` 路径。修复诊断与正确准备都需要；不能保证继续原样请求spec路径就能生成候选。

## 重新安装

新包：`H:\IJ-Plugin-Java\build\distributions\IJ-Plugin-Java-0.1.0-dev-f2-b01.zip`。从磁盘安装后按IDE提示重启，保留H2、聊天和凭据，不需清库。

最终统一All+IdeSmoke退出0：210 tests / 35 suites，失败/错误/跳过0，本机IU-262.10968.63/JBR25.0.4 PASS；日志 `tmp/f2-b01-final.log`，IDE报告2026-10-26T14:47:56Z。

新包 79968796 bytes，SHA256：`2fd8472eb6eccd2bd215ca97297ba4ef822c079d988c645d3e8de05b6f495504`。产品JAR检查无ideSmoke/fixtures/测试/私有文档/数据库条目；前轮f2-g6的哈希不适用于本次产品修复。范围25路径和Repository检查通过，实际暂存区仍为空。

## 成功路径：重新执行B01

1. 新建临时Java工程，如 `F2Blackbox`。把本仓库 [samples/src](samples/src) 下的demo、other两个目录复制到该工程的src下。
2. 确认 `src` 是IDEA的Sources Root，并等待索引完成。打开 `src/demo/Calculator.java`，确认有 `package demo;` 和返回 `a - b` 的 `add(int a,int b)`。不选代码。
3. 在这个临时工程的Course Assistant中新建聊天，启用插件并发送：

```text
请修改 src/demo/Calculator.java 的 demo.Calculator.add(int,int)，将错误的减法改为加法，只改这个方法。请生成可审核的代码修改候选。
```

4. 预期出现待审核候选和“查看Diff”；源码在批准前不变。随后继续原方案B02–B08。仅回复解释、没有候选仍算FAIL，请保留该轮过程中的工具参数/结果和完整错误，不提供key。

## 诊断路径：原spec目录请求

若目标仍在未标为Java源码根的spec目录，新版本预期明确“目标不在Java源码根”及OUTSIDE_SOURCE_ROOT，不再因全工程扫描上限含糊返回INCOMPLETE；不会生成候选或移动文件。这个拒绝只验证边界提示，不能替代上面的B01成功路径验收，也不建议为复测改本仓库源目录配置。

## 自动证据与限制

- 真实IDE红测失败后，修复绿色probe `tmp/f2-b01-green-ide.log` 退出0，报告2026-10-26T14:41:54Z PASS。覆盖大源码树精确路径、Windows相对分隔符、缺失路径不回退、两种绝对路径拒绝、非源码根、遍历拒绝及原有Diff/Undo链路。
- 新核心回归验证OUTSIDE_SOURCE_ROOT的原因透传、零子生成、零候选和原会话正常结束；最终统一结果为210项全部通过。
- 独立Spec/Standards未发现阻断或权限扩大。真实服务未调用，B01仍须用户复测；不承诺模型每次都会选择工具或生成正确补丁。
