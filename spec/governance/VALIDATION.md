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

`-Scope All -IdeSmoke` 在标准 check/buildPlugin 之后，另启隔离 sandbox 中的本机对应 IDEA/JBR，执行测试插件提供的 headless 命令；结果必须同时满足进程退出 0 与 `build/reports/ide-smoke/result.json` 的 PASS。测试插件位于 `src/test/java/edu/courseassist/bootstrap/ide` / `scripts/ide-smoke/plugin.xml`，只在该验证运行中装入，不进入 F0 发布 ZIP。检查真实插件类加载、应用/项目服务归属与释放、后台任务、Swing Tool Window content、Java PSI 读操作、合成 H2/JVector，以及只访问 `127.0.0.1` 的 Spring AI 合成响应。它不证明人工可视交互、成员捕获算法、完整会话恢复/检索/ReAct、真实模型或课程网关能力。

Wrapper 来自已安装官方 Gradle 的 wrapper 任务；初始化时隔离环境 HEAD 校验失败，生成曾使用 `--no-validate-url`，随后恢复 URL 校验并固定官方 ZIP SHA-256 `a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806`（[官方校验文件](https://services.gradle.org/distributions/gradle-9.1.0-bin.zip.sha256)）。下载和实际 Wrapper 执行结果另看进度。构建失败不能静默切版本、仓库镜像或跳过 check 来报成功。

## CI 信任边界

GitHub 构建 context 为 `validation`，main push、普通 `pull_request` 与手动 `workflow_dispatch` 执行同一入口；开发分支只推送时不重复构建。PR只接受同仓库四个固定dev分支并直接目标main。构建job只读，不持有机密或写凭据。

独立 `F0 scope` 工作流从main处理 `workflow_run` 完成事件，只读取PR文件元数据、main上的策略，并发布 `scope` 状态；不检出或执行PR代码、不读其artifact。校验来源/目标、最新head、全部文件数与重命名前后路径；同SHA的多个开放PR保守聚合。这样正常协作下成员修改自己PR中的检查器不会给范围检查放行。main现要求validation与scope；当前完成证据看CURRENT。

构建checkout不保留凭据，不传secrets、不使用共享可写缓存；scope只额外持有发布状态所需权限。不能给执行PR代码的job加入写权限或机密环境。公开fork PR不接受。管理员也按main的PR/CI规则操作；初始建库提交是一次性引导，不作为日常直推先例。

目录检查用于按分工协作时防止误改，并非对恶意写协作者的安全隔离：GitHub写协作者可以修改工作流权限，默认read不能作为不可提升的服务端上限。禁止创建/删除/强推分支及main门禁由GitHub规则强制；目录归属同时依赖仓库规范、hooks和负责人的PR核对，不宣称仅靠scope状态就限制了拥有写权限的账号。

## 需要另留证据

自动构建不能证明 IDE 运行线程/释放、右键与 PSI 同版本、IDE 多色配色切换、流式生命周期、H2 恢复/硬删除、实际 API 工具协议/嵌入或千页规模召回。成员任务明确哪些场景可纯逻辑验证、哪些需目标 SDK/IDE。未跑写 NOT_RUN；换 build、合成 PDF/状态和真实服务分别标记。报告中的“通过”须能追到命令、退出码与范围。

具体模型、压缩预算和失败数值按既定任务由负责人验证并回填，不在初始化时编造。
