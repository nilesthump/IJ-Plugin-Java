package edu.courseassist.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class TaskScopeTest {
    @Test void closeRevokesQueuedAndLateDeliveryWithoutInterruptingWorker() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var scope = new TaskScope(executor);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var finished = new CountDownLatch(1);
            var interrupted = new AtomicBoolean();
            var late = scope.submit(() -> {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                finally { interrupted.set(Thread.currentThread().isInterrupted()); finished.countDown(); }
                return "late";
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var queuedRan = new AtomicBoolean();
            var queued = scope.submit(() -> { queuedRan.set(true); return "queued"; });
            try {
                scope.close();
                assertThrows(CancellationException.class, late::join);
                assertThrows(CancellationException.class, queued::join);
                assertThrows(CancellationException.class, () -> scope.submit(() -> "new").join());
            } finally { release.countDown(); }
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            assertFalse(interrupted.get());
            assertFalse(queuedRan.get());
        }
    }

    @Test void scopesAreIndependentAndOriginalFailuresPropagate() {
        var queue = new ArrayDeque<Runnable>();
        var a = new TaskScope(queue::add);
        var b = new TaskScope(queue::add);
        var cancelled = a.submit(() -> "a");
        var failure = new IllegalStateException("original failure");
        var failed = b.submit(() -> { throw failure; });
        a.close();
        while (!queue.isEmpty()) queue.remove().run();
        assertThrows(CancellationException.class, cancelled::join);
        assertSame(failure, assertThrows(java.util.concurrent.CompletionException.class, failed::join).getCause());
        assertFalse(b.isClosed());
        b.close();
    }
}
