package edu.courseassist.bootstrap;

import edu.courseassist.bootstrap.ide.DependencyChecks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class DependencyRuntimeTest {
    @TempDir Path scratch;
    @Test void dependenciesRunWithSyntheticLocalResponses() throws Exception { DependencyChecks.run(scratch); }
}
