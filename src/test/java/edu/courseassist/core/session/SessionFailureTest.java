package edu.courseassist.core.session;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CredentialUpdate;
import edu.courseassist.core.react.*;
import edu.courseassist.core.session.SessionData.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static edu.courseassist.bootstrap.ide.SessionTestEnvironment.*;

class SessionFailureTest {
    @TempDir Path root;
    @Test void failedFinalCommitKeepsDisplayAndBlocksAcrossOffOnUntilExplicitStorageRecovery() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String s=e.create("a");e.text("a",s,"question");
            e.store.transaction(c->{try(var q=c.createStatement()){q.execute("ALTER TABLE ca_react_attempt ADD CONSTRAINT reject_terminal CHECK(terminal=FALSE)");}return null;}).join();
            e.model.set((r,l)->{finish(l,"UNSAVED_TERMINAL_SENTINEL");return ()->{};});var event=e.submitEvent("a",s);var committed=e.dispatch(event);await(()->e.view("a",s).writesBlocked());
            assertEquals(committed.operationId(),e.dispatch(event).operationId());assertEquals(1,e.requests.size());
            assertTrue(ReactContext.json(e.view("a",s)).contains("UNSAVED_TERMINAL_SENTINEL"));
            assertEquals(ContextEligibility.INELIGIBLE,e.view("a",s).turns().getFirst().attempts().getFirst().contextEligibility());
            e.app.dispatch(new DisablePlugin(e.appMeta())).toCompletableFuture().get(10,TimeUnit.SECONDS);assertEquals(WorkState.OFF,e.app.snapshot().workState());
            e.app.dispatch(new EnablePlugin(e.appMeta())).toCompletableFuture().get();assertEquals(Rejection.UNSAVED_STATE,e.dispatch(e.submitEvent("a",s)).rejection().orElseThrow());
            assertEquals(1,e.requests.size());e.store.transaction(c->{try(var q=c.createStatement()){q.execute("ALTER TABLE ca_react_attempt DROP CONSTRAINT reject_terminal");}return null;}).join();
            e.sessions.recoverSession("a",s).toCompletableFuture().get();assertFalse(e.view("a",s).writesBlocked());
            assertTrue(ReactContext.json(e.view("a",s)).contains("UNSAVED_TERMINAL_SENTINEL"));assertEquals(1,e.requests.size());
        }
    }
    @Test void failedSubmissionRollsBackAllInputAndDoesNotScheduleAModel() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String s=e.create("a");e.text("a",s,"keep draft");
            e.store.transaction(c->{try(var q=c.createStatement()){q.execute("ALTER TABLE ca_command ADD CONSTRAINT reject_command CHECK(id='impossible')");}return null;}).join();
            assertThrows(ExecutionException.class,()->e.dispatch(e.submitEvent("a",s)));
            assertTrue(e.view("a",s).writesBlocked());assertTrue(e.view("a",s).turns().isEmpty());assertEquals("keep draft",e.view("a",s).draft().text());assertTrue(e.requests.isEmpty());
            assertEquals(Rejection.UNSAVED_STATE,e.dispatch(e.submitEvent("a",s)).rejection().orElseThrow());
            e.store.transaction(c->{try(var q=c.createStatement()){q.execute("ALTER TABLE ca_command DROP CONSTRAINT reject_command");}return null;}).join();
            e.sessions.recoverSession("a",s).toCompletableFuture().get();e.submit("a",s);e.idle("a",s);assertEquals(1,e.requests.size());
        }
    }
    @Test void deletedFirstCaptureReservationCannotInsertOrInvokeAdapter() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
            e.sessions.open(new Location("C:/a","a"),"a",()->start->{calls.incrementAndGet();return CompletableFuture.completedFuture(group(start));},false).toCompletableFuture().get();
            var pending=e.store.transaction(c->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test barrier timed out");return null;});
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var ticket=e.sessions.beginCapture(selection("first","a"));var completion=e.sessions.captureCompletion("first");var deletion=e.sessions.dispatch(new DeleteSession(e.meta("a",ticket.targetSessionId())));
            release.countDown();pending.get();deletion.toCompletableFuture().get();
            assertInstanceOf(CancellationException.class,assertThrows(CompletionException.class,()->completion.toCompletableFuture().join()).getCause());
            e.sessions.refreshAll().toCompletableFuture().get();assertTrue(e.sessions.snapshot("a").sessions().isEmpty());assertEquals(0,calls.get());
            assertTrue(e.sessions.failure(ticket.targetSessionId()).isEmpty());
            assertInstanceOf(IllegalArgumentException.class,assertThrows(CompletionException.class,()->e.sessions.captureCompletion("first").toCompletableFuture().join()).getCause());
        }
    }
    @Test void deletionFailureStaysFencedAndCanBeContinued() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String s=e.create("a");e.text("a",s,"question");e.submit("a",s);e.idle("a",s);
            e.store.transaction(c->{try(var q=c.createStatement()){
                q.execute("CREATE TABLE delete_blocker(session_id VARCHAR REFERENCES ca_session(id))");q.execute("INSERT INTO delete_blocker VALUES('"+s+"')");}return null;}).join();
            assertThrows(ExecutionException.class,()->e.dispatch(new DeleteSession(e.meta("a",s))));
            assertEquals(Lifecycle.DELETE_FAILED,e.view("a",s).lifecycle());assertEquals(Rejection.SESSION_DELETED,e.dispatch(e.submitEvent("a",s)).rejection().orElseThrow());
            e.store.transaction(c->{try(var q=c.createStatement()){q.execute("DROP TABLE delete_blocker");}return null;}).join();
            assertEquals(Outcome.APPLIED,e.dispatch(new DeleteSession(e.meta("a",s))).outcome());assertTrue(e.sessions.snapshot("a").sessions().isEmpty());
        }
    }
    @Test void credentialCapabilityIsSingleUseAndNeverAViewOrBusinessPayload() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            String sentinel="synthetic-secret-only";char[] value=sentinel.toCharArray();String ref;
            try(var update=new CredentialUpdate(List.of(new CredentialUpdate.Change(CredentialUpdate.Slot.GENERATION,CredentialUpdate.Action.REPLACE,value)))){
                ref=e.app.stage(update).toCompletableFuture().get();}
            Arrays.fill(value,'\0');var profile=new ProviderProfile("p","synthetic",java.net.URI.create("https://example.invalid"),"synthetic",Optional.of(3));
            var command=new UpdateProviderConfig(e.appMeta(),0,new ProviderConfiguration(profile,profile),Optional.of(ref));
            assertEquals(Outcome.APPLIED,e.app.dispatch(command).toCompletableFuture().get().outcome());
            assertEquals(Rejection.INVALID_INPUT,e.app.dispatch(new UpdateProviderConfig(e.appMeta(),1,new ProviderConfiguration(profile,profile),Optional.of(ref))).toCompletableFuture().get().rejection().orElseThrow());
            assertFalse(ReactContext.json(e.app.snapshot()).contains(sentinel));assertFalse(ReactContext.json(e.app.snapshot()).contains(ref));
            String payload=e.store.transaction(c->{try(var q=c.createStatement();var r=q.executeQuery("SELECT payload FROM ca_app")){r.next();return r.getString(1);}}).join();
            assertFalse(payload.contains(sentinel));assertFalse(payload.contains(ref));
        }
    }
    @Test void closeAfterSaveFailurePreservesTailUntilReopenCanPersistIt() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();var binding=e.open("a","a",false);String s=e.create("a");e.text("a",s,"question");
            e.store.transaction(c->{try(var q=c.createStatement()){q.execute("ALTER TABLE ca_react_attempt ADD CONSTRAINT fail_close CHECK(terminal=FALSE)");}return null;}).join();
            e.model.set((r,l)->{finish(l,"DISPLAYED_TAIL");return ()->{};});e.submit("a",s);await(()->e.view("a",s).writesBlocked());
            assertThrows(ExecutionException.class,()->e.sessions.closeProject("a").toCompletableFuture().get());
            e.store.transaction(c->{try(var q=c.createStatement()){q.execute("ALTER TABLE ca_react_attempt DROP CONSTRAINT fail_close");}return null;}).join();
            assertEquals(binding.projectId(),e.open("a2","a",false).projectId());
            assertTrue(ReactContext.json(e.view("a2",s)).contains("DISPLAYED_TAIL"));assertFalse(e.view("a2",s).writesBlocked());assertEquals(1,e.requests.size());
        }
    }
}
