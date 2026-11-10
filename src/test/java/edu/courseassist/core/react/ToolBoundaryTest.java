package edu.courseassist.core.react;

import edu.courseassist.bootstrap.ide.ReactTestEnvironment;
import edu.courseassist.contract.ChatContracts.AttemptStatus;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static edu.courseassist.core.react.ReactData.*;
import static edu.courseassist.bootstrap.ide.ReactTestEnvironment.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolBoundaryTest {
    @TempDir Path root;
    ToolBoundary.EditTools adapter(java.util.function.Supplier<CompletableFuture<String>> result) {
        return new ToolBoundary.EditTools() {
            public boolean supports(ToolBoundary.Capability capability){return capability==ToolBoundary.Capability.TARGET;}
            public CompletableFuture<String> execute(ToolBoundary.Capability capability,Identity id,String branch,Call call,BooleanSupplier permitted){
                assertEquals(ToolBoundary.Capability.TARGET,capability);assertFalse(branch.isBlank());assertTrue(permitted.getAsBoolean());
                return result.get();
            }
        };
    }
    Model model(AtomicInteger count) {
        return (request,listener)->{
            if(count.incrementAndGet()==1){
                assertEquals(List.of("resolveEditTarget"),request.tools().stream().map(ToolDefinition::name).toList());
                listener.frame(new ToolDelta(0,"target-call","resolveEditTarget","{\"ref\":\"host-request\"}"));
                listener.frame(new End("tool_calls"));listener.complete();
            }else{assertTrue(request.messages().stream().anyMatch(m->m.role().equals("tool")&&m.callId().equals("target-call")));finish(listener,"final");}
            return ()->{};
        };
    }
    @Test void noCourseStillAllowsExplicitTargetCapability() throws Exception {
        try(var h=new ReactTestEnvironment(root,false)) {
            AtomicInteger count=new AtomicInteger(),executions=new AtomicInteger();
            var edits=adapter(()->{executions.incrementAndGet();return CompletableFuture.completedFuture("unique host snapshot");});
            var job=new ReactEngine(model(count),h.journal,h.workers,ReactContext.utf8Budget(),Optional.of(edits))
                    .start(h.identity,h.input,List.of(),h.tools(),LIMITS,ErrorPolicy.PRODUCT,()->true,h.views::add);
            assertEquals(AttemptStatus.SUCCEEDED,h.done(job).status());assertEquals(2,count.get());assertEquals(1,executions.get());assertEquals(0,h.queries.get());
        }
    }
    @Test void editFailureAndCancellationNeverBecomeCourseFallback() throws Exception {
        for(boolean cancel:List.of(false,true))try(var h=new ReactTestEnvironment(root.resolve(""+cancel),false)) {
            AtomicInteger count=new AtomicInteger();
            RuntimeException failure=cancel?new CancellationException("platform canceled"):new IllegalStateException("target failed");
            var job=new ReactEngine(model(count),h.journal,h.workers,ReactContext.utf8Budget(),
                    Optional.of(adapter(()->CompletableFuture.failedFuture(failure))))
                    .start(h.identity,h.input,List.of(),h.tools(),LIMITS,ErrorPolicy.PRODUCT,()->true,h.views::add);
            assertThrows(Exception.class,()->h.done(job));
            assertEquals(cancel?AttemptStatus.STOPPED:AttemptStatus.FAILED,job.snapshot().status());
            assertEquals(1,count.get());assertTrue(job.retrievalFailures().isEmpty());assertEquals(0,h.queries.get());
        }
    }
    @Test void failedInitialCourseSearchLeavesTargetCapabilityAndOriginalCause() throws Exception {
        try(var h=new ReactTestEnvironment(root,true)) {
            var failure=new IllegalStateException("course unavailable");h.query=ignored->{throw failure;};
            AtomicInteger count=new AtomicInteger();
            var job=new ReactEngine(model(count),h.journal,h.workers,ReactContext.utf8Budget(),
                    Optional.of(adapter(()->CompletableFuture.completedFuture("target only"))))
                    .start(h.identity,h.input,List.of(),h.tools(),LIMITS,ErrorPolicy.PRODUCT,()->true,h.views::add);
            assertEquals(AttemptStatus.SUCCEEDED,h.done(job).status());assertEquals(1,h.queries.get());assertEquals(2,count.get());
            assertEquals(List.of(failure),job.retrievalFailures());
        }
    }
    @Test void unavailableUnknownAndApplyToolsCannotExecute() throws Exception {
        try(var h=new ReactTestEnvironment(root,false);var course=h.tools()) {
            var boundary=new ToolBoundary(course,Optional.empty());
            assertTrue(boundary.definitions(false).isEmpty());
            for(String name:List.of("resolveEditTarget","proposeCodeEdit","applyApprovedCodeEdit","executeCommand"))
                assertThrows(ProtocolFailure.class,()->boundary.validate(new Call("id",name,"{\"ref\":\"anything\"}"),false,Set.of()));
            var enabled=new ToolBoundary(course,Optional.of(adapter(()->CompletableFuture.completedFuture("unused"))));
            assertThrows(RuntimeException.class,()->enabled.validate(new Call("id","resolveEditTarget","{\"ref\":\"a\",\"ref\":\"b\"}"),false,Set.of()));
            assertEquals(0,h.queries.get());
        }
    }
    @Test void childBudgetIsCheckedBeforeSubscriptionAndChildOutputSharesParentLimit() throws Exception {
        for(boolean inputLimit:List.of(true,false))try(var h=new ReactTestEnvironment(root.resolve("child-"+inputLimit),false)) {
            AtomicInteger children=new AtomicInteger();
            var edits=new ToolBoundary.EditTools() {
                public boolean supports(ToolBoundary.Capability c){return c==ToolBoundary.Capability.PROPOSAL;}
                public CompletableFuture<String> execute(ToolBoundary.Capability c,Identity i,String b,Call call,BooleanSupplier permitted){throw new AssertionError("Missing owner");}
                public CompletableFuture<String> execute(ToolBoundary.Capability c,Identity i,String b,Call call,BooleanSupplier permitted,java.util.function.Function<Request,String> child) {
                    return CompletableFuture.completedFuture(child.apply(new Request(List.of(Message.text("user","child")),List.of(),1024)));
                }
            };
            Model model=(request,listener)->{if(request.tools().isEmpty()){children.incrementAndGet();finish(listener,"x".repeat(32001));}
                else{listener.frame(new ToolDelta(0,"p","proposeCodeEdit","{\"ref\":\"host\"}"));listener.frame(new End("tool_calls"));listener.complete();}return ()->{};};
            var job=new ReactEngine(model,h.journal,h.workers,request->inputLimit&&request.tools().isEmpty()?Long.MAX_VALUE:1,Optional.of(edits))
                    .start(h.identity,h.input,List.of(),h.tools(),LIMITS,ErrorPolicy.PRODUCT,()->true,h.views::add);
            assertThrows(Exception.class,()->h.done(job));assertEquals(AttemptStatus.LIMITED,job.snapshot().status());
            assertEquals(inputLimit?0:1,children.get());assertTrue(job.retrievalFailures().isEmpty());
        }
    }
}
