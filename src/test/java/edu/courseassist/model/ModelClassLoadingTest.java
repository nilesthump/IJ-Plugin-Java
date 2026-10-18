package edu.courseassist.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelClassLoadingTest {
    @Test void contextLoaderIsScopedAndRestoredOnSuccessAndOriginalFailure() throws Exception {
        var thread = Thread.currentThread();
        var original = thread.getContextClassLoader();
        var foreign = new ClassLoader(original) {};
        try {
            thread.setContextClassLoader(foreign);
            assertSame(ModelClassLoading.class.getClassLoader(), ModelClassLoading.call(thread::getContextClassLoader));
            assertSame(foreign, thread.getContextClassLoader());
            var failure = new IllegalArgumentException("original failure");
            assertSame(failure, assertThrows(IllegalArgumentException.class,
                    () -> ModelClassLoading.call(() -> { throw failure; })));
            assertSame(foreign, thread.getContextClassLoader());
        } finally { thread.setContextClassLoader(original); }
    }
}
