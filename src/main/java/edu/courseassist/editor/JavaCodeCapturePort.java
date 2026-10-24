package edu.courseassist.editor;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import edu.courseassist.bootstrap.CourseAssistantProject;
import edu.courseassist.contract.CaptureContracts.AutomaticContext;
import edu.courseassist.contract.CaptureContracts.CapturedCodeGroup;
import edu.courseassist.contract.CaptureContracts.ContextKind;
import edu.courseassist.contract.CaptureContracts.ContextStatus;
import edu.courseassist.contract.CaptureContracts.SelectionStart;
import edu.courseassist.contract.CaptureContracts.SourceRange;
import edu.courseassist.contract.CodeCapturePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jetbrains.concurrency.CancellablePromise;

/** IDEA Java PSI adapter. The returned DTO contains only fixed source text and source coordinates. */
public final class JavaCodeCapturePort implements CodeCapturePort {
    @Override
    public CompletionStage<CapturedCodeGroup> capture(SelectionStart start) {
        Objects.requireNonNull(start, "start");
        Project project = sourceProject(start);
        CompletableFuture<CapturedCodeGroup> result = new CompletableFuture<>();
        CancellablePromise<CapturedCodeGroup> task = ReadAction.nonBlocking(() -> extract(project, start))
                .expireWith(project)
                .submit(AppExecutorUtil.getAppExecutorService());
        task.onSuccess(result::complete);
        task.onError(result::completeExceptionally);
        return result;
    }

    private static Project sourceProject(SelectionStart start) {
        List<Project> matches = new ArrayList<>();
        for (Project candidate : ProjectManager.getInstance().getOpenProjects()) {
            if (!candidate.isDisposed()
                    && CourseAssistantProject.getInstance(candidate).projectInstanceId().equals(start.projectInstanceId())) {
                matches.add(candidate);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("Frozen capture source project is unavailable");
        }
        return matches.getFirst();
    }

    private static CapturedCodeGroup extract(Project project, SelectionStart start) {
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(start.source().fileRef());
        if (file == null) {
            throw new IllegalStateException("Frozen capture file is unavailable");
        }
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) {
            throw new IllegalStateException("Frozen capture file has no document");
        }
        if (document.getModificationStamp() != start.source().documentVersion()) {
            throw new IllegalStateException("Document changed after capture started");
        }
        CharSequence documentText = document.getCharsSequence();
        int selectionStart = SourceRanges.startOffset(documentText, start.selection());
        int selectionEnd = SourceRanges.endOffset(documentText, start.selection());
        if (selectionStart == selectionEnd
                || !documentText.subSequence(selectionStart, selectionEnd).toString().equals(start.selectedText())) {
            throw new IllegalStateException("Frozen selection no longer matches the document");
        }

        PsiDocumentManager documents = PsiDocumentManager.getInstance(project);
        if (!documents.isCommitted(document)) {
            throw new IllegalStateException("PSI document is not committed for the frozen capture");
        }
        PsiFile psi = documents.getPsiFile(document);
        if (!(psi instanceof PsiJavaFile javaFile)) {
            throw new IllegalStateException("Java PSI does not match the frozen document");
        }
        if (!javaFile.getText().contentEquals(documentText)) {
            throw new IllegalStateException("Java PSI does not match the frozen document");
        }
        return new CapturedCodeGroup(start, List.of(
                methodContext(javaFile, documentText, selectionStart, selectionEnd),
                classContext(javaFile, documentText, selectionStart, selectionEnd)));
    }

    private static AutomaticContext methodContext(PsiJavaFile file, CharSequence text, int startOffset, int endOffset) {
        PsiMethod startMethod = enclosingMethod(file.findElementAt(startOffset));
        PsiMethod endMethod = enclosingMethod(file.findElementAt(endOffset - 1));
        if (startMethod == null && endMethod == null) {
            if (intersectsMethod(file, startOffset, endOffset)) {
                throw new IllegalArgumentException("Selection crosses a method boundary");
            }
            return absent(ContextKind.METHOD, "no-enclosing-method");
        }
        if (startMethod != endMethod) {
            throw new IllegalArgumentException("Selection crosses a method boundary");
        }
        return captured(ContextKind.METHOD, startMethod.getText(),
                SourceRanges.fromOffsets(text, startMethod.getTextRange().getStartOffset(), startMethod.getTextRange().getEndOffset()));
    }

    private static AutomaticContext classContext(PsiJavaFile file, CharSequence text, int startOffset, int endOffset) {
        PsiClass startClass = enclosingClass(file.findElementAt(startOffset));
        PsiClass endClass = enclosingClass(file.findElementAt(endOffset - 1));
        if (startClass == null && endClass == null) {
            return absent(ContextKind.CLASS_STRUCTURE, "no-enclosing-class");
        }
        if (startClass != endClass) {
            throw new IllegalArgumentException("Selection crosses a class boundary");
        }
        return captured(ContextKind.CLASS_STRUCTURE, renderClassStructure(startClass),
                SourceRanges.fromOffsets(text, startClass.getTextRange().getStartOffset(), startClass.getTextRange().getEndOffset()));
    }

    private static PsiMethod enclosingMethod(PsiElement element) {
        return element == null ? null : PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    }

    private static PsiClass enclosingClass(PsiElement element) {
        return element == null ? null : PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    }

    private static boolean intersectsMethod(PsiJavaFile file, int startOffset, int endOffset) {
        return PsiTreeUtil.collectElementsOfType(file, PsiMethod.class).stream()
                .anyMatch(method -> method.getTextRange().getStartOffset() < endOffset
                        && startOffset < method.getTextRange().getEndOffset());
    }

    private static String renderClassStructure(PsiClass psiClass) {
        PsiElement leftBrace = psiClass.getLBrace();
        if (leftBrace == null) {
            throw new IllegalStateException("Enclosing class has no body");
        }
        int classStart = psiClass.getTextRange().getStartOffset();
        int headerEnd = leftBrace.getTextRange().getEndOffset();
        StringBuilder structure = new StringBuilder(psiClass.getText().substring(0, headerEnd - classStart));
        for (PsiField field : psiClass.getFields()) {
            structure.append('\n').append(field.getText());
        }
        for (PsiClass nestedClass : psiClass.getInnerClasses()) {
            structure.append('\n').append(renderClassStructure(nestedClass));
        }
        return structure.append('\n').append('}').toString();
    }

    private static AutomaticContext captured(ContextKind kind, String value, SourceRange range) {
        return new AutomaticContext(kind, ContextStatus.CAPTURED, Optional.of(value), Optional.of(range), Optional.empty());
    }

    private static AutomaticContext absent(ContextKind kind, String reason) {
        return new AutomaticContext(kind, ContextStatus.NOT_APPLICABLE, Optional.empty(), Optional.empty(), Optional.of(reason));
    }
}
