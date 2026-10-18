# 统一验证契约

日常及 GitHub CI 使用同一入口 `scripts/validate.ps1`，默认 `-Scope All`。环境为 Windows + JDK 25，单 Gradle Groovy 工程，Wrapper 9.1.0，IntelliJ Platform Gradle Plugin 2.19.0。准确结果看 [CURRENT.md](../progress/CURRENT.md)，不能因本文存在而声称通过。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate.ps1
```

## 检查内容

- `check-repository.ps1`：公共工程、任务/治理/API入口、固定分支名单、插件身份、纯 Java 业务源码；不以本地克隆只有部分固定引用误判远端分支消失。
- `check-scope.ps1 -Staged`：提交前检查完整暂存差异，包括重命名的两端；`main` 拒绝日常提交，dev 只能改自己的路径。`-BaseRef <提交或引用> -Branch dev-a` 检查 PR 增量；错误退出非零，不忽略失败。
- 默认 All 调用 `gradlew.bat check buildPlugin`：编译产品与 testFixtures、运行实际新增测试、执行带断言的 `FixtureSmoke`，构建插件 ZIP。空测试包不表示成员功能已验证。
- Git 规则行为：`scripts/git/Test-GitPolicy.ps1` 使用隔离临时仓库验证引用与推送；需要 Git for Windows 自带 sh。它不代表 GitHub 服务端规则已生效。

可选参数 `-LocalIdePath <绝对目录>` 选择本机 SDK；默认/CI下载本机对应的 IDEA 2026.2.3，build 262.10968.63。用户已更正为始终只验证本机版本，不再验证旧目标；本机升级时负责人同步版本与记录。`-GradleUserHome <绝对缓存目录>` 用于隔离构建缓存；`-Scope Repository` 不执行 Java/Gradle/PDF/IDE 验证；`-BaseRef` 与 `-Branch` 可增加差异范围检查。

Wrapper 来自已安装官方 Gradle 的 wrapper 任务；初始化时隔离环境 HEAD 校验失败，生成曾使用 `--no-validate-url`，随后恢复 URL 校验并固定官方 ZIP SHA-256 `a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806`（[官方校验文件](https://services.gradle.org/distributions/gradle-9.1.0-bin.zip.sha256)）。下载和实际 Wrapper 执行结果另看进度。构建失败不能静默切版本、仓库镜像或跳过 check 来报成功。

## CI 信任边界

GitHub required context 为 `validation`。main push 和四个 dev 的 PR 执行同一入口；开发分支只推送时不重复构建。PR 只接受同仓库四个固定 dev 分支并直接目标 main。PR 事件使用 `pull_request_target` 的主分支工作流，先从基线保存范围检查器/策略，再检出待合并提交，以基线策略检查完整差异，避免成员改检查器后给自己放行。

随后编译 PR 代码仅使用 `contents: read`，checkout 不保留凭据，不传 secrets，不使用共享可写缓存；不能后续为此任务加入写权限或机密环境。公开 fork PR 不接受。管理员也按 main 的 PR/CI 规则操作；初始建库提交是一次性引导，不作为日常直推先例。

## 需要另留证据

自动构建不能证明 IDE 运行线程/释放、右键与 PSI 同版本、IDE 多色配色切换、流式生命周期、H2 恢复/硬删除、实际 API 工具协议/嵌入或千页规模召回。成员任务明确哪些场景可纯逻辑验证、哪些需目标 SDK/IDE。未跑写 NOT_RUN；换 build、合成 PDF/状态和真实服务分别标记。报告中的“通过”须能追到命令、退出码与范围。

具体模型、压缩预算和失败数值按既定任务由负责人验证并回填，不在初始化时编造。
