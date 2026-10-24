package edu.courseassist.core.session;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProjectAssociationTest {
    @TempDir Path root;

    @Test void chooserExcludesOccupiedProjectsAndAssociationRestoresOriginalSession() throws Exception {
        try (var e = new SessionTestEnvironment(root)) {
            var original = e.open("old", "old-directory", false);
            String session = e.create("old");
            e.text("old", session, "preserved original draft");
            assertTrue(e.sessions.availableBindings().toCompletableFuture().get().isEmpty());
            e.sessions.closeProject("old").toCompletableFuture().get();
            var candidates = e.sessions.availableBindings().toCompletableFuture().get();
            assertEquals(1, candidates.size());
            var source = candidates.getFirst();
            var target = e.open("new", "new-directory", true);
            var associated = e.sessions.associate("new", source.projectId(), source.revision(), target.revision())
                    .toCompletableFuture().get();
            assertEquals(original.projectId(), associated.projectId());
            assertEquals("preserved original draft", e.view("new", session).draft().text());
            assertTrue(e.sessions.availableBindings().toCompletableFuture().get().isEmpty());
            assertTrue(e.requests.isEmpty());
        }
    }
}
