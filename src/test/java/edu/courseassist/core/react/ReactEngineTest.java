package edu.courseassist.core.react;

import edu.courseassist.bootstrap.ide.ReactTestEnvironment;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.storage.ReactRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static edu.courseassist.core.react.ReactData.*;
import static edu.courseassist.bootstrap.ide.ReactTestEnvironment.*;
import static org.junit.jupiter.api.Assertions.*;

class ReactEngineTest {
    @TempDir Path temp;
    private static void eventually(BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean()) { if(System.nanoTime()-until>=0) fail("Condition did not become true"); Thread.sleep(5); }
    }
    @Test void firstSearchFinalCitationDurableStateAndCumulativeViews() throws Exception {
        try (var h = new ReactTestEnvironment(temp, true)) {
            AtomicInteger subscriptions = new AtomicInteger();
            var job = h.start((request, listener) -> {
                assertEquals(1, h.queries.get()); subscriptions.incrementAndGet();
                String ref = h.views.getLast().branches().getFirst().steps().getFirst().suppliedEvidence().getFirst().generationId();
                assertTrue(ReactContext.json(request).contains(ref));
                // Actual chunk id from the exact evidence supplied to this step.
                String chunk = h.views.getLast().branches().getFirst().steps().getFirst().suppliedEvidence().getFirst().chunkId();
                listener.frame(new Thinking("real-thinking")); listener.frame(new Body("  answer\n"));
                listener.frame(new Body("[[course:"+chunk+"]]")); listener.frame(new End("stop"));
                listener.frame(new Usage(new TokenUsage(7,3))); listener.complete(); return () -> {};
            });
            Snapshot result = h.done(job);
            assertTrue(result.eligible()); assertEquals(1, subscriptions.get()); assertEquals(1, result.citations().size());
            assertEquals(3, result.citations().getFirst().firstPhysicalPage()); assertEquals(1, h.sourceRefs());
            assertEquals(result, h.journal.load(h.identity).orElseThrow());
            var step = result.branches().getFirst().steps().getFirst(); assertTrue(step.body().startsWith("  answer\n"));
            assertEquals("real-thinking", step.thinking()); assertEquals(new TokenUsage(7,3), step.usage().orElseThrow());
            long previous = 0; for(var view:h.views) { assertTrue(view.throughSeq()>previous); previous=view.throughSeq(); }
            assertEquals(ContextEligibility.ELIGIBLE_DURABLE_SUCCESS, ReactViews.attempt(result).contextEligibility());
        }
    }
    @Test void emptyAttemptStaysEmptyAfterImportAndNoMatchIsSeparate() throws Exception {
        try (var h = new ReactTestEnvironment(temp, false)) {
            var fixed = h.tools(); h.add("new course");
            var result = h.done(h.start((request,l)->{ assertTrue(request.tools().isEmpty()); finish(l,"general"); return ()->{}; }, LIMITS,
                    ErrorPolicy.DEVELOPMENT, ()->true, h.journal,List.of(),h.identity,fixed));
            assertEquals(RetrievalStatus.NO_INDEX,result.branches().getFirst().retrieval()); assertEquals(0,h.queries.get());
        }
    }
    @Test void normalNoMatchAllowsLaterSearchWithoutLosingPreviousEvidence() throws Exception {
        try (var h = new ReactTestEnvironment(temp,true)) {
            h.query = text -> text.contains("find") ? new float[]{1,0,0} : new float[]{0,1,0};
            AtomicInteger n = new AtomicInteger();
            Snapshot result = h.done(h.start((r,l)->{
                if(n.incrementAndGet()==1) {
                    assertEquals(RetrievalStatus.NO_MATCH,h.views.getLast().branches().getFirst().retrieval());
                    l.frame(new ToolDelta(0,"call-a","searchCourse","{\"query\":\"find\"}")); l.frame(new End("tool_calls"));l.complete();
                } else { assertTrue(ReactContext.json(r).contains("course-sentinel")); finish(l,"answer"); } return ()->{};
            }));
            assertEquals(2,n.get()); assertEquals(RetrievalStatus.EVIDENCE_FOUND,result.branches().getFirst().retrieval());
            ReactContext.validateProtocol(result.branches().getFirst().protocol());
        }
    }
    @Test void fragmentedMultipleToolsArePairedExactlyOnceAndPreviewIsNotFinalBody() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            AtomicInteger n=new AtomicInteger();
            var result=h.done(h.start((r,l)->{
                if(n.incrementAndGet()==1) {
                    String ref=h.views.getLast().branches().getFirst().steps().getFirst().suppliedEvidence().getFirst().chunkId();
                    l.frame(new Body("intermediate-preview"));
                    l.frame(new ToolDelta(0,"search-id","searchCourse","{\"query\":"));
                    l.frame(new ToolDelta(1,"read-id","readCourseEvidence","{\"ref\":\""+ref+"\"}"));
                    assertEquals(1,h.queries.get(),"No tool executes before complete parameters/finish");
                    l.frame(new ToolDelta(0,"","","\"next\"}"));l.frame(new End("tool_calls"));l.complete();
                } else { assertEquals(2,r.messages().stream().filter(m->m.role().equals("tool")).count());finish(l,"final-body"); }
                return ()->{};
            }));
            assertEquals(2,h.queries.get());assertEquals(2,n.get());
            assertEquals("final-body",result.branches().getFirst().steps().getLast().body());
            assertEquals("intermediate-preview",result.branches().getFirst().steps().getFirst().body());
        }
    }
    @Test void productRetrievalFailureRebuildsCleanBranchAndKeepsOriginalError() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            var fault=new IllegalStateException("retrieval-original");h.query=text->{if(text.equals("fault"))throw fault;return new float[]{1,0,0};};
            AtomicInteger n=new AtomicInteger();
            var job=h.start((r,l)->{
                if(n.incrementAndGet()==1){l.frame(new Body("discarded-output-sentinel"));l.frame(new Thinking("discarded-thinking-sentinel"));
                    l.frame(new ToolDelta(0,"call","searchCourse","{\"query\":\"fault\"}"));
                    l.frame(new ToolDelta(1,"pending","searchCourse","{\"query\":\"never-executed\"}"));l.frame(new End("tool_calls"));l.complete();}
                else {String input=ReactContext.json(r);assertFalse(input.contains("course-sentinel"));assertFalse(input.contains("discarded-"));
                    assertTrue(r.tools().isEmpty());finish(l,"general-final");}return ()->{};
            },LIMITS,ErrorPolicy.PRODUCT,()->true,h.journal,List.of(),h.identity,h.tools());
            var result=h.done(job);assertEquals(List.of(fault),job.retrievalFailures());assertEquals(2,n.get());
            assertEquals(BranchStatus.INVALIDATED,result.branches().getFirst().status());assertTrue(result.citations().isEmpty());
            var oldTools=result.branches().getFirst().steps().getFirst().tools();assertEquals(2,oldTools.size());
            assertEquals(ToolStatus.FAILED,oldTools.getFirst().status());assertEquals(ToolStatus.CANCELED,oldTools.getLast().status());assertEquals("",oldTools.getLast().observation());
            String history=ReactContext.json(ReactContext.history("test-project","test-session",List.of(new HistoryTurn(h.input,List.of(result)))));
            assertFalse(history.contains("discarded-"));assertFalse(history.contains("course-sentinel"));assertTrue(history.contains("general-final"));
        }
    }
    @Test void developmentFaultIsExposedAndInitialProductFaultUsesOneGeneralStep() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            var fault=new IllegalArgumentException("query-original");h.query=text->{throw fault;};AtomicInteger calls=new AtomicInteger();
            var job=h.start((r,l)->{calls.incrementAndGet();return ()->{};});
            assertSame(fault,assertThrows(ExecutionException.class,()->h.done(job)).getCause());assertEquals(0,calls.get());
            assertEquals(AttemptStatus.FAILED,job.snapshot().status());assertTrue(job.snapshot().saved());
        }
        try(var h=new ReactTestEnvironment(temp.resolve("product"),true)) {
            h.query=text->{throw new IllegalStateException("original");};
            var result=h.done(h.start(answer("general"),LIMITS,ErrorPolicy.PRODUCT,()->true,h.journal,List.of(),h.identity,h.tools()));
            assertEquals(RetrievalStatus.FAILED,result.branches().getFirst().retrieval());assertTrue(result.eligible());
        }
    }
    @Test void platformCancellationNeverBecomesRetrievalFallback() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            h.query=text->{throw new com.intellij.openapi.progress.ProcessCanceledException();};AtomicInteger calls=new AtomicInteger();
            var job=h.start((r,l)->{calls.incrementAndGet();return ()->{};},LIMITS,ErrorPolicy.PRODUCT,()->true,h.journal,List.of(),h.identity,h.tools());
            assertThrows(ExecutionException.class,()->h.done(job));assertEquals(0,calls.get());assertTrue(job.retrievalFailures().isEmpty());assertEquals(AttemptStatus.STOPPED,job.snapshot().status());
        }
    }
    @Test void eofLengthUnknownEmptyAndUnpairedToolsCannotSucceed() throws Exception {
        List<List<Frame>> scripts=List.of(List.of(new Body("eof-sentinel")),List.of(new Body("limited-sentinel"),new End("length")),
                List.of(new Body("unknown-sentinel"),new End("future_reason")),List.of(new End("stop")),
                List.of(new ToolDelta(0,"x","searchCourse","{\"query\":\"x\"}"),new End("stop")));
        for(int i=0;i<scripts.size();i++)try(var h=new ReactTestEnvironment(temp.resolve("case"+i),true)) {
            var frames=scripts.get(i);var job=h.start((r,l)->{frames.forEach(l::frame);l.complete();return ()->{};});
            assertThrows(ExecutionException.class,()->h.done(job));assertFalse(job.snapshot().eligible());
            String projected=ReactContext.json(ReactContext.history("test-project","test-session",List.of(new HistoryTurn(h.input,List.of(job.snapshot())))));
            assertFalse(projected.contains("sentinel"));assertEquals(1,job.snapshot().branches().getFirst().steps().size());
        }
    }
    @Test void invalidToolBatchExecutesNothingAndUnknownCitationFails() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            var job=h.start((r,l)->{l.frame(new ToolDelta(0,"valid","searchCourse","{\"query\":\"find\"}"));
                l.frame(new ToolDelta(1,"bad","executeFile","{}"));l.frame(new End("tool_calls"));l.complete();return ()->{};});
            assertThrows(ExecutionException.class,()->h.done(job));assertEquals(1,h.queries.get());
        }
        try(var h=new ReactTestEnvironment(temp.resolve("cite"),true)) {
            var job=h.start(answer("[[course:invented]]"));assertThrows(ExecutionException.class,()->h.done(job));assertFalse(job.snapshot().eligible());
        }
    }
    @Test void completeBudgetCheckedBeforeEveryStepIncludingToolSchemaAndTrace() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            AtomicInteger calls=new AtomicInteger(),measures=new AtomicInteger();
            var engine=new ReactEngine((r,l)->{calls.incrementAndGet();l.frame(new ToolDelta(0,"c","searchCourse","{\"query\":\"x\"}"));l.frame(new End("tool_calls"));l.complete();return ()->{};},h.journal,h.workers,r->{
                assertFalse(r.tools().isEmpty());assertTrue(ReactContext.json(r).contains(h.input.question().strip().split(" ")[0]));
                return measures.incrementAndGet()==1?1:Long.MAX_VALUE;});
            var job=engine.start(h.identity,h.input,List.of(),h.tools(),LIMITS,ErrorPolicy.DEVELOPMENT,()->true,h.views::add);
            assertThrows(ExecutionException.class,()->h.done(job));assertEquals(1,calls.get());assertEquals(2,measures.get());assertEquals(AttemptStatus.LIMITED,job.snapshot().status());
        }
    }
    @Test void cancellationFreezesPrefixAndRetryUsesOriginalInputWithoutOldOutput() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            var ready=new CountDownLatch(1);var listener=new AtomicReference<Listener>();var cancellations=new AtomicInteger();
            var job=h.start((r,l)->{listener.set(l);l.frame(new Body("stopped-prefix-sentinel"));ready.countDown();return cancellations::incrementAndGet;});
            assertTrue(ready.await(10,TimeUnit.SECONDS));
            eventually(()->h.views.stream().anyMatch(v->!v.branches().isEmpty()&&!v.branches().getFirst().steps().isEmpty()&&v.branches().getFirst().steps().getFirst().body().contains("sentinel")));
            job.cancel();assertInstanceOf(CancellationException.class,assertThrows(ExecutionException.class,()->h.done(job)).getCause());
            Snapshot old=job.snapshot();listener.get().frame(new Body("late"));assertEquals(old,job.snapshot());assertTrue(cancellations.get()>0);
            assertTrue(old.branches().getFirst().steps().getFirst().body().contains("stopped-prefix-sentinel"));
            var next=new Identity(h.identity.projectId(),h.identity.projectInstanceId(),h.identity.sessionId(),h.identity.turnId(),h.identity.operationId(),"retry",7);
            var retry=h.start((r,l)->{assertFalse(ReactContext.json(r).contains("stopped-prefix-sentinel"));
                assertEquals(1,r.messages().stream().filter(m->m.role().equals("user")).count());finish(l,"new-body");return ()->{};},
                    LIMITS,ErrorPolicy.DEVELOPMENT,()->true,h.journal,List.of(new HistoryTurn(h.input,List.of(old))),next,h.tools());
            assertEquals("new-body",h.done(retry).branches().getFirst().steps().getFirst().body());assertEquals(1,h.journal.load(h.identity).orElseThrow().branches().getFirst().steps().size());
        }
    }
    @Test void saveFailureKeepsVisiblePrefixAndCannotBecomeDurableHistory() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            Journal failing=new Journal(){public void create(Snapshot s){h.journal.create(s);}public void save(Snapshot s,boolean c){
                if(s.status()==AttemptStatus.SUCCEEDED)throw new IllegalStateException("save-original");h.journal.save(s,c);}};
            var job=h.start(answer("visible-but-unsaved"),LIMITS,ErrorPolicy.DEVELOPMENT,()->true,failing,List.of(),h.identity,h.tools());
            var failure=assertThrows(ExecutionException.class,()->h.done(job));assertEquals("save-original",failure.getCause().getMessage());
            assertFalse(job.snapshot().saved());assertFalse(job.snapshot().eligible());assertEquals("visible-but-unsaved",job.snapshot().branches().getFirst().steps().getFirst().body());
            assertEquals(AttemptStatus.RUNNING,h.journal.load(h.identity).orElseThrow().status());
        }
    }
    @Test void restartMarksSavedPrefixInterruptedAndDeletionRejectsLateSave() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            var ready=new CountDownLatch(1);var job=h.start((r,l)->{l.frame(new Body("restart-prefix-sentinel"));ready.countDown();return ()->{};});
            assertTrue(ready.await(10,TimeUnit.SECONDS));eventually(()->h.journal.load(h.identity).orElseThrow().throughSeq()>=3);
            job.cancel();assertInstanceOf(CancellationException.class,assertThrows(ExecutionException.class,()->h.done(job)).getCause());
            // Reconstruct a crash-like RUNNING prefix in a separate explicitly created attempt.
            var id=new Identity("test-project","test-instance","test-session","test-turn","test-operation","crash",7);
            var initial=new Snapshot(id,h.input,0,0,AttemptStatus.RUNNING,true,"","",List.of(),List.of(),"");h.journal.create(initial);
            var reopened=new ReactRepository(h.store,(c,i,a)->{});reopened.initialize();
            assertEquals(AttemptStatus.INTERRUPTED,reopened.interrupt(id).status());assertFalse(reopened.load(id).orElseThrow().eligible());
            h.deleting();h.journal.deleteSession(h.identity);assertEquals(0,h.sourceRefs());assertTrue(h.journal.load(h.identity).isEmpty());
            assertThrows(CompletionException.class,()->h.journal.save(job.snapshot(),true));
            assertThrows(CompletionException.class,()->h.journal.create(initial));assertEquals(1,h.library.snapshot().join().materials().size());
        }
    }
    @Test void epochInvalidationStopsLocallyAndCoordinatorCanSaveWithoutRemoteAck() throws Exception {
        try(var h=new ReactTestEnvironment(temp,false)) {
            var ready=new CountDownLatch(1);var permitted=new AtomicBoolean(true);
            var job=h.start((r,l)->{l.frame(new Body("epoch-prefix"));ready.countDown();return ()->{};},LIMITS,ErrorPolicy.PRODUCT,permitted::get,h.journal,List.of(),h.identity,h.tools());
            assertTrue(ready.await(10,TimeUnit.SECONDS));h.epoch();permitted.set(false);
            assertInstanceOf(CancellationException.class,assertThrows(ExecutionException.class,()->h.done(job)).getCause());assertTrue(job.snapshot().saved());assertEquals(AttemptStatus.STOPPED,h.journal.load(h.identity).orElseThrow().status());
        }
    }
    @Test void stopAndFinalCommitHaveAnAtomicNonblockingDecision() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            var listener=new AtomicReference<Listener>();var ready=new CountDownLatch(1);
            var before=h.start((r,l)->{listener.set(l);l.frame(new Body("stop-wins"));ready.countDown();return ()->{};});
            assertTrue(ready.await(10,TimeUnit.SECONDS));assertTrue(before.cancel());listener.get().frame(new End("stop"));listener.get().complete();
            assertThrows(ExecutionException.class,()->h.done(before));assertEquals(AttemptStatus.STOPPED,before.snapshot().status());assertFalse(before.snapshot().eligible());
        }
        try(var h=new ReactTestEnvironment(temp.resolve("commit"),false)) {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            Journal delayed=new Journal(){public void create(Snapshot s){h.journal.create(s);}public void save(Snapshot s,boolean coordinator){
                if(s.status()==AttemptStatus.SUCCEEDED){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}catch(InterruptedException e){throw new CompletionException(e);}}
                h.journal.save(s,coordinator);}};
            var job=h.start(answer("commit-wins"),LIMITS,ErrorPolicy.DEVELOPMENT,()->true,delayed,List.of(),h.identity,h.tools());
            assertTrue(entered.await(10,TimeUnit.SECONDS));assertFalse(job.cancel(),"Already claimed final save, no accepted stop");release.countDown();
            assertTrue(h.done(job).eligible());
        }
    }
    @Test void finiteStepsToolCountResultSizeOutputAndElapsedLimitsStopWithoutSuccess() throws Exception {
        List<Limits> limits=List.of(new Limits(1,8,16000,32000,200000,1024,Duration.ofSeconds(15),12),
                new Limits(6,0,16000,32000,200000,1024,Duration.ofSeconds(15),12),
                new Limits(6,8,25,32000,200000,1024,Duration.ofSeconds(15),12),
                new Limits(6,8,16000,2,200000,1024,Duration.ofSeconds(15),12),
                new Limits(6,8,16000,32000,200000,1024,Duration.ofMillis(200),12));
        for(int i=0;i<limits.size();i++)try(var h=new ReactTestEnvironment(temp.resolve("limit"+i),true)) {
            int scenario=i;AtomicInteger calls=new AtomicInteger();
            var job=h.start((r,l)->{calls.incrementAndGet();if(scenario==4){l.frame(new Body("waiting-prefix"));}
                else if(scenario==3)finish(l,"over-output");else{l.frame(new ToolDelta(0,"c","searchCourse","{\"query\":\"x\"}"));l.frame(new End("tool_calls"));l.complete();}return ()->{};},
                    limits.get(i),ErrorPolicy.DEVELOPMENT,()->true,h.journal,List.of(),h.identity,h.tools());
            assertThrows(ExecutionException.class,()->h.done(job));assertEquals(AttemptStatus.LIMITED,job.snapshot().status());assertFalse(job.snapshot().eligible());assertTrue(calls.get()<=1);
        }
    }
    @Test void strictToolJsonAndViewFailureDoNotCorruptTheArchive() throws Exception {
        for(String json:List.of("{\"query\":\"x\"} {}","{\"query\":\"x\",\"query\":\"y\"}","{\"query\":\"x\",\"extra\":1}"))
            assertThrows(RuntimeException.class,()->CourseTools.argument(new Call("c","searchCourse",json)));
        try(var h=new ReactTestEnvironment(temp,false)) {
            var viewFailure=new IllegalStateException("view-original");
            var job=new ReactEngine(answer("saved-answer"),h.journal,h.workers,ReactContext.utf8Budget())
                    .start(h.identity,h.input,List.of(),h.tools(),LIMITS,ErrorPolicy.DEVELOPMENT,()->true,s->{throw viewFailure;});
            assertTrue(h.done(job).eligible());assertTrue(job.observerFailures().contains(viewFailure));assertTrue(h.journal.load(h.identity).orElseThrow().eligible());
        }
    }
    @Test void stopDuringFirstToolRetainsEveryPreviouslyDisplayedToolRequest() throws Exception {
        try(var h=new ReactTestEnvironment(temp,true)) {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            h.query=text->{if(text.equals("blocked")){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}
                catch(InterruptedException e){throw new CompletionException(e);}}return new float[]{1,0,0};};
            var job=h.start((r,l)->{l.frame(new ToolDelta(0,"first","searchCourse","{\"query\":\"blocked\"}"));
                l.frame(new ToolDelta(1,"second","searchCourse","{\"query\":\"unused\"}"));l.frame(new End("tool_calls"));l.complete();return ()->{};});
            assertTrue(entered.await(10,TimeUnit.SECONDS));assertEquals(2,job.snapshot().branches().getFirst().steps().getFirst().tools().size());
            assertTrue(job.cancel());assertThrows(ExecutionException.class,()->h.done(job));release.countDown();
            var tools=job.snapshot().branches().getFirst().steps().getFirst().tools();assertEquals(2,tools.size());
            assertTrue(tools.stream().allMatch(t->t.status()==ToolStatus.CANCELED));assertEquals(2,h.queries.get());
        }
    }
}
