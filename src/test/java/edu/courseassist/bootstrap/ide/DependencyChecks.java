package edu.courseassist.bootstrap.ide;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.sun.net.httpserver.HttpServer;
import edu.courseassist.storage.H2Store;
import edu.courseassist.model.ModelClassLoading;
import edu.courseassist.model.SpringAiTransport;
import edu.courseassist.model.CourseEmbeddings;
import edu.courseassist.core.corpus.CorpusData.EmbeddingSpace;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Only synthetic loopback responses. Shared by JUnit and the actual IDE plugin-classloader check. */
public final class DependencyChecks {
    private DependencyChecks() {}

    public static void run(Path scratch) throws Exception {
        try (var store = new H2Store(scratch)) {
            require("persisted".equals(store.transaction(connection -> {
                try (var sql = connection.createStatement()) {
                    sql.execute("CREATE TABLE IF NOT EXISTS runtime_probe(body VARCHAR)");
                    sql.executeUpdate("DELETE FROM runtime_probe");
                    sql.executeUpdate("INSERT INTO runtime_probe VALUES('persisted')");
                }
                return "persisted";
            }).get(10, TimeUnit.SECONDS)), "H2 commit");
        }
        try (var reopened = new H2Store(scratch)) {
            require("persisted".equals(reopened.transaction(connection -> {
                try (var sql = connection.createStatement(); var row = sql.executeQuery("SELECT body FROM runtime_probe")) {
                    require(row.next(), "H2 row after reopen"); return row.getString(1);
                }
            }).get(10, TimeUnit.SECONDS)), "H2 reopen");
        }
        var support = VectorizationProvider.getInstance().getVectorTypeSupport();
        var vectors = new ListRandomAccessVectorValues(List.of(
                support.createFloatVector(new float[]{1, 0, 0}),
                support.createFloatVector(new float[]{0, 1, 0}),
                support.createFloatVector(new float[]{0, 0, 1})), 3);
        try (var pool = new ForkJoinPool(1);
             var builder = GraphIndexBuilder.builder(vectors, VectorSimilarityFunction.COSINE, 2)
                     .withBeamWidth(8).withNeighborOverflow(1.5f).withAlpha(1.2f)
                     .withSimdExecutor(pool).withParallelExecutor(pool).build();
             var graph = builder.build(vectors);
             var searcher = new GraphSearcher(graph)) {
            var result = searcher.search(DefaultSearchScoreProvider.exact(
                    vectors.getVector(1), VectorSimilarityFunction.COSINE, vectors), 1, Bits.ALL);
            require(result.getNodes().length == 1 && result.getNodes()[0].node == 1, "JVector query");
        }
        ModelClassLoading.call(() -> { springAiLoopback(); return null; });
        CorpusRuntimeChecks.run(scratch.resolve("corpus"));
    }

    private static void springAiLoopback() throws Exception {
        AtomicInteger chatRequests = new AtomicInteger();
        AtomicInteger embeddingRequests = new AtomicInteger();
        AtomicBoolean fail = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            chatRequests.incrementAndGet();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean stream = request.contains("\"stream\":true");
            String response = fail.get() ? "{\"error\":{\"message\":\"synthetic failure\"}}" : stream ? """
                    data: {"id":"local-stream","object":"chat.completion.chunk","created":1,"model":"synthetic","choices":[{"index":0,"delta":{"role":"assistant","content":"local "},"finish_reason":null}]}

                    data: {"id":"local-stream","object":"chat.completion.chunk","created":1,"model":"synthetic","choices":[{"index":0,"delta":{"content":"answer"},"finish_reason":"stop"}]}

                    data: [DONE]

                    """ : """
                    {"id":"local-chat","object":"chat.completion","created":1,"model":"synthetic",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"local answer"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":2,"completion_tokens":2,"total_tokens":4}}
                    """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", stream && !fail.get() ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(fail.get() ? 500 : 200, bytes.length);
            try (exchange) { exchange.getResponseBody().write(bytes); }
        });
        server.createContext("/embeddings", exchange -> {
            embeddingRequests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = """
                    {"object":"list","model":"synthetic-embedding","data":[{"object":"embedding","index":0,
                    "embedding":[1.0,0.0,0.0]}],"usage":{"prompt_tokens":1,"total_tokens":1}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (exchange) { exchange.getResponseBody().write(bytes); }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var transport = new SpringAiTransport(Duration.ofSeconds(5), Duration.ofSeconds(5))) {
            var openAi = new OpenAIClientImpl(ClientOptions.builder().baseUrl(baseUrl).apiKey("synthetic-local-only")
                    .maxRetries(0).httpClient(SpringAiOpenAiHttpClient.builder().timeout(Duration.ofSeconds(5)).build()).build());
            try {
            var api = DeepSeekApi.builder().baseUrl(baseUrl).apiKey("synthetic-local-only")
                    .restClientBuilder(transport.restClient()).webClientBuilder(transport.webClient()).build();
            var model = DeepSeekChatModel.builder().deepSeekApi(api)
                    .options(DeepSeekChatOptions.builder().model("synthetic").build())
                    .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
            require("local answer".equals(model.call(new Prompt("local question")).getResult().getOutput().getText()),
                    "Spring AI DeepSeek controlled response");
            require(chatRequests.get() == 1, "One controlled chat request");
            String streamed = model.stream(new Prompt("local stream")).map(response -> response.getResult().getOutput().getText())
                    .reduce("", String::concat).block(Duration.ofSeconds(10));
            require("local answer".equals(streamed) && chatRequests.get() == 2, "One controlled SSE subscription");
            fail.set(true);
            boolean propagated = false;
            try { model.call(new Prompt("local failure")); }
            catch (RuntimeException expected) { propagated = true; }
            require(propagated && chatRequests.get() == 3, "HTTP failure propagated without retry");
            var embeddings = OpenAiEmbeddingModel.builder().openAiClient(openAi)
                    .options(OpenAiEmbeddingOptions.builder().model("synthetic-embedding").dimensions(3).build()).build();
            var courseEmbedding = CourseEmbeddings.springAi(embeddings, new EmbeddingSpace("synthetic", java.net.URI.create(baseUrl),
                    "synthetic-embedding", 3, "document: ", "query: ", "fixture-v1"));
            float[] vector = courseEmbedding.document("local document");
            float[] query = courseEmbedding.query("local question");
            require(vector.length == 3 && vector[0] == 1 && query[0] == 1 && embeddingRequests.get() == 2,
                    "Spring AI corpus document/query controlled embedding response");
            } finally { openAi.close(); }
        } finally { server.stop(0); }
    }

    public static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("Runtime check failed: " + label);
    }
}
