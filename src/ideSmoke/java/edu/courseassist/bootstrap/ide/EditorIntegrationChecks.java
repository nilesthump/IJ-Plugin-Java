package edu.courseassist.bootstrap.ide;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDocumentManager;
import edu.courseassist.bootstrap.CourseAssistantProject;
import edu.courseassist.contract.CaptureContracts.ContextKind;
import edu.courseassist.contract.ChatContracts.CaptureState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;

/** Actual registered editor action, platform PSI and durable capture, without a remote model. */
final class EditorIntegrationChecks {
    static void run(Path root) throws Exception {
        Path directory = Files.createDirectories(root.resolve("project-" + UUID.randomUUID()));
        String source = "class CaptureSample { int field; int twice(int x) { return x * 2; } void unrelated() { System.out.println(99); } }";
        Path file = Files.writeString(directory.resolve("CaptureSample.java"), source);
        var projects = ProjectManagerEx.getInstanceEx();
        var project = projects.openProject(directory, OpenProjectTask.build().asNewProject().withForceOpenInNewFrame(true));
        require(project != null, "Opened isolated editor project");
        try {
            var service = CourseAssistantProject.getInstance(project);
            var sessions = service.application().controllers().get().sessions();
            var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
            require(virtualFile != null, "Fixture Java file refreshed");
            // First use without opening the Tool Window must guide identity recovery, not throw
            // or queue a capture against a later active session.
            ApplicationManager.getApplication().invokeAndWait(() -> {
                var context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build();
                var action = ActionManager.getInstance().getAction("edu.courseassist.CaptureSelection");
                action.actionPerformed(AnActionEvent.createFromAnAction(action, null, ActionPlaces.EDITOR_POPUP, context));
            });
            try { service.openSessions(false).get(20, TimeUnit.SECONDS); }
            catch (java.util.concurrent.ExecutionException expected) {
                require(edu.courseassist.core.react.ReactEngine.unwrap(expected) instanceof edu.courseassist.core.session.SessionData.IdentityUncertain,
                        "First action exposes identity selection");
            }
            service.openSessions(true).get(20, TimeUnit.SECONDS);
            require(sessions.snapshot(service.projectInstanceId()).sessions().isEmpty(), "First action never queues deferred capture");
            ApplicationManager.getApplication().invokeAndWait(() -> {
                var document = FileDocumentManager.getInstance().getDocument(virtualFile);
                require(document != null, "Fixture document");
                WriteAction.run(() -> PsiDocumentManager.getInstance(project).commitDocument(document));
                var editor = EditorFactory.getInstance().createEditor(document, project);
                try {
                    int start = source.indexOf("return x * 2;");
                    editor.getSelectionModel().setSelection(start, start + "return x * 2;".length());
                    var context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
                            .add(CommonDataKeys.EDITOR, editor).add(CommonDataKeys.VIRTUAL_FILE, virtualFile).build();
                    var action = ActionManager.getInstance().getAction("edu.courseassist.CaptureSelection");
                    action.actionPerformed(AnActionEvent.createFromAnAction(action, null, ActionPlaces.EDITOR_POPUP, context));
                } finally { EditorFactory.getInstance().releaseEditor(editor); }
            });
            SessionTestEnvironment.await(() -> sessions.snapshot(service.projectInstanceId()).sessions().stream()
                    .anyMatch(s -> s.draft().captures().stream().anyMatch(c -> c.state() == CaptureState.READY || c.state() == CaptureState.FAILED)));
            var capture = sessions.snapshot(service.projectInstanceId()).sessions().getFirst().draft().captures().getFirst();
            require(capture.state() == CaptureState.READY, "Registered capture completed successfully");
            var group = capture.captured().orElseThrow();
            require(group.start().selectedText().equals("return x * 2;"), "Original selection unchanged");
            require(group.automaticContexts().stream().filter(c -> c.kind() == ContextKind.METHOD).findFirst().orElseThrow()
                    .text().orElseThrow().equals("int twice(int x) { return x * 2; }"), "Exact containing method");
            String structure = group.automaticContexts().stream().filter(c -> c.kind() == ContextKind.CLASS_STRUCTURE)
                    .findFirst().orElseThrow().text().orElseThrow();
            require(structure.contains("int field;") && !structure.contains("println"), "Class fields without unrelated method body");
        } finally {
            ApplicationManager.getApplication().invokeAndWait(() -> projects.forceCloseProject(project, true));
        }
    }
}
