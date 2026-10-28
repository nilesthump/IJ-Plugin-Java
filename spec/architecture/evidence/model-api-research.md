# 生成与嵌入服务核查

查证日期：2026-10-15（Asia/Shanghai）。区分公开官方资料、用户报告及尚未验证的接口能力。本轮未读取密钥、未发送模型请求或课程/代码数据。

## 服务接入

| 来源 | 已核实或用户报告的能力 | 仍未知 |
| --- | --- | --- |
| DeepSeek 官方 API | 官方文档明确提供聊天生成；OpenAI 格式 base URL 为 https://api.deepseek.com，示例聊天路径为 /chat/completions | 本轮未找到官方公开 embedding API 文档，不能由聊天能力推定其提供向量化 |
| 同济课程平台 | 用户报告模型列表有 bge-m3 和 qwen3-embedding，且计划使用该平台 API | API base URL、路径、兼容协议、准确模型 ID、输出维数、账户权限及额度尚未验证 |

DeepSeek 来源：[首次 API 调用](https://api-docs.deepseek.com/)。本轮未将当前官方示例中的模型名锁为项目默认。

课程平台网站入口 https://agi.tongji.edu.cn 由作业列出；网站首页不等于 API base URL。公开读取在 web 工具返回 Internal Error，独立读取亦未取得 API 说明；不能据此断言平台必须登录或不支持某项能力。用户对模型列表的补充保持为明确的用户报告。

Spring AI 提供原生 DeepSeekChatModel，也支持手动装配；OpenAI 风格的聊天与嵌入适配器允许分别配置服务。只有课程平台协议确认兼容后，才可选相应适配器，不自动补写 /v1 等路径。[DeepSeek 集成](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html)、[OpenAI 聊天](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)、[OpenAI 嵌入](https://docs.spring.io/spring-ai/reference/api/embeddings/openai-embeddings.html)

## 两个嵌入候选的官方能力边界

BAAI 官方模型卡给出 BGE-M3 的 dense 向量维数为 1024；原模型还支持 sparse 与多向量表示，但平台名称不能证明 API 暴露了所有模式。普通向量索引方案应核实返回单条 dense 向量。[BAAI 官方模型卡](https://huggingface.co/BAAI/bge-m3)

Qwen3-Embedding 原作者模型包含 0.6B、4B、8B，最大维数分别为 1024、2560、4096，并支持调整输出维数。用户给出的 qwen3-embedding 是家族或平台别名线索，不能据此确定模型尺寸或实际维度。[Qwen 官方家族表与示例](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B)、[4B](https://huggingface.co/Qwen/Qwen3-Embedding-4B)、[8B](https://huggingface.co/Qwen/Qwen3-Embedding-8B)

BAAI 说明 BGE-M3 的查询无需额外指令；Qwen 官方检索示例对查询加入任务指令，对文档不加入。平台是否已封装这些步骤需确认，避免重复。文档与查询应遵循同一模型所要求的成对编码约定，而非简单要求两者的输入字符串格式完全相同。[BAAI FAQ](https://huggingface.co/BAAI/bge-m3#faq)、[Qwen 用法](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B#usage)

工程推论：同维不同模型的向量不处于相同坐标空间。例如两个候选都可能输出 1024 维，但仍不可混入同一索引。embeddingSpaceId 应记录精确提供方/模型及修订、实际输出维数、文档/查询编码约定、归一化和相似度方式。变更影响向量表示的配置后，需建立兼容的新索引代次。

## 接口确认时需要的非秘密信息

API 文档与 base URL；生成和嵌入各自的准确模型 ID 与路径；嵌入的 dense 输出、维度、单文本及批次上限；文档/查询指令处理；认证类型、网络要求及配额规则。凭据值留在实际配置环节处理，不写入架构证据。

本轮不在 BGE-M3 与 Qwen3 之间指定默认赢家。后续应在相同课程样本和问题集上比较检索、时延与成本，再确认默认配置。服务连通性和账户可用性仍未经测试。
