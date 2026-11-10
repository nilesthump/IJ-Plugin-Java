package edu.courseassist.core.session;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.core.react.*;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.core.session.SessionData.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static edu.courseassist.bootstrap.ide.SessionTestEnvironment.*;

class ContextPlannerTest {
    @TempDir Path root;
    @Test void manualSummaryIsSeparateAndExcludesDraftAndInterruptedOutputs() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();var binding=e.open("a","a",false);String s=e.create("a");
            e.model.set((r,l)->{l.frame(new Body("STOPPED_PRIVATE_SENTINEL"));l.failed(new IllegalStateException("synthetic disconnect"));return ()->{};});
            e.text("a",s,"unanswered user question");e.submit("a",s);e.idle("a",s);
            e.model.set((r,l)->{finish(l,"valid answer");return ()->{};});
            e.text("a",s,"second question");e.submit("a",s);e.idle("a",s);
            e.text("a",s,"third question");e.submit("a",s);e.idle("a",s);
            e.text("a",s,"DRAFT_PRIVATE_SENTINEL");int before=e.requests.size();
            e.model.set((r,l)->{assertTrue(r.tools().isEmpty());String input=ReactContext.json(r);assertFalse(input.contains("STOPPED_PRIVATE_SENTINEL"));assertFalse(input.contains("DRAFT_PRIVATE_SENTINEL"));
                assertTrue(input.contains("unanswered user question"));finish(l,"summary with attributed user goal");return ()->{};});
            e.dispatch(new ManualCompact(e.meta("a",s)));e.idle("a",s);
            assertEquals(before+1,e.requests.size());assertEquals(3,e.view("a",s).turns().size());assertEquals("DRAFT_PRIVATE_SENTINEL",e.view("a",s).draft().text());
            Checkpoint cp=e.repository.checkpoint(binding.projectId(),"a",s).orElseThrow();assertEquals(2,cp.coveredTurns());assertEquals(ReactContext.POLICY,cp.policy());
            e.model.set((r,l)->{finish(l,"next answer");return ()->{};});e.submit("a",s);e.idle("a",s);
            String next=ReactContext.json(e.requests.getLast());assertTrue(next.contains("summary with attributed user goal"));assertFalse(next.contains("STOPPED_PRIVATE_SENTINEL"));
            assertFalse(next.contains("unanswered user question"));assertTrue(e.queries.isEmpty());
        }
    }
    @Test void automaticCompactionBudgetsTheCompleteRequestAndRunsOnlyOnce() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.context=new ContextPlanner.Settings(18000,6500,1024,1,3000,Duration.ofSeconds(5),"synthetic-summary");
            AtomicInteger summaries=new AtomicInteger();e.model.set((r,l)->{
                if(r.messages().getFirst().content().startsWith("Summarize")){summaries.incrementAndGet();finish(l,"earlier goals");}
                else finish(l,"answer "+"x".repeat(900));return ()->{};});
            e.configureAndEnable();var binding=e.open("a","a",false);String s=e.create("a");
            for(int n=0;n<3;n++){e.text("a",s,"question-"+n+"z".repeat(1200));e.submit("a",s);e.idle("a",s);}
            assertEquals(1,summaries.get());assertTrue(e.repository.checkpoint(binding.projectId(),"a",s).isPresent());
            Request finalRequest=e.requests.getLast();assertFalse(finalRequest.messages().getFirst().content().startsWith("Summarize"));
            assertTrue(ReactContext.utf8Budget().tokens(finalRequest)<=18000);assertTrue(ReactContext.json(finalRequest).contains("question-2"));
        }
    }
    @Test void checkpointActivationRechecksCancellationAndPrefixEligibility() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();var binding=e.open("a","a",false);String s=e.create("a");
            for(int n=0;n<2;n++){e.text("a",s,"q"+n);e.submit("a",s);e.idle("a",s);}
            var submission=e.repository.submit(binding.projectId(),new ManualCompact(e.meta("a",s)),e.runtime.snapshot().epoch());Identity i=submission.operation().orElseThrow().identity();
            var history=e.repository.history(binding.projectId(),"a",s);
            Checkpoint cp=new Checkpoint(SessionData.id(),s,Optional.empty(),1,ReactContext.POLICY,SessionRepositoryFingerprint(binding.projectId(),s,history.subList(0,1)),"safe summary","synthetic",Optional.empty());
            e.repository.candidate(i,cp);e.repository.cancelOperation(i);
            assertThrows(CancellationException.class,()->e.repository.activate(i,cp));assertTrue(e.repository.checkpoint(binding.projectId(),"a",s).isEmpty());
            e.repository.finishCompact(i);
        }
    }
    private static String SessionRepositoryFingerprint(String p,String s,List<HistoryTurn> h){return edu.courseassist.storage.SessionRepository.fingerprint(p,s,h);}
    @Test void developmentSummaryErrorStopsAndProductMayUseOnlyBudgetedFallback() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.context=new ContextPlanner.Settings(18000,5000,1024,1,3000,Duration.ofSeconds(5),"synthetic-summary");
            AtomicInteger summaryCalls=new AtomicInteger();e.model.set((r,l)->{
                if(r.messages().getFirst().content().startsWith("Summarize")){summaryCalls.incrementAndGet();l.failed(new IllegalStateException("summary failed"));}
                else finish(l,"a".repeat(1200));return ()->{};});
            e.configureAndEnable();var binding=e.open("a","a",false);String s=e.create("a");
            for(int n=0;n<3;n++){e.text("a",s,"q"+n+"z".repeat(1000));e.submit("a",s);e.idle("a",s);}
            assertEquals(1,summaryCalls.get());assertEquals(AttemptStatus.FAILED,e.view("a",s).turns().getLast().attempts().getLast().status());
            assertTrue(e.repository.checkpoint(binding.projectId(),"a",s).isEmpty());assertTrue(e.sessions.failure(s).isPresent());
        }
    }
    @Test void repeatedCompactionUsesOldSummaryAndOnlyNewEligiblePrefix() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();var binding=e.open("a","a",false);String s=e.create("a");
            e.model.set((r,l)->{finish(l,r.messages().getFirst().content().startsWith("Summarize")?"VALID_OLD_SUMMARY":"a".repeat(1400));return ()->{};});
            for(int n=0;n<3;n++){e.text("a",s,"ORIGINAL_PREFIX_"+n+"q".repeat(1400));e.submit("a",s);e.idle("a",s);}
            e.dispatch(new ManualCompact(e.meta("a",s)));e.idle("a",s);
            for(int n=0;n<2;n++){e.text("a",s,"new input "+n+"q".repeat(1400));e.submit("a",s);e.idle("a",s);}
            e.app.dispatch(new edu.courseassist.contract.ApplicationContracts.DisablePlugin(e.appMeta())).toCompletableFuture().get();
            e.context=new ContextPlanner.Settings(11000,11000,1024,1,3000,Duration.ofSeconds(5),"synthetic-summary");
            e.app.dispatch(new edu.courseassist.contract.ApplicationContracts.EnablePlugin(e.appMeta())).toCompletableFuture().get();
            e.model.set((r,l)->{String request=ReactContext.json(r);assertTrue(request.contains("VALID_OLD_SUMMARY"));assertFalse(request.contains("ORIGINAL_PREFIX_0"));
                finish(l,"advanced summary");return ()->{};});
            e.dispatch(new ManualCompact(e.meta("a",s)));e.idle("a",s);
            assertEquals(4,e.repository.checkpoint(binding.projectId(),"a",s).orElseThrow().coveredTurns());
            assertEquals("advanced summary",e.repository.checkpoint(binding.projectId(),"a",s).orElseThrow().summary());
        }
    }
    @Test void productSummaryFailureRebuildsBudgetedCurrentRequestWithoutRetry() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.context=new ContextPlanner.Settings(18000,5000,1024,1,3000,Duration.ofSeconds(5),"synthetic-summary");e.policy=ErrorPolicy.PRODUCT;
            AtomicInteger summaries=new AtomicInteger();e.model.set((r,l)->{if(r.messages().getFirst().content().startsWith("Summarize")){summaries.incrementAndGet();l.failed(new IllegalStateException("controlled summary failure"));}
                else finish(l,"a".repeat(1200));return ()->{};});
            e.configureAndEnable();e.open("a","a",false);String s=e.create("a");
            for(int n=0;n<3;n++){e.text("a",s,"current-"+n+"q".repeat(1000));e.submit("a",s);e.idle("a",s);}
            assertEquals(1,summaries.get());assertEquals(AttemptStatus.SUCCEEDED,e.view("a",s).turns().getLast().attempts().getLast().status());
            assertTrue(ReactContext.json(e.requests.getLast()).contains("current-2"));assertTrue(ReactContext.utf8Budget().tokens(e.requests.getLast())<=18000);assertTrue(e.sessions.failure(s).isPresent());
            assertFalse(e.view("a",s).notices().stream().anyMatch(n->n.code().equals("TASK_FAILED")));
            assertTrue(e.view("a",s).notices().stream().anyMatch(n->n.code().equals("CONTEXT_FALLBACK")&&n.severity()==Severity.WARNING));
            assertFalse(e.view("a",s).writesBlocked());
        }
    }
    @Test void freshSuccessfulSendClearsOnlyPreviousGenerationFailure() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();e.open("a","a",false);String s=e.create("a");
            e.model.set((r,l)->{l.failed(new IllegalStateException("generation failed"));return ()->{};});
            e.text("a",s,"first");e.submit("a",s);e.idle("a",s);
            assertTrue(e.view("a",s).notices().stream().anyMatch(n->n.code().equals("TASK_FAILED")));
            e.model.set((r,l)->{finish(l,"success");return ()->{};});
            e.text("a",s,"new instruction");e.submit("a",s);e.idle("a",s);
            assertEquals(AttemptStatus.FAILED,e.view("a",s).turns().getFirst().attempts().getLast().status());
            assertEquals(AttemptStatus.SUCCEEDED,e.view("a",s).turns().getLast().attempts().getLast().status());
            assertFalse(e.view("a",s).notices().stream().anyMatch(n->n.code().equals("TASK_FAILED")));
        }
    }
}
