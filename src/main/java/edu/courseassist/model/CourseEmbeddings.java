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

    final class InvalidResponse extends IllegalArgumentException {
        private InvalidResponse(String message) { super(message); }
        private InvalidResponse(String message, Throwable cause) { super(message, cause); }
    }

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
                        throw new InvalidResponse("嵌入响应数量或索引不正确，预期单条且 index=0");
                    float[] vector = response.getResult().getOutput();
                    if (vector == null || vector.length != space.dimensions())
                        throw new InvalidResponse("嵌入维度不匹配：配置 " + space.dimensions() + "，服务实际返回 " + (vector == null ? 0 : vector.length) + "；请核对模型支持的维度，原资料版本未改变");
                    try { validateVector(vector, space.dimensions()); }
                    catch (IllegalArgumentException invalid) { throw new InvalidResponse("嵌入服务返回零向量或非有限数值，无法建立课程索引", invalid); }
                    return vector.clone();
                });
            }
        };
    }
}
