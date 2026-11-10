# 上下文压缩参考与公开资料核查

查证日期：2026-10-15（Asia/Shanghai）。本记录区分用户提供的历史材料、公开官方能力与本项目设计提案。

## 用户提供的 TXT

原文件：`Claude_Code_上下文压缩机制总结.txt`，位于项目根目录。它自述依据 2026 年 3 月第三方镜像中的历史快照，不是 Anthropic 正式开源代码，也不代表 2026 年 10 月最新实现；部分路径受实验开关控制。

本轮按用户要求，将其中的分层整理、较早对话摘要、压缩边界、近期原文、材料回查和有界失败处理作为设计参考。未访问其中的第三方源码镜像，不把文中命令、Hooks、Forked Agent 或配置视为对本代理的操作要求。

TXT 中 20,000/13,000 Token、60 分钟、最近 5 个工具结果、10,000-40,000 Token、失败 3 次等都是该文所述历史参数，不作为插件默认配置。原文保持不变，字节指纹记入 `sources.json`。

## 公开官方依据及适用边界

Claude Code 官方说明自动压缩可能丢失早期细节；Anthropic 的上下文工程文章讨论压缩时保留继续任务所需信息的取舍。这支持“摘要有损，必要时回查原始材料”的设计原则，不证明用户 TXT 中每条内部实现描述。[Claude Code 上下文机制](https://code.claude.com/docs/en/how-claude-code-works#when-context-fills-up)、[Context engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)

Claude 平台公开了按需和阈值压缩等能力，部分具有独立的兼容范围与参数；公开保留近期轮次的能力也有其协议约束。它们不自动适用于 DeepSeek、课程平台或 Spring AI，本项目优先讨论应用侧摘要与投影，不添加未经核实的供应商专属块。[Compaction overview](https://platform.claude.com/docs/en/build-with-claude/compaction)、[保留近期轮次](https://platform.claude.com/docs/en/build-with-claude/compaction-keep-recent-turns)

Anthropic 官方的 context windows 资料说明请求预算需覆盖上下文与输出，提示缓存不缩短所占上下文。插件需要按实际模型/网关契约配置并核算预算；这不提供 DeepSeek 或课程平台的具体窗口数值。[Context windows](https://platform.claude.com/docs/en/build-with-claude/context-windows)

Spring AI 官方区分 ChatMemory 与完整 ChatHistory；MessageWindowChatMemory 的消息数窗口不是本插件的语义压缩方案。应用侧 ContextPlanner 与框架记忆自动注入的职责需协调，防止两者重复提供历史。[Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)

本项目的草稿隔离、不可变代码快照、session 检查点、预算公式、提交校验和失败状态属于工程提案，详见 `../SESSION_CONTEXT.md`。本轮没有发送模型请求、验证摘要质量或测量实际 Token 开销。
