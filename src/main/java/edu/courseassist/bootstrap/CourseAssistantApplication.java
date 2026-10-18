package edu.courseassist.bootstrap;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import edu.courseassist.contract.PdfReader;
import edu.courseassist.core.TaskScope;
import edu.courseassist.core.RuntimeGate;
import edu.courseassist.storage.H2Store;
import edu.courseassist.storage.CourseRepository;
import edu.courseassist.storage.ManagedPdfStore;
import edu.courseassist.core.corpus.CourseLibrary;
import edu.courseassist.core.session.*;
import edu.courseassist.storage.SessionRepository;
import edu.courseassist.storage.ApplicationRepository;
import edu.courseassist.model.ConfiguredRuntimeFactory;
import java.util.concurrent.CompletableFuture;
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
    private CourseLibrary courses;
    public record Controllers(ApplicationCoordinator application,SessionCoordinator sessions) {}
    private CompletableFuture<Controllers> controllers;
    private final CompletableFuture<Void> shutdownCompletion=new CompletableFuture<>();
    private final Thread shutdownHook=Thread.ofPlatform().name("courseassist-local-shutdown").unstarted(()->{
        dispose();
        try{shutdownCompletion.get(15,java.util.concurrent.TimeUnit.SECONDS);}
        catch(Exception failure){com.intellij.openapi.diagnostic.Logger.getInstance(CourseAssistantApplication.class)
                .warn("F0 local shutdown incomplete: "+failure.getClass().getSimpleName());}
    });
    public CourseAssistantApplication(){Runtime.getRuntime().addShutdownHook(shutdownHook);}
    public CompletableFuture<Void> shutdownCompletion(){return shutdownCompletion.copy();}

    public static CourseAssistantApplication getInstance() {
        return ApplicationManager.getApplication().getService(CourseAssistantApplication.class);
    }

    /** Manager-only persistence entry; adapters and views use public contracts. */
    public H2Store storage() { return storage; }
    public TaskScope tasks() { return tasks; }
    public RuntimeGate runtime() { return runtime; }

    /** Lazy business initialization off EDT. Failed/corrupt storage remains an exceptional future. */
    public synchronized CompletableFuture<Controllers> controllers() {
        if(disposed)throw new IllegalStateException("Application service disposed");
        if(controllers==null)controllers=CompletableFuture.supplyAsync(()->{
            CourseLibrary library=courses();library.initialize().join();
            var app=new ApplicationCoordinator(new ApplicationRepository(storage),runtime,library,new PlatformCredentials(),new ConfiguredRuntimeFactory(),executor,
                    failure->com.intellij.openapi.diagnostic.Logger.getInstance(CourseAssistantApplication.class).error("F0 observer/resource failure",failure));
            var sessions=new SessionCoordinator(new SessionRepository(storage,runtime),runtime,library,executor,app::resources,
                    failure->com.intellij.openapi.diagnostic.Logger.getInstance(CourseAssistantApplication.class).error("F0 observer failure",failure));
            app.sessions(sessions);
            sessions.citationOpener(citation->com.intellij.ide.BrowserUtil.browse(library.sourcePath(citation.sourceVersionId()).join()
                    .toUri().toASCIIString()+"#page="+citation.firstPhysicalPage()));
            try{sessions.initialize().toCompletableFuture().join();app.initialize().toCompletableFuture().join();return new Controllers(app,sessions);}
            catch(RuntimeException failure){sessions.close();app.close();throw failure;}
        },executor);
        return controllers.copy();
    }

    /** Lazy resource owner; initialize/operations schedule all I/O in the shared background executor. */
    public synchronized CourseLibrary courses() {
        if (disposed) throw new IllegalStateException("Application service disposed");
        if (courses == null) courses = new CourseLibrary(new CourseRepository(storage),
                new ManagedPdfStore(Path.of(PathManager.getConfigPath(), "courseassist-f0", "pdf")), this::pdfReader, executor);
        return courses;
    }

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
        // Disposal may be on EDT: drain local business commits on a worker before closing H2.
        CompletableFuture<Controllers> closing=controllers;
        executor.execute(()->{
            Throwable failure=null;
            try {
                if(closing!=null) {
                    Controllers c=closing.join();
                    try{c.sessions().stopAll().toCompletableFuture().join();}catch(Throwable error){failure=error;}
                    finally{c.sessions().close();}
                    try{c.application().shutdown().join();}catch(Throwable error){if(failure==null)failure=error;else failure.addSuppressed(error);}
                }
            }catch(Throwable error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            finally {
                if(courses!=null)courses.close();storage.close();executor.shutdown();
                try{Runtime.getRuntime().removeShutdownHook(shutdownHook);}catch(IllegalStateException jvmAlreadyStopping){/* The JVM is currently waiting for this registered local drain. */}
                if(failure==null)shutdownCompletion.complete(null);else shutdownCompletion.completeExceptionally(failure);
            }
        });
    }
}
