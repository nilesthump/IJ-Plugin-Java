package edu.courseassist.editor.f2;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import edu.courseassist.contract.EditTargetPort;
import edu.courseassist.contract.ChatContracts.CodePart;
import edu.courseassist.contract.EditContracts.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Reads existing Java files only inside the bound project's directory. Neither clues nor a file URL grant authority. */
public final class JavaEditTargetPort implements EditTargetPort {
    private final Project project;
    private final String instance;
    private final List<CodePart> selections;
    private final BooleanSupplier live;
    public JavaEditTargetPort(Project project,String instance,List<CodePart> selections,BooleanSupplier live) {
        this.project=project;this.instance=instance;this.selections=List.copyOf(selections);this.live=live;
    }
    @Override public CompletionStage<TargetResolution> resolveAndCapture(EditRequest request,TargetQuery query) {
        if(!instance.equals(request.origin().instanceId()))return CompletableFuture.failedFuture(new IllegalArgumentException("Wrong project instance"));
        if(project.isDisposed()||!live.getAsBoolean())return CompletableFuture.failedFuture(new CancellationException("Original project operation closed"));
        var result=new CompletableFuture<TargetResolution>();
        var task=ReadAction.nonBlocking(()->read(request,query)).expireWith(project).submit(AppExecutorUtil.getAppExecutorService());
        task.onSuccess(result::complete);task.onError(result::completeExceptionally);return result;
    }
    private void check(){if(project.isDisposed()||!live.getAsBoolean())throw new CancellationException("Original project operation closed");}
    private record Match(Target target,String before,long stamp,Span span) {}
    private TargetResolution read(EditRequest request,TargetQuery q) {
        check();
        if(DumbService.isDumb(project))return result(ResolutionStatus.NOT_READY,List.of());
        String path=q.pathHint().replace('\\','/');
        if(q.pathHint().length()>512||q.classHint().length()>256||q.methodHint().length()>256||q.parameterHints().size()>32
                ||q.parameterHints().stream().anyMatch(s->s.length()>256)||q.pathHint().contains(".."))
            return result(ResolutionStatus.INCOMPLETE,List.of());
        CodePart selection=null;
        if(request.captureId().isPresent()) {
            selection=selections.stream().filter(s->s.captureId().equals(request.captureId().get())).findFirst().orElseThrow(()->new IllegalArgumentException("Unbound capture"));
        }
        boolean absoluteClue=path.contains(":")||path.startsWith("/");
        if(selection==null&&absoluteClue)return result(ResolutionStatus.INCOMPLETE,List.of());
        if(selection==null&&q.pathHint().isBlank()&&q.classHint().isBlank())return result(ResolutionStatus.INCOMPLETE,List.of());
        var index=ProjectFileIndex.getInstance(project);
        VirtualFile base=project.getBasePath()==null?null:LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
        VirtualFile boundary=base==null?null:base.getCanonicalFile();
        if(boundary==null)return result(ResolutionStatus.NOT_FOUND,List.of());
        List<VirtualFile> files=new ArrayList<>();
        boolean complete;
        if(selection!=null) {
            VirtualFile file=VirtualFileManager.getInstance().findFileByUrl(selection.source().fileRef());
            if(file==null)return result(ResolutionStatus.STALE,List.of());
            if(file.getCanonicalFile()==null||!VfsUtilCore.isAncestor(boundary,file.getCanonicalFile(),false))return result(ResolutionStatus.NOT_FOUND,List.of());
            if(absoluteClue) {
                // Metadata copied by the model may name the already-bound selection file.
                // Compare only with that file; never locate a new target using this clue.
                if(!namesSelectedFile(path,file))return result(ResolutionStatus.NOT_FOUND,List.of());
                path=VfsUtilCore.getRelativePath(file,base,'/');
                if(path==null)return result(ResolutionStatus.NOT_FOUND,List.of());
            }
            files.add(file);complete=true;
        } else if(path.contains("/")) {
            // An exact project-relative path is a single bounded lookup, not a whole-project search.
            // Never fall back to a same-named file when this path is absent or outside the project directory.
            VirtualFile file=base==null?null:base.findFileByRelativePath(path);
            if(file==null||file.isDirectory()||!"java".equals(file.getExtension()))return result(ResolutionStatus.NOT_FOUND,List.of());
            VirtualFile canonical=file.getCanonicalFile();
            if(canonical==null||!VfsUtilCore.isAncestor(boundary,canonical,false))return result(ResolutionStatus.NOT_FOUND,List.of());
            files.add(file);complete=true;
        } else {
            int visited=0;complete=true;
            var pending=new ArrayDeque<VirtualFile>();pending.add(base);
            var directories=new HashSet<String>();
            while(!pending.isEmpty()) {
                check();VirtualFile file=pending.removeFirst();
                if(++visited>10000){complete=false;break;}
                VirtualFile canonical=file.getCanonicalFile();
                if(canonical==null||!(canonical.equals(boundary)||VfsUtilCore.isAncestor(boundary,canonical,false)))continue;
                if(file.isDirectory()) {
                    if(directories.add(canonical.getUrl()))Collections.addAll(pending,file.getChildren());
                } else if("java".equals(file.getExtension())) {
                    files.add(file);if(files.size()>256){complete=false;break;}
                }
            }
        }
        if(!complete)return result(ResolutionStatus.INCOMPLETE,List.of());
        List<Match> matches=new ArrayList<>();
        for(VirtualFile file:files) {
            check();
            if(!(file instanceof VirtualFileWithId))continue;
            VirtualFile canonical=file.getCanonicalFile();
            if(base==null||canonical==null||!VfsUtilCore.isAncestor(boundary,canonical,false))continue;
            String relative=project.getBasePath()==null?null:VfsUtilCore.getRelativePath(file,Objects.requireNonNull(LocalFileSystem.getInstance().findFileByPath(project.getBasePath())), '/');
            if(relative==null||!"java".equals(file.getExtension()))continue;
            if(!q.pathHint().isBlank()&&!relative.equals(path)&&!file.getName().equals(q.pathHint()))continue;
            var document=FileDocumentManager.getInstance().getDocument(file);
            if(document==null)continue;
            if(document.getTextLength()>edu.courseassist.contract.EditContracts.MAX_DOCUMENT_CHARS)return result(ResolutionStatus.INCOMPLETE,List.of());
            long stamp=document.getModificationStamp();String before=document.getText();
            var documents=PsiDocumentManager.getInstance(project);
            if(!documents.isCommitted(document))return result(ResolutionStatus.NOT_READY,List.of());
            // IDEA may expose .java outside source roots as plain-text PSI. Parse the exact
            // captured Document as Java syntax; this does not mark roots or write a physical file.
            var attached=documents.getPsiFile(document);
            PsiFile parsed=attached instanceof PsiJavaFile?attached:PsiFileFactory.getInstance(project)
                    .createFileFromText(file.getName(),com.intellij.ide.highlighter.JavaFileType.INSTANCE,before);
            if(!(parsed instanceof PsiJavaFile javaFile))throw new IllegalStateException("Java snapshot parser unavailable");
            if(!javaFile.getText().equals(before))return result(ResolutionStatus.STALE,List.of());
            Span selected=null;
            if(selection!=null) {
                if(stamp!=selection.source().documentVersion())return result(ResolutionStatus.STALE,List.of());
                int start=offset(before,selection.range().startLine(),selection.range().startColumn());
                int end=offset(before,selection.range().endLine(),selection.range().endColumn());
                if(!before.substring(start,end).equals(selection.text()))return result(ResolutionStatus.STALE,List.of());
                selected=new Span(start,end);
            }
            var module=index.getModuleForFile(file);String moduleName=module==null?"":module.getName();
            if(q.classHint().isBlank()&&q.methodHint().isBlank()) {
                matches.add(new Match(target(file,relative,moduleName,"file"),before,stamp,selected==null?new Span(0,before.length()):selected));
            } else {
                for(PsiClass cls:PsiTreeUtil.findChildrenOfType(javaFile,PsiClass.class)) {
                    if(!q.classHint().isBlank()&&!q.classHint().equals(cls.getName())&&!q.classHint().equals(cls.getQualifiedName()))continue;
                    if(q.methodHint().isBlank())add(matches,file,relative,moduleName,Objects.toString(cls.getQualifiedName(),cls.getName()),cls,before,stamp,selected);
                    else for(PsiMethod method:cls.getMethods()) {
                        boolean zeroParameters=q.methodHint().endsWith("()");
                        String methodName=zeroParameters?q.methodHint().substring(0,q.methodHint().length()-2):q.methodHint();
                        if(!method.getName().equals(methodName))continue;
                        List<String> parameters=Arrays.stream(method.getParameterList().getParameters()).map(p->p.getType().getCanonicalText()).toList();
                        if(zeroParameters&&!parameters.isEmpty())continue;
                        if(!q.parameterHints().isEmpty()&&!q.parameterHints().equals(parameters))continue;
                        add(matches,file,relative,moduleName,cls.getQualifiedName()+"#"+method.getName()+"("+String.join(",",parameters)+")",method,before,stamp,selected);
                    }
                }
            }
            if(stamp!=document.getModificationStamp()||!before.equals(document.getText()))return result(ResolutionStatus.STALE,List.of());
            if(matches.size()>20)return result(ResolutionStatus.INCOMPLETE,List.of());
        }
        check();
        if(matches.size()!=1)return result(matches.isEmpty()?ResolutionStatus.NOT_FOUND:ResolutionStatus.AMBIGUOUS,matches.stream().map(Match::target).toList());
        Match m=matches.getFirst();
        return new TargetResolution(ResolutionStatus.UNIQUE,List.of(m.target()),Optional.of(new EditSnapshot(1,UUID.randomUUID().toString(),request,m.target(),m.stamp(),m.before(),List.of(m.span()))));
    }
    private static boolean namesSelectedFile(String clue,VirtualFile file) {
        String path=clue.startsWith("file://")?clue.substring("file://".length()):clue;
        // Both the IDE's file://H:/... and the conventional file:///H:/... refer to H:/... .
        if(path.length()>3&&path.charAt(0)=='/'&&Character.isLetter(path.charAt(1))&&path.charAt(2)==':')path=path.substring(1);
        return com.intellij.openapi.util.io.FileUtil.pathsEqual(path,file.getPath());
    }
    private static void add(List<Match> matches,VirtualFile file,String relative,String module,String signature,PsiElement element,String before,long stamp,Span selected) {
        var range=element.getTextRange();
        if(selected!=null) {
            int start=selected.start(),end=selected.end();
            while(start<end&&Character.isWhitespace(before.charAt(start)))start++;
            while(end>start&&Character.isWhitespace(before.charAt(end-1)))end--;
            if(start==end||start<range.getStartOffset()||end>range.getEndOffset())return;
        }
        matches.add(new Match(target(file,relative,module,signature),before,stamp,selected==null?new Span(range.getStartOffset(),range.getEndOffset()):selected));
    }
    private static Target target(VirtualFile file,String relative,String module,String signature) {
        return new Target(file.getUrl(),relative,module,signature,"vfs:"+((VirtualFileWithId)file).getId());
    }
    private static int offset(String text,int line,int column) {
        int start=0;for(int i=1;i<line;i++){int next=text.indexOf('\n',start);if(next<0)throw new IllegalArgumentException("Capture line outside document");start=next+1;}
        int end=text.indexOf('\n',start);if(end<0)end=text.length();int offset=start+column-1;
        if(offset>end)throw new IllegalArgumentException("Capture column outside document");return offset;
    }
    private static TargetResolution result(ResolutionStatus status,List<Target> targets){return new TargetResolution(status,targets,Optional.empty());}
}
