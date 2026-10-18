package edu.courseassist.storage;

import org.h2.Driver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Application-owned, serial short transactions. No SQL/network/PDF work on the caller's thread.
 * This is the connection boundary, not a session schema or a substitute for conditional writes.
 */
public final class H2Store implements AutoCloseable {
    @FunctionalInterface public interface Transaction<T> { T execute(Connection connection) throws Exception; }
    private final Path directory;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("courseassist-storage").factory());

    public H2Store(Path directory) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        // H2 interprets semicolons as settings. Never allow a filesystem path to change connection modes.
        if (this.directory.toString().contains(";")) throw new IllegalArgumentException("Unsupported storage path");
    }

    public <T> CompletableFuture<T> transaction(Transaction<T> action) {
        Objects.requireNonNull(action);
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            writer.execute(() -> {
                try {
                    Files.createDirectories(directory);
                    // Explicit Driver avoids DriverManager visibility differences in plugin classloaders.
                    T value;
                    try (Connection connection = new Driver().connect(
                            "jdbc:h2:file:" + directory.resolve("courseassist").toString().replace('\\', '/'),
                            new Properties())) {
                        connection.setAutoCommit(false);
                        try {
                            value = action.execute(connection);
                            connection.commit();
                        } catch (Throwable failure) {
                            try { connection.rollback(); }
                            catch (Throwable rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                            throw failure;
                        }
                    }
                    result.complete(value);
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }

    /** Drain accepted short commits; do not interrupt H2 file I/O. Reject later work. */
    @Override public void close() { writer.shutdown(); }
}
