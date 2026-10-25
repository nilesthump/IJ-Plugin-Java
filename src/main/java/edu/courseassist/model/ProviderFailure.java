package edu.courseassist.model;

/** Only fixed, non-secret descriptions reach view state. Never expose response bodies/URLs/keys. */
public final class ProviderFailure {
    private ProviderFailure() {}
    public static String message(Throwable error) {
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException && java.util.Arrays.stream(cause.getStackTrace()).anyMatch(frame -> frame.getClassName().equals("okhttp3.Headers$Companion") && frame.getMethodName().equals("checkValue")))
                return "嵌入 HTTP 请求头含非法字符，请先停用并重新填写嵌入 API key；若仍失败，请提供任务编号检查客户端请求头";
            if (cause instanceof CourseEmbeddings.InvalidResponse) return cause.getMessage();
            if (cause instanceof ConfiguredRuntimeFactory.ConfigurationProblem) return cause.getMessage();
            if (cause instanceof ConfiguredRuntimeFactory.InvalidEmbeddingConfiguration) return cause.getMessage();
            if (cause instanceof com.openai.errors.OpenAIServiceException service) {
                return switch (service.statusCode()) {
                    case 401 -> "嵌入服务认证失败（HTTP 401），请停用后检查嵌入 API key 是否有效且属于该服务";
                    case 403 -> "嵌入服务拒绝访问（HTTP 403）：" + rejection(service).description;
                    case 404 -> "嵌入接口或模型不存在，请检查 Base URL 与 embedding 模型；聊天模型不能代替";
                    case 400, 422 -> "嵌入请求参数不兼容，请核对模型、维度及服务的 /embeddings 支持";
                    case 429 -> "嵌入服务额度或速率受限，请检查账户后手动重试";
                    default -> "嵌入服务返回 HTTP " + service.statusCode() + "，未自动重试";
                };
            }
            if (cause instanceof edu.courseassist.core.corpus.CorpusData.Conflict) return "资料状态、处理配置或课程库基准不再匹配，请重新选择资料并核对配置后重试；未覆盖当前就绪版本";
            if (cause instanceof java.sql.SQLException) return "本地数据库读写失败，请保留数据并检查 IDEA 日志";
            if (cause instanceof javax.net.ssl.SSLHandshakeException)
                return "无法建立 HTTPS 安全连接（TLS 握手失败），请检查服务连通性、代理/VPN 与证书后手动重试；此次失败不能判断 API key 是否有效，无需清空资料库";
            if (cause instanceof java.net.ConnectException || cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException)
                return "服务连接失败或超时，请检查网络和服务地址";
        }
        Throwable cause = error;
        java.util.Set<Throwable> chain = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (cause.getCause()!=null && chain.add(cause.getCause())) cause=cause.getCause();
        return "本地处理失败（" + cause.getClass().getSimpleName() + "），请提供诊断编号或任务编号，以检查 IDEA 日志";
    }
    /** Only fixed categories leave this boundary; never copy arbitrary provider text or codes. */
    private enum RejectionDetail {
        HTML_PAGE("收到 HTML 错误页，可能由网关或访问控制层返回，不能据此确认模型权限"),
        MODEL_ACCESS("服务提示模型访问未授权，请核对当前嵌入令牌的模型范围与服务端配置"),
        IP_ACCESS("服务提示 IP 访问受限，请核对当前网络出口和平台访问策略"),
        TOKEN_DISABLED("服务提示令牌已禁用，请核对插件当前使用的嵌入令牌"),
        TOKEN_EXPIRED("服务提示令牌已过期，请核对插件当前使用的嵌入令牌"),
        QUOTA("服务提示额度不足，请核对令牌和所属分组额度"),
        JSON_UNCLASSIFIED("收到 JSON 错误响应，但具体拒绝原因未识别；需平台核对该次请求"),
        UNCLASSIFIED("响应中未识别出具体拒绝原因；需平台核对该次请求");
        final String description;
        RejectionDetail(String description) { this.description = description; }
    }
    private static RejectionDetail rejection(com.openai.errors.OpenAIServiceException service) {
        boolean html = service.headers().values("content-type").stream()
                .map(v -> v.split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT))
                .anyMatch(v -> v.equals("text/html") || v.equals("application/xhtml+xml"));
        if (html) return RejectionDetail.HTML_PAGE;
        var body = service.body();
        com.fasterxml.jackson.databind.JsonNode fields = body.asObject().isPresent()
                ? body.convert(com.fasterxml.jackson.databind.JsonNode.class)
                : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        // The SDK normally unwraps error; accept an explicit nested error object as well.
        if (fields.path("error").isObject()) fields = fields.path("error");
        String code = fields.path("code").isTextual() ? fields.path("code").textValue() : "";
        String message = fields.path("message").isTextual() ? fields.path("message").textValue() : "";
        if (code.equals("model_not_allowed") || code.equals("model_access_denied") || message.contains("令牌无权访问该模型"))
            return RejectionDetail.MODEL_ACCESS;
        if (code.equals("ip_not_allowed") || message.contains("IP 不在白名单中") || message.contains("IP不在白名单中"))
            return RejectionDetail.IP_ACCESS;
        if (code.equals("token_disabled") || message.contains("令牌已禁用")) return RejectionDetail.TOKEN_DISABLED;
        if (code.equals("token_expired") || message.contains("令牌已过期")) return RejectionDetail.TOKEN_EXPIRED;
        if (code.equals("insufficient_quota") || message.contains("令牌额度已用尽")) return RejectionDetail.QUOTA;
        boolean json = body.asObject().isPresent() || service.headers().values("content-type").stream()
                .map(v -> v.split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT))
                .anyMatch(v -> v.equals("application/json") || v.endsWith("+json"));
        return json ? RejectionDetail.JSON_UNCLASSIFIED : RejectionDetail.UNCLASSIFIED;
    }
    /** Stack structure and fixed categories only; no exception messages or response fields. */
    public static String safeDiagnostic(Throwable error) {
        StringBuilder out = new StringBuilder();
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause=error; cause!=null && seen.add(cause); cause=cause.getCause()) {
            out.append(cause.getClass().getName()).append('\n');
            if (cause instanceof com.openai.errors.OpenAIServiceException service && service.statusCode() == 403)
                out.append("  HTTP 403 category=").append(rejection(service).name()).append('\n');
            for (StackTraceElement frame : cause.getStackTrace()) out.append("  at ").append(frame).append('\n');
        }
        return out.toString();
    }

}
