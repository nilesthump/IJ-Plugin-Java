package edu.courseassist.bootstrap;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import edu.courseassist.contract.PdfReader;
import edu.courseassist.core.TaskScope;
import edu.courseassist.core.RuntimeGate;
import edu.courseassist.storage.H2Store;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One owner per IDE process. Construction is lazy and does no disk or network I/O. */
public final class CourseAssistantApplication implements Disposable {
    private static final ExtensionPointName<PdfReader> PDF_READERS =
            ExtensionPointName.create("edu.courseassist.f0.pdfReader");
    private final ExecutorService executor = Executors.newCachedThreadPool(
            Thread.ofPlatform().daemon().name("courseassist-background-", 0).factory());
    private final TaskScope tasks = new TaskScope(executor);
    private final H2Store storage = new H2Store(Path.of(PathManager.getConfigPath(), "courseassist-f0"));
    private final Set<TaskScope> projects = new HashSet<>();
    private final RuntimeGate runtime = new RuntimeGate();
    private boolean disposed;

    public static CourseAssistantApplication getInstance() {
        return ApplicationManager.getApplication().getService(CourseAssistantApplication.class);
    }

    /** Manager-only persistence entry; adapters and views use public contracts. */
    public H2Store storage() { return storage; }
    public TaskScope tasks() { return tasks; }
    public RuntimeGate runtime() { return runtime; }

    synchronized TaskScope newProjectScope() {
        if (disposed) throw new IllegalStateException("Application service disposed");
        TaskScope scope = new TaskScope(executor);
        projects.add(scope);
        return scope;
    }

    synchronized void closeProjectScope(TaskScope scope) {
        scope.close();
        projects.remove(scope);
    }

    public PdfReader pdfReader() {
        List<PdfReader> readers = PDF_READERS.getExtensionList();
        if (readers.size() != 1) throw new IllegalStateException("Exactly one PDF adapter must be registered");
        return readers.getFirst();
    }

    @Override public synchronized void dispose() {
        if (disposed) return;
        disposed = true;
        runtime.close();
        projects.forEach(TaskScope::close);
        projects.clear();
        tasks.close();
        storage.close();
        executor.shutdown();
    }
}
