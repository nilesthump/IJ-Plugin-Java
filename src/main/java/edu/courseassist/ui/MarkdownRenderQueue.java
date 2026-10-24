package edu.courseassist.ui;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** One worker and one latest pending snapshot; stale HTML never applies to a newer view. */
final class MarkdownRenderQueue implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "course-assistant-markdown");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Request> pending = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Consumer<Throwable> failure;
    private volatile long latestEpoch;
    private volatile boolean closed;

    MarkdownRenderQueue(Consumer<Throwable> failure) {
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    void submit(String source, boolean separateCode, long epoch, Consumer<SafeMarkdown.Rendered> apply) {
        if (closed) return;
        latestEpoch = epoch;
        pending.set(new Request(Objects.requireNonNull(source, "source"), separateCode, epoch,
                Objects.requireNonNull(apply, "apply")));
        start();
    }

    private void start() {
        if (!closed && running.compareAndSet(false, true)) worker.execute(this::drain);
    }

    private void drain() {
        try {
            Request request;
            while (!closed && (request = pending.getAndSet(null)) != null) {
                Request completed = request;
                try {
                    SafeMarkdown.Rendered rendered = SafeMarkdown.renderView(
                            request.source(), request.separateCode());
                    SwingUtilities.invokeLater(() -> {
                        if (!closed && completed.epoch() == latestEpoch) completed.apply().accept(rendered);
                    });
                } catch (RuntimeException error) {
                    SwingUtilities.invokeLater(() -> {
                        if (!closed && completed.epoch() == latestEpoch) failure.accept(error);
                    });
                }
            }
        } finally {
            running.set(false);
            if (pending.get() != null) start();
        }
    }

    @Override public void close() {
        closed = true;
        pending.set(null);
        worker.shutdownNow();
    }

    private record Request(String source, boolean separateCode, long epoch,
                           Consumer<SafeMarkdown.Rendered> apply) {}
}
