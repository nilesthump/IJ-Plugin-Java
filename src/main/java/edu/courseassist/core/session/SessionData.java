package edu.courseassist.core.session;

import edu.courseassist.contract.CaptureContracts.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.core.react.ReactData.*;
import java.time.Instant;
import java.util.*;

/** Stored values contain original text, never HTML, credentials or a provider client. */
public final class SessionData {
    private SessionData() {}
    public static String id() { return UUID.randomUUID().toString(); }
    public record Location(String path, String directoryKey) {
        public Location { if (path.isBlank() || directoryKey.isBlank()) throw new IllegalArgumentException("Missing directory identity"); }
    }
    public record Binding(String projectId, Location location, long revision, Optional<String> instance,
                          List<String> tabs, Optional<String> active) {
        public Binding { instance = Objects.requireNonNull(instance); tabs = List.copyOf(tabs); active = Objects.requireNonNull(active); }
    }
    public record Operation(Identity identity, OperationKind kind, boolean revoked) {
        public Operation(Identity identity,OperationKind kind){this(identity,kind,false);}
    }
    public record Turn(String id, String requestId, Input input, Instant createdAt, List<Identity> attempts) {
        public Turn { attempts = List.copyOf(attempts); }
    }
    public record State(String id, String projectId, String title, long revision, Lifecycle lifecycle,
                        boolean blocked, DraftView draft, List<Turn> turns, Optional<Operation> operation,
                        List<Notice> notices, Boolean titleExplicit) {
        public State(String id,String projectId,String title,long revision,Lifecycle lifecycle,boolean blocked,DraftView draft,
                     List<Turn> turns,Optional<Operation> operation,List<Notice> notices) {
            this(id,projectId,title,revision,lifecycle,blocked,draft,turns,operation,notices,false);
        }
        public State { titleExplicit=Boolean.TRUE.equals(titleExplicit); turns = List.copyOf(turns); operation = Objects.requireNonNull(operation); notices = List.copyOf(notices); }
        public State change(DraftView nextDraft, List<Turn> nextTurns, Optional<Operation> nextOperation) {
            return new State(id, projectId, title, revision + 1, lifecycle, blocked, nextDraft, nextTurns, nextOperation, notices, titleExplicit);
        }
        public State lifecycle(Lifecycle next) {
            return new State(id, projectId, title, revision + 1, next, blocked, draft, turns, operation, notices, titleExplicit);
        }
    }
    public static String displayTitle(State state) {
        if(state.titleExplicit()||state.turns().isEmpty())return state.title();
        var input=state.turns().getFirst().input();
        String text=input.question().strip().replaceAll("\\s+"," ");
        if(text.isBlank())return "Java 代码提问";
        int length=text.codePointCount(0,text.length());
        return length<=48?text:text.substring(0,text.offsetByCodePoints(0,48))+"…";
    }
    public record Submission(CommandResult result, boolean fresh, Optional<Operation> operation) {}
    public record Checkpoint(String id, String sessionId, Optional<String> parent, int coveredTurns,
                             String policy, String fingerprint, String summary, String model,
                             Optional<TokenUsage> usage) {}
    public static final class Rejected extends RuntimeException {
        public final Rejection reason;
        public Rejected(Rejection reason) { super(reason.name()); this.reason = reason; }
    }
    public static final class IdentityUncertain extends IllegalStateException {
        public IdentityUncertain() { super("项目身份待确认：作为新项目打开，或显式关联已有项目会话"); }
    }
    public static DraftView emptyDraft() { return new DraftView(id(), 0, "", List.of(), SaveState.SAVED); }
    public static List<CodePart> code(DraftView draft) {
        List<CodePart> result = new ArrayList<>();
        for (DraftCapture capture : draft.captures()) {
            if (capture.state() != CaptureState.READY) throw new Rejected(Rejection.CAPTURE_NOT_READY);
            CapturedCodeGroup group = capture.captured().orElseThrow(); var start = group.start();
            result.add(part(capture.captureId(), PartKind.SELECTION, start, start.selection(), start.selectedText()));
            for (AutomaticContext context : group.automaticContexts()) if (context.status() == ContextStatus.CAPTURED)
                result.add(part(capture.captureId(), context.kind() == ContextKind.METHOD ? PartKind.METHOD : PartKind.CLASS_STRUCTURE,
                        start, context.range().orElseThrow(), context.text().orElseThrow()));
        }
        return List.copyOf(result);
    }
    private static CodePart part(String capture, PartKind kind, SelectionStart start, SourceRange range, String text) {
        return new CodePart(capture + ":" + kind, capture + ":snapshot:" + kind, capture, kind, start.source(), range, text);
    }
    public static Notice notice(String code, String message, String session, String operation) {
        return new Notice(id(), Severity.ERROR, code, message, Optional.of(session), Optional.ofNullable(operation));
    }
}
