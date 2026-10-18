package edu.courseassist.contract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static edu.courseassist.contract.CommonContracts.requireId;
import static edu.courseassist.contract.CommonContracts.requireRevision;

/** Code is fixed source text. Neither PSI handles nor rendered HTML cross this boundary. */
public final class CaptureContracts {
    private CaptureContracts() {}

    /** One-based line/column; UTF-16 code units; start inclusive and end exclusive. */
    public record SourceRange(int startLine, int startColumn, int endLine, int endColumn) {
        public SourceRange {
            if (startLine < 1 || startColumn < 1 || endLine < startLine || endColumn < 1
                    || (startLine == endLine && endColumn < startColumn)) {
                throw new IllegalArgumentException("Invalid one-based source range");
            }
        }
    }

    public record FileSnapshotRef(String fileRef, long documentVersion, String languageId) {
        public FileSnapshotRef {
            requireId(fileRef, "fileRef");
            requireRevision(documentVersion);
            requireId(languageId, "languageId");
        }
    }

    /** Created on the source editor action, before asynchronous work or tab changes. */
    public record SelectionStart(String captureId, String projectInstanceId,
                                 FileSnapshotRef source, String selectedText, SourceRange selection) {
        public SelectionStart {
            requireId(captureId, "captureId");
            requireId(projectInstanceId, "projectInstanceId");
            Objects.requireNonNull(source, "source");
            if (Objects.requireNonNull(selectedText, "selectedText").isEmpty()) {
                throw new IllegalArgumentException("Empty selection is not a capture");
            }
            Objects.requireNonNull(selection, "selection");
        }
    }

    /** Core-owned route reservation, not proof that initialization has been persisted. */
    public record CaptureTicket(String captureId, String projectInstanceId, String targetSessionId,
                                String draftId, FileSnapshotRef source, long reservationRevision) {
        public CaptureTicket {
            requireId(captureId, "captureId");
            requireId(projectInstanceId, "projectInstanceId");
            requireId(targetSessionId, "targetSessionId");
            requireId(draftId, "draftId");
            Objects.requireNonNull(source, "source");
            requireRevision(reservationRevision);
        }
    }

    public enum ContextKind { METHOD, CLASS_STRUCTURE }
    public enum ContextStatus { CAPTURED, NOT_APPLICABLE }

    /** Normal absence has a reason. Parse/version/read failures use exceptional completion. */
    public record AutomaticContext(ContextKind kind, ContextStatus status, Optional<String> text,
                                   Optional<SourceRange> range, Optional<String> absenceReason) {
        public AutomaticContext {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(status, "status");
            text = Objects.requireNonNull(text, "text");
            range = Objects.requireNonNull(range, "range");
            absenceReason = Objects.requireNonNull(absenceReason, "absenceReason");
            if (status == ContextStatus.CAPTURED && (text.isEmpty() || range.isEmpty() || absenceReason.isPresent())) {
                throw new IllegalArgumentException("Captured context needs text/range and no absence reason");
            }
            if (status == ContextStatus.NOT_APPLICABLE && (text.isPresent() || range.isPresent() || absenceReason.isEmpty())) {
                throw new IllegalArgumentException("Absent context needs only an explicit reason");
            }
        }
    }

    /** All three pieces belong to start.source().documentVersion(), never a refreshed editor version. */
    public record CapturedCodeGroup(SelectionStart start, List<AutomaticContext> automaticContexts) {
        public CapturedCodeGroup {
            Objects.requireNonNull(start, "start");
            automaticContexts = List.copyOf(automaticContexts);
            if (automaticContexts.size() != 2 || automaticContexts.stream().map(AutomaticContext::kind).distinct().count() != 2) {
                throw new IllegalArgumentException("Exactly METHOD and CLASS_STRUCTURE outcomes are required");
            }
        }
    }
}
