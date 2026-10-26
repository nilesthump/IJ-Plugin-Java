package edu.courseassist.core.session;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import edu.courseassist.contract.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.storage.EditRepository;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EditReviewApplyTest {
    @TempDir Path root;
    static final class Host implements EditPreviewPort,EditApplyPort,EditPreviewPort.Display {
        PreviewToken token;Consumer<ReviewDecision> decisions;Runnable dismissed;
        Runnable closedCallback=()->{};int closes;boolean deferPreview;CompletableFuture<Display> opening=new CompletableFuture<>();
        int opens,writes,applies;boolean defer,validateFail,unknown,inspectFail;Runnable pending;long validatedStamp=7;
        ApplyRequest request;ApplyReceipt observation;CompletableFuture<ApplyReceipt> result;
        public CompletionStage<Display> open(EditProposal proposal){opens++;return deferPreview?opening:CompletableFuture.completedFuture(this);}
        public void whenClosed(Runnable callback){closedCallback=callback;}
        public void enable(PreviewToken token,Consumer<ReviewDecision> decisions,Runnable dismissed){this.token=token;this.decisions=decisions;this.dismissed=dismissed;}
        public void close(){closes++;if(dismissed!=null)dismissed.run();closedCallback.run();}
        public CompletionStage<Long> validate(EditProposal p){return validateFail?CompletableFuture.failedFuture(new IllegalStateException("drift")):CompletableFuture.completedFuture(validatedStamp);}
        public CompletionStage<ApplyReceipt> apply(ApplyRequest r,WritePermit permit) {
            applies++;request=r;result=new CompletableFuture<>();
            pending=()->{
                if(unknown){result.completeExceptionally(new IllegalStateException("no receipt evidence"));return;}
                boolean changed=permit.commit(()->writes++);
                observation=new ApplyReceipt(r.applyId(),r.proposal().ref(),changed?ApplyState.APPLIED_TO_DOCUMENT:ApplyState.FAILED_NOT_APPLIED,
                        changed?DiskState.UNSAVED:DiskState.UNKNOWN,changed?Optional.of(9L):Optional.empty());
                result.complete(observation);
            };
            if(!defer)pending.run();return result;
        }
        public CompletionStage<Optional<ApplyReceipt>> inspect(ApplyRequest r){return inspectFail?CompletableFuture.failedFuture(new IllegalStateException("historical target renamed")):CompletableFuture.completedFuture(Optional.ofNullable(observation).filter(x->x.state()==ApplyState.APPLIED_TO_DOCUMENT));}
    }
    private EditFoundationTest.Candidate candidate(SessionTestEnvironment e)throws Exception{return new EditFoundationTest().candidate(e);}
    private Host attach(SessionTestEnvironment e,EditFoundationTest.Candidate c)throws Exception {
        c.finish();Host h=new Host();e.sessions.attachEditHost("i",new SessionCoordinator.EditHost(h,h)).toCompletableFuture().get();
        e.sessions.refreshAll().toCompletableFuture().get();
        assertEquals(Outcome.APPLIED,e.dispatch(new OpenEditPreview(e.meta("i",c.session()),c.proposal().ref())).outcome());
        e.sessions.refreshAll().toCompletableFuture().get();assertNotNull(h.token);return h;
    }
    private void settled(SessionTestEnvironment e)throws Exception {e.sessions.refreshAll().toCompletableFuture().get(30,TimeUnit.SECONDS);}
    @Test void displayedLocalButtonsPreserveMixedDraftAndMakeZeroNetworkRequests()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);e.text("i",c.session(),"do not consume this draft");
            var draft=e.view("i",c.session()).draft();var turns=e.view("i",c.session()).turns();int calls=e.requests.size(),queries=e.queries.size();
            h.decisions.accept(new ReviewDecision("button-approve",h.token,true));settled(e);settled(e);
            assertEquals(1,h.writes);assertEquals(1,h.applies);
            assertEquals(ApplyState.APPLIED_TO_DOCUMENT,c.view().receipt().orElseThrow().state());
            assertEquals(draft,e.view("i",c.session()).draft());assertEquals(turns,e.view("i",c.session()).turns());
            assertEquals(calls,e.requests.size());assertEquals(queries,e.queries.size());assertTrue(e.view("i",c.session()).activeOperation().isEmpty());
            assertEquals(Outcome.APPLIED,e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token)).outcome());
            assertEquals(1,h.applies);assertEquals(1,h.writes);
            h.observation=new ApplyReceipt(h.request.applyId(),h.request.proposal().ref(),ApplyState.APPLIED_TO_DOCUMENT,DiskState.UNSAVED,Optional.of(10L),DocumentState.UNDONE);
            e.dispatch(new InspectEditApplication(e.meta("i",c.session()),c.proposal().ref()));
            assertEquals(DocumentState.UNDONE,c.view().receipt().orElseThrow().documentState());
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));assertEquals(1,h.writes);
        }
    }
    @Test void rejectAndClosedViewerNeverApplyOrSchedule()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);int calls=e.requests.size();
            h.close();settled(e);
            assertEquals(Outcome.REJECTED,e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token)).outcome());assertEquals(0,h.applies);
            e.dispatch(new OpenEditPreview(e.meta("i",c.session()),c.proposal().ref()));settled(e);
            h.decisions.accept(new ReviewDecision("reject-button",h.token,false));settled(e);
            assertEquals(ReviewState.REJECTED,c.view().review());assertEquals(calls,e.requests.size());assertEquals(0,h.applies);
            assertEquals(Outcome.REJECTED,e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token)).outcome());
        }
    }
    @Test void driftInvalidatesAndIntentFailureCannotWrite()throws Exception {
        for(boolean drift:List.of(true,false))try(var e=new SessionTestEnvironment(root.resolve(""+drift))) {
            var c=candidate(e);var h=attach(e,c);h.validateFail=drift;
            if(!drift)e.store.transaction(db->{db.createStatement().execute("ALTER TABLE ca_edit_apply RENAME TO edit_apply_fault");return null;}).join();
            if(drift)assertEquals(Rejection.STALE_REVISION,e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token)).rejection().orElseThrow());
            else assertThrows(Exception.class,()->e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token)));
            if(!drift)e.store.transaction(db->{db.createStatement().execute("ALTER TABLE edit_apply_fault RENAME TO ca_edit_apply");return null;}).join();
            assertEquals(0,h.applies);assertEquals(0,h.writes);assertTrue(e.repository.states(c.project(),"i").getFirst().operation().isEmpty());
            if(drift)assertEquals(ReviewState.INVALIDATED,c.view().review());
        }
    }
    @Test void cancelAndDeleteBeforeCommitRevokeWritePermit()throws Exception {
        for(boolean delete:List.of(true,false))try(var e=new SessionTestEnvironment(root.resolve(""+delete))) {
            var c=candidate(e);var h=attach(e,c);h.defer=true;
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));
            ViewEvent stop=delete?new DeleteSession(e.meta("i",c.session())):new StopOperation(e.meta("i",c.session()),h.request.applyId());
            var stopped=e.sessions.dispatch(stop);h.pending.run();assertEquals(Outcome.APPLIED,stopped.toCompletableFuture().get(30,TimeUnit.SECONDS).outcome());settled(e);
            assertEquals(0,h.writes);
            if(delete)assertTrue(e.repository.states(c.project(),"i").isEmpty());
            else {assertEquals(ApplyState.FAILED_NOT_APPLIED,c.view().receipt().orElseThrow().state());assertTrue(e.view("i",c.session()).activeOperation().isEmpty());}
        }
    }
    @Test void receiptSaveFailureRetainsActualWriteAndRecoversWithoutReplay()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);h.defer=true;
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));
            e.store.transaction(db->{db.createStatement().execute("ALTER TABLE ca_edit_apply RENAME TO edit_apply_fault");return null;}).join();
            h.pending.run();
            // The callback completes before this barrier; refresh itself cannot read the faulted table.
            assertThrows(Exception.class,()->settled(e));
            e.store.transaction(db->{db.createStatement().execute("ALTER TABLE edit_apply_fault RENAME TO ca_edit_apply");return null;}).join();
            settled(e);assertEquals(1,h.writes);assertTrue(e.view("i",c.session()).writesBlocked());
            assertEquals(Outcome.APPLIED,e.dispatch(new InspectEditApplication(e.meta("i",c.session()),c.proposal().ref())).outcome());
            assertEquals(ApplyState.APPLIED_TO_DOCUMENT,c.view().receipt().orElseThrow().state());assertFalse(e.view("i",c.session()).writesBlocked());
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));assertEquals(1,h.writes);
        }
    }
    @Test void unknownResultBlocksAndNeverReplays()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);h.unknown=true;
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));settled(e);
            assertEquals(ApplyState.OUTCOME_UNKNOWN,c.view().receipt().orElseThrow().state());assertTrue(e.view("i",c.session()).writesBlocked());
            assertEquals(Outcome.APPLIED,e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token)).outcome());assertEquals(1,h.applies);
            assertEquals(Rejection.UNSAVED_STATE,e.dispatch(new RecoverSession(e.meta("i",c.session()))).rejection().orElseThrow());assertEquals(1,h.applies);
        }
    }
    @Test void freshApplyBusyDoesNotQueueAndStampChangeRequiresAnotherDiff()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);e.text("i",c.session(),"new request");e.pauseGeneration.set(true);e.submit("i",c.session());
            assertEquals(Rejection.BUSY,e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token)).rejection().orElseThrow());assertEquals(0,h.applies);
            var active=e.view("i",c.session()).activeOperation().orElseThrow();e.dispatch(new StopOperation(e.meta("i",c.session()),active.operationId()));e.resumeGeneration();e.idle("i",c.session());
            h.validatedStamp=8;
            assertEquals(Rejection.STALE_REVISION,e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token)).rejection().orElseThrow());
            e.dispatch(new OpenEditPreview(e.meta("i",c.session()),c.proposal().ref()));settled(e);
            assertEquals(8,h.token.documentStamp());
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));settled(e);assertEquals(1,h.writes);assertEquals(8,h.request.documentStamp());
        }
    }
    @Test void restartRequiresNewViewerCurrentInstanceAndFreshDocumentStamp()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var original=attach(e,c);var oldToken=original.token;
            e.sessions.closeProject("i").toCompletableFuture().get(30,TimeUnit.SECONDS);e.open("new","directory",false);
            Host host=new Host();host.validatedStamp=42;
            e.sessions.attachEditHost("new",new SessionCoordinator.EditHost(host,host)).toCompletableFuture().get();
            assertEquals(ReviewState.INVALIDATED,e.repository.edits().views(c.project(),"new",c.session()).getFirst().review());assertEquals(0,host.applies);
            assertEquals(Outcome.REJECTED,e.dispatch(new ApproveEditProposal(e.meta("new",c.session()),oldToken)).outcome());
            e.dispatch(new OpenEditPreview(e.meta("new",c.session()),c.proposal().ref()));settled(e);
            assertEquals("new",host.token.instanceId());assertEquals(42,host.token.documentStamp());
            e.dispatch(new ApproveEditProposal(e.meta("new",c.session()),host.token));settled(e);
            assertEquals(1,host.writes);assertEquals("new",host.request.operation().instanceId());assertEquals(42,host.request.documentStamp());
            assertEquals(Outcome.APPLIED,e.dispatch(new ApproveEditProposal(e.meta("new",c.session()),host.token)).outcome());assertEquals(1,host.writes);assertEquals(1,host.applies);
        }
    }
    @Test void cancellationAfterCommitKeepsActualAppliedFact()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);h.defer=true;
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));
            h.pending.run();
            var stopped=e.dispatch(new StopOperation(e.meta("i",c.session()),h.request.applyId()));settled(e);
            assertTrue(stopped.outcome()==Outcome.APPLIED||stopped.rejection().filter(r->r==Rejection.OPERATION_TERMINATED).isPresent());
            assertEquals(1,h.writes);assertEquals(ApplyState.APPLIED_TO_DOCUMENT,c.view().receipt().orElseThrow().state());
        }
    }
    @Test void latePreviewAfterDeleteOrStopCannotRestoreReviewOrDiagnostics()throws Exception {
        for(boolean delete:List.of(true,false))for(boolean fail:List.of(true,false))try(var e=new SessionTestEnvironment(root.resolve(delete+"-"+fail))) {
            var c=candidate(e);c.finish();Host h=new Host();h.deferPreview=true;e.sessions.attachEditHost("i",new SessionCoordinator.EditHost(h,h)).toCompletableFuture().get();
            e.dispatch(new OpenEditPreview(e.meta("i",c.session()),c.proposal().ref()));
            if(delete)e.dispatch(new DeleteSession(e.meta("i",c.session())));else e.sessions.stopAll().toCompletableFuture().get();
            if(fail)h.opening.completeExceptionally(new IllegalStateException("late failure"));else h.opening.complete(h);settled(e);
            assertNull(h.token);if(!fail)assertEquals(1,h.closes);assertTrue(e.sessions.failure(c.session()).isEmpty());
            if(!delete)assertEquals(ReviewState.INVALIDATED,c.view().review());
        }
    }
    @Test void rejectedArchiveViewerClosesOnDeletionWithoutDecision()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);e.dispatch(new RejectEditProposal(e.meta("i",c.session()),h.token));settled(e);
            int before=h.closes;h.token=null;
            e.dispatch(new OpenEditPreview(e.meta("i",c.session()),c.proposal().ref()));settled(e);assertNull(h.token);
            e.dispatch(new DeleteSession(e.meta("i",c.session())));settled(e);assertTrue(h.closes>before);assertEquals(0,h.applies);
        }
    }
    @Test void provenReceiptCanBeReconciledAfterStopReleasedItsSlot()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);h.defer=true;
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));
            e.store.transaction(db->{db.createStatement().execute("ALTER TABLE ca_edit_apply RENAME TO edit_apply_fault");return null;}).join();
            h.pending.run();assertThrows(Exception.class,()->settled(e));
            e.store.transaction(db->{db.createStatement().execute("ALTER TABLE edit_apply_fault RENAME TO ca_edit_apply");return null;}).join();
            e.sessions.stopAll().toCompletableFuture().get();
            assertTrue(e.repository.states(c.project(),"i").getFirst().operation().isEmpty());assertEquals(ApplyState.OUTCOME_UNKNOWN,c.view().receipt().orElseThrow().state());
            assertEquals(Outcome.APPLIED,e.dispatch(new RecoverSession(e.meta("i",c.session()))).outcome());
            assertEquals(ApplyState.APPLIED_TO_DOCUMENT,c.view().receipt().orElseThrow().state());assertEquals(1,h.writes);assertEquals(1,h.applies);assertFalse(e.view("i",c.session()).writesBlocked());
        }
    }
    @Test void terminalHistoricalTargetCannotBlockNewProvenReceiptRecovery()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var old=candidate(e);var h=attach(e,old);
            e.dispatch(new ApproveEditProposal(e.meta("i",old.session()),h.token));settled(e);
            assertEquals(ApplyState.APPLIED_TO_DOCUMENT,old.view().receipt().orElseThrow().state());
            e.text("i",old.session(),"second change");
            var submitted=e.repository.submit(old.project(),e.submitEvent("i",old.session()),e.runtime.snapshot().epoch());
            var parent=submitted.operation().orElseThrow().identity();var input=e.repository.react().load(parent).orElseThrow().input();
            e.repository.react().save(new Snapshot(parent,input,1,1,AttemptStatus.RUNNING,true,"","",List.of(new Branch("branch",BranchStatus.ACTIVE,RetrievalStatus.NO_INDEX,"",List.of(),List.of())),List.of(),""),false);
            var request=new EditRequest("second-request",EditRepository.origin(parent),"instruction",0,"second change",Optional.empty());
            String before=old.proposal().after();
            var snapshot=new EditSnapshot(1,"second-snapshot",request,old.proposal().snapshot().target(),9,before,List.of(new Span(0,before.length())));
            var proposal=new EditProposal(1,"second-proposal",0,snapshot,"branch","tool",before.replace("n=2","n=3"));
            e.repository.edits().stage(proposal);
            new EditFoundationTest.Candidate(e,old.project(),old.session(),parent,proposal,input).finish();
            h.validatedStamp=9;h.defer=true;
            e.dispatch(new OpenEditPreview(e.meta("i",old.session()),proposal.ref()));settled(e);
            e.dispatch(new ApproveEditProposal(e.meta("i",old.session()),h.token));
            e.store.transaction(db->{db.createStatement().execute("ALTER TABLE ca_edit_apply RENAME TO edit_apply_fault");return null;}).join();
            h.pending.run();assertThrows(Exception.class,()->settled(e));
            e.store.transaction(db->{db.createStatement().execute("ALTER TABLE edit_apply_fault RENAME TO ca_edit_apply");return null;}).join();
            h.inspectFail=true;
            assertEquals(Outcome.APPLIED,e.dispatch(new InspectEditApplication(e.meta("i",old.session()),proposal.ref())).outcome());
            assertFalse(e.view("i",old.session()).writesBlocked());assertTrue(e.view("i",old.session()).activeOperation().isEmpty());
            assertEquals(2,h.applies);assertEquals(2,h.writes);
            assertTrue(e.repository.edits().views(old.project(),"i",old.session()).stream().allMatch(v->v.receipt().orElseThrow().state()==ApplyState.APPLIED_TO_DOCUMENT));
        }
    }
}
