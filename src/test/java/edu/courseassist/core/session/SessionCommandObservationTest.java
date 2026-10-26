package edu.courseassist.core.session;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import edu.courseassist.contract.ChatContracts.UpdateDraftText;
import edu.courseassist.contract.CommonContracts.Outcome;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** The test observer must allow writer scheduling before a local command completes. */
class SessionCommandObservationTest {
    @TempDir Path root;
    @Test void delayedWriterStillReturnsActualCommandResult() throws Exception {
        try (var e = new SessionTestEnvironment(root);
             var scheduler = Executors.newSingleThreadScheduledExecutor()) {
            e.open("instance", "directory", false);
            String session = e.create("instance");
            var draft = e.view("instance", session).draft();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var held = e.store.transaction(c -> {
                entered.countDown();
                if (!release.await(25, TimeUnit.SECONDS)) throw new IllegalStateException("Test writer not released");
                return null;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            scheduler.schedule(release::countDown, 11, TimeUnit.SECONDS);
            try {
                var result = e.dispatch(new UpdateDraftText(e.meta("instance", session), draft.draftId(), draft.revision(), "preserved"));
                assertEquals(Outcome.APPLIED, result.outcome());
                assertEquals("preserved", e.view("instance", session).draft().text());
                assertTrue(e.requests.isEmpty());
            } finally {
                release.countDown();
                held.get(30, TimeUnit.SECONDS);
            }
        }
    }
}
