package edu.courseassist.core.session;

import edu.courseassist.core.corpus.CorpusData.*;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.model.CourseEmbeddings;
import java.util.Objects;

/** Constructed locally at enable; no connectivity test or implicit provider/model selection. */
public record RuntimeResources(Model model, CourseEmbeddings embeddings, Settings corpus,
                               SearchSettings search, Limits limits, ContextPlanner.Settings context,
                               Budget budget, ErrorPolicy policy, AutoCloseable transport) implements AutoCloseable {
    public RuntimeResources {
        for(Object value:new Object[]{model,embeddings,corpus,search,limits,context,budget,policy,transport})Objects.requireNonNull(value);
        if(!corpus.space().equals(embeddings.space())||context.inputLimit()>limits.contextTokens()-limits.outputTokens())
            throw new IllegalArgumentException("Inconsistent explicit runtime settings");
    }
    @Override public void close() throws Exception {transport.close();}
}
