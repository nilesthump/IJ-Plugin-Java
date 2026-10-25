package edu.courseassist.core.react;

import java.util.*;
import static edu.courseassist.core.react.ReactData.*;
import tools.jackson.databind.json.JsonMapper;

/** One eligibility projection for requests, retrieval rewriting, summaries and future checkpoint callers.
 * This group does not implement compaction or accept arbitrary prebuilt summaries/protocol caches. */
public final class ReactContext {
    private ReactContext() {}
    private static final JsonMapper JSON = JsonMapper.builder().build();
    public static final String POLICY = "f0-durable-effective-branch-v1";
    public static String input(Input input) {
        var data=new LinkedHashMap<String,Object>();
        data.put("messageParts",edu.courseassist.contract.ChatContracts.visibleParts(input.question(),input.code(),input.captureOffsets(),input.captureOrder()));
        data.put("implicitContext",input.code().stream().filter(c->c.kind()!=edu.courseassist.contract.ChatContracts.PartKind.SELECTION).toList());
        return JSON.writeValueAsString(data);
    }
    public static List<Message> history(String projectId, String sessionId, List<HistoryTurn> turns) {
        List<Message> result = new ArrayList<>();
        for (var turn : turns) {
            result.add(Message.text("user", input(turn.input())));
            Snapshot chosen = null;
            for (var attempt : turn.attempts()) {
                if (!attempt.identity().projectId().equals(projectId) || !attempt.identity().sessionId().equals(sessionId)
                        || !attempt.input().equals(turn.input())) throw new ProtocolFailure("History ownership/input mismatch");
                if (attempt.eligible()) chosen = attempt;
            }
            if (chosen != null) {
                var accepted = chosen;
                var branch = chosen.branches().stream().filter(b -> b.id().equals(accepted.effectiveBranchId())).findFirst().orElseThrow();
                validateProtocol(branch.protocol()); result.addAll(branch.protocol());
            }
        }
        return List.copyOf(result);
    }
    public static void validateProtocol(List<Message> messages) {
        Set<String> pending = new LinkedHashSet<>(), used = new HashSet<>();
        for (Message message : messages) {
            if (!pending.isEmpty() && !message.role().equals("tool")) throw new ProtocolFailure("Unpaired tool request");
            if (message.role().equals("tool")) {
                if (!message.calls().isEmpty() || !pending.remove(message.callId())) throw new ProtocolFailure("Unexpected tool observation");
            } else {
                if (!message.callId().isEmpty()) throw new ProtocolFailure("Unexpected tool call id");
                for (Call call : message.calls()) {
                    if (!message.role().equals("assistant") || call.id().isBlank() || call.name().isBlank()
                            || !used.add(call.id())) throw new ProtocolFailure("Invalid/duplicate tool call");
                    pending.add(call.id());
                }
            }
        }
        if (!pending.isEmpty()) throw new ProtocolFailure("Missing tool observation");
    }
    /** Conservative UTF-8 byte bound, including JSON framing, schema and output reservation supplied separately.
     * A provider tokenizer can replace this only after its exact model contract has been verified. */
    public static Budget utf8Budget() {
        return request -> JSON.writeValueAsBytes(request).length + 1024L + request.tools().size() * 512L
                + request.messages().stream().mapToLong(m -> 256L + m.calls().size() * 256L).sum();
    }
    public static String json(Object value) { return JSON.writeValueAsString(value); }
}
