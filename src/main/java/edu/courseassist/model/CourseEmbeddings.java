package edu.courseassist.model;

import edu.courseassist.core.corpus.CorpusData.EmbeddingSpace;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import java.util.List;
import java.util.Objects;
import static edu.courseassist.core.corpus.CorpusData.validateVector;

/** Credentials/client lifetime and zero-retry policy belong to the application runtime factory.
 * This adapter never discovers models, retries, normalizes text or guesses dimension/encoding. */
public interface CourseEmbeddings {
    EmbeddingSpace space();
    float[] document(String exactChunkText) throws Exception;
    float[] query(String exactQuery) throws Exception;

    static CourseEmbeddings springAi(EmbeddingModel model, EmbeddingSpace space) {
        Objects.requireNonNull(model); Objects.requireNonNull(space);
        return new CourseEmbeddings() {
            @Override public EmbeddingSpace space() { return space; }
            @Override public float[] document(String text) throws Exception { return embed(space.documentPrefix() + text); }
            @Override public float[] query(String text) throws Exception { return embed(space.queryPrefix() + text); }
            private float[] embed(String text) throws Exception {
                return ModelClassLoading.call(() -> {
                    var response = model.call(new EmbeddingRequest(List.of(text), EmbeddingOptions.builder()
                            .model(space.model()).dimensions(space.dimensions()).build()));
                    if (response.getResults().size() != 1 || !Integer.valueOf(0).equals(response.getResult().getIndex()))
                        throw new IllegalArgumentException("Embedding response count/index mismatch");
                    float[] vector = response.getResult().getOutput();
                    validateVector(vector, space.dimensions()); return vector.clone();
                });
            }
        };
    }
}
