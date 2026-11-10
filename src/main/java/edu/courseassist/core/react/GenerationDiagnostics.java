package edu.courseassist.core.react;

import edu.courseassist.contract.ChatContracts.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Fixed generation/F2 diagnostics. Messages and logs never include provider bodies, keys or user text. */
public final class GenerationDiagnostics {
    private static final Logger LOG = Logger.getLogger("edu.courseassist.generation");
    private GenerationDiagnostics() {}
    public record Diagnosis(Throwable cause, FailureView failure) {}
    /** Classifies only by exception type/category, never by copying arbitrary exception messages. */
    public static Diagnosis diagnose(Throwable failure) {
        Throwable cause = ReactEngine.unwrap(Objects.requireNonNull(failure));
        return new Diagnosis(cause, classify(cause));
    }
    public static FailureView classify(Throwable failure) {
        Throwable cause = ReactEngine.unwrap(failure);
        if (cause instanceof ReactData.CodedFailure coded) return coded.failure();
        if (ReactEngine.isCancellation(cause)) return new FailureView(FailureCode.CANCELLED, FailurePhase.LIFECYCLE);
        // Decode/JSON failures are protocol evidence, not transport I/O; check them first.
        if (jsonProblem(cause)) return new FailureView(FailureCode.MODEL_PROTOCOL, FailurePhase.MODEL_GENERATION);
        if (providerProblem(cause)) return new FailureView(FailureCode.PROVIDER_FAILURE, FailurePhase.MODEL_GENERATION);
        return new FailureView(FailureCode.INTERNAL, FailurePhase.INTERNAL);
    }
    /** Safe, concise user-facing text for one fixed category. No provider/private data is interpolated. */
    public static String message(FailureView failure) {
        return switch (failure.code()) {
            case MODEL_OUTPUT_LIMIT -> failure.phase() == FailurePhase.CANDIDATE_GENERATION
                    ? "候选生成输出达到上限（finish_reason=length），未生成候选；请缩小修改范围后重新发送"
                    : failure.phase() == FailurePhase.CONTEXT
                    ? "历史整理输出达到上限（finish_reason=length），未更新摘要；请减少历史后重试"
                    : "模型输出达到上限（finish_reason=length），已保留可见内容；请缩小请求范围后重新发送";
            case ATTEMPT_TIMEOUT -> failure.phase() == FailurePhase.CONTEXT
                    ? "历史整理调用超时，未更新摘要；请重试"
                    : "本次生成超时，已保留可见内容；请缩小请求范围后重新发送";
            case CONTEXT_BUDGET -> failure.phase() == FailurePhase.CANDIDATE_GENERATION
                    ? "候选输入上下文超过预算，未生成候选；请缩小修改范围后重新发送"
                    : failure.phase() == FailurePhase.CONTEXT
                    ? "上下文整理预算不足，未更新摘要；请减少历史后重试"
                    : "输入上下文超过预算，本次请求未完成；请减少附带代码或历史后重新发送";
            case CANDIDATE_FORMAT -> "候选 JSON/结构格式错误，未修改任何文件；请重新发送请求";
            case CANDIDATE_RANGE_BOUNDS -> "候选 start/end 越界，未修改任何文件；请重新发送请求";
            case CANDIDATE_RANGE_OVERLAP -> "候选修改重叠或顺序错误，未修改任何文件；请重新发送请求";
            case CANDIDATE_RANGE_OUTSIDE_ALLOWED -> "候选修改超出允许范围，未修改任何文件；请重新选择范围后发送";
            case CANDIDATE_BEFORE_MISMATCH -> "候选 before 与源码快照不匹配，未修改任何文件；请重新读取源码后发送";
            case RESULT_SIZE_LIMIT -> switch (failure.phase()) {
                case TOOL_RESULT -> "工具结果超过大小上限，已停止本次生成；请缩小请求后重新发送";
                case CONTEXT -> "历史整理输出或事件超过大小上限，未更新摘要；请重试或减少历史";
                case PATCH_VALIDATION -> "候选输出超过大小上限，未修改任何文件；请缩小修改范围后发送";
                default -> "生成输出超过大小上限，已停止本次生成；请缩小范围后重新发送";
            };
            case MODEL_PROTOCOL -> failure.phase() == FailurePhase.CANDIDATE_GENERATION
                    ? "候选生成协议异常（流式/工具调用不完整），未生成候选；请重新发送请求"
                    : failure.phase() == FailurePhase.CONTEXT
                    ? "历史整理模型协议异常，未更新摘要；请重试"
                    : "模型流式协议异常（SSE/工具调用不完整），已停止本次生成；请重新发送请求";
            case PROVIDER_FAILURE -> "模型服务连接或 HTTP 失败，未自动重试；请检查网络与服务后手动重试";
            case CANCELLED -> "用户取消或生命周期中断，已保留可见内容";
            case GENERATION_LIMIT -> "本次生成达到步骤或工具数量上限，已停止；请缩小请求后重新发送";
            case COMPACTION_REJECTED -> "当前没有可整理的较早已终止轮次，或仍有活动轮次；未执行整理";
            case INTERNAL -> "生成失败（内部异常），请记录错误码并查看 IDEA 日志";
        };
    }
    /** Notice text for an async failure; unresolved/internal failures keep the caller's generic notice. */
    public static Optional<String> notice(Throwable failure) {
        Diagnosis diagnosis = diagnose(failure);
        if (diagnosis.failure().code() == FailureCode.INTERNAL) return Optional.empty();
        return Optional.of(message(diagnosis.failure()) + "（" + diagnosis.failure().code() + "）");
    }
    /** Code/phase/type and attempt identity only; safe stack classes/frames contain no messages or bodies. */
    public static void log(ReactData.Identity identity, Diagnosis diagnosis) {
        FailureView failure = diagnosis.failure();
        String line = "CourseAssistant generation diagnostic code=" + failure.code()
                + " phase=" + failure.phase()
                + " type=" + diagnosis.cause().getClass().getName()
                + " attempt=" + identity.attemptId()
                + " operation=" + identity.operationId();
        if (failure.code() == FailureCode.CANCELLED) {
            LOG.log(Level.INFO, line);
        } else {
            LOG.log(Level.WARNING, line + "\n" + safeStack(diagnosis.cause()));
        }
    }
    private static boolean jsonProblem(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            String name = cause.getClass().getName();
            if (name.startsWith("tools.jackson.core.") || name.startsWith("tools.jackson.databind.")
                    || name.startsWith("com.fasterxml.jackson.core.") || name.startsWith("com.fasterxml.jackson.databind.")
                    || name.contains("JacksonException") || name.contains("JsonProcessingException")
                    || name.contains("DecodingException")) return true;
        }
        return false;
    }
    private static boolean providerProblem(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof java.io.IOException || cause instanceof java.util.concurrent.TimeoutException) return true;
            String name = cause.getClass().getName();
            if (name.startsWith("org.springframework.web.reactive.function.client.WebClient")
                    || name.startsWith("org.springframework.web.client.")
                    || name.startsWith("org.springframework.web.reactive.function.client.")
                    || name.startsWith("com.openai.errors.")
                    || name.startsWith("reactor.netty.")
                    || name.startsWith("io.netty.handler.timeout.")) return true;
        }
        return false;
    }
    private static String safeStack(Throwable failure) {
        StringBuilder out = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            out.append(cause.getClass().getName()).append('\n');
            for (StackTraceElement frame : cause.getStackTrace()) out.append("  at ").append(frame).append('\n');
        }
        return out.toString();
    }
}
