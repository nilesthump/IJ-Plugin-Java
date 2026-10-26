package edu.courseassist.storage;

import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CaptureContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.core.RuntimeGate;
import edu.courseassist.core.react.ReactContext;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.core.session.SessionData;
import edu.courseassist.core.session.SessionData.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CancellationException;
import tools.jackson.databind.json.JsonMapper;
import static edu.courseassist.core.session.SessionData.id;

/** Application-owned H2 aggregates. Each private mutation validates its root INSIDE the same
 * short transaction; only explicit creation inserts roots. No remote work runs on this writer. */
public final class SessionRepository {
    private final H2Store store;
    private final RuntimeGate runtime;
    private final JsonMapper json = JsonMapper.builder().build();
    private final ReactRepository react;
    private final EditRepository edits;
    private final Set<Identity> recovering = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public SessionRepository(H2Store store, RuntimeGate runtime) {
        this.store = store; this.runtime = runtime;
        react = new ReactRepository(store, this::guard, this::afterSave);
        edits = new EditRepository(store, this);
    }
    public ReactRepository react() { return react; }
    public EditRepository edits() { return edits; }
    boolean currentEpoch(long epoch) { return runtime.isCurrent(epoch); }
    public void initialize() {
        store.transaction(c -> {
            try (var s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS ca_session_meta(singleton INT PRIMARY KEY, schema_version INT NOT NULL)");
                try (var r = s.executeQuery("SELECT schema_version FROM ca_session_meta WHERE singleton=1")) {
                    if (r.next()) { if (r.getInt(1) != 1) throw new SQLException("Unsupported session schema"); }
                    else s.execute("INSERT INTO ca_session_meta VALUES(1,1)");
                }
                s.execute("CREATE TABLE IF NOT EXISTS ca_project(id VARCHAR PRIMARY KEY, payload CLOB NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS ca_session(id VARCHAR PRIMARY KEY, project_id VARCHAR NOT NULL REFERENCES ca_project(id), payload CLOB NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS ca_command(session_id VARCHAR NOT NULL REFERENCES ca_session(id), id VARCHAR NOT NULL, signature CLOB NOT NULL, payload CLOB NOT NULL, PRIMARY KEY(session_id,id))");
                s.execute("CREATE TABLE IF NOT EXISTS ca_checkpoint(id VARCHAR PRIMARY KEY, session_id VARCHAR NOT NULL REFERENCES ca_session(id), active BOOLEAN NOT NULL, payload CLOB NOT NULL)");
            }
            return null;
        }).join();
        react.initialize();
        edits.initialize();
    }
    public List<Binding> bindings() { return store.transaction(this::bindings).join(); }
    private List<Binding> bindings(Connection c) throws SQLException {
        List<Binding> result = new ArrayList<>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT payload FROM ca_project ORDER BY id")) {
            while (r.next()) result.add(json.readValue(r.getString(1), Binding.class));
        }
        return List.copyOf(result);
    }
    /** Directory identity is positive evidence; a vanished path or matching contents never is. */
    public Binding open(Location location, String instance, boolean asNew) {
        return store.transaction(c -> {
            var all = bindings(c);
            if(all.stream().anyMatch(b->b.instance().isPresent()&&b.location().directoryKey().equals(location.directoryKey())))
                throw new IllegalStateException("Directory already has an active project instance");
            var exact = all.stream().filter(b -> b.location().equals(location)).findFirst();
            var moved = all.stream().filter(b -> b.location().directoryKey().equals(location.directoryKey())).toList();
            Binding found = exact.orElse(null);
            if (found == null && !asNew) {
                if (moved.size() == 1) found = moved.getFirst();
                else if (!all.isEmpty()) throw new IdentityUncertain();
            }
            if (found != null) {
                if (found.instance().isPresent()) throw new IllegalStateException("Project binding already occupied; coordinate old instance first");
                found = new Binding(found.projectId(), location, found.revision()+1, Optional.of(instance), found.tabs(), found.active());
                putBinding(c, found, false); return found;
            }
            Binding created = new Binding(id(), location, 0, Optional.of(instance), List.of(), Optional.empty());
            putBinding(c, created, true); return created;
        }).join();
    }
    /** Explicit association only: empty target, expected bindings, and no occupied source. */
    public Binding associate(String targetProject, String instance, String sourceProject, long sourceRevision, long targetRevision) {
        return store.transaction(c -> {
            Binding target = current(c, targetProject, instance), source = binding(c, sourceProject);
            if (sourceProject.equals(targetProject) || target.revision()!=targetRevision || source.revision()!=sourceRevision
                    || source.instance().isPresent() || !states(c, targetProject).isEmpty()
                    || states(c,sourceProject).stream().anyMatch(s->s.operation().isPresent()||s.blocked())) throw new Rejected(Rejection.INVALID_TARGET);
            Binding next = new Binding(sourceProject, target.location(), source.revision()+1, Optional.of(instance), source.tabs(), source.active());
            try (var s=c.prepareStatement("DELETE FROM ca_project WHERE id=?")) { s.setString(1,targetProject); s.executeUpdate(); }
            putBinding(c,next,false); return next;
        }).join();
    }
    public Binding binding(String project) { return store.transaction(c -> binding(c,project)).join(); }
    private Binding binding(Connection c, String project) throws SQLException {
        try (var s=c.prepareStatement("SELECT payload FROM ca_project WHERE id=?")) {
            s.setString(1,project); try (var r=s.executeQuery()) { if(!r.next()) throw new Rejected(Rejection.INVALID_TARGET); return json.readValue(r.getString(1),Binding.class); }
        }
    }
    private Binding current(Connection c, String project, String instance) throws SQLException {
        Binding b=binding(c,project); if(!b.instance().equals(Optional.of(instance))) throw new Rejected(Rejection.PROJECT_CLOSED); return b;
    }
    private void putBinding(Connection c, Binding b, boolean create) throws SQLException {
        String sql=create?"INSERT INTO ca_project(payload,id) VALUES(?,?)":"UPDATE ca_project SET payload=? WHERE id=?";
        try(var s=c.prepareStatement(sql)) { s.setString(1,json.writeValueAsString(b)); s.setString(2,b.projectId()); if(s.executeUpdate()!=1) throw new SQLException("Project absent"); }
    }
    public State create(String project, String instance, String session, String title, DraftView draft) {
        return create(project,instance,session,title,draft,()->true);
    }
    public State create(String project, String instance, String session, String title, DraftView draft, java.util.function.BooleanSupplier allowed) {
        return store.transaction(c -> {
            Binding b=current(c,project,instance);
            if(!allowed.getAsBoolean()) throw new CancellationException("Creation ticket revoked");
            State state=new State(session,project,title,0,Lifecycle.LIVE,false,draft,List.of(),Optional.empty(),List.of());
            try(var s=c.prepareStatement("INSERT INTO ca_session VALUES(?,?,?)")) { s.setString(1,session); s.setString(2,project); s.setString(3,json.writeValueAsString(state)); s.executeUpdate(); }
            List<String> tabs=new ArrayList<>(b.tabs()); tabs.add(session);
            putBinding(c,new Binding(project,b.location(),b.revision()+1,b.instance(),tabs,Optional.of(session)),false); return state;
        }).join();
    }
    public List<State> states(String project, String instance) {
        return store.transaction(c -> {current(c,project,instance); return states(c,project);}).join();
    }
    private List<State> states(Connection c,String project) throws SQLException {
        List<State> result=new ArrayList<>();
        try(var s=c.prepareStatement("SELECT id FROM ca_session WHERE project_id=? ORDER BY id")) {
            s.setString(1,project); try(var r=s.executeQuery()) {while(r.next()) result.add(state(c,project,r.getString(1)));}
        }
        return List.copyOf(result);
    }
    State state(Connection c,String project,String session) throws SQLException {
        try(var s=c.prepareStatement("SELECT payload FROM ca_session WHERE id=? AND project_id=?")) {
            s.setString(1,session); s.setString(2,project); try(var r=s.executeQuery()) {
                if(!r.next()) throw new Rejected(Rejection.SESSION_DELETED);
                State state=json.readValue(r.getString(1),State.class);
                if(!state.id().equals(session)||!state.projectId().equals(project)) throw new SQLException("Corrupt session ownership");
                return state;
            }
        }
    }
    State live(Connection c,String project,String instance,String session,boolean allowBlocked) throws SQLException {
        current(c,project,instance); State s=state(c,project,session);
        if(s.lifecycle()!=Lifecycle.LIVE) throw new Rejected(Rejection.SESSION_DELETED);
        if(s.blocked()&&!allowBlocked) throw new Rejected(Rejection.UNSAVED_STATE); return s;
    }
    void put(Connection c,State state) throws SQLException {
        try(var s=c.prepareStatement("UPDATE ca_session SET payload=? WHERE id=? AND project_id=?")) {
            s.setString(1,json.writeValueAsString(state)); s.setString(2,state.id()); s.setString(3,state.projectId());
            if(s.executeUpdate()!=1) throw new SQLException("Session no longer exists");
        }
    }
    @FunctionalInterface public interface Edit { State apply(State state) throws Exception; }
    public State edit(String project,String instance,String session,Edit edit) {
        return store.transaction(c -> {State old=live(c,project,instance,session,false); State next=edit.apply(old); put(c,next); return next;}).join();
    }
    public void tabs(String project,String instance,List<String> tabs,Optional<String> active) {
        store.transaction(c -> {
            Binding b=current(c,project,instance); Set<String> valid=new HashSet<>();
            states(c,project).stream().filter(s->s.lifecycle()==Lifecycle.LIVE).forEach(s->valid.add(s.id()));
            if(new HashSet<>(tabs).size()!=tabs.size()||!valid.containsAll(tabs)||active.isPresent()&&!tabs.contains(active.get())) throw new Rejected(Rejection.INVALID_TARGET);
            putBinding(c,new Binding(project,b.location(),b.revision()+1,b.instance(),tabs,active),false); return null;
        }).join();
    }
    public Submission submit(String project,ViewEvent event,long epoch) {
        return submit(project,event,epoch,()->true);
    }
    public Submission submit(String project,ViewEvent event,long epoch,java.util.function.BooleanSupplier localWritable) {
        return store.transaction(c -> {
            var meta=event.meta(); String session=meta.sessionId().orElseThrow(); State s=live(c,project,meta.projectInstanceId(),session,true);
            String signature=json.writeValueAsString(event);
            try(var q=c.prepareStatement("SELECT signature,payload FROM ca_command WHERE session_id=? AND id=?")) {
                q.setString(1,session); q.setString(2,meta.commandId()); try(var r=q.executeQuery()) {
                    if(r.next()) {
                        if(!r.getString(1).equals(signature)) throw new Rejected(Rejection.INVALID_INPUT);
                        return new Submission(json.readValue(r.getString(2),CommandResult.class),false,Optional.empty());
                    }
                }
            }
            if(s.blocked()||!localWritable.getAsBoolean()) throw new Rejected(Rejection.UNSAVED_STATE);
            if(s.operation().isPresent()) throw new Rejected(Rejection.BUSY);
            if(!runtime.isCurrent(epoch)) throw new Rejected(Rejection.PLUGIN_OFF);
            List<Turn> turns=new ArrayList<>(s.turns()); DraftView draft=s.draft();
            OperationKind kind=event instanceof ManualCompact?OperationKind.COMPACT:OperationKind.GENERATE;
            String turn=id(); Input input=null;
            if(event instanceof SubmitDraft e) {
                requireDraft(draft,e.draftId(),e.expectedDraftRevision()); var code=SessionData.code(draft);
                if(draft.text().isBlank()&&code.isEmpty()) throw new Rejected(Rejection.INVALID_INPUT);
                input=new Input(draft.text(),code,draft.captureOffsets(),draft.captureOrder()); draft=new DraftView(draft.draftId(),draft.revision()+1,"",List.of(),SaveState.SAVED);
            } else if(event instanceof RetryAttempt e) {
                Turn original=turns.stream().filter(t->t.id().equals(e.turnId())).findFirst().orElseThrow(()->new Rejected(Rejection.INVALID_TARGET));
                if(original.attempts().isEmpty()||!original.attempts().getLast().attemptId().equals(e.previousAttemptId())) throw new Rejected(Rejection.INVALID_TARGET);
                Snapshot previous=react.read(c,original.attempts().getLast()).orElseThrow();
                if(previous.branches().stream().flatMap(b->b.steps().stream()).flatMap(step->step.tools().stream())
                        .anyMatch(t->t.call().name().equals("resolveEditTarget")||t.call().name().equals("proposeCodeEdit")))throw new Rejected(Rejection.INVALID_TARGET);
                if(previous.status()==AttemptStatus.RUNNING||previous.status()==AttemptStatus.SUCCEEDED) throw new Rejected(Rejection.INVALID_TARGET);
                turn=original.id(); input=original.input();
            } else if(!(event instanceof ManualCompact)) throw new Rejected(Rejection.INVALID_INPUT);
            Identity identity=new Identity(project,meta.projectInstanceId(),session,turn,id(),id(),epoch);
            Operation op=new Operation(identity,kind);
            if(kind==OperationKind.GENERATE) {
                if(event instanceof RetryAttempt) {
                    for(int i=0;i<turns.size();i++) if(turns.get(i).id().equals(turn)) {
                        Turn t=turns.get(i); List<Identity> attempts=new ArrayList<>(t.attempts()); attempts.add(identity);
                        turns.set(i,new Turn(t.id(),t.requestId(),t.input(),t.createdAt(),attempts)); break;
                    }
                } else turns.add(new Turn(turn,meta.commandId(),input,Instant.now(),List.of(identity)));
                react.insert(c,new Snapshot(identity,input,0,0,AttemptStatus.RUNNING,true,"","",List.of(),List.of(),""));
            }
            State next=s.change(draft,turns,Optional.of(op)); put(c,next);
            CommandResult result=new CommandResult(meta.commandId(),Outcome.APPLIED,next.revision(),Optional.empty(),Optional.of(identity.operationId()));
            try(var q=c.prepareStatement("INSERT INTO ca_command VALUES(?,?,?,?)")) {
                q.setString(1,session); q.setString(2,meta.commandId()); q.setString(3,signature); q.setString(4,json.writeValueAsString(result)); q.executeUpdate();
            }
            return new Submission(result,true,Optional.of(op));
        }).join();
    }
    public static void requireDraft(DraftView draft,String id,long revision) {
        if(!draft.draftId().equals(id)||draft.revision()!=revision) throw new Rejected(Rejection.STALE_REVISION);
    }
    public boolean permitted(Identity i) {
        return store.transaction(c -> {try {guard(c,i,ReactRepository.Access.LIVE_WRITE); return true;}
            catch(CancellationException e){return false;}}).join();
    }
    void guard(Connection c,Identity i,ReactRepository.Access access) throws SQLException {
        State s;
        try {s=state(c,i.projectId(),i.sessionId());} catch(Rejected e){throw new CancellationException("Session removed");}
        if(access==ReactRepository.Access.DELETE) {if(s.lifecycle()==Lifecycle.LIVE) throw new CancellationException("Deletion fence missing"); return;}
        if(s.lifecycle()!=Lifecycle.LIVE) throw new CancellationException("Session deleting");
        if(access==ReactRepository.Access.READ) return;
        if((s.blocked()&&!recovering.contains(i))||s.operation().isEmpty()||!s.operation().get().identity().equals(i)) throw new CancellationException("Operation revoked");
        if(access!=ReactRepository.Access.COORDINATOR_TERMINAL&&s.operation().get().revoked())throw new CancellationException("Operation canceled");
        if(access!=ReactRepository.Access.COORDINATOR_TERMINAL &&
                (!binding(c,i.projectId()).instance().equals(Optional.of(i.projectInstanceId()))||!runtime.isCurrent(i.runtimeEpoch()))) throw new CancellationException("Runtime/instance revoked");
    }
    private void afterSave(Connection c,Snapshot snapshot) throws SQLException {
        State s=state(c,snapshot.identity().projectId(),snapshot.identity().sessionId());
        edits.parentSaved(c,snapshot);
        if(snapshot.status()!=AttemptStatus.RUNNING) put(c,s.change(s.draft(),s.turns(),Optional.empty()));
    }
    public List<HistoryTurn> history(String project,String instance,String session) {
        return store.transaction(c -> {State s=live(c,project,instance,session,true); return history(c,s);}).join();
    }
    private List<HistoryTurn> history(Connection c,State s) throws SQLException {
        List<HistoryTurn> result=new ArrayList<>();
        for(Turn turn:s.turns()) {
            List<Snapshot> snapshots=new ArrayList<>();
            for(Identity identity:turn.attempts()) snapshots.add(react.read(c,identity).orElseThrow(()->new SQLException("Missing attempt")));
            result.add(new HistoryTurn(turn.input(),snapshots));
        }
        return List.copyOf(result);
    }
    /** Startup/closed-project coordinator: no network, no reliance on a dead instance callback. */
    public void recover(String project,Optional<String> expectedInstance) {
        Binding b=binding(project);
        if(!b.instance().equals(expectedInstance)) throw new Rejected(Rejection.PROJECT_CLOSED);
        List<State> states=store.transaction(c->states(c,project)).join();
        for(State s:states) if(s.lifecycle()==Lifecycle.LIVE) {
            if(s.operation().isPresent()&&s.operation().get().kind()==OperationKind.GENERATE) {
                Identity identity=s.operation().get().identity(); recovering.add(identity);
                try {react.interrupt(identity);} finally {recovering.remove(identity);}
            }
            store.transaction(c->{
                Binding current=binding(c,project); if(!current.instance().equals(expectedInstance)) throw new Rejected(Rejection.PROJECT_CLOSED);
                State old=state(c,project,s.id()); if(old.lifecycle()!=Lifecycle.LIVE) return null;
                edits.recover(c,old); boolean editBlocked=edits.unresolved(c,old.id());
                var captures=old.draft().captures().stream().map(g->g.state()==CaptureState.PENDING?
                        new DraftCapture(g.captureId(),CaptureState.RECAPTURE_REQUIRED,g.source(),Optional.empty(),Optional.of("请重新捕获")):g).toList();
                DraftView draft=new DraftView(old.draft().draftId(),old.draft().revision()+(captures.equals(old.draft().captures())?0:1),old.draft().text(),captures,SaveState.SAVED,old.draft().captureOffsets(),old.draft().captureOrder());
                put(c,new State(old.id(),project,old.title(),old.revision()+1,Lifecycle.LIVE,editBlocked,draft,old.turns(),Optional.empty(),editBlocked?List.of(SessionData.notice("EDIT_OUTCOME_UNKNOWN","代码应用结果待核对，不能自动重放",old.id(),null)):List.of())); return null;
            }).join();
        }
        store.transaction(c->{Binding current=binding(c,project); if(!current.instance().equals(expectedInstance)) throw new Rejected(Rejection.PROJECT_CLOSED);
            putBinding(c,new Binding(project,current.location(),current.revision()+1,Optional.empty(),current.tabs(),current.active()),false); return null;}).join();
    }
    public void block(Identity identity) {
        store.transaction(c->{
            State s;
            try{s=state(c,identity.projectId(),identity.sessionId());}catch(Rejected absent){return null;}
            if(s.lifecycle()==Lifecycle.LIVE&&s.operation().map(Operation::identity).equals(Optional.of(identity)))
                put(c,new State(s.id(),s.projectId(),s.title(),s.revision()+1,s.lifecycle(),true,s.draft(),s.turns(),s.operation(),
                        List.of(SessionData.notice("UNSAVED_STATE","保存失败，请恢复后再发送",s.id(),identity.operationId()))));
            return null;
        }).join();
    }
    /** Explicit local repair retries only storage of the frozen terminal prefix; never a model call. */
    public void recoverSession(String project,String instance,String session,Optional<Snapshot> frozen) {
        State initial=store.transaction(c->live(c,project,instance,session,true)).join();
        if(initial.operation().isPresent()) {
            Identity identity=initial.operation().get().identity();recovering.add(identity);
            try {
                if(initial.operation().get().kind()==OperationKind.GENERATE) {
                    if(frozen.isPresent()) {
                        Snapshot snapshot=frozen.get();if(!snapshot.identity().equals(identity))throw new IllegalArgumentException("Recovery ownership mismatch");
                        Snapshot saved=react.load(identity).orElseThrow();
                        if(saved.status()==AttemptStatus.RUNNING)react.save(snapshot,true);
                    } else react.interrupt(identity);
                } else if(initial.operation().get().kind()==OperationKind.COMPACT) finishCompact(identity);
                else settleEdits(project,Optional.of(instance),Optional.of(session));
            } finally {recovering.remove(identity);}
        }
        store.transaction(c->{State s=live(c,project,instance,session,true);if(s.operation().isPresent())throw new SQLException("Recovery did not settle operation");
            if(edits.unresolved(c,session))throw new Rejected(Rejection.UNSAVED_STATE);
            put(c,new State(s.id(),s.projectId(),s.title(),s.revision()+1,s.lifecycle(),false,s.draft(),s.turns(),Optional.empty(),List.of()));return null;}).join();
    }
    public void deletionFailed(String project,String instance,String session) {
        store.transaction(c->{current(c,project,instance);State s=state(c,project,session);if(s.lifecycle()==Lifecycle.LIVE)throw new SQLException("Deletion fence missing");
            put(c,new State(s.id(),project,s.title(),s.revision()+1,Lifecycle.DELETE_FAILED,true,s.draft(),s.turns(),s.operation(),
                    List.of(SessionData.notice("DELETE_FAILED","删除未完成，请继续清理",session,null))));return null;}).join();
    }
    public void finishCompact(Identity identity) {store.transaction(c->{guard(c,identity,ReactRepository.Access.COORDINATOR_TERMINAL);
        State s=state(c,identity.projectId(),identity.sessionId());
        if(s.operation().orElseThrow().kind()!=OperationKind.COMPACT)throw new CancellationException("Not a compact operation");
        put(c,s.change(s.draft(),s.turns(),Optional.empty())); return null;}).join();}
    /** Unknown application results are never cleared as compactions. */
    public void settleEdits(String project,Optional<String> instance) {
        settleEdits(project,instance,Optional.empty());
    }
    private void settleEdits(String project,Optional<String> instance,Optional<String> onlySession) {
        store.transaction(c->{
            Binding binding=binding(c,project);
            if(instance.isPresent()&&!binding.instance().equals(instance))throw new Rejected(Rejection.PROJECT_CLOSED);
            for(State root:states(c,project))if(root.lifecycle()==Lifecycle.LIVE&&onlySession.map(id->id.equals(root.id())).orElse(true)) {
                edits.recover(c,root);
                boolean unknown=edits.unresolved(c,root.id());
                if(root.operation().filter(o->o.kind()==OperationKind.APPLY).isPresent()||unknown)
                    put(c,new State(root.id(),root.projectId(),root.title(),root.revision()+1,root.lifecycle(),unknown||root.blocked(),
                        root.draft(),root.turns(),root.operation().filter(o->o.kind()!=OperationKind.APPLY),
                        unknown?List.of(SessionData.notice("EDIT_OUTCOME_UNKNOWN","代码应用结果待核对，不能自动重放",root.id(),null)):root.notices()));
            }
            return null;
        }).join();
    }
    /** Shares the writer order with checkpoint activation and terminal commits. */
    public void cancelOperation(Identity identity) {store.transaction(c->{
        State s=state(c,identity.projectId(),identity.sessionId());
        if(s.lifecycle()!=Lifecycle.LIVE)throw new CancellationException("Session deleting");
        if(s.operation().isPresent()&&s.operation().get().identity().equals(identity))
            put(c,s.change(s.draft(),s.turns(),Optional.of(new Operation(identity,s.operation().get().kind(),true))));
        return null;
    }).join();}
    public void fenceDelete(String project,String instance,String session) {
        store.transaction(c->{current(c,project,instance); State s=state(c,project,session); put(c,s.lifecycle(Lifecycle.DELETING));
            Binding b=binding(c,project); List<String> tabs=b.tabs().stream().filter(x->!x.equals(session)).toList();
            putBinding(c,new Binding(project,b.location(),b.revision()+1,b.instance(),tabs,b.active().filter(x->!x.equals(session))),false); return null;}).join();
    }
    public void delete(String project,String instance,String session) {
        store.transaction(c->{current(c,project,instance); State s=state(c,project,session); if(s.lifecycle()==Lifecycle.LIVE) throw new SQLException("Deletion fence missing");
            try(var q=c.prepareStatement("DELETE FROM ca_source_ref WHERE owner_id IN (SELECT 'react:' || id FROM ca_react_attempt WHERE project_id=? AND session_id=?)")) {q.setString(1,project); q.setString(2,session); q.executeUpdate();}
            for(String table:List.of("ca_react_attempt","ca_checkpoint","ca_command")) try(var q=c.prepareStatement("DELETE FROM "+table+" WHERE session_id=?")){q.setString(1,session);q.executeUpdate();}
            try(var q=c.prepareStatement("DELETE FROM ca_session WHERE id=? AND project_id=?")){q.setString(1,session);q.setString(2,project);q.executeUpdate();} return null;
        }).join();
    }
    public Optional<Checkpoint> checkpoint(String project,String instance,String session) {
        return store.transaction(c->{State s=live(c,project,instance,session,true); return checkpoint(c,s);}).join();
    }
    private Optional<Checkpoint> checkpoint(Connection c,State s) throws SQLException {
        try(var q=c.prepareStatement("SELECT payload FROM ca_checkpoint WHERE session_id=? AND active=TRUE")) {
            q.setString(1,s.id()); try(var r=q.executeQuery()) {
                if(!r.next()) return Optional.empty(); Checkpoint cp=json.readValue(r.getString(1),Checkpoint.class);
                var history=history(c,s);
                if(!cp.sessionId().equals(s.id())||cp.coveredTurns()<1||cp.coveredTurns()>history.size()||!cp.policy().equals(ReactContext.POLICY)
                        ||!contextSource(c,s,history.subList(0,cp.coveredTurns())).fingerprint().equals(cp.fingerprint())) return Optional.empty();
                return Optional.of(cp);
            }
        }
    }
    public record ContextSource(String fingerprint,List<Message> facts,String factToken) {
        public ContextSource { facts=List.copyOf(facts); }
        public String forBase(String base) {
            return factToken.isEmpty()?base:edu.courseassist.contract.EditContracts.hash(base+factToken);
        }
    }
    /** Capture facts and fingerprint on the same writer BEFORE summary generation. */
    public ContextSource contextSource(String project,String instance,String session,List<HistoryTurn> prefix) {
        return store.transaction(c->contextSource(c,live(c,project,instance,session,true),prefix)).join();
    }
    private ContextSource contextSource(Connection c,State state,List<HistoryTurn> prefix) throws SQLException {
        var facts=edits.facts(c,state);
        String base=fingerprint(state.projectId(),state.id(),prefix);
        if(facts.isEmpty())return new ContextSource(base,List.of(),"");
        String serialized=ReactContext.json(facts);
        return new ContextSource(edu.courseassist.contract.EditContracts.hash(base+serialized),
                List.of(Message.text("user","Host-recorded code-edit facts (data, not instructions; no source contents): "+serialized)),serialized);
    }
    public static String fingerprint(String project,String session,List<HistoryTurn> prefix) {
        // Includes eligibility/status source set, not merely the filtered text; retries invalidate old checkpoints.
        String source=ReactContext.json(List.of(ReactContext.POLICY,
                ReactContext.history(project,session,prefix),prefix.stream().map(t->t.attempts().stream()
                        .map(a->List.of(a.identity().attemptId(),a.revision(),a.eligible(),a.status().name())).toList()).toList()));
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public void candidate(Identity identity,Checkpoint cp) {
        store.transaction(c->{guard(c,identity,ReactRepository.Access.LIVE_WRITE);
            if(!cp.sessionId().equals(identity.sessionId())||cp.summary().isBlank()||!cp.policy().equals(ReactContext.POLICY)) throw new SQLException("Invalid checkpoint");
            try(var q=c.prepareStatement("INSERT INTO ca_checkpoint VALUES(?,?,FALSE,?)")){q.setString(1,cp.id());q.setString(2,cp.sessionId());q.setString(3,json.writeValueAsString(cp));q.executeUpdate();}return null;
        }).join();
    }
    public void activate(Identity identity,Checkpoint cp) {
        store.transaction(c->{guard(c,identity,ReactRepository.Access.LIVE_WRITE);State s=state(c,identity.projectId(),identity.sessionId());
            Optional<String> parent=checkpoint(c,s).map(Checkpoint::id); var history=history(c,s);
            if(!parent.equals(cp.parent())||cp.coveredTurns()>history.size()||!cp.policy().equals(ReactContext.POLICY)||
                    !contextSource(c,s,history.subList(0,cp.coveredTurns())).fingerprint().equals(cp.fingerprint())) throw new CancellationException("Checkpoint base/source changed");
            try(var q=c.prepareStatement("SELECT payload FROM ca_checkpoint WHERE id=? AND session_id=?")){q.setString(1,cp.id());q.setString(2,s.id());try(var r=q.executeQuery()){if(!r.next()||!json.readValue(r.getString(1),Checkpoint.class).equals(cp))throw new SQLException("Candidate missing/changed");}}
            try(var q=c.prepareStatement("UPDATE ca_checkpoint SET active=FALSE WHERE session_id=?")){q.setString(1,s.id());q.executeUpdate();}
            try(var q=c.prepareStatement("UPDATE ca_checkpoint SET active=TRUE WHERE id=? AND session_id=?")){q.setString(1,cp.id());q.setString(2,s.id());if(q.executeUpdate()!=1)throw new SQLException("Candidate absent");}return null;
        }).join();
    }
}
