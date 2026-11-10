package edu.courseassist.bootstrap.ide;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDocumentManager;
import edu.courseassist.contract.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CaptureContracts.*;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.core.edit.EditPatch;
import edu.courseassist.core.react.ReactContext;
import edu.courseassist.core.session.*;
import edu.courseassist.editor.JavaCodeCapturePort;
import edu.courseassist.editor.f2.*;
import edu.courseassist.ui.f2.NativeEditPreviewPort.SnapshotDiff;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import static edu.courseassist.core.react.ReactData.*;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;

/** G6: real capture/PSI -> controlled model -> durable candidate -> native buttons -> Document/Undo. */
final class F2EndToEndChecks {
    static void run(Path parent) throws Exception {
        Path root=parent.resolve("project-"+UUID.randomUUID()),src=Files.createDirectories(root.resolve("src"));
        String disk="class X {\n    int f(int n){\n        // snapshot-test\n        return n+1;\n    }\n}\n";
        Path path=src.resolve("X.java");Files.writeString(path,disk);
        var pm=ProjectManagerEx.getInstanceEx();
        var project=pm.openProject(root,OpenProjectTask.build().asNewProject().withForceOpenInNewFrame(true));
        require(project!=null,"G6 real project opened");
        String instance=edu.courseassist.bootstrap.CourseAssistantProject.getInstance(project).projectInstanceId();
        List<SnapshotDiff> viewers=new CopyOnWriteArrayList<>();
        try(var e=new SessionTestEnvironment(parent.resolve("storage-"+UUID.randomUUID()))) {
            edt(()->WriteAction.run(()->{
                var folder=LocalFileSystem.getInstance().refreshAndFindFileByNioFile(src);
                var module=ModuleManager.getInstance(project).newModule(root.resolve("main.iml").toString(),"JAVA_MODULE");
                ModuleRootModificationUtil.updateModel(module,m->m.addContentEntry(folder).addSourceFolder(folder,false));
            }));
            com.intellij.openapi.project.DumbService.getInstance(project).waitForSmartMode();
            var file=LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
            var document=ReadAction.compute(()->FileDocumentManager.getInstance().getDocument(file));
            require(document!=null,"G6 source Document");
            AtomicReference<FileEditor> editor=new AtomicReference<>();
            edt(()->editor.set(Arrays.stream(FileEditorManager.getInstance(project).openFile(file,true)).filter(x->x instanceof TextEditor).findFirst().orElseThrow()));
            // Test timeout is independent of real IDE indexing and UI scheduling; production settings unchanged.
            e.generationTimeout=Duration.ofMinutes(2);e.configureAndEnable();
            var binding=e.sessions.open(new SessionData.Location(root.toString(),"g6-project"),instance,JavaCodeCapturePort::new,true,
                    Optional.of((identity,input,permitted)->new JavaEditTargetPort(project,instance,input.code().stream().filter(p->p.kind()==PartKind.SELECTION).toList(),permitted)))
                    .toCompletableFuture().get(30,TimeUnit.SECONDS);
            EditPreviewPort preview=p->{var future=new CompletableFuture<EditPreviewPort.Display>();
                ApplicationManager.getApplication().invokeLater(()->{try{var diff=new SnapshotDiff(project,p);viewers.add(diff);diff.ready().whenComplete((v,error)->{if(error!=null)future.completeExceptionally(error);else future.complete(diff);});}catch(Throwable error){future.completeExceptionally(error);}});return future;};
            e.sessions.attachEditHost(instance,new SessionCoordinator.EditHost(preview,new JavaEditApplyPort(project,instance))).toCompletableFuture().get(30,TimeUnit.SECONDS);
            AtomicInteger parents=new AtomicInteger(),children=new AtomicInteger();
            // Both entry modes traverse the production controller/engine/tools; only model output is synthetic.
            for(boolean selection:List.of(false,true)) {
                String session=e.create(instance);
                edt(()->WriteAction.run(()->{document.setText("// unsaved "+selection+"\n"+disk);PsiDocumentManager.getInstance(project).commitDocument(document);}));
                String before=ReadAction.compute(document::getText);long stamp=document.getModificationStamp();
                if(selection) {
                    int offset=before.indexOf("    int f("),end=before.indexOf("\n}",offset)+1,line=document.getLineNumber(offset),endLine=document.getLineNumber(end);
                    String selected=before.substring(offset,end);
                    var start=new SelectionStart(UUID.randomUUID().toString(),instance,new FileSnapshotRef(file.getUrl(),stamp,"JAVA"),selected,
                            new SourceRange(line+1,offset-document.getLineStartOffset(line)+1,endLine+1,end-document.getLineStartOffset(endLine)+1));
                    e.sessions.beginCapture(start);
                    SessionTestEnvironment.await(()->e.view(instance,session).draft().captures().stream().anyMatch(c->c.state()==CaptureState.READY));
                }
                e.text(instance,session,selection?"Change the selected method from n+1 to n+2 and preserve its comment":"In src/X.java change X.f(int) from n+1 to n+2");
                parents.set(0);children.set(0);e.model.set((request,listener)->{
                    if(request.tools().isEmpty()) {
                        children.incrementAndGet();int at=before.indexOf("n+1")+2;
                        require(request.messages().toString().contains("n+1"),"Child receives real source");
                        SessionTestEnvironment.finish(listener,ReactContext.json(Map.of("changes",List.of(Map.of("start",at,"end",at+1,"before","1","after","2")))));
                    } else {
                        int step=parents.incrementAndGet();
                        if(step==1) listener.frame(new ToolDelta(0,"resolve","resolveEditTarget",ReactContext.json(Map.of("path",selection?file.getPath():"src/X.java","class","X","method","f","parameters",List.of("int")))));
                        else if(step==2) {
                            var observation=request.messages().stream().filter(m->m.role().equals("tool")&&m.callId().equals("resolve")).findFirst().orElseThrow();
                            String ref=EditPatch.JSON.readTree(observation.content()).get("snapshotRef").asString();
                            listener.frame(new ToolDelta(0,"proposal","proposeCodeEdit",ReactContext.json(Map.of("ref",ref))));
                        } else {require(step==3,"One generation cycle");SessionTestEnvironment.finish(listener,"Candidate ready, not applied.");return ()->{};}
                        listener.frame(new End("tool_calls"));listener.complete();
                    }return ()->{};
                });
                e.pauseGeneration.set(true);e.submit(instance,session);
                String other=e.create(instance);e.text(instance,other,"other session draft");var otherDraft=e.view(instance,other).draft();
                e.resumeGeneration();e.idle(instance,session);
                require(e.view(instance,other).draft().equals(otherDraft)&&e.repository.history(binding.projectId(),instance,other).isEmpty(),"Generation stays in original session after active tab switch");
                var view=e.repository.edits().views(binding.projectId(),instance,session).getFirst();
                require(view.review()==ReviewState.REVIEWABLE&&parents.get()==3&&children.get()==1,"Both entries reach one durable candidate");
                var proposal=e.repository.edits().load(binding.projectId(),instance,session,view.proposal());
                require(proposal.snapshot().before().equals(before),"Full unsaved before survives entire chain");
                require(proposal.after().equals(before.replace("n+1","n+2")),"Only requested range changed");
                int calls=e.requests.size();e.text(instance,session,"preserve draft after generation");var draft=e.view(instance,session).draft();
                e.dispatch(new OpenEditPreview(e.meta(instance,session),view.proposal()));
                SessionTestEnvironment.await(()->!viewers.isEmpty()&&enabled(viewers.getLast()));
                var diff=viewers.getLast();
                require(ReadAction.compute(document::getText).equals(before)&&Files.readString(path).equals(disk),"End-to-end preview has no writes");
                edt(()->button(diff.component(),selection?"rejectEditProposal":"approveEditProposal").doClick());
                SessionTestEnvironment.await(()->{try{return e.repository.edits().views(binding.projectId(),instance,session).getFirst().review()==(selection?ReviewState.REJECTED:ReviewState.APPROVED);}catch(Exception failure){throw new IllegalStateException(failure);}});
                e.idle(instance,session);
                require(e.requests.size()==calls&&e.queries.isEmpty()&&e.view(instance,session).draft().equals(draft),"Both actual buttons preserve draft and zero model/embedding starts");
                require(e.repository.history(binding.projectId(),instance,session).size()==1,"Buttons create no user turn");
                if(selection) require(ReadAction.compute(document::getText).equals(before),"Reject keeps exact source");
                else {
                    require(ReadAction.compute(document::getText).equals(proposal.after()),"Real generated candidate applied exactly");
                    edt(()->UndoManager.getInstance(project).undo(editor.get()));
                    require(ReadAction.compute(document::getText).equals(before),"One Undo restores unsaved input");
                    SessionTestEnvironment.await(()->e.repository.edits().views(binding.projectId(),instance,session).getFirst().receipt().orElseThrow().documentState()==DocumentState.UNDONE);
                    require(e.repository.edits().views(binding.projectId(),instance,session).getFirst().receipt().orElseThrow().documentState()==DocumentState.UNDONE,"Undo is separate observed fact");
                    edt(()->UndoManager.getInstance(project).redo(editor.get()));
                    SessionTestEnvironment.await(()->e.repository.edits().views(binding.projectId(),instance,session).getFirst().receipt().orElseThrow().documentState()==DocumentState.APPLIED);
                    edt(()->UndoManager.getInstance(project).undo(editor.get()));
                    SessionTestEnvironment.await(()->e.repository.edits().views(binding.projectId(),instance,session).getFirst().receipt().orElseThrow().documentState()==DocumentState.UNDONE);
                }
                edt(diff::close);
                // New explicit draft is the only restart and contains the latest local review facts.
                e.model.set((request,listener)->{
                    require(request.messages().toString().contains(selection?"REJECTED":"UNDONE"),"New draft receives local decision/Undo facts");
                    SessionTestEnvironment.finish(listener,"Acknowledged local edit state.");return ()->{};
                });
                e.submit(instance,session);e.idle(instance,session);
                require(e.requests.size()==calls+1&&e.repository.history(binding.projectId(),instance,session).size()==2,"Only explicit send starts next turn");
                require(e.repository.history(binding.projectId(),instance,session).getLast().attempts().getLast().eligible(),"Next draft completed with fact projection");
                require(Files.readString(path).equals(disk),"No implicit disk save in full chain");
                if(selection) {
                    // A duplicate outside Sources Root must not steal the previously selected file.
                    Path duplicate=Files.createDirectories(root.resolve("notes")).resolve("X.java");Files.writeString(duplicate,disk);
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(duplicate);
                    e.model.set((request,listener)->{SessionTestEnvironment.finish(listener,"Choose 1: change n+1 to n+3, preserve the comment and signature.");return ()->{};});
                    e.text(instance,session,"Continue editing the same method");e.submit(instance,session);e.idle(instance,session);
                    edt(()->WriteAction.run(()->{document.setText("// newer unsaved\n"+before);PsiDocumentManager.getInstance(project).commitDocument(document);}));
                    String fresh=ReadAction.compute(document::getText);AtomicInteger follow=new AtomicInteger();
                    e.model.set((request,listener)->{
                        if(request.tools().isEmpty()) {
                            require(request.messages().toString().contains("Choose 1: change n+1 to n+3"),"Numbered answer retains eligible conversation in child");
                            int at=fresh.indexOf("n+1")+2;
                            SessionTestEnvironment.finish(listener,ReactContext.json(Map.of("changes",List.of(Map.of("start",at,"end",at+1,"before","1","after","3")))));
                        } else {
                            int step=follow.incrementAndGet();
                            if(step==1)listener.frame(new ToolDelta(0,"continue-resolve","resolveEditTarget",ReactContext.json(Map.of("path","","class","","method","f","parameters",List.of("int")))));
                            else if(step==2) {
                                var observation=request.messages().stream().filter(m->m.role().equals("tool")&&m.callId().equals("continue-resolve")).findFirst().orElseThrow();
                                var data=EditPatch.JSON.readTree(observation.content());
                                require(data.get("status").asString().equals("UNIQUE"),"Follow-up keeps selected file despite duplicate");
                                listener.frame(new ToolDelta(0,"continue-propose","proposeCodeEdit",ReactContext.json(Map.of("ref",data.get("snapshotRef").asString()))));
                            } else {SessionTestEnvironment.finish(listener,"New candidate ready.");return ()->{};}
                            listener.frame(new End("tool_calls"));listener.complete();
                        }return ()->{};
                    });
                    e.text(instance,session,"1");e.submit(instance,session);e.idle(instance,session);
                    var all=e.repository.edits().views(binding.projectId(),instance,session);
                    require(all.size()==2,"Follow-up creates a new candidate after rejection");
                    var next=all.stream().filter(v->!v.proposal().equals(view.proposal())).findFirst().orElseThrow();
                    var continued=e.repository.edits().load(binding.projectId(),instance,session,next.proposal());
                    require(continued.snapshot().target().relativePath().equals("src/X.java")&&continued.snapshot().before().equals(fresh),"Continuation uses same file and new snapshot");
                    require(continued.after().equals(fresh.replace("n+1","n+3")),"Number choice modifies only current method");
                    require(ReadAction.compute(document::getText).equals(fresh)&&Files.readString(duplicate).equals(disk),"Continuation never applies or edits duplicate automatically");
                }
                if(!selection) {
                    edt(()->FileDocumentManager.getInstance().saveDocument(document));
                    SessionTestEnvironment.await(()->e.repository.edits().views(binding.projectId(),instance,session).getFirst().receipt().orElseThrow().disk()==DiskState.SAVED);
                    require(e.requests.size()==calls+1,"Automatic save/Undo observations never start a model");
                    edt(()->{WriteAction.run(()->document.setText(disk));FileDocumentManager.getInstance().saveDocument(document);});
                    SessionTestEnvironment.await(()->e.repository.edits().views(binding.projectId(),instance,session).getFirst().receipt().orElseThrow().documentState()==DocumentState.CHANGED);
                }
            }
        } finally {
            edt(()->{viewers.forEach(SnapshotDiff::close);var manager=FileEditorManager.getInstance(project);for(var open:manager.getOpenFiles())manager.closeFile(open);pm.forceCloseProject(project);});
        }
    }
    private static boolean enabled(SnapshotDiff diff){AtomicBoolean value=new AtomicBoolean();edt(()->value.set(button(diff.component(),"approveEditProposal").isEnabled()));return value.get();}
    private static void edt(Runnable action){ApplicationManager.getApplication().invokeAndWait(action);}
    private static JButton button(java.awt.Container root,String name){for(var child:root.getComponents()){if(child instanceof JButton b&&name.equals(b.getName()))return b;if(child instanceof java.awt.Container c){var found=button(c,name);if(found!=null)return found;}}return null;}
}
