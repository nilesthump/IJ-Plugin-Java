package edu.courseassist.model;

import java.util.concurrent.Callable;

/** SDK factories may discover services with the thread context loader. Use this plugin's libraries,
 * then restore the caller's loader even when construction or a request fails.
 * Future asynchronous model adapters must also scope their owned scheduler threads.
 */
public final class ModelClassLoading {
    private ModelClassLoading() {}
    public static <T> T call(Callable<T> operation) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(ModelClassLoading.class.getClassLoader());
        try { return operation.call(); }
        finally { thread.setContextClassLoader(previous); }
    }
}
