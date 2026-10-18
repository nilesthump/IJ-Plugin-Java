package edu.courseassist.core.react;

import edu.courseassist.core.corpus.CourseLibrary;
import edu.courseassist.core.corpus.CorpusData.*;
import edu.courseassist.model.CourseEmbeddings;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static edu.courseassist.core.react.ReactData.*;
import tools.jackson.databind.json.JsonMapper;

/** An attempt owns precisely one lease, including the fixed no-library state. */
public final class CourseTools implements AutoCloseable {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    public static final List<ToolDefinition> DEFINITIONS = List.of(
            definition("searchCourse", "Search the fixed course generation", "query"),
            definition("readCourseEvidence", "Read evidence already supplied in this attempt", "ref"));
    private final CourseLibrary.Lease lease;
    private final CourseEmbeddings embeddings;
    private final SearchSettings settings;
    public CourseTools(CourseLibrary.Lease lease, CourseEmbeddings embeddings, SearchSettings settings) {
        this.lease = Objects.requireNonNull(lease); this.embeddings = Objects.requireNonNull(embeddings); this.settings = Objects.requireNonNull(settings);
    }
    public CompletableFuture<Boolean> hasMaterials() { return lease.hasMaterials(); }
    public CompletableFuture<List<Evidence>> search(String query, BooleanSupplier permitted) { return lease.search(query, embeddings, settings, permitted); }
    public CompletableFuture<Evidence> read(String chunkId) { return lease.read(chunkId); }
    public String generation() { return lease.base().generationId().orElse(""); }
    public static String argument(Call call) {
        if (!Set.of("searchCourse", "readCourseEvidence").contains(call.name())) throw new ProtocolFailure("Unknown course tool");
        var node = JSON.readTree(call.arguments());
        String field = call.name().equals("searchCourse") ? "query" : "ref";
        if (!node.isObject() || node.size() != 1 || !node.hasNonNull(field) || !node.get(field).isString()
                || node.get(field).asString().isBlank()) throw new ProtocolFailure("Invalid complete tool arguments");
        return node.get(field).asString();
    }
    private static ToolDefinition definition(String name, String description, String field) {
        return new ToolDefinition(name, description, ReactContext.json(Map.of("type", "object", "properties", Map.of(field, Map.of("type", "string")),
                "required", List.of(field), "additionalProperties", false)));
    }
    @Override public void close() { lease.close(); }
}
