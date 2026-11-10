package edu.courseassist.core.session;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import edu.courseassist.contract.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CaptureContracts.*;
import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.core.react.*;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.core.session.SessionData.*;
import edu.courseassist.storage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static edu.courseassist.bootstrap.ide.SessionTestEnvironment.*;

class SessionCoordinatorTest {
    @TempDir Path root;
    @Test void emptyForegroundSubmitsOnceAndTitlesSurviveRenameAndLaterTurns() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();var binding=e.open("i","directory",false);
            var send=new SubmitNewSession(e.meta("i",null),"  第一段用户消息\n说明问题  ");
            assertEquals(Outcome.APPLIED,e.dispatch(send).outcome());
            String id=e.sessions.snapshot("i").activeSessionId().orElseThrow();e.idle("i",id);
            assertEquals("第一段用户消息 说明问题",e.view("i",id).title());
            assertEquals(1,e.view("i",id).turns().size());
            assertEquals(send.text(),e.repository.history(binding.projectId(),"i",id).getFirst().input().question());
            assertEquals(Outcome.REJECTED,e.dispatch(send).outcome());
            assertEquals(1,e.requests.size());assertEquals(1,e.sessions.snapshot("i").sessions().size());
            // Even a manual name resembling an old generated title remains explicit.
            assertEquals(Outcome.APPLIED,e.dispatch(new RenameSession(e.meta("i",id),"新会话 1")).outcome());
            e.text("i",id,"第二段不能覆盖名称");e.submit("i",id);e.idle("i",id);
            assertEquals("新会话 1",e.view("i",id).title());
            assertTrue(e.repository.states(binding.projectId(),"i").getFirst().titleExplicit());
            e.dispatch(new CloseSessionTab(e.meta("i",id)));
            assertTrue(e.sessions.snapshot("i").activeSessionId().isEmpty());
            e.dispatch(new SubmitNewSession(e.meta("i",null),"另一个话题"));
            String second=e.sessions.snapshot("i").activeSessionId().orElseThrow();e.idle("i",second);
            assertNotEquals(id,second);assertEquals(2,e.sessions.snapshot("i").sessions().size());
            e.dispatch(new OpenSessionTab(e.meta("i",id)));assertEquals("新会话 1",e.view("i",id).title());
        }
    }
    @Test void legacyTitleFieldAndBlockedRenameSurviveDatabaseReopen() throws Exception {
        String id;
        try(var e=new SessionTestEnvironment(root)) {
            var binding=e.open("i","directory",false);id=e.create("i");
            String session=id;
            e.store.transaction(db->{
                try(var query=db.prepareStatement("SELECT payload FROM ca_session WHERE id=?")) {
                    query.setString(1,session);try(var rows=query.executeQuery()) {assertTrue(rows.next());
                        String payload=rows.getString(1);assertTrue(payload.contains("\"titleExplicit\":false"));
                        try(var update=db.prepareStatement("UPDATE ca_session SET payload=? WHERE id=?")) {
                            update.setString(1,payload.replace(",\"titleExplicit\":false",""));update.setString(2,session);update.executeUpdate();
                        }
                    }
                }return null;
            }).join();
            var legacy=e.repository.states(binding.projectId(),"i").getFirst();assertFalse(legacy.titleExplicit());
            e.repository.edit(binding.projectId(),"i",id,s->new State(s.id(),s.projectId(),s.title(),s.revision()+1,s.lifecycle(),true,s.draft(),s.turns(),s.operation(),s.notices(),s.titleExplicit()));
            e.sessions.refreshAll().toCompletableFuture().get();
            assertEquals(Outcome.APPLIED,e.dispatch(new RenameSession(e.meta("i",id),"手动名称保留")).outcome());
            assertTrue(e.repository.states(binding.projectId(),"i").getFirst().blocked());
            assertEquals(legacy.draft(),e.view("i",id).draft());
            e.sessions.closeProject("i").toCompletableFuture().get();
        }
        try(var e=new SessionTestEnvironment(root)) {
            var binding=e.open("j","directory",false);
            assertEquals("手动名称保留",e.view("j",id).title());
            assertTrue(e.repository.states(binding.projectId(),"j").getFirst().titleExplicit());
        }
    }
    @Test void emptyForegroundWhileDisabledDoesNotCreateOrLoseSessionState() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.open("i","directory",false);
            var result=e.dispatch(new SubmitNewSession(e.meta("i",null),"keep locally"));
            assertEquals(Rejection.PLUGIN_OFF,result.rejection().orElseThrow());
            assertTrue(e.sessions.snapshot("i").sessions().isEmpty());assertTrue(e.requests.isEmpty());
        }
    }

    @Test void completedAttemptDoesNotAdvertiseRetry() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String session=e.create("a");
            e.model.set((r,l)->{finish(l,"completed");return ()->{};});
            e.text("a",session,"question");e.submit("a",session);e.idle("a",session);
            assertFalse(e.view("a",session).allowedActions().contains(SessionAction.RETRY));
        }
    }
    @Test void removedCitationShowsNoticeAndKeepsChatWritableAfterReopen() throws Exception {
        Citation citation; String session;
        try(var e=new SessionTestEnvironment(root, request -> new edu.courseassist.contract.PdfContracts.PdfReadResult(request,1,
                List.of(new edu.courseassist.contract.PdfContracts.PdfPage(1,"synthetic course",edu.courseassist.contract.PdfContracts.PageTextStatus.TEXT,Optional.empty()))))) {
            e.configureAndEnable(); e.open("a","a",false); session=e.create("a");
            var resources=e.app.resources();
            var imported=e.courses.importPdf(Files.writeString(root.resolve("source.pdf"),"synthetic bytes"),"source",e.courses.snapshot().get().base(),resources.corpus(),resources.embeddings(),()->true);
            imported.completion().toCompletableFuture().get(); imported.settled().toCompletableFuture().get();
            String chunk;
            try(var lease=e.courses.openAttempt()) { chunk=lease.search("course",resources.embeddings(),resources.search(),()->true).get().getFirst().chunkId(); }
            e.model.set((request,listener)->{finish(listener,"preserved answer [[course:"+chunk+"]]");return ()->{};});
            e.text("a",session,"question");e.submit("a",session);e.idle("a",session);
            citation=e.repository.history(e.sessions.snapshot("a").projectId(),"a",session).getFirst().attempts().getFirst().citations().getFirst();
            var snapshot=e.courses.snapshot().get();var material=snapshot.materials().getFirst();
            e.courses.remove(snapshot.base(),new MaterialTarget(material.documentId(),material.sourceVersionId(),material.materialRevision()),()->true).completion().toCompletableFuture().get();
            e.sessions.citationOpener(c->e.courses.sourcePath(c.sourceVersionId()).join());
            assertEquals(Outcome.APPLIED,e.dispatch(new OpenCitation(e.meta("a",session),citation)).outcome());
            assertEquals("资料已删除",e.sessions.snapshot("a").notices().getFirst().message());
            assertFalse(e.view("a",session).writesBlocked());assertEquals(1,e.view("a",session).turns().size());
            e.model.set((request,listener)->{finish(listener,"next answer");return ()->{};});
            e.text("a",session,"next question");e.submit("a",session);e.idle("a",session);
            assertFalse(e.view("a",session).writesBlocked());assertEquals(2,e.view("a",session).turns().size());
        }
        try(var e=new SessionTestEnvironment(root)) {
            e.open("b","a",false);e.sessions.citationOpener(c->e.courses.sourcePath(c.sourceVersionId()).join());
            e.dispatch(new OpenCitation(e.meta("b",session),citation));
            assertEquals("资料已删除",e.sessions.snapshot("b").notices().getFirst().message());
            assertEquals(2,e.view("b",session).turns().size());
        }
    }
    @Test void dedupPrecedesBusyAndRevisionAndNewDraftSurvives() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String session=e.create("a");e.text("a",session,"original");
            AtomicReference<Listener> listener=new AtomicReference<>();e.model.set((r,l)->{listener.set(l);return ()->{};});
            // Hold the reserved operation before model startup: BUSY/dedup assertions must
            // not race the synthetic model's real elapsed-time limit on slow runners.
            e.pauseGeneration.set(true);
            SubmitDraft event=e.submitEvent("a",session);CommandResult first=e.dispatch(event);
            assertEquals(Outcome.APPLIED,first.outcome());
            e.text("a",session,"next draft");CommandResult duplicate=e.dispatch(event);
            assertEquals(first.operationId(),duplicate.operationId());assertEquals(0,e.requests.size());
            assertEquals(Rejection.BUSY,e.dispatch(e.submitEvent("a",session)).rejection().orElseThrow());
            assertEquals(1,e.view("a",session).turns().size());assertEquals("next draft",e.view("a",session).draft().text());
            e.resumeGeneration();await(()->listener.get()!=null);assertEquals(1,e.requests.size());
            finish(listener.get(),"answer");e.idle("a",session);
            assertEquals("next draft",e.view("a",session).draft().text());assertEquals(ContextEligibility.ELIGIBLE_DURABLE_SUCCESS,e.view("a",session).turns().getFirst().attempts().getFirst().contextEligibility());
            assertEquals(Rejection.STALE_REVISION,e.dispatch(new SubmitDraft(e.meta("a",session),event.draftId(),event.expectedDraftRevision())).rejection().orElseThrow());
        }
    }
    @Test void captureUsesOriginalTargetAndMergesOnlyItsGroup() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();CompletableFuture<CapturedCodeGroup> captured=new CompletableFuture<>();AtomicInteger calls=new AtomicInteger();
            e.sessions.open(new Location("C:/a","a"),"a",()->start->{calls.incrementAndGet();return captured;},false).toCompletableFuture().get();
            String a=e.create("a");SelectionStart start=selection("capture","a");CaptureTicket ticket=e.sessions.beginCapture(start);
            await(()->calls.get()==1);String b=e.create("a");e.text("a",a,"edited during capture");captured.complete(group(start));
            e.sessions.captureCompletion("capture").toCompletableFuture().get(10,TimeUnit.SECONDS);e.sessions.refreshAll().toCompletableFuture().get();
            assertEquals(a,ticket.targetSessionId());assertEquals(CaptureState.READY,e.view("a",a).draft().captures().getFirst().state());
            assertEquals("edited during capture",e.view("a",a).draft().text());assertTrue(e.view("a",b).draft().captures().isEmpty());
            e.submit("a",a);e.idle("a",a);var turn=e.view("a",a).turns().getFirst();assertEquals(2,turn.userMessage().parts().size());
            assertEquals(PartKind.SELECTION,((CodePart)turn.userMessage().parts().getLast()).kind());
            assertTrue(e.requests.getFirst().messages().stream().anyMatch(m->m.content().contains("CLASS_STRUCTURE")));
        }
    }
    @Test void removedCaptureCannotReturnOrBlockRemainingDraft() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();CompletableFuture<CapturedCodeGroup> result=new CompletableFuture<>();
            e.sessions.open(new Location("C:/a","a"),"a",()->s->result,false).toCompletableFuture().get();String s=e.create("a");
            SelectionStart start=selection("c","a");e.sessions.beginCapture(start);await(()->!e.view("a",s).draft().captures().isEmpty());
            assertEquals(Rejection.CAPTURE_NOT_READY,e.dispatch(e.submitEvent("a",s)).rejection().orElseThrow());
            var d=e.view("a",s).draft();e.dispatch(new RemoveDraftCapture(e.meta("a",s),d.draftId(),d.revision(),"c"));
            result.complete(group(start));e.text("a",s,"only this");e.submit("a",s);e.idle("a",s);
            assertTrue(e.view("a",s).draft().captures().isEmpty());assertEquals(1,e.view("a",s).turns().getFirst().userMessage().parts().size());
        }
    }
    @Test void closeTabKeepsOneSubscriptionAndDeleteClearsAllPrivateRows() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();var binding=e.open("a","a",false);String s=e.create("a");String other=e.create("a");e.text("a",s,"question");
            AtomicReference<Listener> listener=new AtomicReference<>();e.model.set((r,l)->{listener.set(l);return ()->{};});e.submit("a",s);await(()->listener.get()!=null);
            listener.get().frame(new Body("partial sentinel"));e.dispatch(new CloseSessionTab(e.meta("a",s)));e.dispatch(new OpenSessionTab(e.meta("a",s)));
            assertEquals(1,e.requests.size());assertTrue(e.view("a",s).activeOperation().isPresent());
            var identity=e.repository.history(binding.projectId(),"a",s).getFirst().attempts().getFirst().identity();
            e.dispatch(new DeleteSession(e.meta("a",s)));finish(listener.get(),"late sentinel");
            e.sessions.refreshAll().toCompletableFuture().get();assertEquals(1,e.sessions.snapshot("a").sessions().size());assertEquals(other,e.sessions.snapshot("a").sessions().getFirst().sessionId());
            assertThrows(CancellationException.class,()->e.repository.react().load(identity));
            long privateRows=e.store.transaction(c->{long count=0;for(String table:List.of("ca_session","ca_command","ca_checkpoint","ca_react_attempt"))try(var q=c.createStatement();var r=q.executeQuery("SELECT COUNT(*) FROM "+table+" WHERE "+(table.equals("ca_session")?"id":"session_id")+"='"+s+"'")){r.next();count+=r.getLong(1);}return count;}).join();
            assertEquals(0,privateRows);assertTrue(e.courses.snapshot().join().materials().isEmpty());
        }
    }
    @Test void interruptedOutputRemainsVisibleButRetryAndNextInputExcludeIt() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String s=e.create("a");e.text("a",s,"original user");
            AtomicReference<Listener> listener=new AtomicReference<>();e.model.set((r,l)->{listener.set(l);return ()->{};});e.submit("a",s);await(()->listener.get()!=null);
            listener.get().frame(new Body("INTERRUPTED_SENTINEL"));await(()->e.view("a",s).turns().getFirst().attempts().getFirst().branches().stream().anyMatch(b->b.steps().stream().anyMatch(st->st.markdown().contains("INTERRUPTED_SENTINEL"))));
            String op=e.view("a",s).activeOperation().orElseThrow().operationId();e.dispatch(new StopOperation(e.meta("a",s),op));e.idle("a",s);
            var turn=e.view("a",s).turns().getFirst();e.model.set((r,l)->{finish(l,"new success");return ()->{};});
            e.dispatch(new RetryAttempt(e.meta("a",s),turn.turnId(),turn.attempts().getFirst().attemptId()));e.idle("a",s);
            assertEquals(1,e.view("a",s).turns().size());assertEquals(2,e.view("a",s).turns().getFirst().attempts().size());
            assertFalse(ReactContext.json(e.requests.getLast()).contains("INTERRUPTED_SENTINEL"));
            assertTrue(e.view("a",s).turns().getFirst().attempts().getFirst().branches().getFirst().steps().getFirst().markdown().contains("INTERRUPTED_SENTINEL"));
        }
    }
    @Test void projectsAreIsolatedAndMovePreservesCopyDoesNotInherit() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();var a=e.open("a","directory-identity",false);String s=e.create("a");e.text("a",s,"private");
            var b=e.open("b","copy-identity",true);assertNotEquals(a.projectId(),b.projectId());assertTrue(e.sessions.snapshot("b").sessions().isEmpty());
            var stolen=new UpdateDraftText(e.meta("b",s),e.view("a",s).draft().draftId(),0,"stolen");assertEquals(Rejection.SESSION_DELETED,e.dispatch(stolen).rejection().orElseThrow());
            e.sessions.closeProject("a").toCompletableFuture().get();
            var moved=e.sessions.open(new Location("C:/moved","directory-identity"),"a2",()->start->CompletableFuture.completedFuture(group(start)),false).toCompletableFuture().get();
            assertEquals(a.projectId(),moved.projectId());assertEquals("private",e.view("a2",s).draft().text());
            assertEquals(Rejection.PROJECT_CLOSED,e.dispatch(new CloseSessionTab(e.meta("a",s))).rejection().orElseThrow());
        }
    }
    @Test void explicitAssociationRequiresEmptyUnoccupiedTargetAndExactBindingRevision() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            var a=e.open("a","a",false);String s=e.create("a");e.sessions.closeProject("a").toCompletableFuture().get();
            var source=e.repository.binding(a.projectId());var target=e.open("b","b",true);
            var moved=e.sessions.associate("b",a.projectId(),source.revision(),target.revision()).toCompletableFuture().get();
            assertEquals(source.projectId(),moved.projectId());assertEquals(s,e.sessions.snapshot("b").sessions().getFirst().sessionId());
            assertEquals(1,e.repository.bindings().size());
        }
    }
    @Test void restartRestoresOriginalCodeDraftTabsAndInterruptsWithoutNetwork() throws Exception {
        String project,session,draft;
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();project=e.open("a","a",false).projectId();session=e.create("a");e.text("a",session,"pending raw ```\n");
            e.sessions.beginCapture(selection("code","a"));e.sessions.captureCompletion("code").toCompletableFuture().get();e.sessions.refreshAll().toCompletableFuture().get();draft=e.view("a",session).draft().draftId();
        }
        try(var e=new SessionTestEnvironment(root)) {
            assertEquals(WorkState.OFF,e.app.snapshot().workState());assertTrue(e.requests.isEmpty());var reopened=e.open("a2","a",false);
            assertEquals(project,reopened.projectId());assertEquals(session,e.sessions.snapshot("a2").activeSessionId().orElseThrow());
            assertEquals(draft,e.view("a2",session).draft().draftId());assertEquals("pending raw ```\n",e.view("a2",session).draft().text());
            assertEquals("int x = 1;",e.view("a2",session).draft().captures().getFirst().captured().orElseThrow().start().selectedText());
        }
    }
    @Test void appOffGatesNetworkAndConfigChangesRequireOff() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.open("a","a",false);String s=e.create("a");e.text("a",s,"question");assertEquals(Rejection.PLUGIN_OFF,e.dispatch(e.submitEvent("a",s)).rejection().orElseThrow());
            e.configureAndEnable();var config=e.app.snapshot();var profile=config.generationProfile().orElseThrow().profile();
            assertEquals(Rejection.CONFIG_REQUIRES_OFF,e.app.dispatch(new UpdateProviderConfig(e.appMeta(),config.configRevision(),new ProviderConfiguration(profile,profile),Optional.empty())).toCompletableFuture().get().rejection().orElseThrow());
            AtomicReference<Listener> listener=new AtomicReference<>();e.model.set((r,l)->{listener.set(l);return ()->{};});e.submit("a",s);await(()->listener.get()!=null);listener.get().frame(new Body("before disable"));
            e.app.dispatch(new DisablePlugin(e.appMeta())).toCompletableFuture().get(10,TimeUnit.SECONDS);assertEquals(WorkState.OFF,e.app.snapshot().workState());
            finish(listener.get(),"late after disable");e.sessions.refreshAll().toCompletableFuture().get();assertTrue(e.view("a",s).activeOperation().isEmpty());
            assertNotEquals(AttemptStatus.SUCCEEDED,e.view("a",s).turns().getFirst().attempts().getFirst().status());
        }
    }
    @Test void stopBeforeWorkerStartsStillReleasesSlotWithoutNetworkOrUnsavedFailure() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String s=e.create("a");e.text("a",s,"question");e.pauseGeneration.set(true);e.submit("a",s);
            String operation=e.view("a",s).activeOperation().orElseThrow().operationId();
            assertEquals(Outcome.APPLIED,e.dispatch(new StopOperation(e.meta("a",s),operation)).outcome());e.resumeGeneration();e.idle("a",s);
            assertTrue(e.requests.isEmpty());assertFalse(e.view("a",s).writesBlocked());assertEquals(AttemptStatus.INTERRUPTED,e.view("a",s).turns().getFirst().attempts().getFirst().status());
        }
    }
}
