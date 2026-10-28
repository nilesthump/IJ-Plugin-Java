package edu.courseassist.bootstrap.ide;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import edu.courseassist.contract.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.core.session.*;
import edu.courseassist.editor.f2.*;
import edu.courseassist.storage.EditRepository;
import edu.courseassist.ui.f2.NativeEditPreviewPort.SnapshotDiff;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;

/** Real native two-sided viewer, dirty Document, IDE command/Undo and separate disk observations. */
final class F2ReviewApplyChecks {
    static void run(Path parent)throws Exception {
        Path root=parent.resolve("project-"+UUID.randomUUID()),src=Files.createDirectories(root.resolve("src"));
        Path path=src.resolve("X.java");String disk="class X { int n=1; }\r\n";Files.writeString(path,disk);
        var pm=ProjectManagerEx.getInstanceEx();var project=pm.openProject(root,OpenProjectTask.build().asNewProject().withForceOpenInNewFrame(true));
        require(project!=null,"F2 apply project opened");AtomicReference<SnapshotDiff> shown=new AtomicReference<>();
        try(var e=new SessionTestEnvironment(parent.resolve("storage-"+UUID.randomUUID()))) {
            edt(()->WriteAction.run(()->{
                var folder=LocalFileSystem.getInstance().refreshAndFindFileByNioFile(src);
                var module=ModuleManager.getInstance(project).newModule(root.resolve("main.iml").toString(),"JAVA_MODULE");
                ModuleRootModificationUtil.updateModel(module,model->model.addContentEntry(folder));
            }));
            com.intellij.openapi.project.DumbService.getInstance(project).waitForSmartMode();
            var file=LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
            var document=ReadAction.compute(()->FileDocumentManager.getInstance().getDocument(file));require(document!=null,"Real Java Document");
            edt(()->WriteAction.run(()->{document.setText("// unsaved input\n"+document.getText());PsiDocumentManager.getInstance(project).commitDocument(document);}));
            String before=ReadAction.compute(document::getText);long stamp=document.getModificationStamp();
            AtomicReference<FileEditor> editor=new AtomicReference<>();
            edt(()->{var opened=FileEditorManager.getInstance(project).openFile(file,true);editor.set(Arrays.stream(opened).filter(x->x instanceof TextEditor).findFirst().orElseThrow());});
            e.configureAndEnable();var binding=e.open("i","f2-apply-project",true);String session=e.create("i");e.text("i",session,"change n to 2");
            var submission=e.repository.submit(binding.projectId(),e.submitEvent("i",session),e.runtime.snapshot().epoch());
            var identity=submission.operation().orElseThrow().identity();var input=e.repository.react().load(identity).orElseThrow().input();
            e.repository.react().save(new Snapshot(identity,input,1,1,AttemptStatus.RUNNING,true,"","",List.of(new Branch("branch",BranchStatus.ACTIVE,RetrievalStatus.NO_INDEX,"",List.of(),List.of())),List.of(),""),false);
            var request=new EditRequest("request",EditRepository.origin(identity),"instruction",0,"change n",Optional.empty());
            var targetPort=new JavaEditTargetPort(project,"i",List.of(),()->true);
            var snapshot=targetPort.resolveAndCapture(request,new TargetQuery("src/X.java","","",List.of())).toCompletableFuture().get(30,TimeUnit.SECONDS).snapshot().orElseThrow();
            require(snapshot.before().equals(before)&&snapshot.documentStamp()==stamp,"Diff before includes same-version unsaved changes");
            var proposal=new EditProposal(1,"proposal",0,snapshot,"branch","tool",before.replace("n=1","n=2"));e.repository.edits().stage(proposal);
            var trace=new ToolTrace(new Call("tool","proposeCodeEdit","{}"),ToolStatus.SUCCEEDED,"candidate",List.of());
            var step=new Step("final",2,StepStatus.FINISHED,"candidate ready","",List.of(trace),"stop",Optional.empty(),List.of());
            e.repository.react().save(new Snapshot(identity,input,2,2,AttemptStatus.SUCCEEDED,true,"branch","final",List.of(new Branch("branch",BranchStatus.COMPLETED,RetrievalStatus.NO_INDEX,"",List.of(step),List.of())),List.of(),""),false);
            var apply=new JavaEditApplyPort(project,"i");
            // HeadlessDialog.show() immediately closes itself; exercise the production native panel here.
            // Visible desktop window latency remains a separate human check.
            EditPreviewPort preview=p->{var future=new CompletableFuture<EditPreviewPort.Display>();
                ApplicationManager.getApplication().invokeLater(()->{try {var diff=new SnapshotDiff(project,p);shown.set(diff);diff.ready().whenComplete((ignored,error)->{if(error!=null)future.completeExceptionally(error);else future.complete(diff);});}catch(Throwable error){future.completeExceptionally(error);}});return future;};
            e.sessions.attachEditHost("i",new SessionCoordinator.EditHost(preview,apply)).toCompletableFuture().get(15,TimeUnit.SECONDS);e.sessions.refreshAll().toCompletableFuture().get();
            e.text("i",session,"keep this draft exactly");var draft=e.view("i",session).draft();
            long previewStarted=System.nanoTime();
            e.dispatch(new OpenEditPreview(e.meta("i",session),proposal.ref()));
            await(()->shown.get()!=null&&shown.get().ready().toCompletableFuture().isDone());
            long previewMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-previewStarted);
            System.out.println("F2 first native Diff panel ready (headless): "+previewMillis+" ms");
            require(previewMillis<15000,"First native Diff panel must not take tens of seconds");
            await(()->button(shown.get().component(),"approveEditProposal").isEnabled());
            edt(()->{
                var diff=shown.get();require(diff.nativeViewer()!=null,"IDEA SimpleDiffViewer really created with two editors");
                require(diff.before().getDocument()!=document&&diff.after().getDocument()!=document,"Live Document absent from Diff");
                require(diff.nativeViewer().getEditor1().isViewer()&&diff.nativeViewer().getEditor2().isViewer(),"Both native panes read-only");
                require(diff.before().getDocument().getText().equals(snapshot.before())&&diff.after().getDocument().getText().equals(proposal.after()),"Displayed exact approval hashes");
                require(document.getText().equals(before)&&document.getModificationStamp()==stamp,"Preview leaves live Document unchanged");
            });
            require(Files.readString(path).equals(disk),"Preview leaves disk bytes/newlines unchanged");
            edt(()->{button(shown.get().component(),"approveEditProposal").doClick();button(shown.get().component(),"rejectEditProposal").doClick();});
            await(()->{e.sessions.refreshAll().toCompletableFuture().get(10,TimeUnit.SECONDS);return e.view("i",session).activeOperation().isEmpty()
                    &&e.repository.edits().views(binding.projectId(),"i",session).getFirst().receipt().isPresent();});
            var receipt=e.repository.edits().views(binding.projectId(),"i",session).getFirst().receipt().orElseThrow();
            require(receipt.state()==ApplyState.APPLIED_TO_DOCUMENT&&receipt.disk()==DiskState.UNSAVED,"Observed Document write separate from disk");
            require(ReadAction.compute(document::getText).equals(proposal.after()),"Applied exact displayed after");
            var lateStop=e.dispatch(new StopOperation(e.meta("i",session),receipt.applyId()));
            require(lateStop.rejection().filter(r->r==Rejection.OPERATION_TERMINATED).isPresent()
                    &&ReadAction.compute(document::getText).equals(proposal.after()),"Stop after real commit cannot erase the applied fact");
            require(Files.readString(path).equals(disk),"Apply does not force save");
            require(e.view("i",session).draft().equals(draft)&&e.requests.isEmpty()&&e.queries.isEmpty(),"Approval consumes no draft and starts no model, ReAct or embeddings");
            edt(()->{var undo=UndoManager.getInstance(project);require(undo.isUndoAvailable(editor.get()),"Named IDE Undo available");undo.undo(editor.get());});
            require(ReadAction.compute(document::getText).equals(before),"One Undo restores exact dirty before");
            e.dispatch(new InspectEditApplication(e.meta("i",session),proposal.ref()));
            require(e.repository.edits().views(binding.projectId(),"i",session).getFirst().receipt().orElseThrow().documentState()==DocumentState.UNDONE,"Undo observation remains distinct from application history");
            var appliedRequest=e.repository.edits().applications(binding.projectId(),"i",session).getFirst();
            var duplicate=apply.apply(appliedRequest,write->{throw new IllegalStateException("Duplicate must never request another write");}).toCompletableFuture().get(15,TimeUnit.SECONDS);
            require(duplicate.documentState()==DocumentState.UNDONE&&ReadAction.compute(document::getText).equals(before),"Undo then same applyId never rewrites source");
            edt(()->UndoManager.getInstance(project).redo(editor.get()));
            edt(()->FileDocumentManager.getInstance().saveDocument(document));
            e.dispatch(new InspectEditApplication(e.meta("i",session),proposal.ref()));
            require(e.repository.edits().views(binding.projectId(),"i",session).getFirst().receipt().orElseThrow().disk()==DiskState.SAVED,"Explicit normal IDE save observed separately");
            require(Files.readString(path).contains("n=2")&&Files.readString(path).contains("\r\n"),"Normal IDE save keeps original disk line separators");
            var reopenedAdapter=new JavaEditApplyPort(project,"new-instance");
            var checked=reopenedAdapter.inspect(appliedRequest,Optional.of(receipt)).toCompletableFuture().get(15,TimeUnit.SECONDS).orElseThrow();
            require(checked.state()==ApplyState.APPLIED_TO_DOCUMENT&&checked.disk()==DiskState.SAVED,"New host instance can observe durable success without granting old write permission");
            var noWrite=apply.apply(new ApplyRequest("cancelled",request.origin(),proposal),write->false).toCompletableFuture().get(15,TimeUnit.SECONDS);
            require(noWrite.state()==ApplyState.FAILED_NOT_APPLIED&&ReadAction.compute(document::getText).equals(proposal.after()),"Revoked permit never mutates real Document");
            edt(()->document.setReadOnly(true));
            require(rejected(apply,new ApplyRequest("readonly",request.origin(),proposal),"Document unavailable/read-only"),"Read-only Document rejects specifically for writability before drift checks");
            edt(()->document.setReadOnly(false));
            // A later changed Document and read-only/renamed target may never receive the old proposal.
            edt(()->WriteAction.run(()->document.setText(before+"// later edit\n")));
            require(rejected(apply,new ApplyRequest("drift",request.origin(),proposal)),"Changed stamp/full before rejects old approval");
            edt(()->WriteAction.run(()->{try{file.rename(F2ReviewApplyChecks.class,"Renamed.java");}catch(Exception ex){throw new IllegalStateException(ex);}}));
            require(rejected(apply,new ApplyRequest("rename",request.origin(),proposal)),"Rename never retargets old file URL");
            require(e.requests.isEmpty()&&e.queries.isEmpty(),"All local review/apply/Undo/save actions retain zero network starts");
        }finally{
            if(shown.get()!=null)edt(shown.get()::close);
            edt(()->{var manager=FileEditorManager.getInstance(project);for(var open:manager.getOpenFiles())manager.closeFile(open);});
            edt(()->pm.forceCloseProject(project));
        }
    }
    private static boolean rejected(JavaEditApplyPort port,ApplyRequest request)throws Exception {
        return rejected(port,request,null);
    }
    private static boolean rejected(JavaEditApplyPort port,ApplyRequest request,String reason)throws Exception {
        try{port.apply(request,write->{write.run();return true;}).toCompletableFuture().get(15,TimeUnit.SECONDS);return false;}
        catch(ExecutionException ex){var cause=edu.courseassist.core.react.ReactEngine.unwrap(ex);return cause instanceof EditApplyPort.NotApplied
                &&(reason==null||cause.getCause()!=null&&cause.getCause().getMessage().contains(reason));}
    }
    private static JButton button(java.awt.Container root,String name){for(var child:root.getComponents()){if(child instanceof JButton b&&name.equals(b.getName()))return b;if(child instanceof java.awt.Container c){var found=button(c,name);if(found!=null)return found;}}return null;}
    private static void edt(Runnable action){ApplicationManager.getApplication().invokeAndWait(action);}
    @FunctionalInterface private interface Check {boolean test()throws Exception;}
    private static void await(Check check)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(!check.test()){if(System.nanoTime()>end)throw new IllegalStateException("F2 local observation timeout");Thread.sleep(25);}}
}
