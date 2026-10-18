package edu.courseassist.core;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Local lifetime fence. Closing invalidates delivery, without interrupting database I/O.
 * Business commits still need their own transactional ownership/epoch checks.
 */
public final class TaskScope implements AutoCloseable {
    private final Executor executor;
    private final Set<CompletableFuture<?>> pending = new HashSet<>();
    private boolean closed;

    public TaskScope(Executor executor) { this.executor = Objects.requireNonNull(executor); }

    public <T> CompletableFuture<T> submit(Callable<T> action) {
        Objects.requireNonNull(action);
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (this) {
            if (closed) return CompletableFuture.failedFuture(new CancellationException("Task scope closed"));
            pending.add(result);
        }
        result.whenComplete((value, failure) -> { synchronized (this) { pending.remove(result); } });
        try {
            executor.execute(() -> {
                if (result.isDone()) return;
                try {
                    T value = action.call();
                    synchronized (this) { if (!closed) result.complete(value); }
                } catch (Throwable failure) {
                    synchronized (this) { if (!closed) result.completeExceptionally(failure); }
                }
            });
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }

    public synchronized boolean isClosed() { return closed; }

    @Override public void close() {
        Set<CompletableFuture<?>> cancelled;
        synchronized (this) {
            if (closed) return;
            closed = true;
            cancelled = Set.copyOf(pending);
        }
        cancelled.forEach(task -> task.completeExceptionally(new CancellationException("Task scope closed")));
    }
}
