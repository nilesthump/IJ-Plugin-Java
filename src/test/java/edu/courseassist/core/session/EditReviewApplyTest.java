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
        Runnable changed=()->{};
        public ViewSubscription observeChanges(Runnable listener){changed=listener;return ()->changed=()->{};}
        Runnable closedCallback=()->{};int closes;boolean deferPreview;CompletableFuture<Display> opening=new CompletableFuture<>();
        int opens,writes,applies;boolean defer,validateFail,unknown,inspectFail;Runnable pending;long validatedStamp=7;
        Map<String,ApplyReceipt> observations=new HashMap<>();
        ApplyRequest request;ApplyReceipt observation;CompletableFuture<ApplyReceipt> result;
        public CompletionStage<Display> open(EditProposal proposal){opens++;return deferPreview?opening:CompletableFuture.completedFuture(this);}
        public void whenClosed(Runnable callback){closedCallback=callback;}
        public void enable(PreviewToken token,Consumer<ReviewDecision> decisions,Runnable dismissed){this.token=token;this.decisions=decisions;this.dismissed=dismissed;}
        public void close(){closes++;if(dismissed!=null)dismissed.run();closedCallback.run();}
        public CompletionStage<Long> validate(EditProposal p){return validateFail?CompletableFuture.failedFuture(new EditApplyPort.NotApplied(new EditApplyPort.SourceChanged())):CompletableFuture.completedFuture(validatedStamp);}
        public CompletionStage<ApplyReceipt> apply(ApplyRequest r,WritePermit permit) {
            applies++;request=r;result=new CompletableFuture<>();
            pending=()->{
                if(unknown){result.completeExceptionally(new IllegalStateException("no receipt evidence"));return;}
                boolean changed=permit.commit(()->writes++);
                observation=new ApplyReceipt(r.applyId(),r.proposal().ref(),changed?ApplyState.APPLIED_TO_DOCUMENT:ApplyState.FAILED_NOT_APPLIED,
                        changed?DiskState.UNSAVED:DiskState.UNKNOWN,changed?Optional.of(9L):Optional.empty());
                observations.put(r.applyId(),observation);result.complete(observation);
            };
            if(!defer)pending.run();return result;
        }
        public CompletionStage<Optional<ApplyReceipt>> inspect(ApplyRequest r){return inspectFail?CompletableFuture.failedFuture(new IllegalStateException("historical target renamed")):CompletableFuture.completedFuture(Optional.ofNullable(observation!=null&&observation.applyId().equals(r.applyId())?observation:observations.get(r.applyId())).filter(x->x.state()==ApplyState.APPLIED_TO_DOCUMENT));}
    }
    private EditFoundationTest.Candidate candidate(SessionTestEnvironment e)throws Exception{return new EditFoundationTest().candidate(e);}
    private Host attach(SessionTestEnvironment e,EditFoundationTest.Candidate c)throws Exception {
        c.finish();Host h=new Host();e.sessions.attachEditHost("i",new SessionCoordinator.EditHost(h,h)).toCompletableFuture().get();
        e.sessions.refreshAll().toCompletableFuture().get();
        assertEquals(Outcome.APPLIED,e.dispatch(new OpenEditPreview(e.meta("i",c.session()),c.proposal().ref())).outcome());
        e.sessions.refreshAll().toCompletableFuture().get();assertNotNull(h.token);return h;
    }
    private void settled(SessionTestEnvironment e)throws Exception {e.sessions.refreshAll().toCompletableFuture().get(30,TimeUnit.SECONDS);}
    @Test void streamBurstDoesNotQueueOneDatabaseRefreshPerFrameAheadOfDiff() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.generationTimeout=java.time.Duration.ofMinutes(2);
            var c=candidate(e);c.finish();Host host=new Host();
            e.sessions.attachEditHost("i",new SessionCoordinator.EditHost(host,host)).toCompletableFuture().get();
            String streaming=e.create("i");e.text("i",streaming,"stream");
            var listener=new AtomicReference<edu.courseassist.core.react.ReactData.Listener>();
            e.model.set((request,sink)->{listener.set(sink);return ()->{};});
            e.submit("i",streaming);SessionTestEnvironment.await(()->listener.get()!=null);settled(e);
            var event=new OpenEditPreview(e.meta("i",c.session()),c.proposal().ref());
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var armed=new AtomicBoolean();var deliveries=new AtomicInteger();
            try(var subscription=e.sessions.observe("i",view->{
                if(armed.compareAndSet(true,false)) {
                    entered.countDown();
                    try {if(!release.await(30,TimeUnit.SECONDS))throw new IllegalStateException("refresh gate timed out");}
                    catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}
                }
                deliveries.incrementAndGet();
            })) {
                settled(e);armed.set(true);
                listener.get().frame(new edu.courseassist.core.react.ReactData.Body("start"));
                assertTrue(entered.await(30,TimeUnit.SECONDS));
                try {
                    for(int n=0;n<40;n++)listener.get().frame(new edu.courseassist.core.react.ReactData.Body("x"));
                    // The real engine has persisted the burst while the command observer is blocked.
                    SessionTestEnvironment.await(()->e.repository.history(c.project(),"i",streaming).stream()
                        .flatMap(t->t.attempts().stream()).flatMap(a->a.branches().stream()).flatMap(b->b.steps().stream())
                        .anyMatch(step->step.body().endsWith("x".repeat(40))));
                    int before=deliveries.get();
                    var opened=e.sessions.dispatch(event);release.countDown();
                    assertEquals(Outcome.APPLIED,opened.toCompletableFuture().get(30,TimeUnit.SECONDS).outcome());
                    assertEquals(1,host.opens);
                    assertTrue(deliveries.get()-before<=4,"obsolete refreshes ahead of Diff: "+(deliveries.get()-before));
                } finally {release.countDown();SessionTestEnvironment.finish(listener.get(),"done");}
            }
            e.idle("i",streaming);assertTrue(e.observerErrors.isEmpty());
            assertTrue(e.view("i",streaming).turns().getFirst().attempts().getFirst().branches().stream()
                .flatMap(b->b.steps().stream()).anyMatch(step->step.markdown().endsWith("x".repeat(40)+"done")));
        }
    }
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
            h.changed.run();settled(e);
            assertEquals(DocumentState.UNDONE,c.view().receipt().orElseThrow().documentState());
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));assertEquals(1,h.writes);
        }
    }
    @Test void automaticObservationFailureRecordsUnknownAndRecoversWithoutReplay() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);
            e.dispatch(new ApproveEditProposal(e.meta("i",c.session()),h.token));settled(e);settled(e);
            int calls=e.requests.size();
            h.inspectFail=true;h.changed.run();settled(e);
            assertFalse(e.view("i",c.session()).writesBlocked());
            assertEquals(DocumentState.UNKNOWN,c.view().receipt().orElseThrow().documentState());
            assertEquals(DiskState.UNKNOWN,c.view().receipt().orElseThrow().disk());
            assertTrue(e.view("i",c.session()).notices().stream().anyMatch(n->n.code().equals("EDIT_OBSERVATION_FAILED")));
            e.text("i",c.session(),"next");
            h.inspectFail=false;h.changed.run();settled(e);
            assertFalse(e.view("i",c.session()).writesBlocked());
            assertTrue(e.view("i",c.session()).notices().stream().noneMatch(n->n.code().equals("EDIT_OBSERVATION_FAILED")));
            assertEquals(calls,e.requests.size());assertEquals(1,h.writes);
            e.pauseGeneration.set(true);e.submit("i",c.session());
            h.inspectFail=true;h.changed.run();settled(e);
            assertTrue(e.view("i",c.session()).allowedActions().contains(SessionAction.STOP));
            var operation=e.view("i",c.session()).activeOperation().orElseThrow();
            assertEquals(Outcome.APPLIED,e.dispatch(new StopOperation(e.meta("i",c.session()),operation.operationId())).outcome());
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
            if(drift){assertEquals(ReviewState.INVALIDATED,c.view().review());
                assertTrue(e.view("i",c.session()).notices().stream().anyMatch(n->n.code().equals("EDIT_SOURCE_CHANGED")&&n.severity()==Severity.WARNING));
                assertTrue(e.sessions.failure(c.session()).isEmpty());}
        }
    }
    @Test void changedSourceWhenReopeningPreviewShowsSpecificNoticeWithoutEnablingApproval()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var c=candidate(e);var h=attach(e,c);h.close();settled(e);h.token=null;h.validateFail=true;
            e.dispatch(new OpenEditPreview(e.meta("i",c.session()),c.proposal().ref()));settled(e);
            assertNull(h.token);assertEquals(0,h.applies);
            assertTrue(e.view("i",c.session()).notices().stream().anyMatch(n->n.code().equals("EDIT_SOURCE_CHANGED")));
            assertTrue(e.sessions.failure(c.session()).isEmpty());assertFalse(e.view("i",c.session()).writesBlocked());
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
