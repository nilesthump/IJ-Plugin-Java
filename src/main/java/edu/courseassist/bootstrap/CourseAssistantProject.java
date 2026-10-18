package edu.courseassist.bootstrap;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import edu.courseassist.contract.CodeCapturePort;
import edu.courseassist.core.TaskScope;
import java.util.List;
import java.util.UUID;

/** Project lifetime only. This ID never substitutes for the durable project identity (work group 4). */
public final class CourseAssistantProject implements Disposable {
    private static final ExtensionPointName<CodeCapturePort> CODE_CAPTURES =
            ExtensionPointName.create("edu.courseassist.f0.codeCapture");
    private final String projectInstanceId = UUID.randomUUID().toString();
    private final CourseAssistantApplication application = CourseAssistantApplication.getInstance();
    private final TaskScope tasks = application.newProjectScope();

    public static CourseAssistantProject getInstance(Project project) {
        return project.getService(CourseAssistantProject.class);
    }

    public String projectInstanceId() { return projectInstanceId; }
    public CourseAssistantApplication application() { return application; }
    public TaskScope tasks() { return tasks; }

    public CodeCapturePort codeCapture() {
        List<CodeCapturePort> ports = CODE_CAPTURES.getExtensionList();
        if (ports.size() != 1) throw new IllegalStateException("Exactly one code capture adapter must be registered");
        return ports.getFirst();
    }

    @Override public void dispose() { application.closeProjectScope(tasks); }
}
