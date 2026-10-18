package edu.courseassist.fixtures;

import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CaptureContracts.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.SaveState;
import edu.courseassist.contract.PdfContracts.*;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * SYNTHETIC presentation data only. No model, database, embedding, PDF parsing, or recovered state.
 * Names beginning "synthetic-" are fixture identities, never deployed service choices.
 */
public final class SyntheticStates {
    private SyntheticStates() {}
    public static final String PROJECT_ID = "synthetic-project";
    public static final String PROJECT_INSTANCE_ID = "synthetic-open-1";
    public static final String SESSION_ID = "synthetic-session-1";
    public static final Instant TIME = Instant.parse("2026-10-18T00:00:00Z");
    public enum Scenario { RUNNING_TOOLS, COMPLETED, INTERRUPTED, UNSAVED, DELETING }

    public static SelectionStart selection() {
        return new SelectionStart("synthetic-capture-1", PROJECT_INSTANCE_ID,
                new FileSnapshotRef("synthetic:///Demo.java", 7, "JAVA"), "return value + 1;",
                new SourceRange(3, 9, 3, 26));
    }

    public static CapturedCodeGroup codeGroup() {
        return new CapturedCodeGroup(selection(), List.of(
                new AutomaticContext(ContextKind.METHOD, ContextStatus.CAPTURED,
                        Optional.of("int increment(int value) {\n    return value + 1;\n}"),
                        Optional.of(new SourceRange(2, 5, 4, 6)), Optional.empty()),
                new AutomaticContext(ContextKind.CLASS_STRUCTURE, ContextStatus.CAPTURED,
                        Optional.of("class Demo {\n    private int count;\n    // method bodies omitted from structure\n}"),
                        Optional.of(new SourceRange(1, 1, 6, 2)), Optional.empty())));
    }

    public static ProjectChatViewState project() { return project(Scenario.RUNNING_TOOLS); }

    public static ProjectChatViewState project(Scenario scenario) {
        DraftView draft = new DraftView("synthetic-draft-1", 5, "这段代码如何对应课程概念？",
                List.of(new DraftCapture(selection().captureId(), CaptureState.READY, selection().source(),
                        Optional.of(codeGroup()), Optional.empty())),
                scenario == Scenario.UNSAVED ? SaveState.FAILED : SaveState.SAVED);
        Citation citation = new Citation("synthetic-evidence-1", "synthetic-document-1", "synthetic-source-v1",
                "合成课程资料（非实际PDF）", 1, 1);
        AnswerBasis basis = new AnswerBasis(AnswerMode.COURSE_GROUNDED, RetrievalStatus.EVIDENCE_FOUND,
                "合成样例：依据固定导入版本的物理第1页。", List.of(citation));
        ToolView firstTool = new ToolView("synthetic-tool-1", "searchCourse", "{\"query\":\"increment\"}",
                ToolStatus.SUCCEEDED, Optional.of("合成检索片段"), List.of(citation));
        ModelStepView first = new ModelStepView("synthetic-step-1", 3, 3, StepStatus.FINISHED,
                "需要补查课程片段。", Optional.empty(), List.of(firstTool), Optional.of("tool_calls"), Optional.empty());
        ModelStepView second = new ModelStepView("synthetic-step-2", 4, 7,
                scenario == Scenario.RUNNING_TOOLS ? StepStatus.STREAMING
                        : scenario == Scenario.COMPLETED || scenario == Scenario.UNSAVED ? StepStatus.FINISHED : StepStatus.INCOMPLETE,
                "这里返回参数加一。\n\n```java\nreturn value + 1;\n", Optional.of("合成供应商思考字段，仅演示折叠呈现。"),
                List.of(), scenario == Scenario.COMPLETED || scenario == Scenario.UNSAVED ? Optional.of("stop") : Optional.empty(),
                Optional.empty());
        AttemptStatus status = switch (scenario) {
            case RUNNING_TOOLS -> AttemptStatus.RUNNING;
            case COMPLETED -> AttemptStatus.SUCCEEDED;
            case UNSAVED -> AttemptStatus.SAVING;
            case INTERRUPTED, DELETING -> AttemptStatus.INTERRUPTED;
        };
        BranchView branch = new BranchView("synthetic-branch-1",
                scenario == Scenario.COMPLETED ? BranchStatus.COMPLETED : BranchStatus.ACTIVE, basis, List.of(first, second));
        AttemptView attempt = new AttemptView("synthetic-attempt-1", "synthetic-operation-1", 7, status,
                scenario == Scenario.UNSAVED ? SaveState.FAILED : SaveState.SAVED,
                scenario == Scenario.COMPLETED ? ContextEligibility.ELIGIBLE_DURABLE_SUCCESS : ContextEligibility.INELIGIBLE,
                Optional.of(branch.branchId()), scenario == Scenario.COMPLETED ? Optional.of(second.stepId()) : Optional.empty(), List.of(branch));
        MessageView user = new MessageView("synthetic-message-1", Role.USER,
                List.of(new TextPart("synthetic-part-text", "解释自增方法。"),
                        new CodePart("synthetic-part-code", "synthetic-snapshot-1", selection().captureId(), PartKind.SELECTION,
                                selection().source(), selection().selection(), selection().selectedText())), TIME);
        boolean busy = scenario == Scenario.RUNNING_TOOLS;
        SessionViewState main = new SessionViewState(SESSION_ID, "合成会话一", 9,
                scenario == Scenario.DELETING ? Lifecycle.DELETING : Lifecycle.LIVE,
                draft, List.of(new TurnView("synthetic-turn-1", "synthetic-request-1", user, List.of(attempt))),
                busy ? Optional.of(new ActiveOperation("synthetic-operation-1", OperationKind.GENERATE, Optional.of(attempt.attemptId()))) : Optional.empty(),
                scenario == Scenario.UNSAVED || scenario == Scenario.DELETING,
                scenario == Scenario.DELETING ? EnumSet.noneOf(SessionAction.class)
                        : busy ? EnumSet.of(SessionAction.EDIT_DRAFT, SessionAction.REMOVE_CAPTURE, SessionAction.STOP,
                        SessionAction.CLOSE_TAB, SessionAction.DELETE)
                        : scenario == Scenario.UNSAVED ? EnumSet.of(SessionAction.CLOSE_TAB, SessionAction.DELETE)
                        : EnumSet.of(SessionAction.EDIT_DRAFT, SessionAction.REMOVE_CAPTURE, SessionAction.SUBMIT,
                        SessionAction.RETRY, SessionAction.COMPACT, SessionAction.CLOSE_TAB, SessionAction.DELETE), List.of());
        SessionViewState other = new SessionViewState("synthetic-session-2", "合成会话二", 1, Lifecycle.LIVE,
                new DraftView("synthetic-draft-2", 0, "独立草稿", List.of(), SaveState.SAVED), List.of(), Optional.empty(), false,
                EnumSet.of(SessionAction.EDIT_DRAFT, SessionAction.SUBMIT, SessionAction.COMPACT, SessionAction.CLOSE_TAB, SessionAction.DELETE), List.of());
        return new ProjectChatViewState(PROJECT_ID, PROJECT_INSTANCE_ID, 12, List.of(main, other),
                List.of(main.sessionId(), other.sessionId()), Optional.of(main.sessionId()), List.of());
    }

    public static ApplicationViewState application() {
        ProviderProfile generation = new ProviderProfile("synthetic-generation", "synthetic-provider",
                URI.create("https://example.invalid/v1"), "synthetic-chat-model", Optional.empty());
        ProviderProfile embedding = new ProviderProfile("synthetic-embedding", "synthetic-provider",
                URI.create("https://example.invalid/v1"), "synthetic-embedding-model", Optional.empty());
        return new ApplicationViewState(4, WorkState.OFF, 2, 8,
                Optional.of(new ProfileView(generation, CredentialStatus.MISSING)),
                Optional.of(new ProfileView(embedding, CredentialStatus.MISSING)), CorpusState.READY,
                new CorpusBase(1, Optional.of("synthetic-generation-1")),
                List.of(new MaterialView("synthetic-document-1", "合成资料", 2, Optional.of("synthetic-source-v1"),
                        MaterialStatus.READY, Optional.of(3), TIME, Optional.of("合成状态，未读取真实PDF"))), false, List.of());
    }

    /** Demonstrates preserved physical numbering, including the middle blank page. */
    public static PdfReadResult threePagePdf(PdfReadRequest request) {
        return new PdfReadResult(request, 3, List.of(
                new PdfPage(1, "Synthetic first physical page.\n", PageTextStatus.TEXT, Optional.empty()),
                new PdfPage(2, "", PageTextStatus.EMPTY, Optional.empty()),
                new PdfPage(3, "Synthetic third physical page.\n", PageTextStatus.TEXT, Optional.of("1"))));
    }
}
