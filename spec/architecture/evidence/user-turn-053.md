# 用户补充嵌入配置及问题

来源：当前会话异步问题答复，2026-10-24。

## 用户原文

deepseek [deepseek.com](https://api.deepseek.com) deepseek-flash 1024，立即失败。问题：必须embedding模型吗

## 代理分析（不是用户原文）

当前向量检索必须由embedding服务产生向量；deepseek-flash是生成模型，任填1024不使其成为embedding模型。已核对官方模型文档 https://api-docs.deepseek.com/api/list-models/ ，不调用真实付费接口。明确的配置错误可以本地拦截；仍不能据此把本机H2关闭异常判作无关或已修复。用户本轮并未选择新的嵌入服务。
