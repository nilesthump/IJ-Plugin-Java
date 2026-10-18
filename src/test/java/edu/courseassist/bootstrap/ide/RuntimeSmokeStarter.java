package edu.courseassist.bootstrap.ide;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import edu.courseassist.bootstrap.BootstrapToolWindowFactory;
import edu.courseassist.bootstrap.CourseAssistantApplication;
import edu.courseassist.bootstrap.CourseAssistantProject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;

/** Test-only command, packaged in a separate plugin, never in the distributable F0 plugin. */
public final class RuntimeSmokeStarter implements ApplicationStarter {
    @Override public int getRequiredModality() { return NOT_IN_EDT; }
    @Override public boolean isHeadless() { return true; }

    @Override public void main(List<String> args) {
        Path report = Path.of(args.get(1));
        int exitCode = 1;
        try {
            Files.createDirectories(report.getParent());
            run(report.getParent().resolve("scratch"));
            Files.writeString(report, """
                    {"status":"PASS","timestamp":"%s","ideBuild":"%s","javaRuntime":"%s",
                    "checks":["plugin-loaded","application-project-services","project-isolation-disposal",
                    "background-work","Swing-ToolWindow-content-release","Java-PSI-read",
                    "H2-commit-reopen","JVector-build-query","SpringAI-local-chat-stream-embedding-no-retry",
                    "corpus-managed-PDF-H2-generation-physical-page","corpus-local-vector-recovery-fixed-attempt",
                    "SpringAI-strict-SSE-finite-ReAct-H2-fixed-citation",
                    "production-session-roots-dedup-typed-code-draft-tabs-restore-hard-delete",
                    "native-directory-identity-same-volume-move-copy-isolation"],
                    "limitations":["headless IDE command; no visual interaction", "synthetic loopback HTTP only",
                    "synthetic model/corpus ports; no real PDF parser, visual UI or real provider validation"]}
                    """.formatted(Instant.now(), ApplicationInfo.getInstance().getBuild().asString(),
                    System.getProperty("java.runtime.version")));
            exitCode = 0;
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(report, "{\"status\":\"FAIL\",\"failureType\":\"" + failure.getClass().getName() + "\"}"); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        } finally {
            ((ApplicationEx) ApplicationManager.getApplication()).exit(true, true, exitCode);
        }
    }

    private static void run(Path scratch) throws Exception {
        Files.createDirectories(scratch.resolve("project-a"));Files.createDirectories(scratch.resolve("project-b"));
        var descriptor = PluginManagerCore.getPlugin(PluginId.getId("edu.courseassist.f0"));
        require(descriptor != null && descriptor.isEnabled(), "Production plugin loaded");
        require(org.springframework.ai.deepseek.DeepSeekChatModel.class.getClassLoader() == descriptor.getPluginClassLoader(),
                "Spring AI loaded by production plugin");
        require(org.h2.Driver.class.getClassLoader() == descriptor.getPluginClassLoader(), "H2 loaded by production plugin");
        require(io.github.jbellis.jvector.graph.GraphSearcher.class.getClassLoader() == descriptor.getPluginClassLoader(),
                "JVector loaded by production plugin");
        var application = CourseAssistantApplication.getInstance();
        require(application == CourseAssistantApplication.getInstance(), "Application singleton");
        require(application.courses() == application.courses(), "Application owns one shared corpus");
        require(!application.tasks().submit(() -> ApplicationManager.getApplication().isDispatchThread())
                .get(10, TimeUnit.SECONDS), "Application work off EDT");
        Project[] projects = new Project[2];
        AtomicReference<CourseAssistantProject> a = new AtomicReference<>();
        AtomicReference<CourseAssistantProject> b = new AtomicReference<>();
        try {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                projects[0] = ProjectManager.getInstance().createProject("F0-smoke-A", scratch.resolve("project-a").toString());
                projects[1] = ProjectManager.getInstance().createProject("F0-smoke-B", scratch.resolve("project-b").toString());
                require(projects[0] != null && projects[1] != null, "Synthetic projects created");
                a.set(CourseAssistantProject.getInstance(projects[0]));
                b.set(CourseAssistantProject.getInstance(projects[1]));
                require(a.get() == CourseAssistantProject.getInstance(projects[0]), "Project singleton");
                require(a.get().application() == b.get().application(), "Shared application owner");
                require(!a.get().projectInstanceId().equals(b.get().projectInstanceId()), "Distinct project instances");
                var manager = ToolWindowManager.getInstance(projects[0]);
                var window = manager.registerToolWindow("F0-smoke", true, ToolWindowAnchor.RIGHT);
                new BootstrapToolWindowFactory().createToolWindowContent(projects[0], window);
                require(window.getContentManager().getContentCount() == 1, "Tool Window factory content");
                var released = new java.util.concurrent.atomic.AtomicBoolean();
                window.getContentManager().getContent(0).setDisposer(() -> released.set(true));
                window.getContentManager().removeAllContents(true);
                require(released.get(), "View disposal");
                require(!a.get().tasks().isClosed(), "View disposal leaves project work alive");
                manager.unregisterToolWindow("F0-smoke");
            });
            String fixedText = ReadAction.nonBlocking(() -> {
                require(!ApplicationManager.getApplication().isDispatchThread(), "PSI background read");
                var file = (PsiJavaFile) PsiFileFactory.getInstance(projects[0]).createFileFromText(
                        "Smoke.java", JavaFileType.INSTANCE, "class Smoke { int field; int twice(int x) { return x * 2; } }");
                return file.getClasses()[0].getMethods()[0].getText();
            }).expireWith(a.get()).submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService())
                    .get(20, TimeUnit.SECONDS);
            require(fixedText.equals("int twice(int x) { return x * 2; }"), "PSI immutable method text");
            ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(() -> Disposer.dispose(projects[0])));
            require(a.get().tasks().isClosed() && !b.get().tasks().isClosed(), "Project disposal isolation");
            try { a.get().tasks().submit(() -> "late").join(); throw new IllegalStateException("Late work accepted"); }
            catch (CancellationException expected) { /* Lifecycle control flow, no retrieval fallback. */ }
            require(!application.tasks().isClosed(), "Project disposal preserves app resources");
            DependencyChecks.run(scratch.resolve("database"));
            ReactRuntimeChecks.run(scratch.resolve("react"));
            SessionRuntimeChecks.run(scratch.resolve("sessions"));
            // Exercise the real lazy application/project service wiring as well as isolated roots.
            b.get().openSessions(true).get(15,TimeUnit.SECONDS);
            var controller=b.get().sessionController().get(15,TimeUnit.SECONDS);
            var manager=application.controllers().get(15,TimeUnit.SECONDS).sessions();
            var create=new edu.courseassist.contract.ChatContracts.CreateSession(new edu.courseassist.contract.ChatContracts.EventMeta(
                    java.util.UUID.randomUUID().toString(),b.get().projectInstanceId(),java.util.Optional.empty()),"service-wiring");
            require(controller.dispatch(create).toCompletableFuture().get(15,TimeUnit.SECONDS).outcome()==edu.courseassist.contract.CommonContracts.Outcome.APPLIED,"Real project controller dispatch");
            String session=manager.snapshot(b.get().projectInstanceId()).activeSessionId().orElseThrow();
            controller.dispatch(new edu.courseassist.contract.ChatContracts.DeleteSession(new edu.courseassist.contract.ChatContracts.EventMeta(
                    java.util.UUID.randomUUID().toString(),b.get().projectInstanceId(),java.util.Optional.of(session)))).toCompletableFuture().get(15,TimeUnit.SECONDS);
        } finally {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                WriteAction.run(() -> {
                    for (var project : projects) if (project != null && !project.isDisposed()) Disposer.dispose(project);
                });
            });
        }
        // Explicitly verify the service's disposal boundary before normal IDE shutdown repeats it.
        application.dispose();
        application.shutdownCompletion().get(20,TimeUnit.SECONDS);
        require(application.tasks().isClosed(), "Application tasks disposed");
        try { application.storage().transaction(connection -> "late").join(); throw new IllegalStateException("Storage accepted late work"); }
        catch (java.util.concurrent.CompletionException expected) {
            require(expected.getCause() instanceof java.util.concurrent.RejectedExecutionException, "Storage closed");
        }
    }
}
