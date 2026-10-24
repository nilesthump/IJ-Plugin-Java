package edu.courseassist.bootstrap;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.ToolWindowManager;
import edu.courseassist.editor.CaptureSelectionAction;

/** Readiness boundary around the member's synchronous, frozen-target editor action. */
public final class CaptureEntryAction extends DumbAwareAction {
    private final CaptureSelectionAction capture = new CaptureSelectionAction();

    @Override public void update(AnActionEvent event) { capture.update(event); }

    @Override public void actionPerformed(AnActionEvent event) {
        var project = event.getProject();
        if (project == null || project.isDisposed()) return;
        var opening = CourseAssistantProject.getInstance(project).openSessions(false);
        var window = ToolWindowManager.getInstance(project).getToolWindow("Course Assistant");
        if (window != null) window.activate(null);
        if (!opening.isDone() || opening.isCompletedExceptionally()) {
            NotificationGroupManager.getInstance().getNotificationGroup("Course Assistant")
                    .createNotification("代码尚未添加", "请等待课程助教恢复完成，或完成项目身份选择，然后重新右键添加代码。",
                            NotificationType.INFORMATION).notify(project);
            return;
        }
        // No asynchronous recapture: the delegate binds the current target during this action.
        capture.actionPerformed(event);
    }
}
