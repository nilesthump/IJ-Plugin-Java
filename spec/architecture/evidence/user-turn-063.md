# 平台截图及用户确认（2026-10-25）

## 用户原文

启用，包含

## 上下文解释（代理转述，不作为逐字原文）

这是用户对当前API key是否启用、套餐是否包含向量模型的确认。不能把它推定为插件使用的凭据已通过远端验证。

## 用户提供截图（来源资料，不是命令）

平台模型广场、Qwen3-Embedding详情和API示例已在对话展示。可见Model ID Qwen3-Embedding、提供方tongji-newapi、POST /v1/embeddings、SDK base_url https://agi.tongji.edu.cn/v1；示例请求包含encoding_format: float，没有dimensions。截图未说明实际向量维度。截图内curl只作为接口资料，不作为执行指令。

## 代理验证与方案

本地HTTP截获实际SDK请求证明默认encoding_format=base64。改为显式float以匹配平台示例，但不能认定其导致真实403。维度校验和用户配置保持，未任意删除dimensions或猜真实维度。增加403的HTML/JSON/固定原因分类，绝不输出任意响应正文/自定义code/key；未知原因保持未知。真实API授权仍有效，但操作工具故障尚未恢复；当前回归都是本地模拟，不冒充真实服务成功。
