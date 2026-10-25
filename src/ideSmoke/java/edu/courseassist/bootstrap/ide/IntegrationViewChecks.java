package edu.courseassist.bootstrap.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import edu.courseassist.bootstrap.BootstrapToolWindowFactory;
import edu.courseassist.bootstrap.CourseAssistantProject;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JTextArea;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;

/** Exercises real Swing actions against the production H2 controller in an isolated IDE. */
final class IntegrationViewChecks {
    static void run(Project project, CourseAssistantProject service) throws Exception {
        var sessions = service.application().controllers().get().sessions();
        int before = sessions.snapshot(service.projectInstanceId()).sessions().size();
        AtomicReference<ToolWindow> window = new AtomicReference<>();
        edt(() -> {
            window.set(ToolWindowManager.getInstance(project).registerToolWindow("F0-integration", true, ToolWindowAnchor.RIGHT));
            new BootstrapToolWindowFactory().createToolWindowContent(project, window.get());
        });
        try {
            awaitComponent(window.get(), "createSession");
            // The name dialog requires a display. Create via the real command, then exercise
            // non-dialog Swing actions; human black-box testing covers the dialog itself.
            sessions.dispatch(new edu.courseassist.contract.ChatContracts.CreateSession(
                    new edu.courseassist.contract.ChatContracts.EventMeta(java.util.UUID.randomUUID().toString(),
                            service.projectInstanceId(), java.util.Optional.empty()), "integration-view"))
                    .toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
            SessionTestEnvironment.await(() -> sessions.snapshot(service.projectInstanceId()).sessions().size() == before + 1);
            String session = sessions.snapshot(service.projectInstanceId()).activeSessionId().orElseThrow();
            // Drain published state before editing the widget belonging to the new session.
            edt(() -> {});
            edt(() -> {
                var root = window.get().getContentManager().getContent(0).getComponent();
                ((javax.swing.text.JTextComponent) find(root, "draftPreview")).setText("integration draft preserved exactly\n");
                // Debounced autosave must persist without a separate save button.
            });
            SessionTestEnvironment.await(() -> sessions.snapshot(service.projectInstanceId()).sessions().stream()
                    .anyMatch(s -> s.sessionId().equals(session) && s.draft().text().equals("integration draft preserved exactly\n")));
            edt(() -> window.get().getContentManager().removeAllContents(true));
            require(!service.tasks().isClosed(), "Closing production view preserves project work");
            require(sessions.snapshot(service.projectInstanceId()).sessions().stream().anyMatch(s -> s.sessionId().equals(session)),
                    "Closing production view preserves durable session");
            edt(() -> new BootstrapToolWindowFactory().createToolWindowContent(project, window.get()));
            awaitComponent(window.get(), "draftPreview");
            edt(() -> require(((javax.swing.text.JTextComponent) find(window.get().getContentManager().getContent(0).getComponent(), "draftPreview"))
                    .getText().equals("integration draft preserved exactly\n"), "Reopened real view restores draft"));
        } finally {
            edt(() -> { window.get().getContentManager().removeAllContents(true);
                ToolWindowManager.getInstance(project).unregisterToolWindow("F0-integration"); });
        }
    }
    private static void awaitComponent(ToolWindow window, String name) throws Exception {
        SessionTestEnvironment.await(() -> {
            var found = new java.util.concurrent.atomic.AtomicBoolean();
            edt(() -> found.set(find(window.getContentManager().getContent(0).getComponent(), name) != null));
            return found.get();
        });
    }
    private static void edt(Runnable operation) { ApplicationManager.getApplication().invokeAndWait(operation); }
    private static Component find(Component component, String name) {
        if (name.equals(component.getName())) return component;
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            Component found = find(child, name); if (found != null) return found;
        }
        return null;
    }
}
