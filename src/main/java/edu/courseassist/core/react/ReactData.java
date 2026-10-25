package edu.courseassist.core.react;

import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.core.corpus.CorpusData.Evidence;
import java.time.Duration;
import java.util.*;

/** Frozen attempt input and cumulative archives. Protocol fields never become display Markdown. */
public final class ReactData {
    private ReactData() {}
    public record Identity(String projectId, String projectInstanceId, String sessionId, String turnId,
                           String operationId, String attemptId, long runtimeEpoch) {
        public Identity {
            for (String id : List.of(projectId, projectInstanceId, sessionId, turnId, operationId, attemptId))
                if (id.isBlank()) throw new IllegalArgumentException("Blank attempt identity");
            if (runtimeEpoch < 0) throw new IllegalArgumentException("Negative epoch");
        }
    }
    public record Input(String question, List<CodePart> code, Map<String,Integer> captureOffsets,List<String> captureOrder) {
        public Input(String question,List<CodePart> code,Map<String,Integer> offsets) { this(question,code,offsets,null); }
        public Input(String question,List<CodePart> code) { this(question,code,Map.of()); }
        public Input { Objects.requireNonNull(question); code = List.copyOf(code); captureOffsets=captureOffsets==null?Map.of():Map.copyOf(captureOffsets); captureOrder=edu.courseassist.contract.ChatContracts.orderedCaptures(code.stream().filter(c->c.kind()==PartKind.SELECTION).map(CodePart::captureId).toList(),captureOrder); }
    }
    public record Call(String id, String name, String arguments) {
        public Call { Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(arguments); }
    }
    public record Message(String role, String content, String thinking, List<Call> calls, String callId) {
        public Message {
            if (!Set.of("system", "user", "assistant", "tool").contains(role)) throw new IllegalArgumentException("Invalid role");
            Objects.requireNonNull(content); Objects.requireNonNull(thinking); calls = List.copyOf(calls); Objects.requireNonNull(callId);
        }
        public static Message text(String role, String body) { return new Message(role, body, "", List.of(), ""); }
    }
    public record ToolDefinition(String name, String description, String schema) {}
    public record Request(List<Message> messages, List<ToolDefinition> tools, int outputTokens) {
        public Request { messages = List.copyOf(messages); tools = List.copyOf(tools); if (outputTokens < 1) throw new IllegalArgumentException(); }
    }
    public sealed interface Frame permits Body, Thinking, ToolDelta, End, Usage {}
    public record Body(String text) implements Frame { public Body { Objects.requireNonNull(text); } }
    public record Thinking(String text) implements Frame { public Thinking { Objects.requireNonNull(text); } }
    /** Index is step-local; id/name may be empty on continuation frames. Arguments are exact deltas. */
    public record ToolDelta(int index, String id, String name, String arguments) implements Frame {
        public ToolDelta { if (index < 0) throw new IllegalArgumentException(); Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(arguments); }
    }
    public record End(String reason) implements Frame { public End { Objects.requireNonNull(reason); } }
    public record Usage(TokenUsage value) implements Frame { public Usage { Objects.requireNonNull(value); } }
    public interface Listener { void frame(Frame frame); void failed(Throwable failure); void complete(); }
    @FunctionalInterface public interface Cancel { void cancel(); }
    /** Exactly one invocation and subscription for a model step. No internal tools, memory or retries. */
    @FunctionalInterface public interface Model { Cancel subscribe(Request request, Listener listener); }
    public enum ErrorPolicy { DEVELOPMENT, PRODUCT }
    public record Limits(int maxSteps, int maxTools, int maxToolChars, int maxOutputChars,
                         long contextTokens, int outputTokens, Duration attemptTimeout, int partialSaveChars) {
        public Limits {
            if (maxSteps < 1 || maxTools < 0 || maxToolChars < 1 || maxOutputChars < 1 || contextTokens < 1
                    || outputTokens < 1 || outputTokens >= contextTokens || partialSaveChars < 1
                    || attemptTimeout == null || attemptTimeout.isNegative() || attemptTimeout.isZero()) throw new IllegalArgumentException("Invalid finite limits");
        }
    }
    public record ToolTrace(Call call, ToolStatus status, String observation, List<Evidence> evidence) {
        public ToolTrace { evidence = List.copyOf(evidence); }
    }
    public record Step(String id, long throughSeq, StepStatus status, String body, String thinking,
                       List<ToolTrace> tools, String finishReason, Optional<TokenUsage> usage,
                       List<Evidence> suppliedEvidence) {
        public Step { tools = List.copyOf(tools); usage = Objects.requireNonNull(usage); suppliedEvidence = List.copyOf(suppliedEvidence); }
    }
    public record Branch(String id, BranchStatus status, RetrievalStatus retrieval, String explanation,
                         List<Step> steps, List<Message> protocol) {
        public Branch { steps = List.copyOf(steps); protocol = List.copyOf(protocol); }
    }
    public record Snapshot(Identity identity, Input input, long revision, long throughSeq, AttemptStatus status,
                           boolean saved, String effectiveBranchId, String finalStepId,
                           List<Branch> branches, List<Citation> citations, String failureType) {
        public Snapshot { branches = List.copyOf(branches); citations = List.copyOf(citations); }
        public boolean eligible() {
            return saved && status == AttemptStatus.SUCCEEDED && branches.stream().anyMatch(b -> b.id().equals(effectiveBranchId)
                    && b.status() == BranchStatus.COMPLETED && b.steps().stream().anyMatch(s -> s.id().equals(finalStepId) && s.status() == StepStatus.FINISHED));
        }
    }
    public record HistoryTurn(Input input, List<Snapshot> attempts) { public HistoryTurn { attempts = List.copyOf(attempts); } }
    public static class ProtocolFailure extends IllegalStateException { public ProtocolFailure(String message) { super(message); } }
    public static final class Limited extends IllegalStateException { public Limited(String message) { super(message); } }
    @FunctionalInterface public interface Budget { long tokens(Request completeRequest); }
    /** Must check ownership/LIVE/instance/operation in the same transaction as the write. */
    public interface Journal { void create(Snapshot initial); void save(Snapshot snapshot, boolean coordinator); }
}
