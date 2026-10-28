# 新真实 API 结果（2026-10-25）

## 用户原文

ERROR · 资料操作失败，新版本未发布：嵌入服务拒绝访问（HTTP 403），请检查嵌入令牌的模型权限、分组、IP 限制及服务端访问策略；更换 PDF 仍需该权限，移除失败资料无需嵌入请求（任务 979f9585-9f9f-4fc5-9710-c5421a22ff90）

## 代理核验（非用户原文）

本机 IDEA 安全日志 2026-10-25 15:13:21.150 对应任务的异常链 CompletionException → com.openai.errors.PermissionDeniedException，栈经过 EmbeddingServiceImpl.create / CourseEmbeddings.document / CourseLibrary.derive。此次已收到 HTTP 403，不是前次 SSLHandshakeException。用户已触发真实请求，不能继续称真实 API 完全未执行；代理仍未独立发起请求。日志不保存原始服务响应正文，无法据此确定令牌限制、分组、网关或上游拒绝的具体原因。需要对应平台调用日志中脱敏错误原因；不猜测 key 错误，不清库，不据403放宽TLS或自动重试。无代码修改。
