package edu.courseassist.contract;

import edu.courseassist.contract.CaptureContracts.CapturedCodeGroup;
import edu.courseassist.contract.CaptureContracts.FileSnapshotRef;
import edu.courseassist.contract.CaptureContracts.SourceRange;
import edu.courseassist.contract.CommonContracts.Notice;
import edu.courseassist.contract.CommonContracts.SaveState;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static edu.courseassist.contract.CommonContracts.requireId;
import static edu.courseassist.contract.CommonContracts.requireRevision;

/** Cumulative view snapshots, not provider deltas or persisted entities. */
public final class ChatContracts {
    private ChatContracts() {}

    public enum Lifecycle { LIVE, DELETING, DELETE_FAILED }
    public enum CaptureState { PENDING, READY, FAILED, RECAPTURE_REQUIRED }
    public enum OperationKind { GENERATE, COMPACT, APPLY }
    public enum AttemptStatus { RUNNING, SAVING, SUCCEEDED, STOPPED, INTERRUPTED, LIMITED, FAILED }
    public enum BranchStatus { ACTIVE, COMPLETED, INVALIDATED }
    public enum StepStatus { STREAMING, TOOL_CALLS_READY, TOOLS_RUNNING, FINISHED, INCOMPLETE }
    public enum ToolStatus { REQUESTED, RUNNING, SUCCEEDED, FAILED, CANCELED }
    public enum PartKind { SELECTION, METHOD, CLASS_STRUCTURE }
    public enum Role { USER, ASSISTANT }
    public enum AnswerMode { COURSE_GROUNDED, GENERAL, NOT_YET_KNOWN }
    public enum RetrievalStatus { NOT_STARTED, RESTORING, EVIDENCE_FOUND, NO_MATCH, NO_INDEX, FAILED }
    public enum ContextEligibility { INELIGIBLE, ELIGIBLE_DURABLE_SUCCESS }
    public enum SessionAction { EDIT_DRAFT, REMOVE_CAPTURE, SUBMIT, STOP, RETRY, COMPACT, CLOSE_TAB, DELETE, RECOVER }

    public sealed interface MessagePart permits TextPart, CodePart { String partId(); }

    /** Markdown source is retained exactly; HTML and syntax colors are derived rendering only. */
    public record TextPart(String partId, String text) implements MessagePart {
        public TextPart { requireId(partId, "partId"); Objects.requireNonNull(text, "text"); }
    }

    /** Typed code is rendered directly, never round-tripped through Markdown/HTML. */
    public record CodePart(String partId, String snapshotId, String captureId, PartKind kind,
                           FileSnapshotRef source, SourceRange range, String text) implements MessagePart {
        public CodePart {
            requireId(partId, "partId"); requireId(snapshotId, "snapshotId"); requireId(captureId, "captureId");
            Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(source, "source");
            Objects.requireNonNull(range, "range"); Objects.requireNonNull(text, "text");
        }
    }

    public static List<MessagePart> visibleParts(String text,List<CodePart> code,java.util.Map<String,Integer> offsets) { return visibleParts(text,code,offsets,code.stream().filter(c->c.kind()==PartKind.SELECTION).map(CodePart::captureId).toList()); }
    public static List<MessagePart> visibleParts(String text, List<CodePart> code, java.util.Map<String,Integer> offsets,List<String> order) {
        java.util.ArrayList<MessagePart> result=new java.util.ArrayList<>();
        var selections=code.stream().filter(c->c.kind()==PartKind.SELECTION)
                .sorted(java.util.Comparator.<CodePart>comparingInt(c->offsets.getOrDefault(c.captureId(),text.length())).thenComparingInt(c->order.indexOf(c.captureId()))).toList();
        int cursor=0,index=0;
        for(var selection:selections) {
            int offset=Math.max(cursor,Math.min(text.length(),offsets.getOrDefault(selection.captureId(),text.length())));
            if(offset>cursor)result.add(new TextPart("text-"+(index++),text.substring(cursor,offset)));
            result.add(selection);cursor=offset;
        }
        if(cursor<text.length())result.add(new TextPart("text-"+index,text.substring(cursor)));
        return List.copyOf(result);
    }

    public static List<String> orderedCaptures(List<String> available,List<String> requested) {
        java.util.LinkedHashSet<String> result=new java.util.LinkedHashSet<>();
        if(requested!=null)for(String id:requested)if(available.contains(id))result.add(id);
        result.addAll(available);return List.copyOf(result);
    }

    public record MessageView(String messageId, Role role, List<MessagePart> parts, Instant createdAt) {
        public MessageView {
            requireId(messageId, "messageId"); Objects.requireNonNull(role, "role");
            parts = List.copyOf(parts); Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    /** Ready means the complete same-version group exists; failed groups never supply half context. */
    public record DraftCapture(String captureId, CaptureState state, FileSnapshotRef source,
                               Optional<CapturedCodeGroup> captured, Optional<String> statusMessage) {
        public DraftCapture {
            requireId(captureId, "captureId"); Objects.requireNonNull(state, "state");
            Objects.requireNonNull(source, "source"); captured = Objects.requireNonNull(captured, "captured");
            statusMessage = Objects.requireNonNull(statusMessage, "statusMessage");
            if ((state == CaptureState.READY) != captured.isPresent()) {
                throw new IllegalArgumentException("Only a ready capture has a complete code group");
            }
            captured.ifPresent(group -> {
                if (!captureId.equals(group.start().captureId()) || !source.equals(group.start().source())) {
                    throw new IllegalArgumentException("Capture identity/version mismatch");
                }
            });
        }
    }

    public record DraftView(String draftId, long revision, String text, List<DraftCapture> captures,
                            SaveState saveState, java.util.Map<String,Integer> captureOffsets,List<String> captureOrder) {
        public DraftView(String id,long rev,String text,List<DraftCapture> captures,SaveState state,java.util.Map<String,Integer> offsets) { this(id,rev,text,captures,state,offsets,null); }
        public DraftView(String draftId,long revision,String text,List<DraftCapture> captures,SaveState saveState) { this(draftId,revision,text,captures,saveState,java.util.Map.of()); }
        public DraftView {
            requireId(draftId, "draftId"); requireRevision(revision); Objects.requireNonNull(text, "text");
            captures = List.copyOf(captures); Objects.requireNonNull(saveState, "saveState");
            captureOrder=orderedCaptures(captures.stream().map(DraftCapture::captureId).toList(),captureOrder);
            captureOffsets = captureOffsets == null ? java.util.Map.of() : java.util.Map.copyOf(captureOffsets);
            for (var entry : captureOffsets.entrySet()) if (entry.getValue()<0 || entry.getValue()>text.length()) throw new IllegalArgumentException("Capture offset outside text");
        }
    }

    /** Fixed imported source identity and physical page/range, not a link to the newest material. */
    public record Citation(String evidenceId, String documentId, String sourceVersionId,
                           String materialName, int firstPhysicalPage, int lastPhysicalPage) {
        public Citation {
            requireId(evidenceId, "evidenceId"); requireId(documentId, "documentId");
            requireId(sourceVersionId, "sourceVersionId"); requireId(materialName, "materialName");
            if (firstPhysicalPage < 1 || lastPhysicalPage < firstPhysicalPage) {
                throw new IllegalArgumentException("Invalid physical page range");
            }
        }
    }

    public record AnswerBasis(AnswerMode mode, RetrievalStatus retrievalStatus, String explanation,
                              List<Citation> citations) {
        public AnswerBasis {
            Objects.requireNonNull(mode, "mode"); Objects.requireNonNull(retrievalStatus, "retrievalStatus");
            Objects.requireNonNull(explanation, "explanation"); citations = List.copyOf(citations);
            if (mode == AnswerMode.GENERAL && !citations.isEmpty()) {
                throw new IllegalArgumentException("General branch cannot claim course citations");
            }
        }
    }

    /** Null/absent usage is unavailable, never fabricated as zero. Counts are step-scoped. */
    public record TokenUsage(long inputTokens, long outputTokens) {
        public TokenUsage {
            if (inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("Negative token count");
        }
    }

    public record ToolView(String toolCallId, String name, String argumentsText, ToolStatus status,
                           Optional<String> observationText, List<Citation> evidence) {
        public ToolView {
            requireId(toolCallId, "toolCallId"); requireId(name, "name");
            Objects.requireNonNull(argumentsText, "argumentsText"); Objects.requireNonNull(status, "status");
            observationText = Objects.requireNonNull(observationText, "observationText"); evidence = List.copyOf(evidence);
        }
    }

    /** The body is cumulative for this step only. Thinking/tool data belongs in a folded process view. */
    public record ModelStepView(String stepId, long contentRevision, long throughSeq, StepStatus status,
                                String markdown, Optional<String> thinkingText, List<ToolView> tools,
                                Optional<String> finishReason, Optional<TokenUsage> usage) {
        public ModelStepView {
            requireId(stepId, "stepId"); requireRevision(contentRevision); requireRevision(throughSeq);
            Objects.requireNonNull(status, "status"); Objects.requireNonNull(markdown, "markdown");
            thinkingText = Objects.requireNonNull(thinkingText, "thinkingText"); tools = List.copyOf(tools);
            finishReason = Objects.requireNonNull(finishReason, "finishReason"); usage = Objects.requireNonNull(usage, "usage");
        }
    }

    public record BranchView(String branchId, BranchStatus status, AnswerBasis basis, List<ModelStepView> steps) {
        public BranchView {
            requireId(branchId, "branchId"); Objects.requireNonNull(status, "status");
            Objects.requireNonNull(basis, "basis"); steps = List.copyOf(steps);
        }
    }

    /** A terminal attempt is retained, including incomplete text. Retry appends another attempt. */
    public record AttemptView(String attemptId, String operationId, long viewRevision, AttemptStatus status,
                              SaveState saveState, ContextEligibility contextEligibility,
                              Optional<String> effectiveBranchId, Optional<String> finalStepId,
                              List<BranchView> branches) {
        public AttemptView {
            requireId(attemptId, "attemptId"); requireId(operationId, "operationId"); requireRevision(viewRevision);
            Objects.requireNonNull(status, "status"); Objects.requireNonNull(saveState, "saveState");
            Objects.requireNonNull(contextEligibility, "contextEligibility");
            effectiveBranchId = Objects.requireNonNull(effectiveBranchId, "effectiveBranchId");
            finalStepId = Objects.requireNonNull(finalStepId, "finalStepId"); branches = List.copyOf(branches);
            if (contextEligibility == ContextEligibility.ELIGIBLE_DURABLE_SUCCESS
                    && (status != AttemptStatus.SUCCEEDED || saveState != SaveState.SAVED
                    || effectiveBranchId.isEmpty() || finalStepId.isEmpty())) {
                throw new IllegalArgumentException("Only saved, successful final output is eligible");
            }
        }
    }

    public record TurnView(String turnId, String requestId, MessageView userMessage, List<AttemptView> attempts) {
        public TurnView {
            requireId(turnId, "turnId"); requireId(requestId, "requestId");
            Objects.requireNonNull(userMessage, "userMessage"); attempts = List.copyOf(attempts);
            if (userMessage.role() != Role.USER) throw new IllegalArgumentException("Turn input must be a user message");
        }
    }

    public record ActiveOperation(String operationId, OperationKind kind, Optional<String> attemptId) {
        public ActiveOperation {
            requireId(operationId, "operationId"); Objects.requireNonNull(kind, "kind");
            attemptId = Objects.requireNonNull(attemptId, "attemptId");
        }
    }

    public record SessionViewState(String sessionId, String title, long revision, Lifecycle lifecycle,
                                   DraftView draft, List<TurnView> turns, Optional<ActiveOperation> activeOperation,
                                   boolean writesBlocked, Set<SessionAction> allowedActions, List<Notice> notices,
                                   List<EditContracts.ProposalView> editProposals) {
        public SessionViewState(String sessionId,String title,long revision,Lifecycle lifecycle,DraftView draft,List<TurnView> turns,
                                Optional<ActiveOperation> activeOperation,boolean writesBlocked,Set<SessionAction> allowedActions,List<Notice> notices) {
            this(sessionId,title,revision,lifecycle,draft,turns,activeOperation,writesBlocked,allowedActions,notices,List.of());
        }
        public SessionViewState {
            requireId(sessionId, "sessionId"); Objects.requireNonNull(title, "title"); requireRevision(revision);
            Objects.requireNonNull(lifecycle, "lifecycle"); Objects.requireNonNull(draft, "draft"); turns = List.copyOf(turns);
            activeOperation = Objects.requireNonNull(activeOperation, "activeOperation");
            allowedActions = Set.copyOf(allowedActions); notices = List.copyOf(notices);
            editProposals=List.copyOf(editProposals);
        }
    }

    public record ProjectChatViewState(String projectId, String projectInstanceId, long revision,
                                       List<SessionViewState> sessions, List<String> openSessionIds,
                                       Optional<String> activeSessionId, List<Notice> notices) {
        public ProjectChatViewState {
            requireId(projectId, "projectId"); requireId(projectInstanceId, "projectInstanceId"); requireRevision(revision);
            sessions = List.copyOf(sessions); openSessionIds = List.copyOf(openSessionIds);
            activeSessionId = Objects.requireNonNull(activeSessionId, "activeSessionId"); notices = List.copyOf(notices);
            Set<String> ids = sessions.stream().map(SessionViewState::sessionId).collect(java.util.stream.Collectors.toSet());
            if (ids.size() != sessions.size() || openSessionIds.stream().distinct().count() != openSessionIds.size()
                    || !ids.containsAll(openSessionIds) || (activeSessionId.isPresent() && !openSessionIds.contains(activeSessionId.get()))) {
                throw new IllegalArgumentException("Tab/session identity mismatch");
            }
        }
    }

    public record EventMeta(String commandId, String projectInstanceId, Optional<String> sessionId) {
        public EventMeta {
            requireId(commandId, "commandId"); requireId(projectInstanceId, "projectInstanceId");
            sessionId = Objects.requireNonNull(sessionId, "sessionId"); sessionId.ifPresent(id -> requireId(id, "sessionId"));
        }
    }

    public sealed interface ViewEvent permits CreateSession, SelectSession, UpdateDraftText, SubmitDraft,
            CloseSessionTab, OpenSessionTab, DeleteSession, RemoveDraftCapture, StopOperation, RetryAttempt,
            ManualCompact, ReorderTabs, OpenCitation, RecoverSession, OpenEditPreview, ApproveEditProposal, RejectEditProposal, InspectEditApplication { EventMeta meta(); }
    public record InspectEditApplication(EventMeta meta,EditContracts.ProposalRef proposal) implements ViewEvent {
        public InspectEditApplication { target(meta);Objects.requireNonNull(proposal); }
    }

    private static void target(EventMeta meta) {
        if (Objects.requireNonNull(meta, "meta").sessionId().isEmpty()) {
            throw new IllegalArgumentException("Explicit session target required");
        }
    }

    public record OpenEditPreview(EventMeta meta, EditContracts.ProposalRef proposal) implements ViewEvent {
        public OpenEditPreview { target(meta); Objects.requireNonNull(proposal); }
    }
    public record ApproveEditProposal(EventMeta meta, EditContracts.PreviewToken preview) implements ViewEvent {
        public ApproveEditProposal { target(meta); Objects.requireNonNull(preview); }
    }
    public record RejectEditProposal(EventMeta meta, EditContracts.PreviewToken preview) implements ViewEvent {
        public RejectEditProposal { target(meta); Objects.requireNonNull(preview); }
    }
    public record CreateSession(EventMeta meta, String title) implements ViewEvent {
        public CreateSession {
            Objects.requireNonNull(meta, "meta"); Objects.requireNonNull(title, "title");
            if (meta.sessionId().isPresent()) throw new IllegalArgumentException("Core assigns the new session identity");
        }
    }
    public record SelectSession(EventMeta meta) implements ViewEvent { public SelectSession { target(meta); } }
    public record OpenSessionTab(EventMeta meta) implements ViewEvent { public OpenSessionTab { target(meta); } }
    public record CloseSessionTab(EventMeta meta) implements ViewEvent { public CloseSessionTab { target(meta); } }
    public record DeleteSession(EventMeta meta) implements ViewEvent { public DeleteSession { target(meta); } }
    public record ManualCompact(EventMeta meta) implements ViewEvent { public ManualCompact { target(meta); } }
    /** Retry local storage coordination only; never resends a model request. */
    public record RecoverSession(EventMeta meta) implements ViewEvent { public RecoverSession { target(meta); } }

    public record UpdateDraftText(EventMeta meta, String draftId, long expectedDraftRevision, String text,
                                  Optional<java.util.Map<String,Integer>> captureOffsets,Optional<List<String>> captureOrder) implements ViewEvent {
        public UpdateDraftText(EventMeta meta,String id,long rev,String text,Optional<java.util.Map<String,Integer>> offsets) { this(meta,id,rev,text,offsets,Optional.empty()); }
        public UpdateDraftText(EventMeta meta,String draftId,long revision,String text) { this(meta,draftId,revision,text,Optional.empty()); }
        public UpdateDraftText { target(meta); requireId(draftId, "draftId"); requireRevision(expectedDraftRevision); Objects.requireNonNull(text, "text"); captureOffsets = Objects.requireNonNull(captureOffsets).map(java.util.Map::copyOf); captureOrder=Objects.requireNonNull(captureOrder).map(List::copyOf); }
    }
    public record SubmitDraft(EventMeta meta, String draftId, long expectedDraftRevision) implements ViewEvent {
        public SubmitDraft { target(meta); requireId(draftId, "draftId"); requireRevision(expectedDraftRevision); }
    }
    public record RemoveDraftCapture(EventMeta meta, String draftId, long expectedDraftRevision, String captureId) implements ViewEvent {
        public RemoveDraftCapture { target(meta); requireId(draftId, "draftId"); requireRevision(expectedDraftRevision); requireId(captureId, "captureId"); }
    }
    public record StopOperation(EventMeta meta, String operationId) implements ViewEvent {
        public StopOperation { target(meta); requireId(operationId, "operationId"); }
    }
    public record RetryAttempt(EventMeta meta, String turnId, String previousAttemptId) implements ViewEvent {
        public RetryAttempt { target(meta); requireId(turnId, "turnId"); requireId(previousAttemptId, "previousAttemptId"); }
    }
    public record OpenCitation(EventMeta meta, Citation citation) implements ViewEvent {
        public OpenCitation { target(meta); Objects.requireNonNull(citation, "citation"); }
    }
    public record ReorderTabs(EventMeta meta, List<String> orderedSessionIds) implements ViewEvent {
        public ReorderTabs { Objects.requireNonNull(meta, "meta"); orderedSessionIds = List.copyOf(orderedSessionIds); }
    }
}
