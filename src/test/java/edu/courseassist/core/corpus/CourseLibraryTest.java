package edu.courseassist.core.corpus;

import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.PdfContracts.*;
import edu.courseassist.contract.PdfReader;
import edu.courseassist.model.CourseEmbeddings;
import edu.courseassist.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static edu.courseassist.core.corpus.CorpusData.*;
import static org.junit.jupiter.api.Assertions.*;

class CourseLibraryTest {
    @TempDir Path temp;
    static final EmbeddingSpace SPACE = new EmbeddingSpace("synthetic", URI.create("https://example.invalid"), "local-fixture-a", 3, "doc:", "query:", "fixture-v1");
    static final Settings SETTINGS = new Settings(SPACE, new Processing("synthetic-page-reader-v1", 512, 20), new IndexSettings(2, 8, 1.5f, 1.2f));
    static final SearchSettings SEARCH = new SearchSettings(3, 0.9f);
    static final PdfReader READER = request -> {
        String text = Files.readString(request.managedSnapshot());
        return new PdfReadResult(request, 3, List.of(new PdfPage(1, text, PageTextStatus.TEXT, Optional.of("iv")),
                new PdfPage(2, " \n", PageTextStatus.EMPTY, Optional.empty()),
                new PdfPage(3, "third:" + text, PageTextStatus.TEXT, Optional.empty())));
    };
    static class Embeddings implements CourseEmbeddings {
        final EmbeddingSpace space; final AtomicInteger documents = new AtomicInteger(), queries = new AtomicInteger();
        Embeddings() { this(SPACE); }
        Embeddings(EmbeddingSpace space) { this.space = space; }
        @Override public EmbeddingSpace space() { return space; }
        @Override public float[] document(String text) throws Exception { documents.incrementAndGet(); return vector(text); }
        @Override public float[] query(String text) { queries.incrementAndGet(); return vector(text); }
        static float[] vector(String text) { return text.startsWith("third:") ? new float[]{0, 0, 1} : text.contains("2") ? new float[]{0, 1, 0} : new float[]{1, 0, 0}; }
    }
    final class Harness implements AutoCloseable {
        final ExecutorService executor = Executors.newCachedThreadPool();
        final H2Store store;
        final CourseRepository repo;
        final ManagedPdfStore files;
        final CourseLibrary library;
        Harness(Path path) { this(path, READER, GenerationHandle::build); }
        Harness(Path path, PdfReader reader, CourseLibrary.GraphFactory factory) {
            store = new H2Store(path.resolve("db")); repo = new CourseRepository(store); files = new ManagedPdfStore(path.resolve("pdf"));
            library = new CourseLibrary(repo, files, () -> reader, executor, factory); await(library.initialize());
        }
        Snapshot snapshot() { return await(library.snapshot()); }
        Change add(Path file, Embeddings e) { return done(library.importPdf(file, file.getFileName().toString(), snapshot().base(), SETTINGS, e, () -> true)); }
        @Override public void close() throws Exception {
            library.close(); executor.shutdown(); assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
            await(store.transaction(c -> null)); store.close();
        }
    }
    Path pdf(String name, String body) throws IOException { return Files.writeString(temp.resolve(name), body); }
    static <T> T await(CompletionStage<T> stage) {
        try { return stage.toCompletableFuture().get(20, TimeUnit.SECONDS); }
        catch (Exception e) { throw new CompletionException(e); }
    }
    static <T> T done(CourseLibrary.Job<T> job) {
        T value = await(job.completion()); await(job.settled()); return value;
    }
    static MaterialTarget target(Snapshot snapshot, String doc) {
        MaterialView m = snapshot.materials().stream().filter(v -> v.documentId().equals(doc)).findFirst().orElseThrow();
        return new MaterialTarget(doc, m.sourceVersionId(), m.materialRevision());
    }
    static void waitFor(CountDownLatch latch) throws InterruptedException { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
    static Throwable root(Throwable t) { while (t.getCause() != null) t = t.getCause(); return t; }

    @Test void fixedManagedCopyDeduplicatesAndMapsNonFirstOrdinalToPhysicalPage() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            var e = new Embeddings(); Path original = pdf("one.pdf", "A1");
            try (var empty = h.library.openAttempt()) {
                var first = h.add(original, e); Files.delete(original);
                assertEquals(Disposition.PUBLISHED, first.disposition()); assertEquals(2, e.documents.get());
                assertFalse(await(empty.hasMaterials())); assertTrue(await(empty.search("question", e, SEARCH, () -> true)).isEmpty());
                assertEquals(0, e.queries.get(), "Fixed no-library attempt must not start embedding after import");
                assertEquals("A1", Files.readString(await(h.library.sourcePath(first.sourceId().orElseThrow()))));
                var duplicate = h.add(pdf("duplicate.pdf", "A1"), e);
                assertEquals(Disposition.DUPLICATE_READY, duplicate.disposition()); assertEquals(first.documentId(), duplicate.documentId()); assertEquals(2, e.documents.get());
                assertEquals(1, h.snapshot().materials().size()); assertEquals(3, h.snapshot().materials().getFirst().physicalPageCount().orElseThrow());
                try (var lease = h.library.openAttempt()) {
                    var hits = await(lease.search("third:A1", e, SEARCH, () -> true));
                    assertEquals(1, hits.size()); var hit = hits.getFirst();
                    assertEquals(3, hit.physicalPage()); assertEquals("third:A1", hit.text()); assertEquals(first.sourceId().orElseThrow(), hit.source().versionId());
                    assertEquals("one.pdf", hit.source().name()); assertEquals(1, hit.score(), 0.0001);
                    assertEquals(hit.chunkId(), await(lease.read(hit.chunkId())).chunkId());
                    assertThrows(CompletionException.class, () -> await(lease.read("made-up-evidence")));
                }
            }
        }
    }
    @Test void replaceRemoveAndHistoryReusePreserveOldAttemptAndSourceIdentity() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            var e = new Embeddings(); var first = h.add(pdf("a.pdf", "A1"), e);
            try (var old = h.library.openAttempt()) {
                var before = h.snapshot(); var oldTarget = target(before, first.documentId());
                var second = done(h.library.replacePdf(pdf("a2.pdf", "A2"), "version two", before.base(), oldTarget, SETTINGS, e, () -> true));
                assertEquals(first.documentId(), second.documentId()); assertNotEquals(first.sourceId(), second.sourceId());
                assertEquals("A1", await(old.search("A1", e, SEARCH, () -> true)).getFirst().text());
                try (var latest = h.library.openAttempt()) { assertEquals("A2", await(latest.search("A2", e, SEARCH, () -> true)).getFirst().text()); }
                assertThrows(CompletionException.class, () -> done(h.library.remove(second.base(), oldTarget, () -> true)));
                int calls = e.documents.get(); var current = h.snapshot();
                var reused = done(h.library.replacePdf(pdf("again.pdf", "A1"), "again", current.base(), target(current, first.documentId()), SETTINGS, e, () -> true));
                assertEquals(first.sourceId(), reused.sourceId()); assertEquals(calls, e.documents.get(), "Historical derivation reuses exact compatible body/space");
                var same = h.snapshot();
                assertEquals(Disposition.UNCHANGED, done(h.library.replacePdf(pdf("unchanged.pdf", "A1"), "again", same.base(), target(same, first.documentId()), SETTINGS, e, () -> true)).disposition());
                assertEquals(same.base(), h.snapshot().base());
                var removed = h.snapshot(); done(h.library.remove(removed.base(), target(removed, first.documentId()), () -> true));
                try (var next = h.library.openAttempt()) { assertFalse(await(next.hasMaterials())); }
                assertEquals("A1", await(old.search("A1", e, SEARCH, () -> true)).getFirst().text());
                assertFalse(await(h.library.previewCleanup()).sourceIds().contains(first.sourceId().orElseThrow()));
            }
        }
    }
    @Test void crossDocumentByteReuseKeepsIndependentSourcesAndCleanupWaitsForLastReference() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            var e = new Embeddings(); var a = h.add(pdf("a.pdf", "A1"), e); var b = h.add(pdf("b.pdf", "B1"), e);
            var snap = h.snapshot(); var replaced = done(h.library.replacePdf(pdf("a-copy.pdf", "A1"), "B replacement", snap.base(), target(snap, b.documentId()), SETTINGS, e, () -> true));
            assertEquals(b.documentId(), replaced.documentId()); assertNotEquals(a.sourceId(), replaced.sourceId());
            Path shared = await(h.library.sourcePath(a.sourceId().orElseThrow())); assertEquals(shared, await(h.library.sourcePath(replaced.sourceId().orElseThrow())));
            try (var lease = h.library.openAttempt()) { await(lease.retainSources("answer-owner", Set.of(a.sourceId().orElseThrow()))); }
            var s1 = h.snapshot(); done(h.library.remove(s1.base(), target(s1, a.documentId()), () -> true));
            var preview = await(h.library.previewCleanup()); await(h.library.cleanup(preview)); assertTrue(Files.exists(shared));
            var s2 = h.snapshot(); done(h.library.remove(s2.base(), target(s2, b.documentId()), () -> true));
            await(h.library.cleanup(await(h.library.previewCleanup()))); assertTrue(Files.exists(shared), "Durable history pin keeps shared bytes");
            await(h.library.releaseReferences("answer-owner")); var finalPreview = await(h.library.previewCleanup());
            assertTrue(finalPreview.sourceIds().contains(a.sourceId().orElseThrow())); await(h.library.cleanup(finalPreview)); assertFalse(Files.exists(shared));
        }
    }
    @Test void failedAndCancelledEmbeddingNeverPublishOrOverwriteOldReadyVersion() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            var e = new Embeddings(); var a = h.add(pdf("a.pdf", "A1"), e); Snapshot base = h.snapshot();
            var failed = h.library.replacePdf(pdf("bad.pdf", "A2"), "bad", base.base(), target(base, a.documentId()), SETTINGS,
                    new Embeddings() { @Override public float[] document(String t) throws IOException { throw new IOException("synthetic embedding failure"); } }, () -> true);
            var error = assertThrows(CompletionException.class, () -> done(failed)); assertEquals("synthetic embedding failure", root(error).getMessage()); await(failed.settled());
            assertEquals(base.base(), h.snapshot().base()); assertEquals(a.sourceId(), h.snapshot().materials().getFirst().sourceVersionId());
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var delayed = new Embeddings() { @Override public float[] document(String t) throws Exception { entered.countDown(); waitFor(release); return super.document(t); } };
            var cancelled = h.library.replacePdf(pdf("cancel.pdf", "A2"), "cancel", base.base(), target(base, a.documentId()), SETTINGS, delayed, () -> true);
            waitFor(entered); cancelled.cancel(); release.countDown(); await(cancelled.settled());
            assertThrows(CompletionException.class, () -> await(cancelled.completion())); assertEquals(base.base(), h.snapshot().base());
            assertEquals("CANCELLED", h.snapshot().jobs().stream().filter(j -> j.id().equals(cancelled.id())).findFirst().orElseThrow().state());
            assertEquals(MaterialStatus.READY, h.snapshot().materials().getFirst().status());
        }
    }
    @Test void platformCancellationIsNotAnEmbeddingFaultAndFailedImportIsNotDuplicateReady() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            Path file = pdf("cancel.pdf", "A1"); var e = new Embeddings() {
                @Override public float[] document(String t) { throw new com.intellij.openapi.progress.ProcessCanceledException(); }
            };
            var job = h.library.importPdf(file, "cancel", h.snapshot().base(), SETTINGS, e, () -> true);
            assertThrows(CompletionException.class, () -> await(job.completion())); await(job.settled());
            assertEquals("CANCELLED", h.snapshot().jobs().getFirst().state()); assertEquals(Availability.EMPTY, h.snapshot().availability());
            assertEquals(Disposition.EXISTS_NOT_READY, h.add(file, new Embeddings()).disposition());
            assertEquals(MaterialStatus.FAILED, h.snapshot().materials().getFirst().status());
        }
    }
    @Test void duplicateWhilePreparingLocatesExistingRowWithoutPretendingReady() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1); Path file = pdf("a.pdf", "A1");
            var e = new Embeddings() { @Override public float[] document(String t) throws Exception { entered.countDown(); waitFor(release); return super.document(t); } };
            var first = h.library.importPdf(file, "A", h.snapshot().base(), SETTINGS, e, () -> true); waitFor(entered);
            var duplicate = h.add(file, new Embeddings()); assertEquals(Disposition.EXISTS_NOT_READY, duplicate.disposition());
            assertEquals(1, h.snapshot().materials().size()); release.countDown(); assertEquals(duplicate.documentId(), done(first).documentId());
        }
    }
    @Test void concurrentDifferentDocumentUpdatesCannotLosePublishedChanges() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            var e = new Embeddings(); var a = h.add(pdf("a.pdf", "A1"), e); var b = h.add(pdf("b.pdf", "B1"), e); Snapshot base = h.snapshot();
            CountDownLatch both = new CountDownLatch(2), releaseA = new CountDownLatch(1), releaseB = new CountDownLatch(1);
            var ea = new Embeddings() { @Override public float[] document(String t) throws Exception { both.countDown(); waitFor(releaseA); return super.document(t); } };
            var eb = new Embeddings() { @Override public float[] document(String t) throws Exception { both.countDown(); waitFor(releaseB); return super.document(t); } };
            var ja = h.library.replacePdf(pdf("a2.pdf", "A2"), "A2", base.base(), target(base, a.documentId()), SETTINGS, ea, () -> true);
            var jb = h.library.replacePdf(pdf("b2.pdf", "B2"), "B2", base.base(), target(base, b.documentId()), SETTINGS, eb, () -> true);
            waitFor(both); releaseA.countDown(); var published = done(ja); releaseB.countDown();
            assertThrows(CompletionException.class, () -> done(jb)); await(jb.settled());
            Snapshot latest = h.snapshot(); assertEquals(published.base(), latest.base());
            assertEquals(published.sourceId(), target(latest, a.documentId()).expectedSourceVersionId());
            assertEquals(b.sourceId(), target(latest, b.documentId()).expectedSourceVersionId());
            // A newly prepared B update now includes A's already committed change.
            done(h.library.replacePdf(temp.resolve("b2.pdf"), "B2", latest.base(), target(latest, b.documentId()), SETTINGS, e, () -> true));
            assertEquals(published.sourceId(), target(h.snapshot(), a.documentId()).expectedSourceVersionId());
        }
    }
    @Test void restorationSharesFutureAndOneCancelledWaitCannotCancelOtherSessions() throws Exception {
        Path path = temp.resolve("corpus"); CorpusBase base;
        try (var h = new Harness(path)) { h.add(pdf("a.pdf", "A1"), new Embeddings()); base = h.snapshot().base(); }
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1); AtomicInteger builds = new AtomicInteger();
        try (var h = new Harness(path, request -> { throw new AssertionError("Restore must not reread PDF"); }, g -> { builds.incrementAndGet(); entered.countDown(); waitFor(release); return GenerationHandle.build(g); })) {
            waitFor(entered); assertEquals(Availability.RESTORING, h.snapshot().availability());
            try (var a = h.library.openAttempt(); var b = h.library.openAttempt()) {
                var waitingA = a.hasMaterials(); var waitingB = b.hasMaterials(); waitingA.cancel(false); a.close();
                assertFalse(waitingB.isDone()); release.countDown(); assertTrue(await(waitingB));
                var e = new Embeddings() { @Override public float[] document(String text) { throw new AssertionError("Restore must not re-embed documents"); } };
                List<CompletableFuture<List<Evidence>>> queries = new ArrayList<>();
                for (int i = 0; i < 24; i++) queries.add(b.search("third:A1", e, SEARCH, () -> true));
                for (var q : queries) assertEquals(3, await(q).getFirst().physicalPage());
                assertEquals(base, b.base()); assertEquals(1, builds.get()); assertEquals(Availability.READY, h.snapshot().availability());
            }
        }
    }
    @Test void restoreFailureAndCorruptPersistedVectorsRemainFailuresNotEmptyHits() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            Path path = temp.resolve("corpus-" + variant);
            try (var h = new Harness(path)) {
                h.add(pdf("a-" + variant + ".pdf", "A1"), new Embeddings());
                final int v = variant;
                await(h.store.transaction(c -> {
                    try (var s = c.createStatement()) {
                        s.executeUpdate(v == 0 ? "UPDATE ca_chunk SET vector_data=X'000000000000000000000000'" : v == 1 ? "DELETE FROM ca_ordinal WHERE ordinal=1" : "DELETE FROM ca_page WHERE page_no=2");
                    }
                    return null;
                }));
            }
            try (var h = new Harness(path); var lease = h.library.openAttempt()) {
                assertThrows(CompletionException.class, () -> await(lease.hasMaterials()));
                assertEquals(Availability.FAILED, h.snapshot().availability());
                assertThrows(CompletionException.class, () -> await(lease.search("A1", new Embeddings(), SEARCH, () -> true)));
            }
        }
    }
    @Test void equalDimensionModelChangesRequireFullReindexAndKeepOldAttemptUsable() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            var e = new Embeddings(); Path file = pdf("a.pdf", "A1"); h.add(file, e);
            var otherSpace = new EmbeddingSpace(SPACE.provider(), SPACE.endpoint(), "local-fixture-b", 3, SPACE.documentPrefix(), SPACE.queryPrefix(), SPACE.revision());
            var other = new Embeddings(otherSpace); var settings = new Settings(otherSpace, SETTINGS.processing(), SETTINGS.index());
            try (var old = h.library.openAttempt()) {
                var base = h.snapshot().base();
                assertThrows(CompletionException.class, () -> await(old.search("A1", other, SEARCH, () -> true))); assertEquals(0, other.queries.get());
                assertEquals(Disposition.REINDEX_REQUIRED, done(h.library.importPdf(file, "A", base, settings, other, () -> true)).disposition());
                var changed = done(h.library.reindex(base, settings, other, () -> true)); assertNotEquals(base, changed.base()); assertEquals(2, other.documents.get());
                assertEquals("A1", await(old.search("A1", e, SEARCH, () -> true)).getFirst().text());
                try (var current = h.library.openAttempt()) { assertThrows(CompletionException.class, () -> await(current.search("A1", e, SEARCH, () -> true))); assertFalse(await(current.search("A1", other, SEARCH, () -> true)).isEmpty()); }
            }
        }
    }
    @Test void failedGraphCandidateAndExpiredRuntimeCannotActivate() throws Exception {
        AtomicBoolean failGraph = new AtomicBoolean();
        try (var h = new Harness(temp.resolve("corpus"), READER, g -> { if (failGraph.get()) throw new IOException("synthetic graph failure"); return GenerationHandle.build(g); })) {
            var e = new Embeddings(); var a = h.add(pdf("a.pdf", "A1"), e); var base = h.snapshot(); failGraph.set(true);
            var job = h.library.replacePdf(pdf("a2.pdf", "A2"), "A2", base.base(), target(base, a.documentId()), SETTINGS, e, () -> true);
            assertEquals("synthetic graph failure", root(assertThrows(CompletionException.class, () -> done(job))).getMessage()); await(job.settled());
            assertEquals(base.base(), h.snapshot().base()); assertEquals(MaterialStatus.READY, h.snapshot().materials().getFirst().status());
            var cancelled = h.library.remove(base.base(), target(base, a.documentId()), () -> false);
            assertThrows(CompletionException.class, () -> done(cancelled)); await(cancelled.settled()); assertEquals(base.base(), h.snapshot().base());
        }
    }
    @Test void referencesAddedAfterPreviewInvalidateCleanupAndClosedLeaseRejectsLateReads() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            var e = new Embeddings(); var a = h.add(pdf("a.pdf", "A1"), e); var old = h.library.openAttempt();
            var s = h.snapshot(); done(h.library.replacePdf(pdf("a2.pdf", "A2"), "A2", s.base(), target(s, a.documentId()), SETTINGS, e, () -> true));
            old.close(); var preview = await(h.library.previewCleanup()); assertTrue(preview.sourceIds().contains(a.sourceId().orElseThrow()));
            h.repo.retain("history", Set.of(a.sourceId().orElseThrow()));
            assertThrows(CompletionException.class, () -> await(h.library.cleanup(preview)));
            assertTrue(Files.exists(await(h.library.sourcePath(a.sourceId().orElseThrow()))));
            assertThrows(CancellationException.class, old::hasMaterials);
        }
    }
    @Test void databaseFailureAtFinalPointerUpdateRollsBackCatalogAndEntireGeneration() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            var e = new Embeddings(); var a = h.add(pdf("a.pdf", "A1"), e); var base = h.snapshot();
            await(h.store.transaction(c -> {
                try (var s = c.createStatement()) { s.execute("ALTER TABLE ca_corpus_meta ADD CONSTRAINT synthetic_commit_failure CHECK (revision <= 1)"); }
                return null;
            }));
            var job = h.library.replacePdf(pdf("a2.pdf", "A2"), "A2", base.base(), target(base, a.documentId()), SETTINGS, e, () -> true);
            assertThrows(CompletionException.class, () -> done(job)); await(job.settled());
            assertEquals(base.base(), h.snapshot().base()); assertEquals(a.sourceId(), h.snapshot().materials().getFirst().sourceVersionId());
            int generations = await(h.store.transaction(c -> { try (var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM ca_generation")) { r.next(); return r.getInt(1); } }));
            assertEquals(1, generations);
            try (var lease = h.library.openAttempt()) { assertEquals("A1", await(lease.search("A1", e, SEARCH, () -> true)).getFirst().text()); }
        }
    }
    @Test void removalWhileReplacementGraphIsPreparedCannotResurrectMaterial() throws Exception {
        CountDownLatch prepared = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var h = new Harness(temp.resolve("corpus"), READER, g -> {
            var handle = GenerationHandle.build(g);
            if (g.members().stream().flatMap(d -> d.chunks().stream()).anyMatch(c -> c.text().equals("A2"))) { prepared.countDown(); waitFor(release); }
            return handle;
        })) {
            var e = new Embeddings(); var a = h.add(pdf("a.pdf", "A1"), e); var base = h.snapshot(); var target = target(base, a.documentId());
            var replacement = h.library.replacePdf(pdf("a2.pdf", "A2"), "A2", base.base(), target, SETTINGS, e, () -> true);
            waitFor(prepared); var removal = done(h.library.remove(base.base(), target, () -> true)); release.countDown();
            assertThrows(CompletionException.class, () -> done(replacement)); await(replacement.settled());
            assertEquals(removal.base(), h.snapshot().base()); assertEquals(MaterialStatus.REMOVED, h.snapshot().materials().getFirst().status());
            try (var lease = h.library.openAttempt()) { assertFalse(await(lease.hasMaterials())); }
        }
    }
    @Test void cleanupFileFailureKeepsDurableBlobDeletionIdentityAndExplicitRetryFinishes() throws Exception {
        try (var h = new Harness(temp.resolve("corpus"))) {
            var e = new Embeddings(); var a = h.add(pdf("a.pdf", "A1"), e); Path path = await(h.library.sourcePath(a.sourceId().orElseThrow()));
            var base = h.snapshot(); done(h.library.replacePdf(pdf("a2.pdf", "A2"), "A2", base.base(), target(base, a.documentId()), SETTINGS, e, () -> true));
            Path backup = path.resolveSibling("failure-fixture-backup"); Files.move(path, backup); Files.createDirectory(path); Path blocker = Files.writeString(path.resolve("blocker"), "fixture");
            assertThrows(CompletionException.class, () -> await(h.library.cleanup(await(h.library.previewCleanup()))));
            int pending = await(h.store.transaction(c -> { try (var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM ca_blob WHERE deleting=TRUE")) { r.next(); return r.getInt(1); } }));
            assertEquals(1, pending);
            Files.delete(blocker); Files.delete(path); Files.move(backup, path);
            await(h.library.cleanup(await(h.library.previewCleanup()))); assertFalse(Files.exists(path));
            int remaining = await(h.store.transaction(c -> { try (var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM ca_blob WHERE deleting=TRUE")) { r.next(); return r.getInt(1); } }));
            assertEquals(0, remaining);
        }
    }
}
