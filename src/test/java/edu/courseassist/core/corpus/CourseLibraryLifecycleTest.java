package edu.courseassist.core.corpus;

import edu.courseassist.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class CourseLibraryLifecycleTest {
    @TempDir Path temp;
    @Test void rejectedJobSchedulingCompletesBothResultAndResourceSettlement() throws Exception {
        AtomicBoolean reject = new AtomicBoolean();
        try (var worker = Executors.newCachedThreadPool(); var store = new H2Store(temp.resolve("db"))) {
            Executor executor = action -> { if (reject.get()) throw new RejectedExecutionException("synthetic shutdown"); worker.execute(action); };
            try (var library = new CourseLibrary(new CourseRepository(store), new ManagedPdfStore(temp.resolve("pdf")), () -> CourseLibraryTest.READER, executor)) {
                library.initialize().get(10, TimeUnit.SECONDS); reject.set(true);
                var job = library.importPdf(temp.resolve("never-read.pdf"), "fixture", new edu.courseassist.contract.ApplicationContracts.CorpusBase(0, java.util.Optional.empty()),
                        CourseLibraryTest.SETTINGS, new CourseLibraryTest.Embeddings(), () -> true);
                var failure = assertThrows(ExecutionException.class, () -> job.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
                assertInstanceOf(RejectedExecutionException.class, CourseLibraryTest.root(failure));
                job.settled().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
        }
    }
    @Test void closeDuringQueuedInitializationCannotStrandJobWhenExecutorStopsAcceptingCallbacks() throws Exception {
        var queue = new ArrayDeque<Runnable>(); AtomicBoolean reject = new AtomicBoolean();
        Executor executor = action -> { if (reject.get()) throw new RejectedExecutionException("synthetic shutdown"); queue.add(action); };
        try (var store = new H2Store(temp.resolve("db"));
             var library = new CourseLibrary(new CourseRepository(store), new ManagedPdfStore(temp.resolve("pdf")), () -> CourseLibraryTest.READER, executor)) {
            var initialization = library.initialize();
            var job = library.importPdf(temp.resolve("never-read.pdf"), "fixture", new edu.courseassist.contract.ApplicationContracts.CorpusBase(0, java.util.Optional.empty()),
                    CourseLibraryTest.SETTINGS, new CourseLibraryTest.Embeddings(), () -> true);
            library.close(); reject.set(true); queue.removeFirst().run();
            assertTrue(initialization.isCompletedExceptionally());
            var failure = assertThrows(ExecutionException.class, () -> job.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, CourseLibraryTest.root(failure));
            job.settled().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(queue.isEmpty());
        }
    }
    @Test void resultCallbacksMayReadLibraryWithoutHoldingItsPublicationLock() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var worker = Executors.newCachedThreadPool(); var store = new H2Store(temp.resolve("db"));
             var library = new CourseLibrary(new CourseRepository(store), new ManagedPdfStore(temp.resolve("pdf")), () -> CourseLibraryTest.READER, worker)) {
            library.initialize().get(10, TimeUnit.SECONDS);
            Path input = Files.writeString(temp.resolve("fixture.pdf"), "A1");
            var embeddings = new CourseLibraryTest.Embeddings() {
                @Override public float[] document(String text) throws Exception { entered.countDown(); CourseLibraryTest.waitFor(release); return super.document(text); }
            };
            var job = library.importPdf(input, "fixture", library.snapshot().get(5, TimeUnit.SECONDS).base(), CourseLibraryTest.SETTINGS, embeddings, () -> true);
            CourseLibraryTest.waitFor(entered);
            var callback = job.completion().thenRun(() -> {
                try { assertEquals(1, library.snapshot().get(5, TimeUnit.SECONDS).materials().size()); }
                catch (Exception failure) { throw new CompletionException(failure); }
            });
            release.countDown(); callback.toCompletableFuture().get(10, TimeUnit.SECONDS); job.settled().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }
    @Test void cancellationResultDoesNotHideFailureToSaveLocalTerminalState() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var worker = Executors.newCachedThreadPool(); var store = new H2Store(temp.resolve("db"));
             var library = new CourseLibrary(new CourseRepository(store), new ManagedPdfStore(temp.resolve("pdf")), () -> CourseLibraryTest.READER, worker)) {
            library.initialize().get(10, TimeUnit.SECONDS);
            Path input = Files.writeString(temp.resolve("fixture.pdf"), "A1");
            var embeddings = new CourseLibraryTest.Embeddings() {
                @Override public float[] document(String text) throws Exception { entered.countDown(); CourseLibraryTest.waitFor(release); return super.document(text); }
            };
            var job = library.importPdf(input, "fixture", library.snapshot().get(5, TimeUnit.SECONDS).base(), CourseLibraryTest.SETTINGS, embeddings, () -> true);
            CourseLibraryTest.waitFor(entered); job.cancel(); store.close(); release.countDown();
            var failure = assertThrows(ExecutionException.class, () -> job.settled().toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class, CourseLibraryTest.root(failure));
            var result = assertThrows(ExecutionException.class, () -> job.completion().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, CourseLibraryTest.root(result));
        }
    }
}
