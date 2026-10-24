package edu.courseassist.editor;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import edu.courseassist.bootstrap.CourseAssistantProject;
import edu.courseassist.contract.CaptureContracts.FileSnapshotRef;
import edu.courseassist.contract.CaptureContracts.SelectionStart;
import java.util.UUID;

/** Freezes a Java editor selection before the core reserves its destination session and draft. */
public final class CaptureSelectionAction extends DumbAwareAction {
    @Override
    public void update(AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean enabled = project != null
                && !project.isDisposed()
                && editor != null
                && editor.getSelectionModel().hasSelection()
                && file != null
                && JavaFileType.INSTANCE.equals(file.getFileType());
        event.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = require(event.getProject(), "Capture requires a project");
        Editor editor = require(event.getData(CommonDataKeys.EDITOR), "Capture requires an editor");
        if (project.isDisposed()) {
            throw new IllegalStateException("Capture project is already closed");
        }

        Document document = editor.getDocument();
        VirtualFile file = require(FileDocumentManager.getInstance().getFile(document),
                "Capture requires a file-backed editor document");
        if (!JavaFileType.INSTANCE.equals(file.getFileType())) {
            throw new IllegalArgumentException("Only Java editor selections can be captured");
        }

        int startOffset = editor.getSelectionModel().getSelectionStart();
        int endOffset = editor.getSelectionModel().getSelectionEnd();
        if (startOffset == endOffset) {
            throw new IllegalArgumentException("Empty selection is not a capture");
        }
        CourseAssistantProject projectService = CourseAssistantProject.getInstance(project);
        SelectionStart start = freezeSelection(
                projectService.projectInstanceId(),
                file.getUrl(),
                document.getModificationStamp(),
                JavaLanguage.INSTANCE.getID(),
                document.getCharsSequence(),
                startOffset,
                endOffset);
        projectService.selectionSink().beginCapture(start);
    }

    static SelectionStart freezeSelection(String projectInstanceId, String fileRef, long documentVersion,
                                          String languageId, CharSequence text, int startOffset, int endOffset) {
        var range = SourceRanges.fromOffsets(text, startOffset, endOffset);
        String selectedText = text.subSequence(startOffset, endOffset).toString();
        return new SelectionStart(
                UUID.randomUUID().toString(),
                projectInstanceId,
                new FileSnapshotRef(fileRef, documentVersion, languageId),
                selectedText,
                range);
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
