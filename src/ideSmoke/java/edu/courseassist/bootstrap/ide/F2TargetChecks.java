package edu.courseassist.bootstrap.ide;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.*;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDocumentManager;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CaptureContracts.*;
import edu.courseassist.editor.f2.JavaEditTargetPort;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;

/** Actual project roots, PSI and unsaved Documents. Never writes through an F2 adapter. */
final class F2TargetChecks {
    static void run(Path parent) throws Exception {
        Path root=parent.resolve("project-"+UUID.randomUUID());
        Files.createDirectories(root);String source="class X { int f(int n){return n+1;} int f(){return 0;} class Nested { void g(){} } }\n";
        Path a=Files.createDirectories(root.resolve("a")),b=Files.createDirectories(root.resolve("b"));
        Files.writeString(a.resolve("X.java"),source);Files.writeString(b.resolve("X.java"),source);
        var projects=ProjectManagerEx.getInstanceEx();var project=projects.openProject(root,OpenProjectTask.build().asNewProject().withForceOpenInNewFrame(true));
        require(project!=null,"F2 isolated project opened");AtomicBoolean live=new AtomicBoolean(true);
        try {
            ApplicationManager.getApplication().invokeAndWait(()->WriteAction.run(()->{
                for(Path path:List.of(a,b)) {
                    var folder=LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);require(folder!=null,"F2 source folder");
                    var module=ModuleManager.getInstance(project).newModule(path.resolve(path.getFileName()+".iml").toString(),"JAVA_MODULE");
                    ModuleRootModificationUtil.updateModel(module,model->model.addContentEntry(folder).addSourceFolder(folder,false));
                }
            }));
            com.intellij.openapi.project.DumbService.getInstance(project).waitForSmartMode();
            var request=new EditRequest("r",new Origin("p","i","s","t","a","o",0),"t",0,"Change f",Optional.empty());
            var port=new JavaEditTargetPort(project,"i",List.of(),live::get);
            var ambiguous=resolve(port,request,new TargetQuery("X.java","","",List.of()));
            require(ambiguous.status()==ResolutionStatus.AMBIGUOUS,"Same file name across modules stays ambiguous: "+ambiguous.status()+" candidates="+ambiguous.candidates());
            require(resolve(port,request,new TargetQuery("a/X.java","X","f",List.of())).status()==ResolutionStatus.AMBIGUOUS,"Overloads not guessed");
            require(resolve(port,request,new TargetQuery("a/X.java","X","f()",List.of())).status()==ResolutionStatus.UNIQUE,"Explicit zero parameter overload resolves");
            var exact=resolve(port,request,new TargetQuery("a/X.java","X","f",List.of("int")));
            require(exact.status()==ResolutionStatus.UNIQUE,"Exact signature resolves");
            require(exact.snapshot().orElseThrow().before().equals(source),"Complete same-version before");
            require(exact.snapshot().orElseThrow().target().fileIdentity().startsWith("vfs:"),"Stable VFS identity separate from URL");
            require(resolve(port,request,new TargetQuery("a/X.java","Nested","g",List.of())).status()==ResolutionStatus.UNIQUE,"Nested class resolves");
            require(resolve(port,request,new TargetQuery("absent.java","","",List.of())).status()==ResolutionStatus.NOT_FOUND,"Missing distinct");
            require(resolve(port,request,new TargetQuery("","","f",List.of())).status()==ResolutionStatus.INCOMPLETE,"Incomplete query distinct");
            var file=LocalFileSystem.getInstance().findFileByNioFile(a.resolve("X.java"));
            var document=ReadAction.compute(()->FileDocumentManager.getInstance().getDocument(file));
            long oldStamp=document.getModificationStamp();
            var selected=new CodePart("part","snap","capture",PartKind.SELECTION,new FileSnapshotRef(file.getUrl(),oldStamp,"JAVA"),new SourceRange(1,1,1,6),"class");
            var selectedRequest=new EditRequest("r2",request.origin(),"t",0,"change",Optional.of("capture"));
            var selectedPort=new JavaEditTargetPort(project,"i",List.of(selected),live::get);
            require(resolve(selectedPort,selectedRequest,new TargetQuery("","","",List.of())).status()==ResolutionStatus.UNIQUE,"Frozen selection completes full before");
            require(resolve(selectedPort,selectedRequest,new TargetQuery("b/X.java","","",List.of())).status()==ResolutionStatus.NOT_FOUND,"Conflicting text cannot retarget selection");
            ApplicationManager.getApplication().invokeAndWait(()->WriteAction.run(()->{document.setText(source.replace("n+1","n+2"));PsiDocumentManager.getInstance(project).commitDocument(document);}));
            var dirty=resolve(port,request,new TargetQuery("a/X.java","X","f",List.of("int")));
            require(dirty.snapshot().orElseThrow().before().contains("n+2"),"Unsaved Document captured");
            require(Files.readString(a.resolve("X.java")).equals(source),"Capture never saves or writes source");
            require(resolve(selectedPort,selectedRequest,new TargetQuery("","","",List.of())).status()==ResolutionStatus.STALE,"Old F0 capture cannot acquire newer before");
            var dumb=com.intellij.openapi.project.DumbService.getInstance(project);
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            dumb.queueTask(new com.intellij.openapi.project.DumbModeTask(){
                public void performInDumbMode(com.intellij.openapi.progress.ProgressIndicator indicator){entered.countDown();try{if(!release.await(30,TimeUnit.SECONDS))throw new IllegalStateException("Index test release missing");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new CancellationException();}}
            });
            try {
                require(entered.await(15,TimeUnit.SECONDS),"Real dumb mode entered");
                require(resolve(port,request,new TargetQuery("a/X.java","","",List.of())).status()==ResolutionStatus.NOT_READY,"Index unready is explicit, never a missing target");
            } finally {release.countDown();}
            dumb.waitForSmartMode();
            for(int n=0;n<257;n++)Files.writeString(a.resolve("Bound"+n+".java"),"class Bound"+n+" {}\n");
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(a).refresh(false,true);
            dumb.waitForSmartMode();
            require(resolve(port,request,new TargetQuery("a/X.java","","",List.of())).status()==ResolutionStatus.INCOMPLETE,"Truncated search cannot claim unique even with an exact clue");
            live.set(false);
            try{resolve(port,request,new TargetQuery("a/X.java","","",List.of()));throw new IllegalStateException("Revoked target accepted");}
            catch(ExecutionException expected){require(edu.courseassist.core.react.ReactEngine.unwrap(expected) instanceof CancellationException,"Original lifecycle cancellation");}
            catch(CancellationException expected){require(!live.get(),"Original lifecycle cancellation is not retrieval failure");}
        } finally {
            ApplicationManager.getApplication().invokeAndWait(()->projects.forceCloseProject(project));
        }
    }
    private static TargetResolution resolve(JavaEditTargetPort port,EditRequest request,TargetQuery query)throws Exception {
        return port.resolveAndCapture(request,query).toCompletableFuture().get(30,TimeUnit.SECONDS);
    }
}
