package edu.courseassist.storage;

import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.core.session.SessionData.*;
import edu.courseassist.core.session.SessionData;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import tools.jackson.databind.json.JsonMapper;

/** Independent F2 records, using the session writer and mandatory root/operation guards.
 * Never performs model, viewer, Document, disk or network work. */
public final class EditRepository {
    private final H2Store store;
    private final SessionRepository sessions;
    private final JsonMapper json=JsonMapper.builder().build();
    EditRepository(H2Store store,SessionRepository sessions){this.store=store;this.sessions=sessions;}
    void initialize() {
        store.transaction(c->{
            try(var s=c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS ca_edit_meta(singleton INT PRIMARY KEY, schema_version INT NOT NULL)");
                try(var r=s.executeQuery("SELECT schema_version FROM ca_edit_meta WHERE singleton=1")) {
                    if(r.next()){if(r.getInt(1)!=1)throw new SQLException("Unsupported F2 schema");}
                    else s.execute("INSERT INTO ca_edit_meta VALUES(1,1)");
                }
                s.execute("CREATE TABLE IF NOT EXISTS ca_edit_artifact(id VARCHAR PRIMARY KEY, session_id VARCHAR NOT NULL REFERENCES ca_session(id) ON DELETE CASCADE, attempt_id VARCHAR NOT NULL, request_id VARCHAR NOT NULL, payload CLOB NOT NULL, UNIQUE(session_id,request_id))");
                s.execute("CREATE TABLE IF NOT EXISTS ca_edit_review(proposal_id VARCHAR PRIMARY KEY REFERENCES ca_edit_artifact(id) ON DELETE CASCADE, session_id VARCHAR NOT NULL REFERENCES ca_session(id) ON DELETE CASCADE, state VARCHAR NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS ca_edit_preview(id VARCHAR PRIMARY KEY, session_id VARCHAR NOT NULL REFERENCES ca_session(id) ON DELETE CASCADE, proposal_id VARCHAR NOT NULL REFERENCES ca_edit_artifact(id) ON DELETE CASCADE, payload CLOB NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS ca_edit_apply(id VARCHAR PRIMARY KEY, session_id VARCHAR NOT NULL REFERENCES ca_session(id) ON DELETE CASCADE, proposal_id VARCHAR NOT NULL UNIQUE REFERENCES ca_edit_artifact(id) ON DELETE CASCADE, identity_json CLOB NOT NULL, receipt_json CLOB NOT NULL, preview_json CLOB NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS ca_edit_command(session_id VARCHAR NOT NULL REFERENCES ca_session(id) ON DELETE CASCADE, id VARCHAR NOT NULL, signature CLOB NOT NULL, result_json CLOB NOT NULL, PRIMARY KEY(session_id,id))");
                s.execute("CREATE TABLE IF NOT EXISTS ca_edit_generation(session_id VARCHAR NOT NULL REFERENCES ca_session(id) ON DELETE CASCADE, request_id VARCHAR NOT NULL, turn_id VARCHAR NOT NULL, snapshot_json CLOB NOT NULL, observation CLOB NOT NULL, PRIMARY KEY(session_id,request_id))");
                s.execute("CREATE TABLE IF NOT EXISTS ca_edit_snapshot(id VARCHAR PRIMARY KEY, session_id VARCHAR NOT NULL REFERENCES ca_session(id) ON DELETE CASCADE, branch_id VARCHAR NOT NULL, payload CLOB NOT NULL)");
            }
            return null;
        }).join();
    }
    public static Identity identity(Origin o) {return new Identity(o.projectId(),o.instanceId(),o.sessionId(),o.turnId(),o.operationId(),o.attemptId(),o.epoch());}
    public static Origin origin(Identity i) {return new Origin(i.projectId(),i.projectInstanceId(),i.sessionId(),i.turnId(),i.attemptId(),i.operationId(),i.runtimeEpoch());}
    public void capture(EditSnapshot snapshot,String branch) {
        store.transaction(c->{var o=snapshot.request().origin();sessions.guard(c,identity(o),ReactRepository.Access.LIVE_WRITE);
            Snapshot parent=sessions.react().read(c,identity(o)).orElseThrow();
            if(parent.status()!=AttemptStatus.RUNNING||parent.branches().stream().noneMatch(b->b.id().equals(branch)&&b.status()==BranchStatus.ACTIVE))throw new CancellationException("Capture branch revoked");
            try(var q=c.prepareStatement("INSERT INTO ca_edit_snapshot VALUES(?,?,?,?)")) {
                q.setString(1,snapshot.snapshotId());q.setString(2,o.sessionId());q.setString(3,branch);q.setString(4,json.writeValueAsString(snapshot));q.executeUpdate();
            }return null;
        }).join();
    }
    /** Reserve before subscription. Even failed/interrupted requests retain their once-only identity. */
    public Optional<String> reserveGeneration(EditSnapshot snapshot) {
        return store.transaction(c->{var o=snapshot.request().origin();sessions.guard(c,identity(o),ReactRepository.Access.LIVE_WRITE);
            try(var q=c.prepareStatement("SELECT observation FROM ca_edit_generation WHERE session_id=? AND request_id=?")) {
                q.setString(1,o.sessionId());q.setString(2,snapshot.request().requestId());try(var r=q.executeQuery()){if(r.next())return Optional.of(r.getString(1));}
            }
            try(var q=c.prepareStatement("INSERT INTO ca_edit_generation VALUES(?,?,?,?,?)")) {
                q.setString(1,o.sessionId());q.setString(2,snapshot.request().requestId());q.setString(3,o.turnId());q.setString(4,json.writeValueAsString(snapshot));q.setString(5,"{\"status\":\"RESERVED_OR_INTERRUPTED\"}");q.executeUpdate();
            }return Optional.<String>empty();
        }).join();
    }
    public void generationResult(EditSnapshot snapshot,String observation) {
        store.transaction(c->{var o=snapshot.request().origin();sessions.guard(c,identity(o),ReactRepository.Access.LIVE_WRITE);
            try(var q=c.prepareStatement("UPDATE ca_edit_generation SET observation=? WHERE session_id=? AND request_id=?")) {
                q.setString(1,observation);q.setString(2,o.sessionId());q.setString(3,snapshot.request().requestId());
                if(q.executeUpdate()!=1)throw new IllegalStateException("Missing generation reservation");
            }return null;
        }).join();
    }
    public EditProposal stage(EditProposal proposal) {
        return store.transaction(c->{
            var source=proposal.snapshot().request().origin();
            sessions.guard(c,identity(source),ReactRepository.Access.LIVE_WRITE);
            State root=sessions.state(c,source.projectId(),source.sessionId());
            if(root.operation().orElseThrow().kind()!=OperationKind.GENERATE)throw new CancellationException("Not a generation operation");
            Snapshot parent=sessions.react().read(c,identity(source)).orElseThrow();
            if(parent.status()!=AttemptStatus.RUNNING)throw new CancellationException("Parent terminal");
            if(parent.branches().stream().noneMatch(b->b.id().equals(proposal.branchId())&&b.status()!=BranchStatus.INVALIDATED))
                throw new IllegalArgumentException("Unknown/invalid parent branch");
            try(var q=c.prepareStatement("SELECT payload FROM ca_edit_artifact WHERE session_id=? AND request_id=?")) {
                q.setString(1,source.sessionId());q.setString(2,proposal.snapshot().request().requestId());
                try(var r=q.executeQuery()){if(r.next()){
                    EditProposal old=json.readValue(r.getString(1),EditProposal.class);
                    if(!old.equals(proposal))throw new Rejected(Rejection.INVALID_INPUT);
                    return old;
                }}
            }
            try(var q=c.prepareStatement("INSERT INTO ca_edit_artifact VALUES(?,?,?,?,?)")){
                q.setString(1,proposal.proposalId());q.setString(2,source.sessionId());q.setString(3,source.attemptId());
                q.setString(4,proposal.snapshot().request().requestId());q.setString(5,json.writeValueAsString(proposal));q.executeUpdate();
            }
            try(var q=c.prepareStatement("INSERT INTO ca_edit_review VALUES(?,?,'STAGED')")){
                q.setString(1,proposal.proposalId());q.setString(2,source.sessionId());q.executeUpdate();
            }
            return proposal;
        }).join();
    }
    /** Called inside the parent save transaction, BEFORE the same transaction releases its slot. */
    void parentSaved(Connection c,Snapshot parent) throws SQLException {
        for(EditProposal p:artifacts(c,parent.identity().sessionId())) {
            if(!identity(p.snapshot().request().origin()).equals(parent.identity()))continue;
            ReviewState current=review(c,p.proposalId());
            if(current!=ReviewState.STAGED)continue;
            boolean invalid=parent.branches().stream().anyMatch(b->b.id().equals(p.branchId())&&b.status()==BranchStatus.INVALIDATED);
            if(parent.status()==AttemptStatus.RUNNING&&!invalid)continue;
            boolean eligible=parent.eligible()&&parent.effectiveBranchId().equals(p.branchId())&&parent.branches().stream()
                    .filter(b->b.id().equals(p.branchId())).flatMap(b->b.steps().stream()).flatMap(s->s.tools().stream())
                    .anyMatch(t->t.status()==ToolStatus.SUCCEEDED&&t.call().id().equals(p.toolCallId())&&t.call().name().equals("proposeCodeEdit"));
            setReview(c,p.proposalId(),eligible?ReviewState.REVIEWABLE:ReviewState.INVALIDATED);
        }
    }
    public List<ProposalView> views(String project,String instance,String session) {
        return store.transaction(c->{sessions.live(c,project,instance,session,true);List<ProposalView> out=new ArrayList<>();
            for(var p:artifacts(c,session))out.add(new ProposalView(p.ref(),review(c,p.proposalId()),receipt(c,p.proposalId()),p.snapshot().target().relativePath(),p.snapshot().request().origin().turnId(),p.snapshot().request().origin().attemptId(),p.branchId()));
            return List.copyOf(out);
        }).join();
    }
    /** Archive read under the current instance; does not grant fresh preview or approval. */
    public EditProposal load(String project,String instance,String session,ProposalRef ref) {
        return store.transaction(c->{
            sessions.live(c,project,instance,session,true);
            EditProposal proposal=artifact(c,session,ref.proposalId());
            if(!proposal.ref().equals(ref))throw new Rejected(Rejection.STALE_REVISION);
            return proposal;
        }).join();
    }
    private EditProposal require(Connection c,String project,String instance,String session,ProposalRef ref) throws SQLException {
        sessions.live(c,project,instance,session,false);
        EditProposal p=artifact(c,session,ref.proposalId());
        if(!p.ref().equals(ref))throw new Rejected(Rejection.STALE_REVISION);
        Origin source=p.snapshot().request().origin();
        if(!source.projectId().equals(project))throw new Rejected(Rejection.PROJECT_CLOSED);
        Snapshot parent=sessions.react().read(c,identity(source)).orElseThrow();
        if(!parent.eligible()||!parent.effectiveBranchId().equals(p.branchId()))throw new Rejected(Rejection.INVALID_TARGET);
        if(parent.branches().stream().filter(b->b.id().equals(p.branchId())&&b.status()!=BranchStatus.INVALIDATED)
                .flatMap(b->b.steps().stream()).flatMap(s->s.tools().stream()).noneMatch(t->t.status()==ToolStatus.SUCCEEDED
                        &&t.call().id().equals(p.toolCallId())&&t.call().name().equals("proposeCodeEdit")))throw new Rejected(Rejection.INVALID_TARGET);
        return p;
    }
    /** Host calls this only after the real read-only Diff was displayed and revalidated. */
    public PreviewToken displayed(String project,String instance,String session,ProposalRef ref) {
        var p=load(project,instance,session,ref);
        if(!p.snapshot().request().origin().instanceId().equals(instance))throw new Rejected(Rejection.PROJECT_CLOSED);
        return displayed(project,instance,session,ref,p.snapshot().documentStamp());
    }
    /** Fresh viewer plus host validation of the same full before; old tokens stay revoked. */
    public PreviewToken displayed(String project,String instance,String session,ProposalRef ref,long documentStamp) {
        return store.transaction(c->{
            EditProposal p=require(c,project,instance,session,ref);
            var state=review(c,p.proposalId());
            if((state!=ReviewState.REVIEWABLE&&state!=ReviewState.INVALIDATED)||receipt(c,p.proposalId()).isPresent())throw new Rejected(Rejection.INVALID_TARGET);
            setReview(c,p.proposalId(),ReviewState.REVIEWABLE);
            PreviewToken token=new PreviewToken(SessionData.id(),ref,instance,documentStamp);
            try(var q=c.prepareStatement("INSERT INTO ca_edit_preview VALUES(?,?,?,?)")){
                q.setString(1,token.tokenId());q.setString(2,session);q.setString(3,p.proposalId());q.setString(4,json.writeValueAsString(token));q.executeUpdate();
            }
            return token;
        }).join();
    }
    public void dismiss(PreviewToken token) {
        store.transaction(c->{try(var q=c.prepareStatement("DELETE FROM ca_edit_preview WHERE id=?")){q.setString(1,token.tokenId());q.executeUpdate();}return null;}).join();
    }
    public void invalidate(String project,String instance,String session,ProposalRef ref) {
        store.transaction(c->{require(c,project,instance,session,ref);
            if(review(c,ref.proposalId())==ReviewState.REVIEWABLE)setReview(c,ref.proposalId(),ReviewState.INVALIDATED);return null;}).join();
    }
    public List<ApplyRequest> applications(String project,String instance,String session) {
        return store.transaction(c->{sessions.live(c,project,instance,session,true);List<ApplyRequest> result=new ArrayList<>();
            for(var p:artifacts(c,session)) {var r=receipt(c,p.proposalId());if(r.isPresent())result.add(application(c,session,r.get().applyId()));}
            return List.copyOf(result);
        }).join();
    }
    /** Observation only: accepts host evidence, does not reserve a slot or authorize another write. */
    public void observed(String project,String instance,String session,ApplyReceipt result) {
        store.transaction(c->{State root=sessions.live(c,project,instance,session,true);
            var request=application(c,session,result.applyId());
            if(!request.proposal().ref().equals(result.proposal())||result.state()!=ApplyState.APPLIED_TO_DOCUMENT)throw new Rejected(Rejection.INVALID_INPUT);
            boolean previouslyUnknown=unresolved(c,session);
            writeReceipt(c,result);
            boolean unknown=unresolved(c,session);
            sessions.put(c,new State(root.id(),root.projectId(),root.title(),root.revision()+1,root.lifecycle(),unknown||(root.blocked()&&!previouslyUnknown),
                    root.draft(),root.turns(),root.operation(),unknown?root.notices():List.of(),root.titleExplicit()));return null;
        }).join();
    }
    /** A coordinator-held execution receipt may outlive lifecycle slot settlement. This only
     * records that exact execution's observed outcome; it never authorizes or repeats a write. */
    public void reconcile(String project,String instance,ApplyRequest request,ApplyReceipt result) {
        store.transaction(c->{String session=request.operation().sessionId();State root=sessions.live(c,project,instance,session,true);
            if(!application(c,session,request.applyId()).equals(request)||!result.applyId().equals(request.applyId())
                    ||!result.proposal().equals(request.proposal().ref())||result.state()==ApplyState.INTENT||result.state()==ApplyState.APPLYING)
                throw new Rejected(Rejection.INVALID_INPUT);
            var old=receipt(c,request.proposal().proposalId()).orElseThrow();
            if(old.state()==ApplyState.APPLIED_TO_DOCUMENT||old.state()==ApplyState.FAILED_NOT_APPLIED) {
                if(!old.equals(result))throw new Rejected(Rejection.OPERATION_TERMINATED);return null;
            }
            if(root.operation().isPresent()&&!root.operation().get().identity().equals(identity(request.operation())))throw new Rejected(Rejection.BUSY);
            writeReceipt(c,result);if(result.state()==ApplyState.FAILED_NOT_APPLIED)setReview(c,request.proposal().proposalId(),ReviewState.INVALIDATED);
            boolean unknown=unresolved(c,session);
            sessions.put(c,new State(root.id(),root.projectId(),root.title(),root.revision()+1,root.lifecycle(),unknown,
                    root.draft(),root.turns(),Optional.empty(),unknown?List.of(SessionData.notice("EDIT_OUTCOME_UNKNOWN","代码应用结果待核对，不能自动重放",session,request.applyId())):List.of(),root.titleExplicit()));return null;
        }).join();
    }
    private EditProposal displayed(Connection c,String project,EventMeta meta,PreviewToken token) throws SQLException {
        String session=meta.sessionId().orElseThrow();
        EditProposal p=require(c,project,meta.projectInstanceId(),session,token.proposal());
        if(!token.instanceId().equals(meta.projectInstanceId()))throw new Rejected(Rejection.PROJECT_CLOSED);
        requireStoredPreview(c,session,token);
        return p;
    }
    public void validatePreview(String project,EventMeta meta,PreviewToken token) {
        store.transaction(c->{var p=displayed(c,project,meta,token);if(review(c,p.proposalId())!=ReviewState.REVIEWABLE)throw new Rejected(Rejection.INVALID_TARGET);return null;}).join();
    }
    private void requireStoredPreview(Connection c,String session,PreviewToken token) throws SQLException {
        try(var q=c.prepareStatement("SELECT payload FROM ca_edit_preview WHERE id=? AND session_id=? AND proposal_id=?")){
            q.setString(1,token.tokenId());q.setString(2,session);q.setString(3,token.proposal().proposalId());
            try(var r=q.executeQuery()){if(!r.next()||!json.readValue(r.getString(1),PreviewToken.class).equals(token))throw new Rejected(Rejection.STALE_REVISION);}
        }
    }
    public CommandResult reject(String project,EventMeta meta,PreviewToken token) {
        return store.transaction(c->{
            String session=meta.sessionId().orElseThrow();State root=sessions.live(c,project,meta.projectInstanceId(),session,true);
            if(!token.instanceId().equals(meta.projectInstanceId()))throw new Rejected(Rejection.PROJECT_CLOSED);
            String signature="reject:"+json.writeValueAsString(token);
            var duplicate=command(c,meta,signature);if(duplicate.isPresent())return duplicate.get();
            EditProposal p=displayed(c,project,meta,token);
            if(review(c,p.proposalId())!=ReviewState.REVIEWABLE)throw new Rejected(Rejection.INVALID_TARGET);
            setReview(c,p.proposalId(),ReviewState.REJECTED);
            State next=root.change(root.draft(),root.turns(),root.operation());sessions.put(c,next);
            CommandResult result=new CommandResult(meta.commandId(),Outcome.APPLIED,next.revision(),Optional.empty(),Optional.empty());
            remember(c,meta,signature,result);return result;
        }).join();
    }
    /** Foundation for G5. Does not invoke an adapter; production approve remains unsupported in G1. */
    public ApplyRequest beginApply(String project,EventMeta meta,PreviewToken token,long epoch) {
        return store.transaction(c->{
            String session=meta.sessionId().orElseThrow();State root=sessions.live(c,project,meta.projectInstanceId(),session,true);
            if(!token.instanceId().equals(meta.projectInstanceId()))throw new Rejected(Rejection.PROJECT_CLOSED);
            String signature="approve:"+json.writeValueAsString(token);
            var duplicate=command(c,meta,signature);
            if(duplicate.isPresent())return application(c,session,duplicate.get().operationId().orElseThrow());
            // Same proposal under another command id returns the original intent, never a new write.
            var prior=receipt(c,token.proposal().proposalId());
            if(prior.isPresent()) {
                EditProposal existing=artifact(c,session,token.proposal().proposalId());
                if(!existing.ref().equals(token.proposal())||!application(c,session,prior.get().applyId()).operation().instanceId().equals(meta.projectInstanceId()))
                    throw new Rejected(Rejection.STALE_REVISION);
                // Replay reads the durable record even while unresolved; it never reacquires a slot.
                try(var q=c.prepareStatement("SELECT preview_json FROM ca_edit_apply WHERE id=? AND session_id=?")) {
                    q.setString(1,prior.get().applyId());q.setString(2,session);
                    try(var r=q.executeQuery()){if(!r.next()||!json.readValue(r.getString(1),PreviewToken.class).equals(token))throw new Rejected(Rejection.STALE_REVISION);}
                }
                remember(c,meta,signature,new CommandResult(meta.commandId(),Outcome.APPLIED,root.revision(),Optional.empty(),Optional.of(prior.get().applyId())));
                return application(c,session,prior.get().applyId());
            }
            EditProposal p=displayed(c,project,meta,token);
            if(review(c,p.proposalId())!=ReviewState.REVIEWABLE)throw new Rejected(Rejection.INVALID_TARGET);
            if(root.blocked())throw new Rejected(Rejection.UNSAVED_STATE);
            if(root.operation().isPresent())throw new Rejected(Rejection.BUSY);
            if(!sessions.currentEpoch(epoch))throw new Rejected(Rejection.PLUGIN_OFF);
            String applyId=SessionData.id();Origin source=p.snapshot().request().origin();
            Identity op=new Identity(project,meta.projectInstanceId(),session,source.turnId(),applyId,source.attemptId(),epoch);
            ApplyReceipt intent=new ApplyReceipt(applyId,p.ref(),ApplyState.INTENT,DiskState.UNKNOWN,Optional.empty());
            try(var q=c.prepareStatement("INSERT INTO ca_edit_apply VALUES(?,?,?,?,?,?)")){
                q.setString(1,applyId);q.setString(2,session);q.setString(3,p.proposalId());q.setString(4,json.writeValueAsString(op));q.setString(5,json.writeValueAsString(intent));q.setString(6,json.writeValueAsString(token));q.executeUpdate();
            }
            setReview(c,p.proposalId(),ReviewState.APPROVED);
            State next=root.change(root.draft(),root.turns(),Optional.of(new Operation(op,OperationKind.APPLY)));sessions.put(c,next);
            remember(c,meta,signature,new CommandResult(meta.commandId(),Outcome.APPLIED,next.revision(),Optional.empty(),Optional.of(applyId)));
            return new ApplyRequest(applyId,origin(op),p,token.documentStamp());
        }).join();
    }
    /** Reserve durable APPLYING under its own current operation/epoch before any future IDE write. */
    public void applying(ApplyRequest request) {
        store.transaction(c->{Identity op=identity(request.operation());sessions.guard(c,op,ReactRepository.Access.LIVE_WRITE);
            ApplyRequest held=application(c,op.sessionId(),request.applyId());if(!held.equals(request))throw new Rejected(Rejection.INVALID_INPUT);
            ApplyReceipt old=receipt(c,request.proposal().proposalId()).orElseThrow();
            if(old.state()!=ApplyState.INTENT)throw new Rejected(Rejection.OPERATION_TERMINATED);
            writeReceipt(c,new ApplyReceipt(old.applyId(),old.proposal(),ApplyState.APPLYING,DiskState.UNKNOWN,Optional.empty()));return null;
        }).join();
    }
    public void outcome(ApplyRequest request,ApplyReceipt result) {
        store.transaction(c->{
            Identity op=identity(request.operation());
            // A late observed result may be recorded after cancellation, never after root deletion.
            State heldRoot=sessions.state(c,op.projectId(),op.sessionId());
            if(heldRoot.lifecycle()!=Lifecycle.LIVE||heldRoot.operation().isEmpty()||!heldRoot.operation().get().identity().equals(op))throw new CancellationException("Apply operation revoked/deleted");
            if(!application(c,op.sessionId(),request.applyId()).equals(request)||!result.applyId().equals(request.applyId())
                    ||!result.proposal().equals(request.proposal().ref())||result.state()==ApplyState.INTENT||result.state()==ApplyState.APPLYING)
                throw new Rejected(Rejection.INVALID_INPUT);
            ApplyReceipt old=receipt(c,request.proposal().proposalId()).orElseThrow();
            if(old.state()!=ApplyState.APPLYING && !(old.state()==ApplyState.INTENT&&result.state()==ApplyState.FAILED_NOT_APPLIED))
                throw new Rejected(Rejection.OPERATION_TERMINATED);
            writeReceipt(c,result);
            State root=sessions.state(c,op.projectId(),op.sessionId());
            if(result.state()==ApplyState.FAILED_NOT_APPLIED)setReview(c,request.proposal().proposalId(),ReviewState.INVALIDATED);
            boolean unresolved=unresolved(result.state());
            sessions.put(c,new State(root.id(),root.projectId(),root.title(),root.revision()+1,root.lifecycle(),unresolved,
                    root.draft(),root.turns(),Optional.empty(),unresolved?List.of(SessionData.notice("EDIT_OUTCOME_UNKNOWN","代码应用结果待核对，不能自动重放",root.id(),op.operationId())):List.of(),root.titleExplicit()));
            return null;
        }).join();
    }
    /** Startup/close/stop: preserve a visible unknown result, never perform or retry a write. */
    void recover(Connection c,State root) throws SQLException {
        try(var q=c.prepareStatement("DELETE FROM ca_edit_preview WHERE session_id=?")){q.setString(1,root.id());q.executeUpdate();}
        for(var p:artifacts(c,root.id())) {
            ReviewState review=review(c,p.proposalId());
            if(review==ReviewState.STAGED||review==ReviewState.REVIEWABLE)setReview(c,p.proposalId(),ReviewState.INVALIDATED);
            var receipt=receipt(c,p.proposalId());
            if(receipt.isPresent()&&(receipt.get().state()==ApplyState.INTENT||receipt.get().state()==ApplyState.APPLYING))
                writeReceipt(c,new ApplyReceipt(receipt.get().applyId(),p.ref(),ApplyState.OUTCOME_UNKNOWN,DiskState.UNKNOWN,Optional.empty()));
            else if(receipt.isPresent()&&receipt.get().state()==ApplyState.APPLIED_TO_DOCUMENT)
                writeReceipt(c,new ApplyReceipt(receipt.get().applyId(),p.ref(),ApplyState.APPLIED_TO_DOCUMENT,DiskState.UNKNOWN,receipt.get().documentStamp(),DocumentState.UNKNOWN));
        }
    }
    boolean unresolved(Connection c,String session) throws SQLException {
        for(var p:artifacts(c,session))if(receipt(c,p.proposalId()).map(r->unresolved(r.state())).orElse(false))return true;
        return false;
    }
    private static boolean unresolved(ApplyState state){return state==ApplyState.OUTCOME_UNKNOWN||state==ApplyState.RECEIPT_PENDING;}
    public record Fact(String proposalId,long revision,String turnId,String attemptId,ReviewState review,Optional<ApplyReceipt> receipt) {}
    List<Fact> facts(Connection c,State root) throws SQLException {
        List<Fact> result=new ArrayList<>();
        for(var p:artifacts(c,root.id())) {
            var origin=p.snapshot().request().origin();var parent=sessions.react().read(c,identity(origin));
            if(parent.isPresent()&&parent.get().eligible()&&parent.get().effectiveBranchId().equals(p.branchId()))
                result.add(new Fact(p.proposalId(),p.revision(),origin.turnId(),origin.attemptId(),review(c,p.proposalId()),receipt(c,p.proposalId())));
        }
        return List.copyOf(result);
    }
    private ApplyRequest application(Connection c,String session,String applyId) throws SQLException {
        try(var q=c.prepareStatement("SELECT proposal_id,identity_json,preview_json FROM ca_edit_apply WHERE id=? AND session_id=?")){
            q.setString(1,applyId);q.setString(2,session);try(var r=q.executeQuery()){
                if(!r.next())throw new Rejected(Rejection.INVALID_TARGET);
                return new ApplyRequest(applyId,origin(json.readValue(r.getString(2),Identity.class)),artifact(c,session,r.getString(1)),json.readValue(r.getString(3),PreviewToken.class).documentStamp());
            }
        }
    }
    private Optional<CommandResult> command(Connection c,EventMeta meta,String signature) throws SQLException {
        try(var q=c.prepareStatement("SELECT signature,result_json FROM ca_edit_command WHERE session_id=? AND id=?")){
            q.setString(1,meta.sessionId().orElseThrow());q.setString(2,meta.commandId());try(var r=q.executeQuery()){
                if(!r.next())return Optional.empty();if(!signature.equals(r.getString(1)))throw new Rejected(Rejection.INVALID_INPUT);
                return Optional.of(json.readValue(r.getString(2),CommandResult.class));
            }
        }
    }
    private void remember(Connection c,EventMeta meta,String signature,CommandResult result) throws SQLException {
        try(var q=c.prepareStatement("INSERT INTO ca_edit_command VALUES(?,?,?,?)")){
            q.setString(1,meta.sessionId().orElseThrow());q.setString(2,meta.commandId());q.setString(3,signature);q.setString(4,json.writeValueAsString(result));q.executeUpdate();
        }
    }
    private List<EditProposal> artifacts(Connection c,String session) throws SQLException {
        List<EditProposal> result=new ArrayList<>();
        try(var q=c.prepareStatement("SELECT payload FROM ca_edit_artifact WHERE session_id=? ORDER BY id")){
            q.setString(1,session);try(var r=q.executeQuery()){while(r.next())result.add(json.readValue(r.getString(1),EditProposal.class));}
        }
        return result;
    }
    private EditProposal artifact(Connection c,String session,String id) throws SQLException {
        return artifacts(c,session).stream().filter(p->p.proposalId().equals(id)).findFirst().orElseThrow(()->new Rejected(Rejection.INVALID_TARGET));
    }
    private ReviewState review(Connection c,String id) throws SQLException {
        try(var q=c.prepareStatement("SELECT state FROM ca_edit_review WHERE proposal_id=?")){
            q.setString(1,id);try(var r=q.executeQuery()){if(!r.next())throw new SQLException("Missing F2 review");return ReviewState.valueOf(r.getString(1));}
        }
    }
    private void setReview(Connection c,String id,ReviewState state) throws SQLException {
        try(var q=c.prepareStatement("UPDATE ca_edit_review SET state=? WHERE proposal_id=?")){
            q.setString(1,state.name());q.setString(2,id);if(q.executeUpdate()!=1)throw new SQLException("Missing F2 review");
        }
    }
    private Optional<ApplyReceipt> receipt(Connection c,String id) throws SQLException {
        try(var q=c.prepareStatement("SELECT receipt_json FROM ca_edit_apply WHERE proposal_id=?")){
            q.setString(1,id);try(var r=q.executeQuery()){return r.next()?Optional.of(json.readValue(r.getString(1),ApplyReceipt.class)):Optional.empty();}
        }
    }
    private void writeReceipt(Connection c,ApplyReceipt receipt) throws SQLException {
        try(var q=c.prepareStatement("UPDATE ca_edit_apply SET receipt_json=? WHERE id=?")){
            q.setString(1,json.writeValueAsString(receipt));q.setString(2,receipt.applyId());if(q.executeUpdate()!=1)throw new SQLException("Missing application intent");
        }
    }
}
