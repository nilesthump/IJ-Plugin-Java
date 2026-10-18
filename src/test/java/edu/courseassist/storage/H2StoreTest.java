package edu.courseassist.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class H2StoreTest {
    @TempDir Path directory;

    @Test void commitsRollbackAndReopenUseActualDiskDatabase() throws Exception {
        var failure = new IllegalArgumentException("rollback sentinel");
        var caller = Thread.currentThread();
        try (var store = new H2Store(directory)) {
            store.transaction(connection -> {
                assertNotSame(caller, Thread.currentThread());
                try (var sql = connection.createStatement()) { sql.execute("CREATE TABLE sample(id INT PRIMARY KEY)"); }
                return null;
            }).get(5, TimeUnit.SECONDS);
            store.transaction(connection -> {
                try (var sql = connection.createStatement()) { sql.executeUpdate("INSERT INTO sample VALUES(1)"); }
                return null;
            }).get(5, TimeUnit.SECONDS);
            var rolledBack = store.transaction(connection -> {
                try (var sql = connection.createStatement()) { sql.executeUpdate("INSERT INTO sample VALUES(2)"); }
                throw failure;
            });
            assertSame(failure, assertThrows(CompletionException.class, rolledBack::join).getCause());
        }
        try (var reopened = new H2Store(directory)) {
            assertEquals(1, reopened.transaction(connection -> {
                try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT COUNT(*) FROM sample")) {
                    rows.next(); return rows.getInt(1);
                }
            }).get(5, TimeUnit.SECONDS));
        }
    }

    @Test void disposalDrainsAcceptedCommitsAndDoesNotInterruptIo() throws Exception {
        var store = new H2Store(directory);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        var accepted = store.transaction(connection -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            interrupted.set(Thread.currentThread().isInterrupted());
            return "committed";
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            store.close();
            var rejected = store.transaction(connection -> "too late");
            assertInstanceOf(RejectedExecutionException.class,
                    assertThrows(CompletionException.class, rejected::join).getCause());
        } finally { release.countDown(); }
        assertEquals("committed", accepted.get(5, TimeUnit.SECONDS));
        assertFalse(interrupted.get());
    }

    @Test void pathCannotInjectH2ConnectionSettings() {
        assertThrows(IllegalArgumentException.class, () -> new H2Store(directory.resolve("x;AUTO_SERVER=TRUE")));
    }
}
