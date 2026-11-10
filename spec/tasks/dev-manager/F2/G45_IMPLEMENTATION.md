# F2 G4/G5：本地审核、准确应用与 Undo

日期：2026-10-26。唯一负责人：用户本人；既有 dev-manager，基线 main `4de5417ef81cd3e409c5721b1eb0a8d508ff3443`。用户授权下两组统一一个 PR 提交并合并。G2/G3 已经 PR #20 合入核验，旧待合入段落属于历史。本组最终本地209项/35套件及IDE已通过，尚待远端门禁与合入核验，不提前标记 main DONE；G6 不展开，架构未冻结。

## 实际行为

- 时间线增量呈现目标文件、候选审核和应用状态。原生 `SimpleDiffViewer` 两侧使用独立只读 Document，强制并排；宿主确认实际 viewer、左右文档与哈希一致后才签发准确 `PreviewToken`，启用“同意并应用”“拒绝”。窗口关闭不作决定；拒绝和批准不经过发送、重试、模型或摘要入口，不改 draft 原文、代码位置或历史 turn。
- token 绑定 proposal/revision/beforeHash/afterHash、当前项目实例与本次验证的 Document stamp。批准前再次验证，IDE 写命令内再复验稳定 VFS 身份、项目源码归属、相对路径、可写性、stamp 和完整 before。只应用耐久候选的完整 after，不重生成、不模糊合并、不格式化、不额外保存。方法范围已经由 G3 的严格替换构造器约束。
- 审核、本地 APPLY 与生成各有边界。重复命令或同候选的新 commandId 返回原 applyId/结果，不再申请槽或写入；新应用 busy 不排队，只有 ON 的当前 epoch 可以开始。意图和 APPLYING 在 IDE 写入前保存；命名 IDE 命令支持一次 Undo/Redo。
- 应用事实、Document 当前观察及磁盘保存分别记录。`APPLIED_TO_DOCUMENT` 是历史执行事实；`DocumentState` 区分 APPLIED/UNDONE/CHANGED/UNKNOWN，磁盘 UNKNOWN/UNSAVED/SAVED 单独展示并标明上次核对。正常 IDE 保存/用户 Undo 不被隐式重放覆盖；时间线可显式本地核对。
- H2 与 Document 不伪装原子。前置拒绝明确未应用；写入后异常或缺少执行证据保留未知。回执保存失败保存进程内确切执行观察并阻塞，恢复只补记；即使停用已释放旧槽，也按当前 LIVE 根、原 request 和准确 receipt 补记，不重复编辑。崩溃后缺少执行证据的未知结果保持阻塞，不能仅凭文件文字匹配推定本次应用；用户可回看或删除该聊天。
- 重新打开项目不恢复批准、网络或编辑。旧候选仍可回看；只有真实目标完整 before/身份重新验证、重新展示 Diff 后，才能在当前实例/stamp 下重新审核。已成功的耐久回执可由新 host 做只读核对，不能因此获得旧请求写权限。
- 停用/关闭/删除撤销未完成预览和写入许可。最终许可与真正写入以同一内存互斥裁决，提交前取消零修改，提交已发生则保留真实结果。所有归档 viewer 均纳入释放，包括拒绝态回看；迟到预览不复活审核或删除后的诊断。删除清 F2 私有数据/进程缓存，不回滚已经修改的用户源文件。
- 后续模型/摘要仍只在新显式 draft 提交时读取本地审核/应用事实与资格指纹；不回写终态 ReAct Snapshot，不恢复失败候选全文或协议资格。

## 验证与修复记录

统一入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1 -Scope All -IdeSmoke -LocalIdePath 'H:\IntelliJ IDEA 2026.1.1' -GradleUserHome 'H:\IJ-Plugin-Java\tmp\gradle-user-home'
```

日志按真实批次保留，路径前缀统一为 `tmp/f2-g45-`：baseline、first、second、third、fourth、fifth、final、delivery、delivery-final（后缀 `.log`）。基线 All 成功但测试 UP-TO-DATE，不宣称重执行；第一轮 All 成功。第二轮因前轮尚运行而复制测试 sandbox 的映射中 JAR 失败，此后改为串行。第三轮 202 项 JUnit 与本机 IDE 通过；其后新增恢复与迟到回调检查。第四轮暴露重新审核可能提升“父成功但候选工具失败”的旧候选，已在共用准入检查补回实际成功工具来源，保留原失败断言。第五轮 208 项 JUnit 通过，IDE 测试清理代码使用本机 SDK 不存在的 closeAllFiles 导致编译失败，改为 getOpenFiles/closeFile 后 final 与 delivery 批次 All+IDE 通过。最后新增历史终态目标与新回执恢复隔离检查；最终结论只采用 delivery-final，结果见 STATUS/HANDOFF。

新增 `EditReviewApplyTest` 覆盖：两按钮零网络/draft不变，重复/关闭/拒绝，漂移/意图失败，取消/删除提交前及提交后事实，回执失败经用户可见核对按钮恢复、停用释放槽后的补记，未知不重放，busy，stamp变化必须重开Diff，新实例重新审核及重复，迟到预览成功/失败隔离，拒绝归档viewer释放，以及旧终态文件不可访问不阻塞新回执补记。

`F2ReviewApplyChecks` 使用本机真实项目/源码根、PSI、脏 Document、H2 和内置双侧只读 viewer，校验预览前后完整文字/stamp/磁盘不变、原生同意后紧接拒绝不产生第二决定、应用确切 after、命名 Undo/Redo、Undo 后重复 applyId 不重写、正常 IDE 保存及原磁盘换行符、只读 Document 的准确拒绝原因、撤销许可、提交后 StopOperation 不抹掉应用事实、漂移/重命名拒绝、新实例读取耐久回执。模型/嵌入请求计数均为0。确定性并发交错另由核心/H2夹具覆盖，不冒充人工窗口竞争。headless UI 环境输出过平台外观警告，实际断言与最终 result.json 状态分别保留。

两路独立 Standards/Spec 检查发现的恢复去重、归档/迟到viewer释放、只读跨实例观察及回执补记问题均落实修复和回归。最后入口增量的独立检查另发现旧终态目标影响恢复的问题，已收窄为仅核对未决项并增加回归；最终独立增量复核无遗留。一次代理补查因服务403未完成，已用新的独立检查补齐，不算检查通过。

## 未执行边界

人工视觉、用户原生窗口/输入法体验、真实服务与付费 API 均 NOT_RUN。既有 F0 自动回归继续执行，不替代用户此前已完成的验收或本轮人工 F2 验收。新建/删除/移动文件、多文件、逐块批准、代码执行/自动测试、服务进程不在范围。G6 仍 NOT_STARTED。
