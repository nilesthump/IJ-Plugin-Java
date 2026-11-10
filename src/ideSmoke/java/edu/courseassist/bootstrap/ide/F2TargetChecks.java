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
        Files.createDirectories(root);String source="class X {\n    int f(int n){\n        // snapshot-test\n        return n+1;\n    }\n    int f(){return 0;} class Nested { void g(){} }\n}\n";
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
            Path notes=Files.createDirectories(root.resolve("notes")),examples=Files.createDirectories(root.resolve("examples"));
            String loose="class Loose { int add(int a,int b){return a-b;} }\n";
            Files.writeString(notes.resolve("Loose.java"),loose);Files.writeString(examples.resolve("Loose.java"),loose);
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(notes.resolve("Loose.java"));
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(examples.resolve("Loose.java"));
            com.intellij.openapi.project.DumbService.getInstance(project).waitForSmartMode();
            var looseMatches=resolve(port,request,new TargetQuery("Loose.java","Loose","add",List.of("int","int")));
            require(looseMatches.status()==ResolutionStatus.AMBIGUOUS&&looseMatches.snapshot().isEmpty(),"Unmarked directories participate in ambiguity; no first match guessed: "+looseMatches.status()+" candidates="+looseMatches.candidates());
            require(resolve(port,request,new TargetQuery("notes/Loose.java","Loose","add",List.of("int","int"))).status()==ResolutionStatus.UNIQUE,"Explicit path disambiguates Java method without a source root or module");
            var file=LocalFileSystem.getInstance().findFileByNioFile(a.resolve("X.java"));
            var document=ReadAction.compute(()->FileDocumentManager.getInstance().getDocument(file));
            long oldStamp=document.getModificationStamp();
            var selected=new CodePart("part","snap","capture",PartKind.SELECTION,new FileSnapshotRef(file.getUrl(),oldStamp,"JAVA"),new SourceRange(1,1,1,6),"class");
            var selectedRequest=new EditRequest("r2",request.origin(),"t",0,"change",Optional.of("capture"));
            var selectedPort=new JavaEditTargetPort(project,"i",List.of(selected),live::get);
            require(resolve(selectedPort,selectedRequest,new TargetQuery("","","",List.of())).status()==ResolutionStatus.UNIQUE,"Frozen selection completes full before");
            require(resolve(selectedPort,selectedRequest,new TargetQuery("b/X.java","","",List.of())).status()==ResolutionStatus.NOT_FOUND,"Conflicting text cannot retarget selection");
            int selectionStart=source.indexOf("    int f(int"),selectionEnd=source.indexOf("    int f()");
            var padded=new CodePart("padded","padded-snapshot","padded-capture",PartKind.SELECTION,new FileSnapshotRef(file.getUrl(),oldStamp,"JAVA"),new SourceRange(2,1,6,1),source.substring(selectionStart,selectionEnd));
            var paddedRequest=new EditRequest("padded-request",request.origin(),"t",0,"Change the selected method and preserve comments",Optional.of(padded.captureId()));
            var paddedPort=new JavaEditTargetPort(project,"i",List.of(padded),live::get);
            var relativeSelection=resolve(paddedPort,paddedRequest,new TargetQuery("a/X.java","X","f",List.of("int")));
            var absoluteSelection=resolve(paddedPort,paddedRequest,new TargetQuery(file.getPath(),"X","f",List.of("int")));
            var urlSelection=resolve(paddedPort,paddedRequest,new TargetQuery(file.getUrl(),"X","f",List.of("int")));
            require(relativeSelection.status()==ResolutionStatus.UNIQUE&&absoluteSelection.status()==ResolutionStatus.UNIQUE&&urlSelection.status()==ResolutionStatus.UNIQUE,
                    "Padded real method selection resolves with relative/absolute/URL clues: "+relativeSelection.status()+"/"+absoluteSelection.status()+"/"+urlSelection.status());
            for(String clue:List.of("", "a/X.java",file.getPath(),file.getPath().replace('/', '\\'),file.getUrl(),a.resolve("X.java").toUri().toString())) {
                var resolved=resolve(paddedPort,paddedRequest,new TargetQuery(clue,"X","f",List.of("int")));
                require(resolved.status()==ResolutionStatus.UNIQUE,"Bound selection path alias resolves: "+clue);
                var snapshot=resolved.snapshot().orElseThrow();
                require(snapshot.target().fileRef().equals(file.getUrl())&&snapshot.before().equals(source)&&snapshot.documentStamp()==oldStamp,"Path alias keeps original file/version/text");
                require(snapshot.allowedRanges().equals(List.of(new Span(selectionStart,selectionEnd))),"Whitespace matching never widens or replaces the frozen selection range");
            }
            var otherFile=LocalFileSystem.getInstance().findFileByNioFile(b.resolve("X.java"));
            for(String clue:List.of("b/X.java",otherFile.getPath(),otherFile.getUrl()))
                require(resolve(paddedPort,paddedRequest,new TargetQuery(clue,"X","f",List.of("int"))).status()==ResolutionStatus.NOT_FOUND,"Conflicting relative/absolute/URL clues cannot retarget a bound selection");
            require(resolve(port,request,new TargetQuery(file.getPath(),"X","f",List.of("int"))).status()==ResolutionStatus.INCOMPLETE,"Text-only absolute path still requires project-relative clue");
            var crossing=new CodePart("crossing","crossing-snapshot","crossing-capture",PartKind.SELECTION,new FileSnapshotRef(file.getUrl(),oldStamp,"JAVA"),new SourceRange(1,1,6,1),source.substring(0,selectionEnd));
            require(resolve(new JavaEditTargetPort(project,"i",List.of(crossing),live::get),new EditRequest("crossing-request",request.origin(),"t",0,"Change f",Optional.of(crossing.captureId())),new TargetQuery("a/X.java","X","f",List.of("int"))).status()==ResolutionStatus.NOT_FOUND,"Non-whitespace outside method is not trimmed away");
            var whitespace=new CodePart("whitespace","whitespace-snapshot","whitespace-capture",PartKind.SELECTION,new FileSnapshotRef(file.getUrl(),oldStamp,"JAVA"),new SourceRange(2,1,2,5),"    ");
            require(resolve(new JavaEditTargetPort(project,"i",List.of(whitespace),live::get),new EditRequest("whitespace-request",request.origin(),"t",0,"Change f",Optional.of(whitespace.captureId())),new TargetQuery(file.getUrl(),"X","f",List.of("int"))).status()==ResolutionStatus.NOT_FOUND,"Whitespace-only selection does not match a method");
            Path foreignPath=parent.resolve("outside-"+UUID.randomUUID()+".java");Files.writeString(foreignPath,source);
            var foreignFile=LocalFileSystem.getInstance().refreshAndFindFileByNioFile(foreignPath);
            require(resolve(paddedPort,paddedRequest,new TargetQuery(foreignFile.getPath(),"X","f",List.of("int"))).status()==ResolutionStatus.NOT_FOUND,"External clue cannot replace the selected file");
            var foreignCapture=new CodePart("foreign","foreign-snapshot","foreign-capture",PartKind.SELECTION,new FileSnapshotRef(foreignFile.getUrl(),0,"JAVA"),new SourceRange(1,1,1,6),"class");
            require(resolve(new JavaEditTargetPort(project,"i",List.of(foreignCapture),live::get),new EditRequest("foreign-request",request.origin(),"t",0,"Change f",Optional.of(foreignCapture.captureId())),new TargetQuery(foreignFile.getPath(),"X","f",List.of("int"))).status()==ResolutionStatus.NOT_FOUND,"Matching alias cannot authorize a capture outside the project directory");
            ApplicationManager.getApplication().invokeAndWait(()->WriteAction.run(()->{document.setText(source.replace("n+1","n+2"));PsiDocumentManager.getInstance(project).commitDocument(document);}));
            var dirty=resolve(port,request,new TargetQuery("a/X.java","X","f",List.of("int")));
            require(dirty.snapshot().orElseThrow().before().contains("n+2"),"Unsaved Document captured");
            require(Files.readString(a.resolve("X.java")).equals(source),"Capture never saves or writes source");
            require(resolve(selectedPort,selectedRequest,new TargetQuery("","","",List.of())).status()==ResolutionStatus.STALE,"Old F0 capture cannot acquire newer before");
            require(resolve(paddedPort,paddedRequest,new TargetQuery(file.getPath(),"X","f",List.of("int"))).status()==ResolutionStatus.STALE,"Absolute alias cannot bypass stale capture");
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
            require(resolve(port,request,new TargetQuery("X.java","","",List.of())).status()==ResolutionStatus.INCOMPLETE,"Truncated basename search cannot claim unique");
            require(resolve(port,request,new TargetQuery("a/X.java","X","f",List.of("int"))).status()==ResolutionStatus.UNIQUE,"Exact project-relative path must not scan unrelated Java files");
            require(resolve(port,request,new TargetQuery("a\\X.java","X","f",List.of("int"))).status()==ResolutionStatus.UNIQUE,"Windows relative separators use exact lookup");
            require(resolve(port,request,new TargetQuery("missing/X.java","","",List.of())).status()==ResolutionStatus.NOT_FOUND,"Missing exact path never falls back to another X.java");
            require(resolve(port,request,new TargetQuery("/a/X.java","","",List.of())).status()==ResolutionStatus.INCOMPLETE,"Absolute path is not project-relative");
            require(resolve(port,request,new TargetQuery("\\a\\X.java","","",List.of())).status()==ResolutionStatus.INCOMPLETE,"Windows rooted path is not project-relative");
            Path samples=Files.createDirectories(root.resolve("spec/samples"));Files.writeString(samples.resolve("X.java"),source);
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(samples.resolve("X.java"));
            dumb.waitForSmartMode();
            var unmarked=resolve(port,request,new TargetQuery("spec/samples/X.java","X","f",List.of("int")));
            require(unmarked.status()==ResolutionStatus.UNIQUE,"Existing Java method outside source roots remains editable inside the project: "+unmarked.status());
            require(resolve(port,request,new TargetQuery("../X.java","","",List.of())).status()==ResolutionStatus.INCOMPLETE,"Traversal cannot expand exact lookup");
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
