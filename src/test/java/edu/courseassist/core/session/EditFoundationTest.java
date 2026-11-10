package edu.courseassist.core.session;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.core.react.*;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.storage.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class EditFoundationTest {
    @TempDir Path root;
    record Candidate(SessionTestEnvironment e,String project,String session,Identity parent,EditProposal proposal,Input input) {
        Snapshot snapshot(AttemptStatus status,boolean validTool) {
            var trace=new ToolTrace(new Call("tool","proposeCodeEdit","{\"ref\":\"host\"}"),validTool?ToolStatus.SUCCEEDED:ToolStatus.FAILED,"candidate",List.of());
            var step=new Step("final",2,status==AttemptStatus.SUCCEEDED?StepStatus.FINISHED:StepStatus.INCOMPLETE,"parent body","",List.of(trace),"stop",Optional.empty(),List.of());
            return new Snapshot(parent,input,2,2,status,true,status==AttemptStatus.SUCCEEDED?"branch":"",status==AttemptStatus.SUCCEEDED?"final":"",
                    List.of(new Branch("branch",status==AttemptStatus.SUCCEEDED?BranchStatus.COMPLETED:BranchStatus.ACTIVE,RetrievalStatus.NO_INDEX,"",List.of(step),List.of())),List.of(),"");
        }
        void finish(){e.repository.react().save(snapshot(AttemptStatus.SUCCEEDED,true),false);}
        PreviewToken preview(){return e.repository.edits().displayed(project,"i",session,proposal.ref());}
        ProposalView view(){return e.repository.edits().views(project,"i",session).getFirst();}
    }
    Candidate candidate(SessionTestEnvironment e) throws Exception { return candidate(e,true); }
    Candidate candidate(SessionTestEnvironment e,boolean fresh) throws Exception {
        if(fresh)e.configureAndEnable();
        var binding=fresh?e.open("i","directory",false):e.repository.binding(e.sessions.snapshot("i").projectId());
        String session=e.create("i");
        e.text("i",session,"change method");
        var submission=e.repository.submit(binding.projectId(),e.submitEvent("i",session),e.runtime.snapshot().epoch());
        Identity parent=submission.operation().orElseThrow().identity();
        Input input=e.repository.react().load(parent).orElseThrow().input();
        Snapshot running=new Snapshot(parent,input,1,1,AttemptStatus.RUNNING,true,"","",
                List.of(new Branch("branch",BranchStatus.ACTIVE,RetrievalStatus.NO_INDEX,"",List.of(),List.of())),List.of(),"");
        e.repository.react().save(running,false);
        var request=new EditRequest("logical-request",EditRepository.origin(parent),"instruction",0,"change method",Optional.empty());
        String before="class X { int n=1; }\n";
        var snapshot=new EditSnapshot(1,"snapshot",request,new Target("file-id","X.java","","X"),7,before,List.of(new Span(0,before.length())));
        var proposal=new EditProposal(1,"proposal-"+session,0,snapshot,"branch","tool","class X { int n=2; }\n");
        e.repository.edits().stage(proposal);
        return new Candidate(e,binding.projectId(),session,parent,proposal,input);
    }
    @Test void parentCommitPromotionAndLocalEventsNeverLaunchGeneration() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);
            assertEquals(ReviewState.STAGED,c.view().review());
            assertThrows(RuntimeException.class,c::preview);
            c.finish();
            assertTrue(e.repository.states(c.project(),"i").getFirst().operation().isEmpty());
            assertEquals(ReviewState.REVIEWABLE,c.view().review());
            PreviewToken token=c.preview();
            e.sessions.refreshAll().toCompletableFuture().get();
            var before=e.view("i",c.session());int requests=e.requests.size(),queries=e.queries.size();
            assertEquals(Rejection.NOT_SUPPORTED,e.dispatch(new OpenEditPreview(e.meta("i",c.session()),c.proposal().ref())).rejection().orElseThrow());
            assertEquals(Rejection.NOT_SUPPORTED,e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),token)).rejection().orElseThrow());
            var reject=new RejectEditProposal(e.meta("i",c.session()),token);
            CommandResult result=e.dispatch(reject);assertEquals(Outcome.APPLIED,result.outcome());
            assertEquals(result,e.dispatch(reject));assertEquals(ReviewState.REJECTED,c.view().review());
            assertEquals(before.draft(),e.view("i",c.session()).draft());
            assertEquals(before.turns(),e.view("i",c.session()).turns());
            assertEquals(requests,e.requests.size());assertEquals(queries,e.queries.size());
            assertTrue(e.view("i",c.session()).activeOperation().isEmpty());
            assertEquals(c.snapshot(AttemptStatus.SUCCEEDED,true),e.repository.react().load(c.parent()).orElseThrow());
        }
    }
    @Test void parentTerminalRollbackKeepsCandidateStagedAndSlotOwned() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);
            e.store.transaction(db->{db.createStatement().execute("ALTER TABLE ca_edit_review RENAME TO ca_edit_review_fault");return null;}).join();
            assertThrows(RuntimeException.class,c::finish);
            e.store.transaction(db->{db.createStatement().execute("ALTER TABLE ca_edit_review_fault RENAME TO ca_edit_review");return null;}).join();
            assertEquals(AttemptStatus.RUNNING,e.repository.react().load(c.parent()).orElseThrow().status());
            assertTrue(e.repository.states(c.project(),"i").getFirst().operation().isPresent());
            assertEquals(ReviewState.STAGED,c.view().review());assertThrows(RuntimeException.class,c::preview);
            c.finish();assertEquals(ReviewState.REVIEWABLE,c.view().review());
        }
    }
    @Test void stoppedOrUnprovenToolCannotBecomeReviewable() throws Exception {
        for(boolean stopped:List.of(true,false))try(var e=new SessionTestEnvironment(root.resolve(""+stopped))) {
            Candidate c=candidate(e);
            e.repository.react().save(c.snapshot(stopped?AttemptStatus.STOPPED:AttemptStatus.SUCCEEDED,false),stopped);
            assertEquals(ReviewState.INVALIDATED,c.view().review());assertThrows(RuntimeException.class,c::preview);
            assertFalse(e.repository.contextSource(c.project(),"i",c.session(),e.repository.history(c.project(),"i",c.session())).facts().toString().contains("class X"));
        }
    }
    @Test void immutableArtifactsAndDisplayIdentityRejectForgedVersions() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);
            assertEquals(c.proposal(),e.repository.edits().stage(c.proposal()));
            var changed=new EditProposal(1,"other",0,c.proposal().snapshot(),"branch","tool","changed");
            assertThrows(RuntimeException.class,()->e.repository.edits().stage(changed));
            c.finish();PreviewToken token=c.preview();
            var forged=new PreviewToken(token.tokenId(),new ProposalRef(c.proposal().proposalId(),1,token.proposal().beforeHash(),token.proposal().afterHash()),"i");
            assertThrows(RuntimeException.class,()->e.repository.edits().reject(c.project(),e.meta("i",c.session()),forged));
            var b=e.open("other","other-directory",true);
            assertThrows(RuntimeException.class,()->e.repository.edits().reject(b.projectId(),e.meta("other",c.session()),token));
            assertThrows(RuntimeException.class,()->e.repository.edits().reject(c.project(),e.meta("old-instance",c.session()),token));
            assertEquals(ReviewState.REVIEWABLE,c.view().review());
        }
    }
    @Test void approveIntentDedupHasOwnSlotAndOutcomeNeverRewritesParent() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);c.finish();var token=c.preview();var meta=e.meta("i",c.session());
            ApplyRequest apply=e.repository.edits().beginApply(c.project(),meta,token,e.runtime.snapshot().epoch());
            assertNotEquals(c.parent().operationId(),apply.applyId());
            assertEquals(OperationKind.APPLY,e.repository.states(c.project(),"i").getFirst().operation().orElseThrow().kind());
            assertEquals(apply,e.repository.edits().beginApply(c.project(),meta,token,e.runtime.snapshot().epoch()));
            assertEquals(apply,e.repository.edits().beginApply(c.project(),e.meta("i",c.session()),token,e.runtime.snapshot().epoch()));
            assertThrows(RuntimeException.class,()->e.repository.finishCompact(EditRepository.identity(apply.operation())));
            e.repository.edits().applying(apply);
            e.repository.edits().outcome(apply,new ApplyReceipt(apply.applyId(),apply.proposal().ref(),ApplyState.APPLIED_TO_DOCUMENT,DiskState.UNSAVED,Optional.of(8L)));
            assertEquals(ApplyState.APPLIED_TO_DOCUMENT,c.view().receipt().orElseThrow().state());
            assertTrue(e.repository.states(c.project(),"i").getFirst().operation().isEmpty());
            assertEquals(apply,e.repository.edits().beginApply(c.project(),meta,token,e.runtime.snapshot().epoch()));
            assertEquals(c.snapshot(AttemptStatus.SUCCEEDED,true),e.repository.react().load(c.parent()).orElseThrow());
            assertTrue(e.requests.isEmpty()); // Receipt is a synthetic storage fixture, not an actual IDE edit.
        }
    }
    @Test void unknownApplyRemainsBlockedThroughStopCloseAndOrdinaryRecovery() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);c.finish();var token=c.preview();
            var request=e.repository.edits().beginApply(c.project(),e.meta("i",c.session()),token,e.runtime.snapshot().epoch());
            e.repository.edits().applying(request);
            e.sessions.stopAll().toCompletableFuture().get();
            assertEquals(ApplyState.OUTCOME_UNKNOWN,c.view().receipt().orElseThrow().state());
            var replayMeta=e.meta("i",c.session());
            assertEquals(request,e.repository.edits().beginApply(c.project(),replayMeta,token,e.runtime.snapshot().epoch()));
            assertEquals(request,e.repository.edits().beginApply(c.project(),replayMeta,token,e.runtime.snapshot().epoch()));
            var changedToken=new PreviewToken("different-token",token.proposal(),"i");
            assertThrows(RuntimeException.class,()->e.repository.edits().beginApply(c.project(),replayMeta,changedToken,e.runtime.snapshot().epoch()));
            assertTrue(e.repository.states(c.project(),"i").getFirst().operation().isEmpty());
            assertTrue(e.repository.states(c.project(),"i").getFirst().blocked());
            assertThrows(RuntimeException.class,()->e.repository.recoverSession(c.project(),"i",c.session(),Optional.empty()));
            e.sessions.closeProject("i").toCompletableFuture().get();e.open("new","directory",false);
            assertTrue(e.view("new",c.session()).writesBlocked());
            assertThrows(RuntimeException.class,()->e.repository.edits().beginApply(c.project(),e.meta("new",c.session()),token,e.runtime.snapshot().epoch()));
            assertEquals(Rejection.UNSAVED_STATE,e.dispatch(e.submitEvent("new",c.session())).rejection().orElseThrow());
            assertThrows(RuntimeException.class,()->e.repository.edits().outcome(request,new ApplyReceipt(request.applyId(),request.proposal().ref(),ApplyState.FAILED_NOT_APPLIED,DiskState.UNKNOWN,Optional.empty())));
        }
    }
    @Test void deletionCleansAllF2DataAndRejectsLateWrites() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);c.finish();var token=c.preview();
            var apply=e.repository.edits().beginApply(c.project(),e.meta("i",c.session()),token,e.runtime.snapshot().epoch());
            e.dispatch(new DeleteSession(e.meta("i",c.session())));
            for(String table:List.of("ca_edit_artifact","ca_edit_review","ca_edit_preview","ca_edit_apply","ca_edit_command")) {
                long count=e.store.transaction(db->{try(var q=db.createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}).join();
                assertEquals(0,count,table);
            }
            assertThrows(RuntimeException.class,()->e.repository.edits().stage(c.proposal()));
            assertThrows(RuntimeException.class,()->e.repository.edits().applying(apply));
        }
    }
    @Test void factsInvalidateCheckpointAndAreCapturedBeforeSummary() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);c.finish();PreviewToken token=c.preview();
            var history=e.repository.history(c.project(),"i",c.session());
            var before=e.repository.contextSource(c.project(),"i",c.session(),history);
            assertFalse(ReactContext.json(before.facts()).contains("class X"));
            assertTrue(ReactContext.json(before.facts()).contains("REVIEWABLE"));
            var op=e.repository.submit(c.project(),new ManualCompact(e.meta("i",c.session())),e.runtime.snapshot().epoch()).operation().orElseThrow().identity();
            AtomicInteger calls=new AtomicInteger();
            Model model=(request,listener)->{
                calls.incrementAndGet();assertTrue(ReactContext.json(request).contains("REVIEWABLE"));
                e.repository.edits().reject(c.project(),e.meta("i",c.session()),token);
                SessionTestEnvironment.finish(listener,"stale pending summary");return ()->{};
            };
            var planner=new ContextPlanner(e.repository,model,ReactContext.utf8Budget(),
                    new ContextPlanner.Settings(100000,90000,1000,0,8000,Duration.ofSeconds(5),"synthetic"),ErrorPolicy.DEVELOPMENT,t->{});
            assertThrows(RuntimeException.class,()->planner.manual(op,history,()->true));
            assertEquals(1,calls.get());assertTrue(e.repository.checkpoint(c.project(),"i",c.session()).isEmpty());
            assertNotEquals(before.fingerprint(),e.repository.contextSource(c.project(),"i",c.session(),history).fingerprint());
            assertTrue(ReactContext.json(e.repository.contextSource(c.project(),"i",c.session(),history).facts()).contains("REJECTED"));
            e.repository.finishCompact(op);
        }
    }
    @Test void projectionDropsStaleSummaryWhenReviewChangesAfterCheckpointRead() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);c.finish();var token=c.preview();
            var history=e.repository.history(c.project(),"i",c.session());
            var before=e.repository.contextSource(c.project(),"i",c.session(),history);
            var cp=new SessionData.Checkpoint("checkpoint",c.session(),Optional.empty(),1,ReactContext.POLICY,
                    before.fingerprint(),"STALE_PENDING_SUMMARY","synthetic",Optional.empty());
            e.repository.edits().reject(c.project(),e.meta("i",c.session()),token);
            var after=e.repository.contextSource(c.project(),"i",c.session(),history);
            var result=ContextPlanner.project(c.parent(),history,Optional.of(cp),after);
            assertFalse(ReactContext.json(result).contains("STALE_PENDING_SUMMARY"));
            assertTrue(ReactContext.json(result).contains("REJECTED"));
            assertFalse(ReactContext.json(result).contains("class X"));
        }
    }
    @Test void oldInputAndOldDatabaseMigrateWithoutChangingOriginalText() throws Exception {
        var json=JsonMapper.builder().build();
        Input input=json.readValue("{\"question\":\"  原文\\n\",\"code\":[]}",Input.class);
        assertEquals("  原文\n",input.question());assertTrue(input.captureOffsets().isEmpty());assertTrue(input.captureOrder().isEmpty());
        try(var e=new SessionTestEnvironment(root)) {
            var binding=e.open("i","directory",false);String session=e.create("i");e.text("i",session,"  untouched\n");
            e.store.transaction(db->{try(var q=db.createStatement()){for(String table:List.of("ca_edit_command","ca_edit_apply","ca_edit_preview","ca_edit_review","ca_edit_artifact","ca_edit_meta"))q.execute("DROP TABLE "+table);}return null;}).join();
            e.repository.initialize();
            assertEquals("  untouched\n",e.repository.states(binding.projectId(),"i").getFirst().draft().text());
            assertTrue(e.repository.edits().views(binding.projectId(),"i",session).isEmpty());
            e.store.transaction(db->{db.createStatement().execute("UPDATE ca_edit_meta SET schema_version=99");return null;}).join();
            assertThrows(RuntimeException.class,e.repository::initialize);
            assertEquals("  untouched\n",e.repository.states(binding.projectId(),"i").getFirst().draft().text());
        }
    }
    @Test void recoveringOneApplyDoesNotInvalidateAnotherSessionsPreview() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate first=candidate(e);first.finish();var token=first.preview();
            e.repository.edits().beginApply(first.project(),e.meta("i",first.session()),token,e.runtime.snapshot().epoch());
            Candidate other=candidate(e,false);other.finish();var otherToken=other.preview();
            assertThrows(RuntimeException.class,()->e.repository.recoverSession(first.project(),"i",first.session(),Optional.empty()));
            assertEquals(ReviewState.REVIEWABLE,other.view().review());
            assertEquals(Outcome.APPLIED,e.repository.edits().reject(other.project(),e.meta("i",other.session()),otherToken).outcome());
            assertEquals(ApplyState.OUTCOME_UNKNOWN,first.view().receipt().orElseThrow().state());
        }
    }
    @Test void actualReopenPreservesImmutableSourceButNeverRestoresApproval() throws Exception {
        String project,session;EditProposal proposal;
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);c.finish();c.preview();project=c.project();session=c.session();proposal=c.proposal();
        }
        try(var e=new SessionTestEnvironment(root)) {
            e.open("reopened","directory",false);
            assertEquals(proposal,e.repository.edits().load(project,"reopened",session,proposal.ref()));
            assertEquals(ReviewState.INVALIDATED,e.repository.edits().views(project,"reopened",session).getFirst().review());
            assertThrows(RuntimeException.class,()->e.repository.edits().displayed(project,"reopened",session,proposal.ref()));
            assertTrue(e.requests.isEmpty());assertTrue(e.queries.isEmpty());
        }
    }
    @Test void formatAndOffsetsFailExplicitlyWithoutNormalizingText() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            Candidate c=candidate(e);EditSnapshot snapshot=c.proposal().snapshot();
            assertThrows(IllegalArgumentException.class,()->new EditSnapshot(99,"id",snapshot.request(),snapshot.target(),7,snapshot.before(),snapshot.allowedRanges()));
            assertThrows(IllegalArgumentException.class,()->new EditSnapshot(1,"id",snapshot.request(),snapshot.target(),7,snapshot.before(),List.of(new Span(0,2),new Span(1,3))));
            assertNotEquals(edu.courseassist.contract.EditContracts.hash("\ud800"),edu.courseassist.contract.EditContracts.hash("?"));
        }
    }
}
