package edu.courseassist.ui.f2;

import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.core.react.GenerationDiagnostics;
import java.util.Optional;

/** Fixed terminal-state labels. Legacy archived attempts without a code retain the old generic text. */
public final class FailurePresentation {
    private FailurePresentation() {}
    public static String state(AttemptStatus status, Optional<FailureView> failure) {
        return switch (status) {
            case RUNNING -> "正在生成";
            case SAVING -> "正在保存";
            case SUCCEEDED -> "已完成";
            case STOPPED -> failure.map(f -> "已暂停 · " + withCode(f))
                    .orElse("已暂停 · 继续将从原问题重新生成");
            case INTERRUPTED -> failure.map(f -> "已中断 · " + withCode(f)).orElse("已中断");
            case LIMITED -> failure.map(FailurePresentation::withCode).orElse("达到限制（此历史尝试无诊断码）");
            case FAILED -> failure.map(FailurePresentation::withCode).orElse("生成失败（此历史尝试无诊断码）");
        };
    }
    public static String withCode(FailureView failure) {
        return message(failure) + "（" + failure.code() + "）";
    }
    public static String message(FailureView failure) {
        return GenerationDiagnostics.message(failure);
    }
}
