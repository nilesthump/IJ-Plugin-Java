package edu.courseassist.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Owns read-only IDEA editors and rebuilds highlighters on scheme changes. */
final class IdeCodeViewer implements AutoCloseable {
    private final Project project;
    private final MessageBusConnection colors;
    private final List<Entry> editors = new ArrayList<>();
    private boolean closed;

    IdeCodeViewer(Project project) {
        this.project = Objects.requireNonNull(project, "project");
        colors = ApplicationManager.getApplication().getMessageBus().connect();
        colors.subscribe(EditorColorsManager.TOPIC, scheme -> SwingUtilities.invokeLater(() -> {
            if (!closed) refreshColors(scheme);
        }));
    }

    JComponent create(String source, String language) {
        assertEdt();
        if (closed) throw new IllegalStateException("Code viewer was closed");
        FileType type = fileType(language);
        EditorEx editor = (EditorEx) EditorFactory.getInstance().createViewer(
                EditorFactory.getInstance().createDocument(Objects.requireNonNull(source, "source")), project);
        editor.setViewer(true);
        editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, type));
        editor.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());
        editor.getSettings().setLineNumbersShown(false);
        editors.add(new Entry(editor, type));
        return editor.getComponent();
    }

    void clear() {
        assertEdt();
        for (Entry entry : editors) EditorFactory.getInstance().releaseEditor(entry.editor());
        editors.clear();
    }

    private void refreshColors(EditorColorsScheme scheme) {
        assertEdt();
        for (Entry entry : editors) {
            entry.editor().setColorsScheme(scheme);
            entry.editor().setHighlighter(EditorHighlighterFactory.getInstance()
                    .createEditorHighlighter(project, entry.type()));
        }
    }

    private static FileType fileType(String language) {
        FileTypeManager manager = FileTypeManager.getInstance();
        if ("java".equalsIgnoreCase(language)) {
            FileType java = manager.getFileTypeByExtension("java");
            if (!"JAVA".equalsIgnoreCase(java.getName())) {
                throw new IllegalStateException("IDEA Java syntax highlighter is unavailable");
            }
            return java;
        }
        return manager.getFileTypeByExtension("txt");
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("IDE editor access requires EDT");
    }

    @Override public void close() {
        closed = true;
        colors.disconnect();
        if (SwingUtilities.isEventDispatchThread()) clear();
        else SwingUtilities.invokeLater(this::clear);
    }

    private record Entry(EditorEx editor, FileType type) {}
}
