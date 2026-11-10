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
    @Test void adjacentCaptureOrderPersistsWithEmptyLegacyMetadataCompatible() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        assertTrue(json.readValue("{\"draftId\":\"d\",\"revision\":0,\"text\":\"old\",\"captures\":[],\"saveState\":\"SAVED\"}",DraftView.class).captureOrder().isEmpty());
        assertTrue(json.readValue("{\"question\":\"old\",\"code\":[]}",edu.courseassist.core.react.ReactData.Input.class).captureOffsets().isEmpty());
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String session=e.create("a");
            e.sessions.beginCapture(SessionTestEnvironment.selection("first","a"));e.sessions.beginCapture(SessionTestEnvironment.selection("second","a"));
            await(()->e.view("a",session).draft().captures().size()==2&&e.view("a",session).draft().captures().stream().allMatch(c->c.state()==CaptureState.READY));
            var d=e.view("a",session).draft();
            e.dispatch(new UpdateDraftText(e.meta("a",session),d.draftId(),d.revision(),"tail",Optional.of(Map.of("first",0,"second",0)),Optional.of(List.of("second","first"))));
            e.sessions.closeProject("a").toCompletableFuture().get();e.open("b","a",false);assertEquals(List.of("second","first"),e.view("b",session).draft().captureOrder());
            e.submit("b",session);await(()->!e.requests.isEmpty());e.idle("b",session);
            var parts=e.view("b",session).turns().getFirst().userMessage().parts();assertEquals("second",((CodePart)parts.get(0)).captureId());assertEquals("first",((CodePart)parts.get(1)).captureId());
        }
    }

    @Test void mixedOrderPersistsAndImplicitContextIsNotRendered() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String session=e.create("a");
            e.sessions.beginCapture(SessionTestEnvironment.selection("first","a"));
            e.sessions.beginCapture(SessionTestEnvironment.selection("second","a"));
            await(()->e.view("a",session).draft().captures().size()==2 && e.view("a",session).draft().captures().stream().allMatch(c->c.state()==CaptureState.READY));
            var draft=e.view("a",session).draft();var offsets=Map.of("first",0,"second",6);
            e.dispatch(new UpdateDraftText(e.meta("a",session),draft.draftId(),draft.revision(),"beforeafter",Optional.of(offsets)));
            e.sessions.closeProject("a").toCompletableFuture().get();e.open("a2","a",false);
            assertEquals(offsets,e.view("a2",session).draft().captureOffsets());
            e.submit("a2",session);await(()->!e.requests.isEmpty());e.idle("a2",session);
            var parts=e.view("a2",session).turns().getFirst().userMessage().parts();
            assertEquals(4,parts.size());assertInstanceOf(CodePart.class,parts.get(0));assertEquals("before",((TextPart)parts.get(1)).text());
            assertEquals(PartKind.SELECTION,((CodePart)parts.get(2)).kind());assertEquals("after",((TextPart)parts.get(3)).text());
            String request=ReactContext.json(e.requests.getFirst());assertTrue(request.contains("CLASS_STRUCTURE"));assertTrue(request.contains("METHOD"));
            e.sessions.closeProject("a2").toCompletableFuture().get();e.open("a3","a",false);
            assertEquals(parts,e.view("a3",session).turns().getFirst().userMessage().parts());
        }
    }

    @Test void materialFailureHasSearchableSafeLogAndRetainsTaskCause() throws Exception {
        var messages = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var logger = java.util.logging.Logger.getLogger(ApplicationCoordinator.class.getName());
        var handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) { messages.add(record.getMessage()); }
            public void flush() {} public void close() {}
        };
        logger.addHandler(handler);
        try (var e = new SessionTestEnvironment(root)) {
            e.configureAndEnable();
            var result = e.app.dispatch(new ImportCoursePdf(e.appMeta(),
                    Path.of("spec/tasks/dev-manager/blackbox-fixtures/course-a-v1.pdf").toAbsolutePath(),
                    e.app.snapshot().corpusBase())).toCompletableFuture().get();
            String id = result.operationId().orElseThrow();
            await(() -> messages.stream().anyMatch(m -> m.contains("F0 material diagnostic " + id)));
            assertInstanceOf(UnsupportedOperationException.class, e.app.failure(id).orElseThrow());
            String log = messages.stream().filter(m -> m.contains(id)).findFirst().orElseThrow();
            assertFalse(log.contains("No PDF adapter in session test"), "Raw exception message must not be logged");
            assertTrue(log.contains("SessionTestEnvironment"));
        } finally { logger.removeHandler(handler); }
    }

    @Test void removalSavesMissingStatusAndReplacementRestoresOnlySelectedSlot() throws Exception {
        try (var e = new SessionTestEnvironment(root)) {
            var p = new ProviderProfile("p", "synthetic", java.net.URI.create("https://example.invalid"), "model", Optional.of(3));
            e.vault.write(CredentialUpdate.Slot.GENERATION, "old-synthetic".toCharArray());
            e.vault.write(CredentialUpdate.Slot.EMBEDDING, "embedding-synthetic".toCharArray());
            for (var action : List.of(CredentialUpdate.Action.REMOVE, CredentialUpdate.Action.REPLACE)) {
                String ref;
                try (var update = new CredentialUpdate(List.of(new CredentialUpdate.Change(CredentialUpdate.Slot.GENERATION,
                        action, action == CredentialUpdate.Action.REMOVE ? new char[0] : "replacement-synthetic".toCharArray())))) {
                    ref = e.app.stage(update).toCompletableFuture().get();
                }
                var result = e.app.dispatch(new UpdateProviderConfig(e.appMeta(), e.app.snapshot().configRevision(),
                        new ProviderConfiguration(p,p), Optional.of(ref))).toCompletableFuture().get();
                assertEquals(Outcome.APPLIED, result.outcome());
                assertEquals(action == CredentialUpdate.Action.REMOVE ? CredentialStatus.MISSING : CredentialStatus.CONFIGURED,
                        e.app.snapshot().generationProfile().orElseThrow().credentialStatus());
                assertEquals(CredentialStatus.CONFIGURED, e.app.snapshot().embeddingProfile().orElseThrow().credentialStatus());
                assertFalse(ReactContext.json(e.app.snapshot()).contains("replacement-synthetic"));
            }
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
