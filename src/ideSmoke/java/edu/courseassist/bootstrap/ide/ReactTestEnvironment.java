package edu.courseassist.bootstrap.ide;

import edu.courseassist.contract.PdfContracts.*;
import edu.courseassist.core.corpus.*;
import edu.courseassist.core.react.*;
import edu.courseassist.core.react.ReactData.Snapshot;
import edu.courseassist.model.CourseEmbeddings;
import edu.courseassist.storage.*;
import java.net.URI;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import static edu.courseassist.core.corpus.CorpusData.*;
import static edu.courseassist.core.react.ReactData.*;

/** Test-only owned resources and explicit synthetic ports, also usable under the real IDE classloader.
 * The small SQL root is a guard fixture, not the group 4 production session implementation. */
public final class ReactTestEnvironment implements AutoCloseable {
    public final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    public final H2Store store;
    public final CourseLibrary library;
    public final ReactRepository journal;
    public final Identity identity = new Identity("test-project", "test-instance", "test-session", "test-turn", "test-operation", UUID.randomUUID().toString(), 7);
    public final Input input = new Input("explain Java  空格\n", List.of());
    public final AtomicInteger queries = new AtomicInteger();
    public volatile Function<String,float[]> query = ignored -> new float[]{1, 0, 0};
    public final CourseEmbeddings embeddings;
    public final List<Snapshot> views = new CopyOnWriteArrayList<>();
    public final Path root;
    public static final Limits LIMITS = new Limits(6, 8, 16_000, 32_000, 200_000, 1024, Duration.ofSeconds(15), 12);
    public static final EmbeddingSpace SPACE = new EmbeddingSpace("fixture", URI.create("https://example.invalid"), "fixture", 3, "", "", "v1");
    public static final Settings SETTINGS = new Settings(SPACE, new Processing("fixture", 512, 16), new IndexSettings(2, 8, 1.5f, 1.2f));
    public ReactTestEnvironment(Path root, boolean populate) throws Exception { this(root,populate,e->e); }
    public ReactTestEnvironment(Path root, boolean populate, UnaryOperator<Executor> decorateLibraryExecutor) throws Exception {
        this.root = root; Files.createDirectories(root); store = new H2Store(root.resolve("db"));
        embeddings = new CourseEmbeddings() {
            public EmbeddingSpace space() { return SPACE; }
            public float[] document(String text) { return new float[]{1,0,0}; }
            public float[] query(String text) { queries.incrementAndGet(); return query.apply(text); }
        };
        library = new CourseLibrary(new CourseRepository(store), new ManagedPdfStore(root.resolve("pdf")), () -> request -> {
            String body = Files.readString(request.managedSnapshot());
            return new PdfReadResult(request, 3, List.of(new PdfPage(1, "", PageTextStatus.EMPTY, Optional.empty()),
                    new PdfPage(2, "", PageTextStatus.EMPTY, Optional.empty()), new PdfPage(3, body, PageTextStatus.TEXT, Optional.empty())));
        }, decorateLibraryExecutor.apply(workers));
        library.initialize().get(10, TimeUnit.SECONDS);
        store.transaction(c -> {
            try (var s = c.createStatement()) {
                s.execute("CREATE TABLE react_test_root(project_id VARCHAR, session_id VARCHAR, instance_id VARCHAR, epoch BIGINT, lifecycle VARCHAR, operation_id VARCHAR)");
                s.execute("INSERT INTO react_test_root VALUES('test-project','test-session','test-instance',7,'LIVE','test-operation')");
            }
            return null;
        }).join();
        journal = new ReactRepository(store, (c, id, access) -> {
            try (var s = c.prepareStatement("SELECT * FROM react_test_root WHERE project_id=? AND session_id=?")) {
                s.setString(1, id.projectId()); s.setString(2, id.sessionId());
                try (var r = s.executeQuery()) {
                    if (!r.next()) throw new SQLException("Root absent");
                    if (access == ReactRepository.Access.READ) return;
                    if (access == ReactRepository.Access.DELETE) {
                        if (!r.getString("lifecycle").equals("DELETING")) throw new SQLException("No deletion fence");
                        return;
                    }
                    if (!r.getString("lifecycle").equals("LIVE") || !r.getString("instance_id").equals(id.projectInstanceId())
                            || !r.getString("operation_id").equals(id.operationId())) throw new SQLException("Root/instance/operation invalid");
                    if (access != ReactRepository.Access.COORDINATOR_TERMINAL && r.getLong("epoch") != id.runtimeEpoch()) throw new CancellationException("Epoch invalid");
                }
            }
        });
        journal.initialize();
        if (populate) add("course-sentinel: exact Java evidence");
    }
    public void add(String text) throws Exception {
        Path pdf = Files.writeString(root.resolve(UUID.randomUUID()+".pdf"), text);
        var base = library.snapshot().get(10, TimeUnit.SECONDS).base();
        var job = library.importPdf(pdf, "Synthetic Java notes", base, SETTINGS, embeddings, () -> true);
        job.completion().toCompletableFuture().get(10, TimeUnit.SECONDS); job.settled().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
    public CourseTools tools() { return new CourseTools(library.openAttempt(), embeddings, new SearchSettings(2, 0.9f)); }
    public ReactEngine.Job start(Model model) { return start(model, LIMITS, ErrorPolicy.DEVELOPMENT, () -> true, journal, List.of(), identity, tools()); }
    public ReactEngine.Job start(Model model, Limits limits, ErrorPolicy policy, BooleanSupplier permitted, Journal writer,
                                 List<HistoryTurn> history, Identity id, CourseTools tools) {
        return new ReactEngine(model, writer, workers, ReactContext.utf8Budget()).start(id, input, history, tools, limits, policy, permitted, views::add);
    }
    public Snapshot done(ReactEngine.Job job) throws Exception { return job.completion().get(20, TimeUnit.SECONDS); }
    public long sourceRefs() { return store.transaction(c -> { try (var s=c.createStatement(); var r=s.executeQuery("SELECT COUNT(*) FROM ca_source_ref")) { r.next(); return r.getLong(1); } }).join(); }
    public void deleting() { store.transaction(c -> { try(var s=c.createStatement()) { s.execute("UPDATE react_test_root SET lifecycle='DELETING'"); } return null; }).join(); }
    public void epoch() { store.transaction(c -> { try(var s=c.createStatement()) { s.execute("UPDATE react_test_root SET epoch=8"); } return null; }).join(); }
    public static void finish(Listener listener, String text) { listener.frame(new Body(text)); listener.frame(new End("stop")); listener.complete(); }
    public static Model answer(String body) { return (request, listener) -> { finish(listener, body); return () -> {}; }; }
    @Override public void close() throws Exception {
        library.close(); workers.shutdown();
        if (!workers.awaitTermination(20, TimeUnit.SECONDS)) throw new IllegalStateException("Test workers did not terminate");
        store.transaction(c -> null).get(10, TimeUnit.SECONDS); store.close();
    }
}
