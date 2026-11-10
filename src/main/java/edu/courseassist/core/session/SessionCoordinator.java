package edu.courseassist.core.session;

import edu.courseassist.contract.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CaptureContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.core.RuntimeGate;
import edu.courseassist.core.corpus.CourseLibrary;
import edu.courseassist.core.react.*;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.core.session.SessionData.*;
import edu.courseassist.storage.SessionRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Commands have one short local order; model/summary waits use separate workers. Views own only
 * subscriptions. Route reservations are memory-only and checked again inside first INSERT. */
public final class SessionCoordinator implements SessionController, SelectionSink, AutoCloseable {
    @FunctionalInterface public interface EditTargets { EditTargetPort bind(Identity identity,Input input,BooleanSupplier permitted); }
    public record EditHost(EditPreviewPort preview,EditApplyPort apply) {}
    private final Map<String,EditHost> editHosts=new ConcurrentHashMap<>();
    private final Map<String,ViewSubscription> editObservations=new ConcurrentHashMap<>();
    private record ObservationFailure(String session,Throwable cause,boolean blocks) {}
    private final Map<String,Throwable> summaryFailures=new ConcurrentHashMap<>(),generationFailures=new ConcurrentHashMap<>();
    private final Map<String,ObservationFailure> observationFailures=new ConcurrentHashMap<>();
    private boolean observationFailed(String session){return observationFailures.values().stream().anyMatch(f->f.session().equals(session)&&f.blocks());}
    private void detachEditHost(String instance) {
        var subscription=editObservations.remove(instance);if(subscription!=null)subscription.close();
        editHosts.remove(instance);
    }
    private void observeEditChanges(String instance,EditHost host) {
        if(closed)return;
        command(()->{
            var p=projects.get(instance);
            if(closed||p==null||!p.open.get()||editHosts.get(instance)!=host)return null;
            boolean changed=false;
            for(var session:repository.states(p.binding.projectId(),instance)) {
                if(session.lifecycle()!=Lifecycle.LIVE)continue;
                for(var request:repository.edits().applications(p.binding.projectId(),instance,session.id())) {
                    if(applying.containsKey(request.applyId()))continue;
                    var known=repository.edits().views(p.binding.projectId(),instance,session.id()).stream()
                        .filter(v->v.proposal().equals(request.proposal().ref())).findFirst().orElseThrow().receipt();
                    if(known.isEmpty()||known.get().state()!=ApplyState.APPLIED_TO_DOCUMENT)continue;
                    try {
                        var observed=host.apply().inspect(request,known).toCompletableFuture().join();
                        if(closed||!p.open.get()||editHosts.get(instance)!=host)return null;
                        if(observed.isEmpty())throw new IllegalStateException("Applied target could not be observed");
                        if(!observed.get().applyId().equals(request.applyId())||!observed.get().proposal().equals(request.proposal().ref()))
                            throw new IllegalStateException("Observation does not match the requested application");
                        if(!observed.equals(known)) {
                            repository.edits().observed(p.binding.projectId(),instance,session.id(),observed.get());changed=true;
                        }
                        if(observationFailures.remove(request.applyId())!=null)changed=true;
                    }catch(RuntimeException failure) {
                        if(closed||!p.open.get())return null;
                        Throwable cause=ReactEngine.unwrap(failure);boolean blocks=false;
                        var unknown=new ApplyReceipt(request.applyId(),request.proposal().ref(),ApplyState.APPLIED_TO_DOCUMENT,
                            DiskState.UNKNOWN,known.get().documentStamp(),DocumentState.UNKNOWN);
                        try {if(!known.get().equals(unknown))repository.edits().observed(p.binding.projectId(),instance,session.id(),unknown);}
                        catch(RuntimeException saveFailure){cause.addSuppressed(ReactEngine.unwrap(saveFailure));blocks=true;}
                        observationFailures.put(request.applyId(),new ObservationFailure(session.id(),cause,blocks));
                        observerFailures.accept(cause);changed=true;
                    }
                }
            }
            if(changed)refresh(p);
            return null;
        }).whenComplete((v,failure)->{if(failure!=null&&!closed)observerFailures.accept(ReactEngine.unwrap(failure));});
    }
    private final Map<String,ApplyRun> applying=new ConcurrentHashMap<>();
    private final Map<String,ApplyReceipt> pendingReceipts=new ConcurrentHashMap<>();
    private final Map<String,ApplyRequest> pendingApplies=new ConcurrentHashMap<>();
    private record DisplayBinding(String instance,String session,ProposalRef proposal,EditPreviewPort.Display display) {}
    private final Map<String,DisplayBinding> editDisplays=new ConcurrentHashMap<>();
    private record PreviewOpening(String instance,String session,AtomicBoolean permitted) {}
    private final Map<String,PreviewOpening> openingPreviews=new ConcurrentHashMap<>();
    private void revokePreviews(Predicate<PreviewOpening> predicate){openingPreviews.values().stream().filter(predicate).forEach(o->o.permitted().set(false));}
    private static final class ApplyRun {
        final ApplyRequest request;final BooleanSupplier live;
        boolean permitted=true;
        CompletableFuture<ApplyReceipt> completion;
        ApplyRun(ApplyRequest request,BooleanSupplier live){this.request=request;this.live=live;}
        synchronized boolean commit(Runnable write){if(!permitted||!live.getAsBoolean())return false;write.run();return true;}
        synchronized void revoke(){permitted=false;}
    }
    public CompletionStage<Void> attachEditHost(String instance,EditHost host) {
        return command(()->{project(instance);if(editHosts.putIfAbsent(instance,host)!=null)throw new IllegalStateException("F2 host already attached");
            try {editObservations.put(instance,host.apply().observeChanges(()->observeEditChanges(instance,host)));}
            catch(RuntimeException failure){editHosts.remove(instance,host);throw failure;}
            observeEditChanges(instance,host);return null;});
    }
    private final SessionRepository repository;
    private final RuntimeGate runtime;
    private final CourseLibrary courses;
    private final Executor workers;
    private final Supplier<RuntimeResources> resources;
    private final ExecutorService commands=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("courseassist-commands").factory());
    private final Map<String,ProjectState> projects=new ConcurrentHashMap<>();
    private final Map<String,Run> runs=new ConcurrentHashMap<>();
    private final Map<String,Snapshot> overlays=new ConcurrentHashMap<>();
    private final Set<String> blocked=ConcurrentHashMap.newKeySet();
    private final Map<String,Reservation> reservations=new ConcurrentHashMap<>();
    private final Map<String,Throwable> failures=new ConcurrentHashMap<>();
    private final Map<String,Notice> editNotices=new ConcurrentHashMap<>();
    private final Map<String,DraftView> unsavedDrafts=new ConcurrentHashMap<>();
    private final Consumer<Throwable> observerFailures;
    private volatile boolean closed;
    private static final class ProjectState {
        volatile Binding binding; final String instance; final Supplier<CodeCapturePort> capture;
        Optional<EditTargets> edits=Optional.empty();
        final AtomicBoolean open=new AtomicBoolean(true);
        volatile ProjectChatViewState view;
        final List<Consumer<ProjectChatViewState>> observers=new CopyOnWriteArrayList<>();
        ProjectState(Binding binding,String instance,Supplier<CodeCapturePort> capture){this.binding=binding;this.instance=instance;this.capture=capture;}
    }
    private static final class Reservation {
        final SelectionStart start;final CaptureTicket ticket;final boolean create;final AtomicBoolean valid=new AtomicBoolean(true);
        final CompletableFuture<Void> completion=new CompletableFuture<>();
        Reservation(SelectionStart start,CaptureTicket ticket,boolean create){this.start=start;this.ticket=ticket;this.create=create;}
    }
    private static final class Run {
        final Operation operation; final AtomicBoolean allowed=new AtomicBoolean(true);
        final AtomicBoolean refreshQueued=new AtomicBoolean(); volatile ReactEngine.Job job;
        Run(Operation op){operation=op;}
        boolean cancel(){allowed.set(false);ReactEngine.Job j=job;return j==null||j.cancel();}
    }
    public SessionCoordinator(SessionRepository repository,RuntimeGate runtime,CourseLibrary courses,Executor workers,
                              Supplier<RuntimeResources> resources,Consumer<Throwable> observerFailures) {
        this.repository=repository;this.runtime=runtime;this.courses=courses;this.workers=workers;this.resources=resources;this.observerFailures=observerFailures;
    }
    public CompletionStage<Void> initialize() {
        return command(()->{repository.initialize();for(Binding b:repository.bindings())repository.recover(b.projectId(),b.instance());return null;});
    }
    public CompletionStage<Binding> open(Location location,String instance,Supplier<CodeCapturePort> capture,boolean asNew) {
        return open(location,instance,capture,asNew,Optional.empty());
    }
    public CompletionStage<Binding> open(Location location,String instance,Supplier<CodeCapturePort> capture,boolean asNew,Optional<EditTargets> edits) {
        return command(()->{if(projects.containsKey(instance))throw new IllegalStateException("Instance already open");
            for(Binding existing:repository.bindings())if(existing.instance().isPresent()&&existing.location().directoryKey().equals(location.directoryKey())) {
                ProjectState old=projects.get(existing.instance().get());
                if(old!=null&&!old.open.get()) {
                    for(State s:repository.states(existing.projectId(),old.instance))if(s.lifecycle()==Lifecycle.LIVE&&blocked.contains(s.id()))restore(old,s.id());
                    repository.recover(existing.projectId(),existing.instance());projects.remove(old.instance,old);detachEditHost(old.instance);
                }
            }
            Binding b=repository.open(location,instance,asNew);ProjectState p=new ProjectState(b,instance,capture);p.edits=edits;projects.put(instance,p);refresh(p);return b;});
    }
    public CompletionStage<Binding> associate(String instance,String source,long sourceRevision,long targetRevision) {
        return command(()->{ProjectState p=project(instance);Binding b=repository.associate(p.binding.projectId(),instance,source,sourceRevision,targetRevision);p.binding=b;refresh(p);return b;});
    }
    /** Identity chooser metadata only; actual association rechecks revisions and occupancy. */
    public CompletionStage<List<Binding>> availableBindings() {
        return command(() -> repository.bindings().stream().filter(b -> b.instance().isEmpty()).toList());
    }
    public CompletionStage<Void> closeProject(String instance) {
        ProjectState p=projects.get(instance);if(p==null)return CompletableFuture.completedFuture(null);
        p.open.set(false);revokeReservations(instance,Optional.empty());
        revokePreviews(o->o.instance().equals(instance));
        applying.values().stream().filter(r->r.request.operation().instanceId().equals(instance)).forEach(ApplyRun::revoke);
        runs.values().stream().filter(r->r.operation.identity().projectInstanceId().equals(instance)).forEach(Run::cancel);
        return command(()->{
            settle(p.binding.projectId(),Optional.of(instance));
            if(repository.states(p.binding.projectId(),instance).stream().anyMatch(s->blocked.contains(s.id()))) {
                p.observers.clear();throw new Rejected(Rejection.UNSAVED_STATE);
            }
            repository.recover(p.binding.projectId(),Optional.of(instance));
            p.observers.clear();projects.remove(instance,p);detachEditHost(instance);return null;
        });
    }
    /** Revoke synchronously, then coordinate local snapshots; never wait for remote acknowledgement. */
    public CompletionStage<Void> stopAll() {
        runs.values().forEach(Run::cancel);
        revokePreviews(o->true);
        applying.values().forEach(ApplyRun::revoke);
        return command(()->{for(ProjectState p:projects.values()){settle(p.binding.projectId(),Optional.empty());refresh(p);}return null;});
    }
    private void settle(String project,Optional<String> instance) {
        for(var run:List.copyOf(applying.values()))if(run.request.operation().projectId().equals(project)
                &&instance.map(i->i.equals(run.request.operation().instanceId())).orElse(true)){run.revoke();finishApply(run);}
        editDisplays.values().stream().filter(d->instance.map(d.instance()::equals).orElse(true)).forEach(d->d.display().close());
        repository.settleEdits(project,instance);
        for(Run run:List.copyOf(runs.values())) {
            Identity i=run.operation.identity();if(!i.projectId().equals(project)||instance.isPresent()&&!instance.get().equals(i.projectInstanceId()))continue;
            run.cancel();
            try {
                repository.cancelOperation(i);
                if(run.job!=null) {
                    Snapshot snapshot=run.job.snapshot();
                    // The journal may already have committed a terminal result. interrupt is conditional.
                    if(!snapshot.saved()) {
                        Snapshot frozen=interrupted(snapshot);
                        try{repository.react().save(frozen,true);}catch(RuntimeException e){
                            Optional<Snapshot> persisted=repository.react().load(i);
                            if(persisted.isEmpty()||persisted.get().status()==AttemptStatus.RUNNING)throw e;
                        }
                    }
                }
                if(run.operation.kind()==OperationKind.GENERATE)repository.react().interrupt(i);
                else if(run.operation.kind()==OperationKind.COMPACT) repository.finishCompact(i);
                else throw new IllegalStateException("APPLY must use local edit coordination");
            }catch(RuntimeException failure){
                Throwable cause=ReactEngine.unwrap(failure);
                if(!ReactEngine.isCancellation(cause)&&!(cause instanceof Rejected r&&r.reason==Rejection.SESSION_DELETED))markBlocked(i,cause);
            }
            runs.remove(i.operationId(),run);
        }
    }
    private static Snapshot interrupted(Snapshot s) {
        List<Branch> branches=s.branches().stream().map(b->new Branch(b.id(),b.status(),b.retrieval(),b.explanation(),b.steps().stream()
                .map(step->new Step(step.id(),step.throughSeq(),step.status()==StepStatus.STREAMING||step.status()==StepStatus.TOOLS_RUNNING?StepStatus.INCOMPLETE:step.status(),
                        step.body(),step.thinking(),step.tools(),step.finishReason(),step.usage(),step.suppliedEvidence())).toList(),b.protocol())).toList();
        return new Snapshot(s.identity(),s.input(),s.revision()+1,s.throughSeq(),AttemptStatus.INTERRUPTED,true,"","",branches,List.of(),"lifecycle-interrupted");
    }
    @Override public CompletionStage<CommandResult> dispatch(ViewEvent event) {
        if(event instanceof DeleteSession)revokeReservations(event.meta().projectInstanceId(),event.meta().sessionId());
        if(event instanceof DeleteSession)revokePreviews(o->o.instance().equals(event.meta().projectInstanceId())&&event.meta().sessionId().filter(o.session()::equals).isPresent());
        if(event instanceof DeleteSession||event instanceof StopOperation)applying.values().stream()
                .filter(r->r.request.operation().instanceId().equals(event.meta().projectInstanceId())&&event.meta().sessionId().filter(r.request.operation().sessionId()::equals).isPresent()
                        &&(!(event instanceof StopOperation stop)||stop.operationId().equals(r.request.applyId()))).forEach(ApplyRun::revoke);
        return command(()->{
            ProjectState p;
            try {p=project(event.meta().projectInstanceId());}
            catch(Rejected rejected){return reject(event,rejected.reason,0);}
            try {CommandResult result=apply(p,event);refresh(p);return result;}
            catch(RuntimeException failure){Throwable cause=ReactEngine.unwrap(failure);
                if(cause instanceof Rejected rejected)return reject(event,rejected.reason,p.view.revision());
                if (event instanceof OpenCitation && cause instanceof CourseLibrary.MaterialDeleted) {
                    refresh(p); var view = p.view;
                    p.view = new ProjectChatViewState(view.projectId(), p.instance, view.revision()+1, view.sessions(), view.openSessionIds(), view.activeSessionId(),
                            List.of(SessionData.notice("MATERIAL_DELETED", "资料已删除", event.meta().sessionId().orElse(null), null)));
                    for (var observer : p.observers) deliver(observer, p.view);
                    return new CommandResult(event.meta().commandId(), Outcome.APPLIED, p.view.revision(), Optional.empty(), Optional.empty());
                }
                if(!ReactEngine.isCancellation(cause)&&event.meta().sessionId().isPresent()) {
                    String session=event.meta().sessionId().get();failures.put(session,cause);
                    if(event instanceof UpdateDraftText edit) {
                        var before=p.view.sessions().stream().filter(s->s.sessionId().equals(session)).findFirst();
                        before.ifPresent(s->unsavedDrafts.put(session,new DraftView(edit.draftId(),edit.expectedDraftRevision(),edit.text(),s.draft().captures(),SaveState.FAILED,placements(edit,s.draft()),edit.captureOrder().orElse(s.draft().captureOrder()))));
                    }
                    if(event instanceof UpdateDraftText||event instanceof SubmitDraft||event instanceof RemoveDraftCapture)blocked.add(session);
                    publishFailure(p,session);
                }
                throw ReactEngine.propagate(cause);}
        });
    }
    private CommandResult apply(ProjectState p,ViewEvent event) {
        String project=p.binding.projectId(),instance=p.instance;var meta=event.meta();String target=meta.sessionId().orElse("");
        if(blocked.contains(target)&&(event instanceof UpdateDraftText||event instanceof RemoveDraftCapture))
            throw new Rejected(Rejection.UNSAVED_STATE);
        if(event instanceof SubmitNewSession e) {
            if(repository.binding(project).active().isPresent())throw new Rejected(Rejection.STALE_REVISION);
            if(!runtime.isCurrent(runtime.snapshot().epoch()))throw new Rejected(Rejection.PLUGIN_OFF);
            String session=SessionData.id();var empty=SessionData.emptyDraft();
            var draft=new DraftView(empty.draftId(),0,e.text(),List.of(),SaveState.SAVED);
            repository.create(project,instance,session,"新会话",draft);
            // Bind once on the command thread; subsequent view changes cannot redirect this message.
            try {return apply(p,new SubmitDraft(new EventMeta(meta.commandId(),instance,Optional.of(session)),draft.draftId(),0));}
            finally {refresh(p);} // A failed submission must still expose the durable draft for recovery.
        }
        if(event instanceof RenameSession e) {
            repository.rename(project,instance,target,e.title());
            return new CommandResult(meta.commandId(),Outcome.APPLIED,p.view.revision()+1,Optional.empty(),Optional.empty());
        }
        if(event instanceof OpenEditPreview e) {
            var host=editHost(instance);
            var proposal=repository.edits().load(project,instance,target,e.proposal());
            var view=repository.edits().views(project,instance,target).stream().filter(v->v.proposal().equals(e.proposal())).findFirst().orElseThrow();
            String displayId=SessionData.id();var opening=new PreviewOpening(instance,target,new AtomicBoolean(true));openingPreviews.put(displayId,opening);
            CompletionStage<EditPreviewPort.Display> opened;
            try{opened=host.preview().open(proposal);}catch(RuntimeException failure){openingPreviews.remove(displayId);throw failure;}
            opened.whenComplete((display,error)->{
                if(closed){if(display!=null)display.close();return;}
                command(()->{
                openingPreviews.remove(displayId);
                boolean live;
                try {live=opening.permitted().get()&&p.open.get()&&repository.states(project,instance).stream().anyMatch(s->s.id().equals(target)&&s.lifecycle()==Lifecycle.LIVE);}
                catch(RuntimeException failure){if(display!=null)display.close();observerFailures.accept(ReactEngine.unwrap(failure));return null;}
                if(!live){if(display!=null)display.close();return null;}
                if(error!=null){if(!ReactEngine.isCancellation(ReactEngine.unwrap(error)))failures.put(target,ReactEngine.unwrap(error));refresh(p);return null;}
                editDisplays.put(displayId,new DisplayBinding(instance,target,e.proposal(),display));
                display.whenClosed(()->command(()->{editDisplays.remove(displayId);return null;}));
                try {
                    project(instance);
                    if(view.review()!=ReviewState.REVIEWABLE&&view.review()!=ReviewState.INVALIDATED)return null;
                    long stamp=host.apply().validate(proposal).toCompletableFuture().join();
                    var token=repository.edits().displayed(project,instance,target,e.proposal(),stamp);
                    display.enable(token,decision->dispatch(decision.approve()
                            ?new ApproveEditProposal(new EventMeta(decision.commandId(),instance,Optional.of(target)),decision.preview())
                            :new RejectEditProposal(new EventMeta(decision.commandId(),instance,Optional.of(target)),decision.preview())),
                            ()->command(()->{editDisplays.remove(displayId);repository.edits().dismiss(token);return null;}));
                }catch(RuntimeException failure){display.close();
                    if(sourceChanged(failure))editNotice(target,meta.commandId(),false);
                    else if(!ReactEngine.isCancellation(ReactEngine.unwrap(failure)))failures.put(target,ReactEngine.unwrap(failure));
                    if(p.open.get())refresh(p);}
                return null;
                }).whenComplete((ignored,failure)->{if(failure!=null){if(display!=null)display.close();observerFailures.accept(ReactEngine.unwrap(failure));}});
            });
            return new CommandResult(meta.commandId(),Outcome.APPLIED,p.view.revision(),Optional.empty(),Optional.empty());
        }
        if(event instanceof ApproveEditProposal e)return approve(p,e);
        if(event instanceof InspectEditApplication e) {
            var request=repository.edits().applications(project,instance,target).stream().filter(r->r.proposal().ref().equals(e.proposal())).findFirst().orElseThrow(()->new Rejected(Rejection.INVALID_TARGET));
            if(applying.containsKey(request.applyId()))throw new Rejected(Rejection.BUSY);
            var known=repository.edits().views(project,instance,target).stream().filter(v->v.proposal().equals(e.proposal())).findFirst().orElseThrow().receipt();
            if(pendingApplies.containsKey(request.applyId())||known.filter(r->r.state()==ApplyState.OUTCOME_UNKNOWN||r.state()==ApplyState.RECEIPT_PENDING).isPresent()) {
                restore(p,target);
                return new CommandResult(meta.commandId(),Outcome.APPLIED,p.view.revision()+1,Optional.empty(),Optional.of(request.applyId()));
            }
            var observation=editHost(instance).apply().inspect(request,known).toCompletableFuture().join();
            observation.ifPresent(r->{repository.edits().observed(project,instance,target,r);observationFailures.remove(request.applyId());});
            return new CommandResult(meta.commandId(),Outcome.APPLIED,p.view.revision()+1,Optional.empty(),Optional.of(request.applyId()));
        }
        if(event instanceof RejectEditProposal e) {
            var result=repository.edits().reject(project,meta,e.preview());
            closeProposalDisplays(e.preview().proposal());return result;
        }
        if(event instanceof RecoverSession)restore(p,target);
        else if(event instanceof CreateSession e)repository.create(project,instance,SessionData.id(),e.title(),SessionData.emptyDraft());
        else if(event instanceof DeleteSession) {
            for(var run:List.copyOf(applying.values()))if(run.request.operation().sessionId().equals(target)){run.revoke();finishApply(run);}
            Optional<State> found=repository.states(project,instance).stream().filter(s->s.id().equals(target)).findFirst();
            if(found.isPresent()) {
                repository.fenceDelete(project,instance,target);
                runs.values().stream().filter(r->r.operation.identity().sessionId().equals(target)).forEach(Run::cancel);
                // No private files are used: original code, protocol, summaries and diagnostics share H2.
                overlays.entrySet().removeIf(e->e.getValue().identity().sessionId().equals(target));
                runs.entrySet().removeIf(e->e.getValue().operation.identity().sessionId().equals(target));
                try{repository.delete(project,instance,target);}
                catch(RuntimeException failure){try{repository.deletionFailed(project,instance,target);refresh(p);}catch(RuntimeException mark){failure.addSuppressed(mark);}throw failure;}
            }
            observationFailures.values().removeIf(f->f.session().equals(target));failures.remove(target);editNotices.remove(target);summaryFailures.remove(target);generationFailures.remove(target);blocked.remove(target);unsavedDrafts.remove(target);reservations.entrySet().removeIf(e->e.getValue().ticket.targetSessionId().equals(target));
            for(var entry:List.copyOf(pendingApplies.entrySet()))if(entry.getValue().operation().sessionId().equals(target)) {
                pendingApplies.remove(entry.getKey());pendingReceipts.remove(entry.getKey());
            }
            editDisplays.values().stream().filter(d->d.session().equals(target)).forEach(d->d.display().close());
            var host=editHosts.get(instance);if(host!=null)host.apply().forgetSession(target);
        } else if(event instanceof UpdateDraftText e)repository.edit(project,instance,target,s->{
            SessionRepository.requireDraft(s.draft(),e.draftId(),e.expectedDraftRevision());
            return s.change(new DraftView(s.draft().draftId(),s.draft().revision()+1,e.text(),s.draft().captures(),SaveState.SAVED,placements(e,s.draft()),e.captureOrder().orElse(s.draft().captureOrder())),s.turns(),s.operation());
        });
        else if(event instanceof RemoveDraftCapture e) {
            repository.edit(project,instance,target,s->{SessionRepository.requireDraft(s.draft(),e.draftId(),e.expectedDraftRevision());
                if(s.draft().captures().stream().noneMatch(g->g.captureId().equals(e.captureId())))throw new Rejected(Rejection.INVALID_TARGET);
                return s.change(new DraftView(s.draft().draftId(),s.draft().revision()+1,s.draft().text(),s.draft().captures().stream()
                        .filter(g->!g.captureId().equals(e.captureId())).toList(),SaveState.SAVED,s.draft().captureOffsets(),s.draft().captureOrder()),s.turns(),s.operation());});
            Reservation r=reservations.remove(e.captureId());if(r!=null)r.valid.set(false);
        } else if(event instanceof SubmitDraft||event instanceof RetryAttempt||event instanceof ManualCompact) {
            long epoch=runtime.snapshot().epoch();
            // Pin before committing a fresh attempt; a duplicate only closes this unused local lease.
            var lease=event instanceof ManualCompact?null:courses.openAttempt();
            Submission submission;
            try{submission=repository.submit(project,event,epoch,()->!blocked.contains(target)&&!observationFailed(target));}catch(RuntimeException error){if(lease!=null)lease.close();throw error;}
            if(!submission.fresh()){if(lease!=null)lease.close();return submission.result();}
            editNotices.remove(target);summaryFailures.remove(target);Throwable previous=generationFailures.remove(target);if(previous!=null)failures.remove(target,previous);
            RuntimeResources configured;
            try{configured=resources.get();}catch(RuntimeException error){if(lease!=null)lease.close();markBlocked(submission.operation().orElseThrow().identity(),error);throw error;}
            Run run=new Run(submission.operation().orElseThrow());runs.put(run.operation.identity().operationId(),run);
            try{workers.execute(()->start(run,configured,lease));}
            catch(RuntimeException error){if(lease!=null)lease.close();markBlocked(run.operation.identity(),error);throw error;}
            return submission.result();
        } else if(event instanceof StopOperation e) {
            ApplyRun edit=applying.get(e.operationId());
            if(edit!=null&&edit.request.operation().sessionId().equals(target)&&edit.request.operation().instanceId().equals(instance)) {
                edit.revoke();finishApply(edit);return new CommandResult(meta.commandId(),Outcome.APPLIED,p.view.revision()+1,Optional.empty(),Optional.of(e.operationId()));
            }
            // Validate ownership before looking up a globally indexed running operation.
            State state=repository.states(project,instance).stream().filter(s->s.id().equals(target)).findFirst().orElseThrow(()->new Rejected(Rejection.SESSION_DELETED));
            Run run=runs.get(e.operationId());
            if(state.operation().isEmpty()||run==null||!state.operation().get().identity().equals(run.operation.identity())
                    ||!run.operation.identity().operationId().equals(e.operationId()))throw new Rejected(Rejection.OPERATION_TERMINATED);
            ReactEngine.Job job=run.job;
            if(job!=null&&!job.cancel())throw new Rejected(Rejection.OPERATION_TERMINATED);
            run.allowed.set(false);
            repository.cancelOperation(run.operation.identity());
        } else if(event instanceof OpenCitation e) {
            var history=repository.history(project,instance,target);
            boolean owned=history.stream().flatMap(t->t.attempts().stream()).anyMatch(a->a.citations().contains(e.citation()));
            if(!owned)throw new Rejected(Rejection.INVALID_TARGET);
            citationOpener.accept(e.citation());
        } else {
            Binding b=repository.binding(project);List<String> tabs=new ArrayList<>(b.tabs());Optional<String> active=b.active();
            if(event instanceof CloseSessionTab){if(!tabs.remove(target))throw new Rejected(Rejection.INVALID_TARGET);active=active.filter(x->!x.equals(target));}
            else if(event instanceof OpenSessionTab||event instanceof SelectSession){if(!tabs.contains(target))tabs.add(target);active=Optional.of(target);}
            else if(event instanceof ReorderTabs e){if(!new HashSet<>(tabs).equals(new HashSet<>(e.orderedSessionIds())))throw new Rejected(Rejection.INVALID_TARGET);tabs=new ArrayList<>(e.orderedSessionIds());}
            else throw new Rejected(Rejection.NOT_SUPPORTED);
            repository.tabs(project,instance,tabs,active);
        }
        return new CommandResult(meta.commandId(),Outcome.APPLIED,p.view.revision()+1,Optional.empty(),Optional.empty());
    }
    private EditHost editHost(String instance) {var host=editHosts.get(instance);if(host==null)throw new Rejected(Rejection.NOT_SUPPORTED);return host;}
    private static boolean sourceChanged(Throwable failure) {
        Throwable cause=ReactEngine.unwrap(failure);
        return cause instanceof EditApplyPort.SourceChanged
                ||cause instanceof EditApplyPort.NotApplied&&cause.getCause() instanceof EditApplyPort.SourceChanged;
    }
    private void editNotice(String session,String operation,boolean selection) {
        editNotices.put(session,new Notice("edit-change:"+operation,Severity.WARNING,
                selection?"EDIT_SELECTION_CHANGED":"EDIT_SOURCE_CHANGED",
                selection?"原选区或其所在文件已被更改，请移除旧代码条并重新选择后发送。":"文件已被更改，本次未应用修改。请重新发送请求生成候选。",
                Optional.of(session),Optional.of(operation)));
    }
    private void closeProposalDisplays(ProposalRef ref){editDisplays.values().stream().filter(d->d.proposal().equals(ref)).forEach(d->d.display().close());}
    private CommandResult approve(ProjectState p,ApproveEditProposal event) {
        String project=p.binding.projectId(),session=event.meta().sessionId().orElseThrow();var host=editHost(p.instance);
        var current=repository.edits().views(project,p.instance,session).stream().filter(v->v.proposal().equals(event.preview().proposal())).findFirst().orElseThrow(()->new Rejected(Rejection.INVALID_TARGET));
        // De-duplication precedes current Document validation, busy and epoch checks.
        if(current.receipt().isEmpty()) {
            repository.edits().validatePreview(project,event.meta(),event.preview());
            if(blocked.contains(session))throw new Rejected(Rejection.UNSAVED_STATE);
            var proposal=repository.edits().load(project,p.instance,session,event.preview().proposal());
            try {long stamp=host.apply().validate(proposal).toCompletableFuture().join();if(stamp!=event.preview().documentStamp())throw new EditApplyPort.SourceChanged();}
            catch(RuntimeException failure){repository.edits().invalidate(project,p.instance,session,proposal.ref());closeProposalDisplays(proposal.ref());
                if(sourceChanged(failure)){editNotice(session,event.meta().commandId(),false);refresh(p);throw new Rejected(Rejection.STALE_REVISION);}
                throw failure;}
        }
        var request=repository.edits().beginApply(project,event.meta(),event.preview(),runtime.snapshot().epoch());
        closeProposalDisplays(request.proposal().ref());
        var receipt=repository.edits().views(project,p.instance,session).stream().filter(v->v.proposal().equals(request.proposal().ref())).findFirst().orElseThrow().receipt().orElseThrow();
        if(receipt.state()==ApplyState.INTENT&&!applying.containsKey(request.applyId())) {
            ApplyRun run=new ApplyRun(request,()->p.open.get()&&runtime.isCurrent(request.operation().epoch())&&!closed);
            applying.put(request.applyId(),run);
            try {
                repository.edits().applying(request);
                run.completion=host.apply().apply(request,run::commit).toCompletableFuture();
            }catch(RuntimeException failure){run.completion=CompletableFuture.failedFuture(new EditApplyPort.NotApplied(failure));}
            run.completion.whenComplete((r,error)->command(()->{
                finishApply(run);
                observeEditChanges(p.instance,host);
                if(p.open.get())try{refresh(p);}catch(RuntimeException readFailure){markBlocked(edu.courseassist.storage.EditRepository.identity(request.operation()),ReactEngine.unwrap(readFailure));publishFailure(p,session);}
                return null;
            }));
        }
        return new CommandResult(event.meta().commandId(),Outcome.APPLIED,p.view.revision()+1,Optional.empty(),Optional.of(request.applyId()));
    }
    private void finishApply(ApplyRun run) {
        if(applying.get(run.request.applyId())!=run)return;
        ApplyReceipt result;
        try {result=run.completion.join();}
        catch(RuntimeException failure) {
            Throwable cause=ReactEngine.unwrap(failure);
            if(sourceChanged(cause))editNotice(run.request.operation().sessionId(),run.request.applyId(),false);
            else failures.put(run.request.operation().sessionId(),cause);
            result=new ApplyReceipt(run.request.applyId(),run.request.proposal().ref(),cause instanceof EditApplyPort.NotApplied?ApplyState.FAILED_NOT_APPLIED:ApplyState.OUTCOME_UNKNOWN,DiskState.UNKNOWN,Optional.empty());
        }
        try {repository.edits().outcome(run.request,result);}
        catch(RuntimeException saveFailure){
            pendingApplies.put(run.request.applyId(),run.request);pendingReceipts.put(run.request.applyId(),result);
            markBlocked(edu.courseassist.storage.EditRepository.identity(run.request.operation()),ReactEngine.unwrap(saveFailure));
        }finally{applying.remove(run.request.applyId(),run);}
    }
    private static Map<String,Integer> placements(UpdateDraftText event,DraftView draft) {
        if(event.captureOrder().isPresent()) {
            var order=event.captureOrder().get();var ids=draft.captures().stream().map(DraftCapture::captureId).collect(java.util.stream.Collectors.toSet());
            if(order.size()!=ids.size() || !new HashSet<>(order).equals(ids))throw new Rejected(Rejection.INVALID_INPUT);
        }
        Map<String,Integer> supplied=event.captureOffsets().orElse(draft.captureOffsets());
        Map<String,Integer> result=new HashMap<>();
        for(var capture:draft.captures()) if(supplied.containsKey(capture.captureId())) {
            int offset=supplied.get(capture.captureId());
            if(offset<0 || event.captureOffsets().isPresent() && offset>event.text().length())throw new Rejected(Rejection.INVALID_INPUT);
            result.put(capture.captureId(),Math.min(offset,event.text().length()));
        }
        if(event.captureOffsets().isPresent() && supplied.keySet().stream().anyMatch(id->draft.captures().stream().noneMatch(c->c.captureId().equals(id)))) throw new Rejected(Rejection.INVALID_INPUT);
        return Map.copyOf(result);
    }
    private Consumer<Citation> citationOpener=citation->{throw new IllegalStateException("Citation opener not configured");};
    public void citationOpener(Consumer<Citation> opener){citationOpener=Objects.requireNonNull(opener);}
    private void start(Run run,RuntimeResources configured,CourseLibrary.Lease lease) {
        Identity i=run.operation.identity();
        try {
            List<HistoryTurn> history=repository.history(i.projectId(),i.projectInstanceId(),i.sessionId());
            ContextPlanner planner=new ContextPlanner(repository,configured.model(),configured.budget(),configured.context(),configured.policy(),
                    failure->{if(allowed(run))summaryFailures.put(i.sessionId(),failure);});
            if(run.operation.kind()==OperationKind.COMPACT) {
                planner.manual(i,history,()->allowed(run));repository.finishCompact(i);complete(run,null);return;
            }
            Input input=history.stream().flatMap(t->t.attempts().stream()).filter(a->a.identity().equals(i)).findFirst().orElseThrow().input();
            CourseTools tools=new CourseTools(Objects.requireNonNull(lease),configured.embeddings(),configured.search());
            if(!allowed(run))throw new CancellationException("Operation revoked before start");
            boolean originalSubmission=history.stream().anyMatch(turn->!turn.attempts().isEmpty()&&turn.attempts().getFirst().identity().equals(i));
            Optional<ToolBoundary.EditTools> edits=originalSubmission?project(i.projectInstanceId()).edits.map(factory->{
                var target=factory.bind(i,input,()->allowed(run));
                EditTargetPort notifying=(request,query)->target.resolveAndCapture(request,query).thenCompose(result->{
                    if(result.status()==ResolutionStatus.STALE)return command(()->{
                        var p=projects.get(i.projectInstanceId());
                        if(p!=null&&p.open.get()&&allowed(run)) {
                            editNotice(i.sessionId(),i.operationId(),request.captureId().isPresent());refresh(p);
                        }
                        return result;
                    });
                    return CompletableFuture.completedFuture(result);
                });
                return new edu.courseassist.core.edit.CodeEditTools(i,input,notifying,repository.edits(),configured.limits().outputTokens(),history);
            }):Optional.empty();
            run.job=new ReactEngine(configured.model(),repository.react(),workers,configured.budget(),edits).start(i,input,history,tools,
                    configured.limits(),configured.policy(),()->allowed(run),snapshot->{
                        ProjectState p=projects.get(i.projectInstanceId());
                        if(p!=null&&p.open.get()&&runs.get(i.operationId())==run) {
                            overlays.put(i.attemptId(),snapshot);
                            // Stream frames update the latest overlay, not an unbounded queue of DB projections.
                            // Clear before refresh so a frame arriving during projection can enqueue one successor.
                            if(run.refreshQueued.compareAndSet(false,true))command(()->{
                                run.refreshQueued.set(false);
                                if(runs.get(i.operationId())==run&&p.open.get())refresh(p);
                                return null;
                            });
                        }
                    },(earlier,request)->planner.plan(i,earlier,request,()->allowed(run)));
            if(!run.allowed.get())run.job.cancel();
            run.job.completion().whenComplete((result,error)->complete(run,error));
        } catch(Throwable failure) {if(lease!=null)lease.close();complete(run,failure);}
    }
    private boolean allowed(Run run) {
        ProjectState p=projects.get(run.operation.identity().projectInstanceId());
        return run.allowed.get()&&runs.get(run.operation.identity().operationId())==run&&p!=null&&p.open.get()
                &&runtime.isCurrent(run.operation.identity().runtimeEpoch())&&!blocked.contains(run.operation.identity().sessionId());
    }
    private void complete(Run run,Throwable error) {
        command(()->{
            Identity i=run.operation.identity();if(runs.get(i.operationId())!=run)return null;
            if(error!=null) {
                Throwable cause=ReactEngine.unwrap(error);if(!ReactEngine.isCancellation(cause)){failures.put(i.sessionId(),cause);generationFailures.put(i.sessionId(),cause);}
                if(run.operation.kind()==OperationKind.COMPACT) {
                    try{repository.finishCompact(i);}catch(RuntimeException save){markBlocked(i,save);}
                } else {
                    try {
                        Optional<Snapshot> saved=repository.react().load(i);
                        if(saved.isEmpty()||saved.get().status()==AttemptStatus.RUNNING) {
                            if(ReactEngine.isCancellation(cause)&&saved.isPresent()) {
                                if(run.job!=null)repository.react().save(interrupted(run.job.snapshot()),true);
                                else repository.react().interrupt(i);
                            } else markBlocked(i,cause);
                        }
                    }catch(RuntimeException readFailure){cause.addSuppressed(readFailure);markBlocked(i,cause);}
                }
            }
            if(!blocked.contains(i.sessionId()))overlays.remove(i.attemptId());
            runs.remove(i.operationId(),run);ProjectState p=projects.get(i.projectInstanceId());if(p!=null&&p.open.get()) {
                if(blocked.contains(i.sessionId()))publishFailure(p,i.sessionId());
                else try{refresh(p);}catch(RuntimeException readFailure){markBlocked(i,readFailure);publishFailure(p,i.sessionId());}
            }return null;
        });
    }
    private void markBlocked(Identity i,Throwable failure) {
        blocked.add(i.sessionId());failures.put(i.sessionId(),failure);
        try{repository.block(i);}catch(RuntimeException persistenceFailure){failure.addSuppressed(persistenceFailure);}
    }
    @Override public CaptureTicket beginCapture(SelectionStart start) {
        ProjectState p=project(start.projectInstanceId());Reservation existing=reservations.get(start.captureId());
        if(existing!=null){if(!existing.start.equals(start))throw new IllegalArgumentException("Capture identity reused");return existing.ticket;}
        Reservation reservation;
        synchronized(p) {
            var view=p.view;Optional<String> active=view.activeSessionId();
            boolean create=active.isEmpty();String session=active.orElseGet(SessionData::id);
            String draft=create?SessionData.id():view.sessions().stream().filter(s->s.sessionId().equals(session)).findFirst().orElseThrow().draft().draftId();
            CaptureTicket ticket=new CaptureTicket(start.captureId(),p.instance,session,draft,start.source(),view.revision());
            reservation=new Reservation(start,ticket,create);reservations.put(start.captureId(),reservation);
            // Reserve the current route immediately so a second action before disk initialization uses it.
            if(create) {
                SessionViewState placeholder=new SessionViewState(session,"新会话",0,Lifecycle.LIVE,
                        new DraftView(draft,0,"",List.of(),SaveState.SAVING),List.of(),Optional.empty(),true,Set.of(SessionAction.DELETE),List.of());
                var sessions=new ArrayList<>(view.sessions());sessions.add(placeholder);var tabs=new ArrayList<>(view.openSessionIds());tabs.add(session);
                p.view=new ProjectChatViewState(view.projectId(),p.instance,view.revision()+1,sessions,tabs,Optional.of(session),view.notices());
            }
        }
        command(()->{
            try {
                if(!reservation.valid.get()||!p.open.get())throw new CancellationException("Capture reservation revoked");
                var ticket=reservation.ticket;
                if(reservation.create)repository.create(p.binding.projectId(),p.instance,ticket.targetSessionId(),"新会话",
                        new DraftView(ticket.draftId(),0,"",List.of(),SaveState.SAVED),()->reservation.valid.get()&&p.open.get());
                repository.edit(p.binding.projectId(),p.instance,ticket.targetSessionId(),s->{
                    if(!reservation.valid.get()||!s.draft().draftId().equals(ticket.draftId()))throw new CancellationException("Capture revoked");
                    List<DraftCapture> groups=new ArrayList<>(s.draft().captures());groups.add(new DraftCapture(start.captureId(),CaptureState.PENDING,start.source(),Optional.empty(),Optional.empty()));
                    return s.change(new DraftView(ticket.draftId(),s.draft().revision()+1,s.draft().text(),groups,SaveState.SAVED,s.draft().captureOffsets(),s.draft().captureOrder()),s.turns(),s.operation());});
                refresh(p);
                // The adapter runs only after the durable group exists, outside the command writer.
                workers.execute(()->{
                    try{if(!reservation.valid.get()||!p.open.get())throw new CancellationException("Capture revoked");
                        p.capture.get().capture(start).whenComplete((group,error)->captureDone(p,reservation,group,error));}
                    catch(Throwable failure){captureDone(p,reservation,null,failure);}
                });
            }catch(Throwable failure){reservation.completion.completeExceptionally(failure);
                if(!ReactEngine.isCancellation(ReactEngine.unwrap(failure))){blocked.add(reservation.ticket.targetSessionId());failures.put(reservation.ticket.targetSessionId(),failure);publishFailure(p,reservation.ticket.targetSessionId());}
                throw ReactEngine.propagate(failure);}
            return null;
        });
        return reservation.ticket;
    }
    private void captureDone(ProjectState p,Reservation r,CapturedCodeGroup group,Throwable error) {
        command(()->{
            if(!r.valid.get()||!p.open.get()){r.completion.completeExceptionally(new CancellationException("Capture revoked"));return null;}
            try {
                if(error==null&&!group.start().equals(r.start))throw new IllegalArgumentException("Capture changed frozen selection");
                repository.edit(p.binding.projectId(),p.instance,r.ticket.targetSessionId(),s->{
                    if(!r.valid.get()||!s.draft().draftId().equals(r.ticket.draftId()))throw new CancellationException("Capture revoked");
                    var captures=new ArrayList<>(s.draft().captures());int index=-1;
                    for(int n=0;n<captures.size();n++)if(captures.get(n).captureId().equals(r.start.captureId()))index=n;
                    if(index<0)throw new CancellationException("Capture group removed");
                    captures.set(index,new DraftCapture(r.start.captureId(),error==null?CaptureState.READY:CaptureState.FAILED,r.start.source(),
                            Optional.ofNullable(group).filter(ignored->error==null),error==null?Optional.empty():Optional.of("捕获失败，请重新捕获")));
                    return s.change(new DraftView(s.draft().draftId(),s.draft().revision()+1,s.draft().text(),captures,SaveState.SAVED,s.draft().captureOffsets(),s.draft().captureOrder()),s.turns(),s.operation());});
                if(error!=null){failures.put(r.ticket.targetSessionId(),ReactEngine.unwrap(error));r.completion.completeExceptionally(error);}else r.completion.complete(null);
                refresh(p);
            }catch(Throwable failure){r.completion.completeExceptionally(failure);
                if(!ReactEngine.isCancellation(ReactEngine.unwrap(failure))){blocked.add(r.ticket.targetSessionId());failures.put(r.ticket.targetSessionId(),failure);publishFailure(p,r.ticket.targetSessionId());}
                throw ReactEngine.propagate(failure);}
            return null;
        });
    }
    public CompletionStage<Void> captureCompletion(String capture){Reservation r=reservations.get(capture);if(r==null)return CompletableFuture.failedFuture(new IllegalArgumentException("Capture absent"));return r.completion.copy();}
    private void revokeReservations(String instance,Optional<String> session){reservations.values().stream().filter(r->r.ticket.projectInstanceId().equals(instance)
            &&(session.isEmpty()||session.get().equals(r.ticket.targetSessionId()))).forEach(r->{r.valid.set(false);r.completion.completeExceptionally(new CancellationException("Capture revoked"));});}
    @Override public ViewSubscription observe(String instance,Consumer<ProjectChatViewState> listener) {
        ProjectState p=project(instance);p.observers.add(listener);AtomicBoolean subscribed=new AtomicBoolean(true);
        command(()->{if(subscribed.get())deliver(listener,p.view);return null;});
        return ()->{subscribed.set(false);p.observers.remove(listener);};
    }
    public ProjectChatViewState snapshot(String instance){return project(instance).view;}
    public Optional<Throwable> failure(String session){return Optional.ofNullable(failures.get(session)).or(()->Optional.ofNullable(summaryFailures.get(session)));}
    public boolean hasUnsavedState(){return !blocked.isEmpty();}
    public CompletionStage<Void> recoverSession(String instance,String session){return command(()->{
        ProjectState p=project(instance);
        restore(p,session);refresh(p);return null;
    });}
    private void restore(ProjectState p,String session) {
        String instance=p.instance;
        if(runs.values().stream().anyMatch(r->r.operation.identity().sessionId().equals(session)))throw new Rejected(Rejection.BUSY);
        if(applying.values().stream().anyMatch(r->r.request.operation().sessionId().equals(session)))throw new Rejected(Rejection.BUSY);
        Optional<Snapshot> snapshot=overlays.values().stream().filter(s->s.identity().sessionId().equals(session)).findFirst().map(SessionCoordinator::interrupted);
        EditHost host=editHosts.get(instance);
        if(host!=null)for(var request:repository.edits().applications(p.binding.projectId(),instance,session)) {
            ApplyReceipt receipt=pendingReceipts.get(request.applyId());
            if(receipt!=null) {repository.edits().reconcile(p.binding.projectId(),instance,request,receipt);pendingReceipts.remove(request.applyId());pendingApplies.remove(request.applyId());}
            var known=repository.edits().views(p.binding.projectId(),instance,session).stream().filter(v->v.proposal().equals(request.proposal().ref())).findFirst().orElseThrow().receipt();
            if(known.filter(r->r.state()==ApplyState.OUTCOME_UNKNOWN||r.state()==ApplyState.RECEIPT_PENDING).isPresent()) {
                var observed=host.apply().inspect(request,known).toCompletableFuture().join();
                if(observed.isPresent())repository.edits().observed(p.binding.projectId(),instance,session,observed.get());
            }
        }
        repository.recoverSession(p.binding.projectId(),instance,session,snapshot);
        DraftView draft=unsavedDrafts.get(session);
        if(draft!=null)repository.edit(p.binding.projectId(),instance,session,s->{
            SessionRepository.requireDraft(s.draft(),draft.draftId(),draft.revision());
            return s.change(new DraftView(draft.draftId(),draft.revision()+1,draft.text(),draft.captures(),SaveState.SAVED,draft.captureOffsets(),draft.captureOrder()),s.turns(),s.operation());});
        blocked.remove(session);unsavedDrafts.remove(session);failures.remove(session);summaryFailures.remove(session);generationFailures.remove(session);overlays.entrySet().removeIf(e->e.getValue().identity().sessionId().equals(session));
    }
    /** Local command barrier for lifecycle integration and deterministic state observation. */
    public CompletionStage<Void> refreshAll(){return command(()->{for(ProjectState p:projects.values())refresh(p);return null;});}
    private void refresh(ProjectState p) {
        if(!p.open.get())return;
        Binding b=repository.binding(p.binding.projectId());p.binding=b;var stored=repository.states(b.projectId(),p.instance);
        List<SessionViewState> sessions=new ArrayList<>();
        for(State s:stored) {
            if(s.lifecycle()!=Lifecycle.LIVE){sessions.add(new SessionViewState(s.id(),SessionData.displayTitle(s),s.revision(),s.lifecycle(),s.draft(),List.of(),Optional.empty(),true,Set.of(SessionAction.DELETE),s.notices()));continue;}
            List<HistoryTurn> history=repository.history(s.projectId(),p.instance,s.id());List<TurnView> turns=new ArrayList<>();
            for(int n=0;n<s.turns().size();n++) {
                Turn turn=s.turns().get(n);List<MessagePart> parts=edu.courseassist.contract.ChatContracts.visibleParts(turn.input().question(),turn.input().code(),turn.input().captureOffsets(),turn.input().captureOrder());
                var attempts=history.get(n).attempts().stream().map(a->{Snapshot overlay=overlays.get(a.identity().attemptId());return ReactViews.attempt(overlay!=null&&overlay.revision()>=a.revision()?overlay:a);}).toList();
                turns.add(new TurnView(turn.id(),turn.requestId(),new MessageView(turn.id()+":message",Role.USER,parts,turn.createdAt()),attempts));
            }
            boolean failed=s.blocked()||blocked.contains(s.id())||observationFailed(s.id());Set<SessionAction> actions=EnumSet.of(SessionAction.CLOSE_TAB,SessionAction.DELETE);
            if(!failed){actions.add(SessionAction.EDIT_DRAFT);actions.add(SessionAction.REMOVE_CAPTURE);
                if(s.operation().isPresent())actions.add(SessionAction.STOP);else if(runtime.snapshot().state()==edu.courseassist.contract.ApplicationContracts.WorkState.ON){actions.add(SessionAction.SUBMIT);
                    if(!history.isEmpty()&&!history.getLast().attempts().isEmpty()) {
                        var previous=history.getLast().attempts().getLast();
                        boolean terminal=switch(previous.status()){case STOPPED,INTERRUPTED,LIMITED,FAILED->true;default->false;};
                        boolean editAttempt=previous.branches().stream().flatMap(branch->branch.steps().stream()).flatMap(step->step.tools().stream())
                            .anyMatch(t->t.call().name().equals("resolveEditTarget")||t.call().name().equals("proposeCodeEdit"));
                        if(terminal&&!editAttempt)actions.add(SessionAction.RETRY);
                    }
                    actions.add(SessionAction.COMPACT);}}
            else actions.add(SessionAction.RECOVER);
            if(observationFailed(s.id())&&s.operation().isPresent())actions.add(SessionAction.STOP);
            List<Notice> notices=new ArrayList<>(s.notices());
            if(editNotices.containsKey(s.id()))notices.add(editNotices.get(s.id()));
            if(observationFailures.values().stream().anyMatch(f->f.session().equals(s.id())))notices.add(SessionData.notice("EDIT_OBSERVATION_FAILED",
                observationFailed(s.id())?"代码核验状态保存失败，暂不能发送；请重试状态恢复":"当前代码状态无法核验，已标记未知；可重试状态恢复",s.id(),null));
            if(summaryFailures.containsKey(s.id())&&!failures.containsKey(s.id())&&s.operation().isEmpty())notices.add(new Notice("summary-fallback:"+s.id(),Severity.WARNING,"CONTEXT_FALLBACK",
                "自动整理历史未完成，本轮已使用预算内的现有上下文",Optional.of(s.id()),Optional.empty()));
            if(failures.containsKey(s.id())&&notices.stream().noneMatch(n->n.severity()==Severity.ERROR))notices.add(SessionData.notice("TASK_FAILED","操作失败，请查看任务状态后重试",s.id(),null));
            sessions.add(new SessionViewState(s.id(),SessionData.displayTitle(s),s.revision(),s.lifecycle(),unsavedDrafts.getOrDefault(s.id(),s.draft()),turns,s.operation().map(o->new ActiveOperation(o.identity().operationId(),o.kind(),
                    o.kind()==OperationKind.GENERATE?Optional.of(o.identity().attemptId()):Optional.empty())),failed,actions,notices,repository.edits().views(s.projectId(),p.instance,s.id())));
        }
        long revision=p.view==null?0:p.view.revision()+1;
        p.view=new ProjectChatViewState(b.projectId(),p.instance,revision,sessions,b.tabs(),b.active(),List.of());
        for(var observer:p.observers)deliver(observer,p.view);
    }
    private void deliver(Consumer<ProjectChatViewState> listener,ProjectChatViewState view){try{listener.accept(view);}catch(RuntimeException failure){observerFailures.accept(failure);}}
    /** A database failure must itself be visible; projecting it cannot depend on another DB read. */
    private void publishFailure(ProjectState p,String session) {
        if(!p.open.get()||p.view==null)return;var old=p.view;
        var sessions=old.sessions().stream().map(s->s.sessionId().equals(session)?new SessionViewState(s.sessionId(),s.title(),s.revision()+1,s.lifecycle(),
                unsavedDrafts.getOrDefault(session,new DraftView(s.draft().draftId(),s.draft().revision(),s.draft().text(),s.draft().captures(),SaveState.FAILED,s.draft().captureOffsets(),s.draft().captureOrder())),s.turns(),s.activeOperation(),true,
                Set.of(SessionAction.CLOSE_TAB,SessionAction.DELETE,SessionAction.RECOVER),List.of(SessionData.notice("UNSAVED_STATE","保存失败，请恢复后再发送",session,null)),s.editProposals()):s).toList();
        p.view=new ProjectChatViewState(old.projectId(),p.instance,old.revision()+1,sessions,old.openSessionIds(),old.activeSessionId(),old.notices());
        for(var observer:p.observers)deliver(observer,p.view);
    }
    private ProjectState project(String instance){ProjectState p=projects.get(instance);if(closed||p==null||!p.open.get())throw new Rejected(Rejection.PROJECT_CLOSED);return p;}
    private static CommandResult reject(ViewEvent event,Rejection why,long revision){return new CommandResult(event.meta().commandId(),Outcome.REJECTED,revision,Optional.of(why),Optional.empty());}
    private <T> CompletableFuture<T> command(Callable<T> work) {
        CompletableFuture<T> future=new CompletableFuture<>();
        try{commands.execute(()->{try{future.complete(work.call());}catch(Throwable failure){future.completeExceptionally(ReactEngine.unwrap(failure));}});}
        catch(RuntimeException failure){future.completeExceptionally(failure);}return future;
    }
    @Override public void close(){closed=true;projects.values().forEach(p->p.open.set(false));reservations.values().forEach(r->r.valid.set(false));runs.values().forEach(Run::cancel);applying.values().forEach(ApplyRun::revoke);editDisplays.values().forEach(d->d.display().close());editObservations.values().forEach(ViewSubscription::close);editObservations.clear();commands.shutdown();}
}
