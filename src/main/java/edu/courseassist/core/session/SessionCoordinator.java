package edu.courseassist.core.session;

import edu.courseassist.contract.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CaptureContracts.*;
import edu.courseassist.contract.CommonContracts.*;
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
        final Operation operation; final AtomicBoolean allowed=new AtomicBoolean(true); volatile ReactEngine.Job job;
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
                    repository.recover(existing.projectId(),existing.instance());projects.remove(old.instance,old);
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
        runs.values().stream().filter(r->r.operation.identity().projectInstanceId().equals(instance)).forEach(Run::cancel);
        return command(()->{
            settle(p.binding.projectId(),Optional.of(instance));
            if(repository.states(p.binding.projectId(),instance).stream().anyMatch(s->blocked.contains(s.id()))) {
                p.observers.clear();throw new Rejected(Rejection.UNSAVED_STATE);
            }
            repository.recover(p.binding.projectId(),Optional.of(instance));
            p.observers.clear();projects.remove(instance,p);return null;
        });
    }
    /** Revoke synchronously, then coordinate local snapshots; never wait for remote acknowledgement. */
    public CompletionStage<Void> stopAll() {
        runs.values().forEach(Run::cancel);
        return command(()->{for(ProjectState p:projects.values()){settle(p.binding.projectId(),Optional.empty());refresh(p);}return null;});
    }
    private void settle(String project,Optional<String> instance) {
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
        // G1 has no Diff or apply adapter. Never reserve/consume/launch generation here.
        if(event instanceof OpenEditPreview||event instanceof ApproveEditProposal) {
            repository.history(project,instance,target);
            throw new Rejected(Rejection.NOT_SUPPORTED);
        }
        if(event instanceof RejectEditProposal e)return repository.edits().reject(project,meta,e.preview());
        if(event instanceof RecoverSession)restore(p,target);
        else if(event instanceof CreateSession e)repository.create(project,instance,SessionData.id(),e.title(),SessionData.emptyDraft());
        else if(event instanceof DeleteSession) {
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
            failures.remove(target);blocked.remove(target);unsavedDrafts.remove(target);reservations.entrySet().removeIf(e->e.getValue().ticket.targetSessionId().equals(target));
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
            try{submission=repository.submit(project,event,epoch,()->!blocked.contains(target));}catch(RuntimeException error){if(lease!=null)lease.close();throw error;}
            if(!submission.fresh()){if(lease!=null)lease.close();return submission.result();}
            RuntimeResources configured;
            try{configured=resources.get();}catch(RuntimeException error){if(lease!=null)lease.close();markBlocked(submission.operation().orElseThrow().identity(),error);throw error;}
            Run run=new Run(submission.operation().orElseThrow());runs.put(run.operation.identity().operationId(),run);
            try{workers.execute(()->start(run,configured,lease));}
            catch(RuntimeException error){if(lease!=null)lease.close();markBlocked(run.operation.identity(),error);throw error;}
            return submission.result();
        } else if(event instanceof StopOperation e) {
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
                    failure->failures.put(i.sessionId(),failure));
            if(run.operation.kind()==OperationKind.COMPACT) {
                planner.manual(i,history,()->allowed(run));repository.finishCompact(i);complete(run,null);return;
            }
            Input input=history.stream().flatMap(t->t.attempts().stream()).filter(a->a.identity().equals(i)).findFirst().orElseThrow().input();
            CourseTools tools=new CourseTools(Objects.requireNonNull(lease),configured.embeddings(),configured.search());
            if(!allowed(run))throw new CancellationException("Operation revoked before start");
            boolean originalSubmission=history.stream().anyMatch(turn->!turn.attempts().isEmpty()&&turn.attempts().getFirst().identity().equals(i));
            Optional<ToolBoundary.EditTools> edits=originalSubmission?project(i.projectInstanceId()).edits.map(factory->new edu.courseassist.core.edit.CodeEditTools(i,input,factory.bind(i,input,()->allowed(run)),repository.edits(),configured.limits().outputTokens())):Optional.empty();
            run.job=new ReactEngine(configured.model(),repository.react(),workers,configured.budget(),edits).start(i,input,history,tools,
                    configured.limits(),configured.policy(),()->allowed(run),snapshot->{
                        ProjectState p=projects.get(i.projectInstanceId());
                        if(p!=null&&p.open.get()&&runs.get(i.operationId())==run) {
                            overlays.put(i.attemptId(),snapshot); command(()->{if(runs.get(i.operationId())==run&&p.open.get())refresh(p);return null;});
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
                Throwable cause=ReactEngine.unwrap(error);if(!ReactEngine.isCancellation(cause))failures.put(i.sessionId(),cause);
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
    public Optional<Throwable> failure(String session){return Optional.ofNullable(failures.get(session));}
    public boolean hasUnsavedState(){return !blocked.isEmpty();}
    public CompletionStage<Void> recoverSession(String instance,String session){return command(()->{
        ProjectState p=project(instance);
        restore(p,session);refresh(p);return null;
    });}
    private void restore(ProjectState p,String session) {
        String instance=p.instance;
        if(runs.values().stream().anyMatch(r->r.operation.identity().sessionId().equals(session)))throw new Rejected(Rejection.BUSY);
        Optional<Snapshot> snapshot=overlays.values().stream().filter(s->s.identity().sessionId().equals(session)).findFirst().map(SessionCoordinator::interrupted);
        repository.recoverSession(p.binding.projectId(),instance,session,snapshot);
        DraftView draft=unsavedDrafts.get(session);
        if(draft!=null)repository.edit(p.binding.projectId(),instance,session,s->{
            SessionRepository.requireDraft(s.draft(),draft.draftId(),draft.revision());
            return s.change(new DraftView(draft.draftId(),draft.revision()+1,draft.text(),draft.captures(),SaveState.SAVED,draft.captureOffsets(),draft.captureOrder()),s.turns(),s.operation());});
        blocked.remove(session);unsavedDrafts.remove(session);failures.remove(session);overlays.entrySet().removeIf(e->e.getValue().identity().sessionId().equals(session));
    }
    /** Local command barrier for lifecycle integration and deterministic state observation. */
    public CompletionStage<Void> refreshAll(){return command(()->{for(ProjectState p:projects.values())refresh(p);return null;});}
    private void refresh(ProjectState p) {
        if(!p.open.get())return;
        Binding b=repository.binding(p.binding.projectId());p.binding=b;var stored=repository.states(b.projectId(),p.instance);
        List<SessionViewState> sessions=new ArrayList<>();
        for(State s:stored) {
            if(s.lifecycle()!=Lifecycle.LIVE){sessions.add(new SessionViewState(s.id(),s.title(),s.revision(),s.lifecycle(),s.draft(),List.of(),Optional.empty(),true,Set.of(SessionAction.DELETE),s.notices()));continue;}
            List<HistoryTurn> history=repository.history(s.projectId(),p.instance,s.id());List<TurnView> turns=new ArrayList<>();
            for(int n=0;n<s.turns().size();n++) {
                Turn turn=s.turns().get(n);List<MessagePart> parts=edu.courseassist.contract.ChatContracts.visibleParts(turn.input().question(),turn.input().code(),turn.input().captureOffsets(),turn.input().captureOrder());
                var attempts=history.get(n).attempts().stream().map(a->{Snapshot overlay=overlays.get(a.identity().attemptId());return ReactViews.attempt(overlay!=null&&overlay.revision()>=a.revision()?overlay:a);}).toList();
                turns.add(new TurnView(turn.id(),turn.requestId(),new MessageView(turn.id()+":message",Role.USER,parts,turn.createdAt()),attempts));
            }
            boolean failed=s.blocked()||blocked.contains(s.id());Set<SessionAction> actions=EnumSet.of(SessionAction.CLOSE_TAB,SessionAction.DELETE);
            if(!failed){actions.add(SessionAction.EDIT_DRAFT);actions.add(SessionAction.REMOVE_CAPTURE);
                if(s.operation().isPresent())actions.add(SessionAction.STOP);else if(runtime.snapshot().state()==edu.courseassist.contract.ApplicationContracts.WorkState.ON){actions.add(SessionAction.SUBMIT);actions.add(SessionAction.RETRY);actions.add(SessionAction.COMPACT);}}
            else actions.add(SessionAction.RECOVER);
            List<Notice> notices=new ArrayList<>(s.notices());if(failures.containsKey(s.id())&&notices.isEmpty())notices.add(SessionData.notice("TASK_FAILED","操作失败，请查看任务状态后重试",s.id(),null));
            sessions.add(new SessionViewState(s.id(),s.title(),s.revision(),s.lifecycle(),unsavedDrafts.getOrDefault(s.id(),s.draft()),turns,s.operation().map(o->new ActiveOperation(o.identity().operationId(),o.kind(),
                    o.kind()==OperationKind.GENERATE?Optional.of(o.identity().attemptId()):Optional.empty())),failed,actions,notices));
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
                Set.of(SessionAction.CLOSE_TAB,SessionAction.DELETE,SessionAction.RECOVER),List.of(SessionData.notice("UNSAVED_STATE","保存失败，请恢复后再发送",session,null))):s).toList();
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
    @Override public void close(){closed=true;projects.values().forEach(p->p.open.set(false));reservations.values().forEach(r->r.valid.set(false));runs.values().forEach(Run::cancel);commands.shutdown();}
}
