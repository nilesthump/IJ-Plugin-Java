package edu.courseassist.core.react;

import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.SaveState;
import edu.courseassist.core.corpus.CorpusData.Evidence;
import java.util.*;
import static edu.courseassist.core.react.ReactData.*;

/** Cumulative DTO projection; consuming this never starts a model subscription. */
public final class ReactViews {
    private ReactViews() {}
    public static AttemptView attempt(Snapshot snapshot) {
        var branches = snapshot.branches().stream().map(b -> {
            boolean grounded = b.retrieval() == RetrievalStatus.EVIDENCE_FOUND;
            List<Citation> cited = b.id().equals(snapshot.effectiveBranchId()) ? snapshot.citations() : List.of();
            if (snapshot.status()!=AttemptStatus.SUCCEEDED || !b.id().equals(snapshot.effectiveBranchId())) {
                // Display labels only for actual supplied evidence mentioned by this displayed body.
                // This does not change the archived raw response or durable citation eligibility.
                Map<String,Citation> visible=new LinkedHashMap<>();
                for(var step:b.steps()) for(var evidence:step.suppliedEvidence())
                    if(step.body().contains("[[course:"+evidence.chunkId()+"]]")) visible.put(evidence.chunkId(),citation(evidence));
                cited=List.copyOf(visible.values());
            }
            var basis = new AnswerBasis(grounded ? AnswerMode.COURSE_GROUNDED : AnswerMode.GENERAL, b.retrieval(), b.explanation(), cited);
            var steps = b.steps().stream().map(s -> new ModelStepView(s.id(), snapshot.revision(), s.throughSeq(), s.status(), s.body(),
                    s.thinking().isEmpty() ? Optional.empty() : Optional.of(s.thinking()), s.tools().stream().map(t ->
                    new ToolView(t.call().id().isEmpty() ? "pending:" + s.id() + ":" + s.tools().indexOf(t) : t.call().id(),
                            t.call().name().isEmpty() ? "pending" : t.call().name(), t.call().arguments(), t.status(),
                            t.observation().isEmpty() ? Optional.empty() : Optional.of(t.observation()), t.evidence().stream().map(ReactViews::citation).toList())).toList(),
                    s.finishReason().isEmpty() ? Optional.empty() : Optional.of(s.finishReason()), s.usage())).toList();
            return new BranchView(b.id(), b.status(), basis, steps);
        }).toList();
        return new AttemptView(snapshot.identity().attemptId(), snapshot.identity().operationId(), snapshot.revision(), snapshot.status(),
                snapshot.saved() ? SaveState.SAVED : SaveState.UNSAVED,
                snapshot.eligible() ? ContextEligibility.ELIGIBLE_DURABLE_SUCCESS : ContextEligibility.INELIGIBLE,
                optional(snapshot.effectiveBranchId()), optional(snapshot.finalStepId()), branches);
    }
    private static Optional<String> optional(String id) { return id.isEmpty() ? Optional.empty() : Optional.of(id); }
    private static Citation citation(Evidence e) {
        return new Citation(e.chunkId(), e.source().documentId(), e.source().versionId(), e.source().name(), e.physicalPage(), e.physicalPage());
    }
}
