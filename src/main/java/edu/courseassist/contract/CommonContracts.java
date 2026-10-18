package edu.courseassist.contract;

import java.util.Objects;
import java.util.Optional;

/** Shared, platform-free values for API v1. Identity strings are opaque. */
public final class CommonContracts {
    private CommonContracts() {}

    public enum Outcome { APPLIED, REJECTED }
    public enum Rejection {
        BUSY, STALE_REVISION, PROJECT_CLOSED, SESSION_DELETED, OPERATION_TERMINATED,
        CAPTURE_NOT_READY, PLUGIN_OFF, CONFIG_REQUIRES_OFF, UNSAVED_STATE,
        INVALID_TARGET, INVALID_INPUT, NOT_SUPPORTED
    }
    public enum SaveState { SAVED, SAVING, UNSAVED, FAILED }
    public enum Severity { INFO, WARNING, ERROR }

    /** Exceptional failures complete the stage exceptionally; they are not ordinary rejections. */
    public record CommandResult(String commandId, Outcome outcome, long stateRevision,
                                Optional<Rejection> rejection, Optional<String> operationId) {
        public CommandResult {
            requireId(commandId, "commandId");
            Objects.requireNonNull(outcome, "outcome");
            requireRevision(stateRevision);
            rejection = Objects.requireNonNull(rejection, "rejection");
            operationId = Objects.requireNonNull(operationId, "operationId");
            if ((outcome == Outcome.REJECTED) != rejection.isPresent()) {
                throw new IllegalArgumentException("Only rejected commands have a rejection reason");
            }
        }
    }

    /** User-facing status, never a credential, raw exception dump, or source of model context. */
    public record Notice(String noticeId, Severity severity, String code, String message,
                         Optional<String> sessionId, Optional<String> operationId) {
        public Notice {
            requireId(noticeId, "noticeId");
            Objects.requireNonNull(severity, "severity");
            requireId(code, "code");
            Objects.requireNonNull(message, "message");
            sessionId = Objects.requireNonNull(sessionId, "sessionId");
            operationId = Objects.requireNonNull(operationId, "operationId");
        }
    }

    static String requireId(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static void requireRevision(long revision) {
        if (revision < 0) throw new IllegalArgumentException("Revision must be non-negative");
    }
}
