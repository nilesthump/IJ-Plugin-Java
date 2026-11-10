# F2 G6 本地回归与人工交付

日期：2026-10-26。负责人：用户本人；既有 dev-manager / HEAD `9ac4448ec649890646979215fa35601314168761`。G4/G5 已由 PR #21 合入并核验：受检 head `053aa37ce06049694f98dd513af45e86d0f0caf4` 的 validation/scope 成功，main 合并提交为上述 HEAD。旧待合入文档属于原时点，不需重做 G4/G5。

本轮用户要求最后任务与黑盒方案，人工通过后再统一 PR 合并。规划 G0–G6 实际仅余 G6，本轮做 G5 收尾补记与 G6 回归准备，不虚构新组、不新增功能或分支。G6 本地检查完成不等于人工验收或 main 交付完成。架构未冻结。

## 实际增量

- 新增 `src/ideSmoke/java/edu/courseassist/bootstrap/ide/F2EndToEndChecks.java`，接入统一 IDE smoke 和 JSON 检查清单。两入口均通过实际 SessionCoordinator / ReactEngine / CodeEditTools / H2；文字定位用真实项目源码根/PSI，选区走真实 JavaCodeCapturePort，模型是可控本地合成响应。
- 同一条链路验证真实未保存完整 before、严格方法/选区替换、唯一子生成、耐久 REVIEWABLE、真实原生只读 Diff、实际按钮应用/拒绝、草稿保留、零新增模型/嵌入、无额外 turn、准确 Document 写入、一次 Undo、核对回执、显式新 draft 携带最新 REJECTED/UNDONE 事实。生成预约后切换到另一聊天并写新草稿，证明结果仍归原会话。
- 不修改生产实现、契约、依赖、hooks 或权限。新增 [32 项黑盒方案](BLACKBOX_TEST_PLAN.md)及两份可复制 Java 样例，覆盖重名/重载、版本漂移、审核、Undo/保存、生命周期及F0回归。真实服务候选质量由用户实际验收。

## 验证

统一命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'
```

本机 product-info 实读 IDEA 2026.2.3 / IU-262.10968.63，JBR 25.0.4。基线 `tmp/f2-g6-baseline.log` 退出0；基线JUnit UP-TO-DATE，不算重执行。首次增量 `tmp/f2-g6-first.log` 退出0，209项/35套件，失败/错误/跳过0；实际 IDE PASS，包含新增双入口链路。最终固定版本复核 `tmp/f2-g6-final.log` 退出0；209项/35套件实际重执行，失败/错误/跳过0，IDE报告2026-10-26T14:08:14Z为PASS且包含新增完整链路条目。

独立 Spec 检查指出原“生成→候选”与“手工stage→应用”之间缺完整链路，现已补齐。独立 Standards 检查指出新增夹具的合成instance不能用于真实编辑器采集，以及Swing观察需EDT；已改为真实项目服务instance和EDT helper。插入会话隔离断言时的中间文本语法错误已在编译前修正。首次All阶段已编译的旧夹具未执行该新增IDE链路；随后同一统一入口的 runIde 阶段实际重编译了修正版（源码22:01:11、class22:02:25），报告22:02:59含新检查并PASS。没有把潜在夹具问题伪报为实际失败；各轮日志均保留。

JUnit运行约6分钟时读取线程栈 `tmp/f2-g6-first-thread-dump.txt`，显示既有Swing用例在doClick执行；随后正常完成，未判定死锁、未停止进程或跳过测试。保留既有预期跨方法拒绝异常和IDE外观警告，实际通过以退出码及result.json共同判断。

## 安装包

人工安装路径：`H:\IJ-Plugin-Java\build\distributions\IJ-Plugin-Java-0.1.0-dev-f2-g6.zip`（最终验证结束后已复制并核对同一SHA256）。生产代码未变化，产物与已验证G4/G5内容相同。

SHA256：`17defe73bb9977fd576e16a4fcba64e1ae8adc86aeb3311051b397189b144772`，79,967,508 bytes。ZIP检查：60条目，产品JAR340条目、9个F2适配/视图类；无ideSmoke/fixtures/TestEnvironment/测试类/私有文档/数据库。构建目录继续Git忽略，不提交ZIP。

## 准确限制

- 人工 B01–B32、真实服务协议/候选语义质量、真实用户窗口和输入法体验均 NOT_RUN；自动合成结果不替代人工验收。
- T05采集中写竞争未作确定性调度注入；实际捕获在IDE读锁内固定全文/stamp/PSI，并已验证脏Document、旧选区过期及写前漂移拒绝。不能声称任意时序全部实测。
- T17实际项目关闭瞬间与IDE写提交竞争未作确定性端到端复现；已有核心许可/关闭/删除及真实IDE撤销许可、提交后停止用例通过。新链路清理会关闭真实项目，但不冒充此竞争检查。
- 本轮不提交/推送/创建PR/合并；待用户黑盒通过后再按既有dev-manager→main门禁交付。G6维持本地待验收，不提前记DONE。

最终文档与范围检查：统一Repository退出0（tmp/f2-g6-docs.log）；隔离临时Git索引运行check-scope.ps1 -Staged，18路径通过（tmp/f2-g6-scope.log），实际暂存区保持空。全部变更Markdown本地链接及git diff空白检查通过。
