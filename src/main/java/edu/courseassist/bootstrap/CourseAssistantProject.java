package edu.courseassist.bootstrap;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import edu.courseassist.contract.CodeCapturePort;
import edu.courseassist.core.TaskScope;
import java.util.List;
import java.util.UUID;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import edu.courseassist.core.session.SessionData.Binding;
import edu.courseassist.contract.SessionController;
import edu.courseassist.contract.SelectionSink;

/** Project lifetime only. This ID never substitutes for the durable project identity (work group 4). */
public final class CourseAssistantProject implements Disposable {
    private static final ExtensionPointName<CodeCapturePort> CODE_CAPTURES =
            ExtensionPointName.create("edu.courseassist.f0.codeCapture");
    private final String projectInstanceId = UUID.randomUUID().toString();
    private final CourseAssistantApplication application = CourseAssistantApplication.getInstance();
    private final TaskScope tasks = application.newProjectScope();
    private final Project project;
    private CompletableFuture<Binding> binding;

    public CourseAssistantProject(Project project){this.project=project;}

    public static CourseAssistantProject getInstance(Project project) {
        return project.getService(CourseAssistantProject.class);
    }

    public String projectInstanceId() { return projectInstanceId; }
    public CourseAssistantApplication application() { return application; }
    public TaskScope tasks() { return tasks; }

    /** Unknown identity stays exceptional until the view explicitly chooses new/association. */
    public synchronized CompletableFuture<Binding> openSessions(boolean asNew) {
        if(binding==null||binding.isCompletedExceptionally()) {
            binding=application.tasks().submit(()->{
                if(project.getBasePath()==null)throw new IllegalStateException("Project has no local directory");
                return ProjectLocations.read(Path.of(project.getBasePath()));
            }).thenCompose(location->application.controllers().thenCompose(c->c.sessions().open(location,projectInstanceId,this::codeCapture,asNew)).toCompletableFuture());
        }
        return binding.copy();
    }
    public CompletableFuture<SessionController> sessionController(){return openSessions(false).thenCompose(ignored->application.controllers().thenApply(c->c.sessions()));}
    public SelectionSink selectionSink(){return start->{
        if(!start.projectInstanceId().equals(projectInstanceId))throw new IllegalArgumentException("Capture from another project instance");
        CompletableFuture<Binding> current;
        synchronized(this){current=binding;}
        if(current==null||!current.isDone()||current.isCompletedExceptionally())throw new IllegalStateException("会话正在初始化，请完成项目身份选择后重试");
        return application.controllers().join().sessions().beginCapture(start);
    };}

    public CodeCapturePort codeCapture() {
        List<CodeCapturePort> ports = CODE_CAPTURES.getExtensionList();
        if (ports.size() != 1) throw new IllegalStateException("Exactly one code capture adapter must be registered");
        return ports.getFirst();
    }

    @Override public void dispose() {
        CompletableFuture<Binding> current;
        synchronized(this){current=binding;}
        if(current!=null)current.thenCompose(ignored->application.controllers()).thenCompose(c->c.sessions().closeProject(projectInstanceId));
        application.closeProjectScope(tasks);
    }
}
