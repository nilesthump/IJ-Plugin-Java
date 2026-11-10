package edu.courseassist.editor.f2;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import edu.courseassist.contract.EditApplyPort;
import edu.courseassist.contract.EditContracts.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** One existing Java Document, exact immutable candidate, one named IDE Undo command. */
public final class JavaEditApplyPort implements EditApplyPort {
    private final Project project;
    private final String instance;
    private final Map<String,Observed> observed=new ConcurrentHashMap<>();
    private volatile Runnable changeSignal=()->{};
    private record Observed(ApplyRequest request,Document document,AtomicReference<DocumentState> state) {}
    public JavaEditApplyPort(Project project,String instance){this.project=project;this.instance=instance;}
    @Override public edu.courseassist.contract.ViewSubscription observeChanges(Runnable changed) {
        var lifetime=com.intellij.openapi.util.Disposer.newDisposable("Course Assistant edit observations");
        com.intellij.openapi.util.Disposer.register(project,lifetime);
        var queued=new java.util.concurrent.atomic.AtomicBoolean();
        var active=new java.util.concurrent.atomic.AtomicBoolean(true);
        Runnable signal=()->{
            if(active.get()&&queued.compareAndSet(false,true)) ApplicationManager.getApplication().invokeLater(()->{
                queued.set(false); if(active.get()&&!project.isDisposed())changed.run();
            });
        };
        changeSignal=signal;
        com.intellij.openapi.editor.EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
            new com.intellij.openapi.editor.event.DocumentListener() {
                @Override public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
                    if(observed.values().stream().anyMatch(v->v.document()==event.getDocument()))signal.run();
                }
            },lifetime);
        ApplicationManager.getApplication().getMessageBus().connect(lifetime).subscribe(
            com.intellij.AppTopics.FILE_DOCUMENT_SYNC,new com.intellij.openapi.fileEditor.FileDocumentManagerListener() {
                @Override public void beforeDocumentSaving(Document document) {
                    if(observed.values().stream().anyMatch(v->v.document()==document))signal.run();
                }
            });
        return ()->{active.set(false);changeSignal=()->{};com.intellij.openapi.util.Disposer.dispose(lifetime);};
    }
    @Override public CompletionStage<Long> validate(EditProposal proposal) {
        var result=new CompletableFuture<Long>();
        ApplicationManager.getApplication().invokeLater(()->{
            try {
                var source=proposal.snapshot().request().origin();
                var request=new ApplyRequest("validation-only",new Origin(source.projectId(),instance,source.sessionId(),source.turnId(),source.attemptId(),source.operationId(),source.epoch()),proposal);
                var document=target(request);var snapshot=proposal.snapshot();
                if(!document.getText().equals(snapshot.before()))throw new SourceChanged();
                result.complete(document.getModificationStamp());
            }catch(Exception failure){result.completeExceptionally(new NotApplied(failure));}
        });return result;
    }
    @Override public CompletionStage<ApplyReceipt> apply(ApplyRequest request,WritePermit permit) {
        var result=new CompletableFuture<ApplyReceipt>();
        ApplicationManager.getApplication().invokeLater(()->{
            if(observed.containsKey(request.applyId())) { inspect(request).whenComplete((r,e)->{if(e!=null)result.completeExceptionally(e);else result.complete(r.orElseThrow());});return; }
            boolean[] touched={false};
            try {
                WriteCommandAction.writeCommandAction(project).withName("Course Assistant: apply Java proposal")
                        .withGroupId(request.applyId()).run(()->permit.commit(()->{
                            Document document=target(request);
                            var snapshot=request.proposal().snapshot();
                            if(document.getModificationStamp()!=request.documentStamp()||!document.getText().equals(snapshot.before()))
                                throw new SourceChanged();
                            var state=new AtomicReference<>(DocumentState.APPLIED);
                            // Keep evidence even if the IDE write or subsequent receipt publication fails.
                            touched[0]=true;
                            document.setText(request.proposal().after());
                            observed.put(request.applyId(),new Observed(request,document,state));
                            UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(document) {
                                @Override public void undo(){state.set(DocumentState.UNDONE);changeSignal.run();}
                                @Override public void redo(){state.set(DocumentState.APPLIED);changeSignal.run();}
                            });
                        }));
                if(!touched[0])result.complete(failed(request));
                else result.complete(receipt(observed.get(request.applyId())));
            } catch(Exception failure) {
                // The coordinator records the original exception. An exception after touching the
                // Document cannot truthfully be reported as FAILED_NOT_APPLIED.
                if(touched[0])result.completeExceptionally(failure);
                else result.completeExceptionally(new NotApplied(failure));
            }
        });
        return result;
    }
    @Override public void forgetSession(String sessionId){observed.entrySet().removeIf(e->e.getValue().request().operation().sessionId().equals(sessionId));}
    private Document target(ApplyRequest request) {
        return target(request,true);
    }
    private Document target(ApplyRequest request,boolean writing) {
        var snapshot=request.proposal().snapshot();var target=snapshot.target();
        if(project.isDisposed()||writing&&!instance.equals(request.operation().instanceId()))
            throw new IllegalStateException("F2 original project closed");
        VirtualFile file=VirtualFileManager.getInstance().findFileByUrl(target.fileRef());
        VirtualFile base=project.getBasePath()==null?null:LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
        if(file==null||!file.isValid()||writing&&!file.isWritable()||!"java".equals(file.getExtension())||!(file instanceof VirtualFileWithId id)
                ||!target.fileIdentity().equals("vfs:"+id.getId())||base==null||base.getCanonicalFile()==null||file.getCanonicalFile()==null
                ||!VfsUtilCore.isAncestor(base.getCanonicalFile(),file.getCanonicalFile(),false)
                ||!target.relativePath().equals(VfsUtilCore.getRelativePath(file,base,'/')))
            throw new IllegalStateException("F2 file identity, scope or writability changed");
        Document document=FileDocumentManager.getInstance().getDocument(file);
        if(document==null||writing&&!document.isWritable())throw new IllegalStateException("F2 Document unavailable/read-only");
        return document;
    }
    private ApplyReceipt receipt(Observed value) {
        var request=value.request();var document=value.document();
        DocumentState state=value.state().get();
        if(state==DocumentState.APPLIED&&!document.getText().equals(request.proposal().after())
                ||state==DocumentState.UNDONE&&!document.getText().equals(request.proposal().snapshot().before()))state=DocumentState.CHANGED;
        return new ApplyReceipt(request.applyId(),request.proposal().ref(),ApplyState.APPLIED_TO_DOCUMENT,
                FileDocumentManager.getInstance().isDocumentUnsaved(document)?DiskState.UNSAVED:DiskState.SAVED,
                Optional.of(document.getModificationStamp()),state);
    }
    private static ApplyReceipt failed(ApplyRequest r){return new ApplyReceipt(r.applyId(),r.proposal().ref(),ApplyState.FAILED_NOT_APPLIED,DiskState.UNKNOWN,Optional.empty());}
    @Override public CompletionStage<Optional<ApplyReceipt>> inspect(ApplyRequest request) {
        return inspect(request,Optional.empty());
    }
    @Override public CompletionStage<Optional<ApplyReceipt>> inspect(ApplyRequest request,Optional<ApplyReceipt> durable) {
        var result=new CompletableFuture<Optional<ApplyReceipt>>();
        ApplicationManager.getApplication().invokeLater(()->{
            try {
                Observed value=observed.get(request.applyId());
                if(value!=null&&!value.request().equals(request))throw new IllegalArgumentException("Apply identity reused");
                if(value==null&&durable.filter(r->r.applyId().equals(request.applyId())&&r.proposal().equals(request.proposal().ref())&&r.state()==ApplyState.APPLIED_TO_DOCUMENT).isPresent()) {
                    // A durable receipt proves the historical write. Matching text alone never does.
                    Document document=target(request,false);
                    value=new Observed(request,document,new AtomicReference<>(document.getText().equals(request.proposal().after())?DocumentState.APPLIED:DocumentState.CHANGED));
                    observed.put(request.applyId(),value);
                }
                result.complete(value==null?Optional.empty():Optional.of(receipt(value)));
            }catch(Exception failure){result.completeExceptionally(failure);}
        });return result;
    }
}
