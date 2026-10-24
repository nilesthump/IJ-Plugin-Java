# Gradle 测试沙箱类加载诊断

- 日期：2026-10-23
- 分支 / HEAD：`dev-a` / `9cf52caeb0276367aab9d5f467da0f9c6bb9a04d`
- 对照基线：`origin/main` 同为 `9cf52caeb0276367aab9d5f467da0f9c6bb9a04d`
- Gradle：Wrapper 9.1.0；IntelliJ Platform Gradle Plugin 2.19.0
- 测试 JVM：IDEA 2026.2.3 自带 JBR 25.0.4

## 复现结果

以下两条命令都在 `:compileTestJava`、`:instrumentTestCode` 和 `:prepareTestSandbox` 完成后，于 `:test` 失败，退出码均为 1：

```powershell
.\gradlew.bat --no-daemon --console=plain --stacktrace --info --gradle-user-home "C:\Users\Lenovo\.gradle" test --tests "edu.courseassist.editor.SourceRangesTest"
.\gradlew.bat --no-daemon --console=plain --stacktrace --info --gradle-user-home "C:\Users\Lenovo\.gradle" test --tests "edu.courseassist.core.TaskScopeTest"
```

任务新增测试的最终原因链是：

```text
Could not execute test class 'edu.courseassist.editor.SourceRangesTest'.
Caused by: java.lang.ClassNotFoundException: edu.courseassist.editor.SourceRangesTest
    at com.intellij.util.lang.UrlClassLoader.findClass(UrlClassLoader.java:268)
```

未修改的基线测试得到同构原因链，类名替换为 `edu.courseassist.core.TaskScopeTest`。两份完整异常分别保存在 `source-ranges-test-result.xml` 和 `task-scope-test-result.xml`。带 `--info --stacktrace` 的原始控制台输出保存在同目录的本地 `.log` 文件中；仓库规则忽略 `.log`，因此它们不作为提交证据。

## 编译产物

两个测试类均存在于普通编译目录与 IntelliJ 插桩目录。`compiled-test-classes.txt` 记录路径、长度、时间和 SHA-256；普通与插桩副本的哈希相同：

| 测试类 | 大小 | SHA-256 |
| --- | ---: | --- |
| `SourceRangesTest` | 2752 | `8BEC8B7791963EF7D1FC70A62636D546E8627EAA9FDC0FA313521FF09E3F2EA5` |
| `TaskScopeTest` | 6872 | `00EEE6D35D3EA43E56124A04C9AD6C44A09A35626D7562BEE832E7B56031F28B` |

`javap -classpath build\instrumented\instrumentTestCode` 能解析两个类，退出码 0，见 `javap-instrumented-tests.txt`。因此不是测试源码未编译、类文件缺失或类文件不可读。

## 实际运行时 classpath

两次 `--info` 输出记录了 Gradle Test Executor 使用的 `@gradle-worker-classpath...txt`。原始参数文件已分别保存为 `source-ranges-worker-classpath.txt` 和 `task-scope-worker-classpath.txt`。

- 两份文件 SHA-256 都是 `15BC383FDF8C40CE0C7C8B7987E3AF0FADD776AF490C767582EE62E63A55B7A5`，说明两次运行使用完全相同的 classpath。
- 每份包含 1741 个条目。
- 首项是 Gradle worker JAR，第二项是 `build\instrumented\instrumentTestCode`。
- 按 UTF-8 读取后，第二项包含正确的中文工作区路径且 `Test-Path` 为 `True`。控制台按系统代码页读取时显示的乱码不是参数文件内容损坏。

## 结论与边界

当前证据把故障定位到 IntelliJ 测试 JVM 的类加载阶段：测试类已经生成、插桩输出已经进入实际 worker classpath，普通 `javap` 可以读取，但由 `com.intellij.util.lang.UrlClassLoader` 作为系统类加载器运行的 Gradle Test Executor 对新增测试和既有基线测试均报 `ClassNotFoundException`。

这证明故障不是 `SourceRangesTest` 独有，也不是缺少测试编译产物；尚未证明是 IntelliJ Platform Gradle Plugin 2.19.0、IDEA 2026.2.3/JBR 25、Windows 中文路径，或三者组合中的哪一项。修复需要在共享构建配置或不同路径的对照环境中继续验证。
