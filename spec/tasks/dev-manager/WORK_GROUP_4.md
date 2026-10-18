# 第四组：会话、提交、恢复、压缩与删除

日期：2026-10-18。开工基线 `8a68daa35e6d3229c9255227da3b0988ba7cf160`（PR #6 已合入 main）。仅已有 dev-manager；不开展第五组，不修改成员 editor/pdf/ui 或成员任务。架构仍未冻结。

## 实现边界

- `SessionRepository` 在应用共享 H2 中保存项目绑定、session 根、原始草稿、捕获组、typed 代码、turn/attempt 身份、提交去重、布局和 checkpoint。独立 schema v1；未知版本和损坏状态传播原错，不清库。ReAct 与共享来源保留仍使用第三组存储。
- 新提交在同一短事务内依次检查归属/本次实例/LIVE、同 commandId 去重、新请求的 busy/revision/捕获就绪与 runtimeEpoch，然后保存固定用户输入、空 attempt、活跃操作、去重结果并消费确切草稿版本。只有提交成功才调度；重复返回原 operation，不再订阅模型。草稿新文字与捕获补齐只修改自己的字段。
- 捕获入口快速绑定原项目当前标签；缺省预留新 session。首次 INSERT 在事务内再次检查创建票据，持久组保存后才调用成员 CodeCapturePort。移除/删除/项目关闭撤销迟到资格；相同文字的新 captureId 保留独立身份。重启的 PENDING 明确要求重新捕获，不自动读新源码。
- ReAct 每次写入在事务中检查真实根/实例/operation/epoch；终态保存与活跃槽释放共同提交。原始问题/代码只保存一次，重试增加 attempt；关闭标签不取消，重开不请求模型。未完成输出在档案/累计视图中保留，但未来输入仍共用 ReactContext 的耐久最终分支资格。
- 项目路径使用 real path；Windows通过IDE已有JNA读取卷与128位FILE_ID_INFO、创建时间（标准fileKey在本机为null），其余平台需要可验证的fileKey。可证明的同卷移动保持 projectId；副本使用新归属。未知目录或同路径身份变化先要求明确选择，旧路径消失不授予会话。显式关联检查空目标、两端绑定版本与无源实例占用，不复制/合并记录；跨机器/无法取得可靠目录身份的自动识别未承诺。Windows字段依据 [Microsoft FILE_ID_INFO查询](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-getfileinformationbyhandleex)，本机真实场景另验。
- 手动摘要占用同一 session 活跃槽，不消费草稿、不检索、不追加聊天；自动摘要在首检后依据含本轮证据/tools 的完整请求规划。只取较早已终止前缀，统一资格过滤；单次、无工具、有限摘要调用，不递归/不换模型。候选先保存，激活事务复验根、实例、operation、epoch、父 checkpoint、来源资格指纹和边界。原始档案不删除。
- 删除先提交 DELETING 并撤销捕获/生成/摘要和未落盘创建票据，再清内存与全部 H2 私有记录/来源引用，最后硬删根和布局。没有 session 私有旁存文件。清理失败保留 DELETE_FAILED 栅栏与继续清理入口；共享 PDF/向量/资料不级联删除。迟到回调只能终止，不能 INSERT/upsert 根。
- `ApplicationCoordinator` 提供应用控制器及短期凭据交付。凭据单次 ref 两分钟过期，清零临时数组；非秘密 profile 单独入 H2。生产凭据使用 PasswordSafe。OFF 才能改配置；启用只本地构造客户端，不作连通测试。停用先撤 epoch，保存已接收终止快照并释放旧槽；不等远端物理取消。保存失败保持原始显示和发送阻塞，重新 ON 不解除，需显式本地存储恢复。

## 装配与实施参数

应用服务 `controllers()` 惰性后台初始化真实业务控制器，项目服务 `openSessions(...)` / `sessionController()` / `selectionSink()` 提供生命周期接线；UI、编辑器与 PDF 实际实现仍由成员交付。未注册成员端口明确失败，不用合成适配器冒充生产成功。引用在校验档案citation后通过托管固定版本和物理页URI打开；普通 Markdown 链接不视为引用，真实可视PDF跳页仍NOT_RUN。应用关闭先收尾本地再关H2，并有JVM hook最多等待15s；IDE强杀不承诺未提交字节，人工正常关闭交互尚未验证。

生产兼容路线为显式配置的 DeepSeek 风格 `/chat/completions` SSE 与 OpenAI 风格 embedding，通过既有 Spring AI 类型/适配器；准确 endpoint/modelId/维度由 profile 提供，默认不自动选 alias、无重试。当前摘要复用所选生成模型，独立 purpose/请求且不启用 tools，不偷偷切模型。实际服务协议、账号能力、摘要语义质量与 tokenizer 仍须单独证明。

MVP 候选限制：6 steps、8 tools、工具 16000 chars、输出 32000 chars、attempt 120s、partial 保存阈值 256 chars；本地保守 UTF-8 byte-bound 计量（不是实际 tokenizer），主输入 32768、输出预留 2048、自动触发 24576；摘要输出 1024、保留近期 2 turns、摘要最多 8000 chars/30s/一次；PDF 分块 1200/重叠120，图16/100/1.5/1.2，TopK5/阈值0.65。它们是本轮可控验证的显式实施候选，不代表任意真实模型窗口、生产召回或千页性能已经验证。运行配置变更仍重新计量，不静默裁剪代码。

## 验证与交付

准确命令、退出码、独立检查与远端交付以 STATUS 和 evidence/work-group-4-* 为准。开工 All 退出0。实施期间依赖包名/私有工具方法编译失败与业务拒绝包装错误保留原始日志，修正后重新验证；取消控制流的测试断言也据实际 dependent future 语义修正，不把失败记录改写成通过。

NOT_RUN：真实付费/课程服务、真实成员 PDF/编辑器/可视 UI 联调、摘要语义质量/实际 tokenizer/服务额度、真实课程规模与跨机器迁移。合成响应、真实 H2 与 headless 本机 IDEA 场景分别记录。第四组只有在最新 PR head validation/scope、独立检查、main 合入并核验后才 DONE；整体 F0 和第五组仍未完成。
