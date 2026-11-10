package edu.courseassist.core.edit;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.core.react.*;
import edu.courseassist.core.session.SessionData.Location;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static edu.courseassist.core.react.ReactData.*;
import static org.junit.jupiter.api.Assertions.*;

class CodeEditToolsTest {
    @TempDir Path root;
    @Test void staleSelectionProducesHostWarningWithoutCandidateOrGenericError() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();
            var binding=e.sessions.open(new Location("C:/synthetic/stale","stale"),"i",
                    ()->start->CompletableFuture.completedFuture(SessionTestEnvironment.group(start)),true,
                    Optional.of((identity,input,permitted)->(request,query)->CompletableFuture.completedFuture(
                            new TargetResolution(ResolutionStatus.STALE,List.of(),Optional.empty())))).toCompletableFuture().get();
            String session=e.create("i");
            e.sessions.beginCapture(SessionTestEnvironment.selection("selected","i"));
            e.sessions.captureCompletion("selected").toCompletableFuture().get();
            e.sessions.refreshAll().toCompletableFuture().get();e.text("i",session,"Change selected method");
            AtomicInteger calls=new AtomicInteger();
            e.model.set((request,listener)->{
                if(calls.incrementAndGet()==1){
                    listener.frame(new ToolDelta(0,"resolve","resolveEditTarget","{\"path\":\"\",\"class\":\"\",\"method\":\"\",\"parameters\":[]}"));
                    listener.frame(new End("tool_calls"));listener.complete();
                }else{
                    String observation=request.messages().stream().filter(m->m.role().equals("tool")).findFirst().orElseThrow().content();
                    assertTrue(observation.contains("STALE"));assertTrue(observation.contains("capture the selection again"));
                    SessionTestEnvironment.finish(listener,"Please capture the selection again.");
                }return ()->{};
            });
            e.submit("i",session);e.idle("i",session);
            var notices=e.view("i",session).notices();
            assertTrue(notices.stream().anyMatch(n->n.code().equals("EDIT_SELECTION_CHANGED")&&n.severity()==Severity.WARNING));
            assertTrue(notices.stream().noneMatch(n->n.code().equals("TASK_FAILED")));
            assertTrue(e.repository.edits().views(binding.projectId(),"i",session).isEmpty());assertEquals(2,calls.get());
            assertTrue(e.sessions.failure(session).isEmpty());
        }
    }
    @Test void realSpringAiLoopbackProtocol(){assertDoesNotThrow(()->edu.courseassist.bootstrap.ide.F2GenerationChecks.run(root));}
    static final String BEFORE="class X { int n=1; }\n";
    static String patch(){int at=BEFORE.indexOf("1");return ReactContext.json(Map.of("changes",List.of(Map.of("start",at,"end",at+1,"before","1","after","2"))));}
    String open(SessionTestEnvironment e) throws Exception {
        e.configureAndEnable();
        e.sessions.open(new Location("C:/synthetic/edit","edit"),"i",()->start->CompletableFuture.completedFuture(SessionTestEnvironment.group(start)),true,
                Optional.of((identity,input,permitted)->(request,query)->CompletableFuture.completedFuture(new TargetResolution(ResolutionStatus.UNIQUE,
                        List.of(new Target("file:///X.java","X.java","m","X")),Optional.of(new EditSnapshot(1,UUID.randomUUID().toString(),request,new Target("file:///X.java","X.java","m","X"),7,BEFORE,List.of(new Span(0,BEFORE.length()))))))))
                .toCompletableFuture().get(10,TimeUnit.SECONDS);
        String session=e.create("i");e.text("i",session,"Change n from 1 to 2 in X.java");return session;
    }
    Model model(AtomicInteger parent,AtomicInteger child,String output,boolean repeat,boolean failParent) {
        return (request,listener)->{
            if(request.tools().isEmpty()) {child.incrementAndGet();assertTrue(request.messages().getFirst().content().contains("Return only JSON"));SessionTestEnvironment.finish(listener,output);return ()->{};}
            int n=parent.incrementAndGet();
            if(n==1) {
                // Deliberately split arguments: no adapter may run before the complete tool step.
                listener.frame(new ToolDelta(0,"resolve","resolveEditTarget","{\"path\":\"X.java\","));
                listener.frame(new ToolDelta(0,"","","\"class\":\"\",\"method\":\"\",\"parameters\":[]}"));
            } else if(n==2||(repeat&&n==3)) {
                var observation=request.messages().stream().filter(m->m.role().equals("tool")&&m.callId().equals("resolve")).findFirst().orElseThrow();
                String ref=EditPatch.JSON.readTree(observation.content()).get("snapshotRef").asString();
                listener.frame(new ToolDelta(0,"proposal-"+n,"proposeCodeEdit",ReactContext.json(Map.of("ref",ref))));
            } else {
                if(failParent)listener.failed(new IllegalStateException("parent failed after child"));
                else SessionTestEnvironment.finish(listener,"Candidate ready for later review, no file edited.");return ()->{};
            }
            listener.frame(new End("tool_calls"));listener.complete();return ()->{};
        };
    }
    @Test void noCourseUsesOneChildAndRepeatedCallIdsKeepOneDurableCandidate() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            String session=open(e);AtomicInteger parent=new AtomicInteger(),child=new AtomicInteger();
            e.model.set(model(parent,child,patch(),true,false));e.submit("i",session);e.idle("i",session);
            String project=e.sessions.snapshot("i").projectId();
            var views=e.repository.edits().views(project,"i",session);assertEquals(1,views.size());assertEquals(ReviewState.REVIEWABLE,views.getFirst().review());
            var proposal=e.repository.edits().load(project,"i",session,views.getFirst().proposal());
            assertEquals(BEFORE,proposal.snapshot().before());assertEquals(BEFORE.replace("1","2"),proposal.after());
            assertEquals(1,child.get());assertEquals(4,parent.get());assertEquals(0,e.queries.size());
            var history=e.repository.history(project,"i",session);
            assertTrue(history.getFirst().attempts().getLast().eligible());
            var owner=history.getFirst().attempts().getLast();assertEquals(owner.identity().turnId(),views.getFirst().turnId());assertEquals(owner.identity().attemptId(),views.getFirst().attemptId());assertEquals(owner.effectiveBranchId(),views.getFirst().branchId());
            assertFalse(ReactContext.history(project,session,history).toString().contains("\"changes\""));
            assertEquals(2,history.getFirst().attempts().getLast().branches().getFirst().protocol().stream().filter(m->m.role().equals("tool")&&m.callId().startsWith("proposal-")).count());
        }
    }
    @Test void childCompleteParentFailureInvalidatesAndRetryCannotRegenerate() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            String session=open(e);AtomicInteger parent=new AtomicInteger(),child=new AtomicInteger();
            e.model.set(model(parent,child,patch(),false,true));e.submit("i",session);e.idle("i",session);
            String project=e.sessions.snapshot("i").projectId();var history=e.repository.history(project,"i",session);var attempt=history.getFirst().attempts().getLast();
            assertEquals(AttemptStatus.FAILED,attempt.status());assertEquals(ReviewState.INVALIDATED,e.repository.edits().views(project,"i",session).getFirst().review());
            assertFalse(ReactContext.history(project,session,history).toString().contains("changes"));
            assertEquals(Rejection.INVALID_TARGET,e.dispatch(new RetryAttempt(e.meta("i",session),attempt.identity().turnId(),attempt.identity().attemptId())).rejection().orElseThrow());
            assertEquals(1,child.get());
            e.text("i",session,"Change n from 1 to 2 in a new draft");parent.set(0);e.model.set(model(parent,child,patch(),false,false));e.submit("i",session);e.idle("i",session);assertEquals(2,child.get());
        }
    }
    @Test void noChangeDoesNotCreateArtifactAndInvalidCandidateIsNotCourseFallback() throws Exception {
        for(boolean invalid:List.of(false,true))try(var e=new SessionTestEnvironment(root.resolve(""+invalid))) {
            String session=open(e);AtomicInteger parent=new AtomicInteger(),child=new AtomicInteger();
            e.model.set(model(parent,child,invalid?"{\"changes\":[{\"start\":0,\"end\":1,\"before\":\"wrong\",\"after\":\"X\"}]}":"{\"changes\":[]}",false,false));
            e.submit("i",session);e.idle("i",session);String project=e.sessions.snapshot("i").projectId();
            assertTrue(e.repository.edits().views(project,"i",session).isEmpty());assertEquals(1,child.get());
            assertEquals(invalid?AttemptStatus.FAILED:AttemptStatus.SUCCEEDED,e.repository.history(project,"i",session).getFirst().attempts().getLast().status());
        }
    }
    @Test void outsideSourceRootExplainsBoundaryWithoutChildOrCandidate() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.configureAndEnable();
            var binding=e.sessions.open(new Location("C:/synthetic/outside-source","outside-source"),"i",
                    ()->start->CompletableFuture.completedFuture(SessionTestEnvironment.group(start)),true,
                    Optional.of((identity,input,permitted)->(request,query)->CompletableFuture.completedFuture(
                            new TargetResolution(ResolutionStatus.OUTSIDE_SOURCE_ROOT,List.of(),Optional.empty()))))
                    .toCompletableFuture().get(10,TimeUnit.SECONDS);
            String session=e.create("i");e.text("i",session,"Change spec/samples/X.java");AtomicInteger parents=new AtomicInteger();
            e.model.set((request,listener)->{
                assertFalse(request.tools().isEmpty(),"An out-of-scope resolution must not start child generation");
                if(parents.incrementAndGet()==1) {
                    listener.frame(new ToolDelta(0,"resolve","resolveEditTarget","{\"path\":\"spec/samples/X.java\",\"class\":\"\",\"method\":\"\",\"parameters\":[]}"));
                    listener.frame(new End("tool_calls"));listener.complete();
                } else {
                    var observation=request.messages().stream().filter(m->m.role().equals("tool")&&m.callId().equals("resolve")).findFirst().orElseThrow();
                    var json=EditPatch.JSON.readTree(observation.content());
                    assertEquals("OUTSIDE_SOURCE_ROOT",json.get("status").asString());
                    assertTrue(json.get("reason").asString().contains("Sources Root"));
                    assertFalse(json.has("snapshotRef"));
                    SessionTestEnvironment.finish(listener,"Target is outside configured Java source roots; no candidate generated.");
                }
                return ()->{};
            });
            e.submit("i",session);e.idle("i",session);
            assertEquals(2,parents.get());assertEquals(2,e.requests.size());assertTrue(e.queries.isEmpty());
            assertTrue(e.repository.edits().views(binding.projectId(),"i",session).isEmpty());
            assertTrue(e.repository.history(binding.projectId(),"i",session).getFirst().attempts().getLast().eligible());
        }
    }
    @Test void exactPatchRejectsOverlapScopeMismatchDuplicatesAndTrailingData() {
        var origin=new Origin("p","i","s","t","a","o",0);var request=new EditRequest("r",origin,"t",0,"change",Optional.empty());
        var snapshot=new EditSnapshot(1,"s",request,new Target("file","X.java","",""),1,"abcdef",List.of(new Span(1,5)));
        assertEquals("aXdef",EditPatch.apply(snapshot,"{\"changes\":[{\"start\":1,\"end\":3,\"before\":\"bc\",\"after\":\"X\"}]}"));
        for(String body:List.of(
                "{\"changes\":[{\"start\":0,\"end\":1,\"before\":\"a\",\"after\":\"X\"}]}",
                "{\"changes\":[{\"start\":1,\"end\":3,\"before\":\"bc\",\"after\":\"X\"},{\"start\":2,\"end\":4,\"before\":\"cd\",\"after\":\"Y\"}]}",
                "{\"changes\":[{\"start\":1,\"end\":2,\"before\":\"z\",\"after\":\"X\"}]}",
                "{\"changes\":[],\"changes\":[]}","{\"changes\":[]} trailing","{\"changes\":[],\"approved\":true}"))assertThrows(RuntimeException.class,()->EditPatch.apply(snapshot,body));
    }
    @Test void concurrentAndFailedRequestsReserveOnceAndFallbackCannotResolveAgain() throws Exception {
        for(boolean fail:List.of(false,true))try(var e=new SessionTestEnvironment(root.resolve("race-"+fail))) {
            String session=open(e),project=e.sessions.snapshot("i").projectId();
            var submission=e.repository.submit(project,e.submitEvent("i",session),e.runtime.snapshot().epoch());
            Identity id=submission.operation().orElseThrow().identity();Input input=e.repository.react().load(id).orElseThrow().input();
            e.repository.react().save(new Snapshot(id,input,1,1,AttemptStatus.RUNNING,true,"","",List.of(new Branch("branch",BranchStatus.ACTIVE,RetrievalStatus.NO_INDEX,"",List.of(),List.of())),List.of(),""),false);
            AtomicInteger reads=new AtomicInteger(),subscriptions=new AtomicInteger();
            var adapter=new CodeEditTools(id,input,(request,query)->{
                reads.incrementAndGet();var target=new Target("file:///X.java","X.java","m","X");
                return CompletableFuture.completedFuture(new TargetResolution(ResolutionStatus.UNIQUE,List.of(target),Optional.of(new EditSnapshot(1,"snap",request,target,7,BEFORE,List.of(new Span(0,BEFORE.length()))))));
            },e.repository.edits(),1024);
            adapter.plannedHistory(List.of(Message.text("user","uncompressed-history-sentinel")));
            adapter.plannedHistory(List.of(Message.text("system","planned-summary-sentinel")));
            var resolve=new Call("r","resolveEditTarget","{\"path\":\"X.java\",\"class\":\"\",\"method\":\"\",\"parameters\":[]}");
            String resolved=adapter.execute(ToolBoundary.Capability.TARGET,id,"branch",resolve,()->true).get();
            String ref=EditPatch.JSON.readTree(resolved).get("snapshotRef").asString();
            var started=new CountDownLatch(1);var release=new CountDownLatch(1);
            java.util.function.Function<Request,String> generate=request->{assertTrue(request.messages().toString().contains("planned-summary-sentinel"));assertFalse(request.messages().toString().contains("uncompressed-history-sentinel"));subscriptions.incrementAndGet();started.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Test release missing");}catch(InterruptedException ex){throw new RuntimeException(ex);}if(fail)throw new ProtocolFailure("synthetic failure");return patch();};
            var first=CompletableFuture.supplyAsync(()->adapter.execute(ToolBoundary.Capability.PROPOSAL,id,"branch",new Call("p1","proposeCodeEdit",ReactContext.json(Map.of("ref",ref))),()->true,generate).join(),e.workers);
            assertTrue(started.await(5,TimeUnit.SECONDS));
            var duplicate=adapter.execute(ToolBoundary.Capability.PROPOSAL,id,"branch",new Call("p2","proposeCodeEdit",ReactContext.json(Map.of("ref",ref))),()->true,generate);
            release.countDown();
            if(fail){assertThrows(ExecutionException.class,()->first.get(10,TimeUnit.SECONDS));assertThrows(ExecutionException.class,()->duplicate.get(10,TimeUnit.SECONDS));}
            else assertEquals(first.get(10,TimeUnit.SECONDS),duplicate.get(10,TimeUnit.SECONDS));
            var again=adapter.execute(ToolBoundary.Capability.PROPOSAL,id,"branch",new Call("p3","proposeCodeEdit",ReactContext.json(Map.of("ref",ref))),()->true,generate);
            if(fail)assertThrows(ExecutionException.class,again::get);else assertEquals(first.get(),again.get());
            assertEquals(1,subscriptions.get());
            assertTrue(adapter.execute(ToolBoundary.Capability.TARGET,id,"fallback",resolve,()->true).get().contains("NEW_DRAFT_REQUIRED"));assertEquals(1,reads.get());
            assertThrows(ProtocolFailure.class,()->adapter.execute(ToolBoundary.Capability.PROPOSAL,id,"fallback",new Call("p4","proposeCodeEdit",ReactContext.json(Map.of("ref",ref))),()->true,generate));
            assertEquals(1L,e.store.transaction(c->{try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM ca_edit_generation")){r.next();return r.getLong(1);}}).join());
        }
    }
    @Test void stopDuringChildRetainsArchiveButExcludesPrefixFromFutureContext()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            e.generationTimeout=java.time.Duration.ofMinutes(2);
            String session=open(e);AtomicInteger parent=new AtomicInteger(),child=new AtomicInteger(),canceled=new AtomicInteger();
            var entered=new CountDownLatch(1);Model normal=model(parent,child,patch(),false,false);
            e.model.set((request,listener)->{
                if(request.tools().isEmpty()){child.incrementAndGet();listener.frame(new Body("incomplete-child-sentinel"));entered.countDown();return canceled::incrementAndGet;}
                return normal.subscribe(request,listener);
            });
            e.submit("i",session);assertTrue(entered.await(10,TimeUnit.SECONDS));
            String owner=e.sessions.snapshot("i").projectId();
            SessionTestEnvironment.await(()->e.repository.history(owner,"i",session).stream().flatMap(t->t.attempts().stream()).flatMap(a->a.branches().stream()).flatMap(b->b.steps().stream()).anyMatch(s->s.body().contains("incomplete-child-sentinel")));
            var op=e.view("i",session).activeOperation().orElseThrow();
            // Reproduce observation/user latency beyond the fixture's ordinary 10-second deadline.
            // This scenario tests explicit cancellation, independently of the deadline-limit tests.
            Thread.sleep(11_000);
            assertEquals(Outcome.APPLIED,e.dispatch(new StopOperation(e.meta("i",session),op.operationId())).outcome());e.idle("i",session);
            String project=e.sessions.snapshot("i").projectId();var history=e.repository.history(project,"i",session);var attempt=history.getFirst().attempts().getLast();
            assertEquals(AttemptStatus.STOPPED,attempt.status());assertTrue(canceled.get()>0);assertEquals(1,child.get());
            assertTrue(e.repository.edits().views(project,"i",session).isEmpty());
            assertFalse(ReactContext.history(project,session,history).toString().contains("incomplete-child-sentinel"));
            assertTrue(attempt.branches().stream().flatMap(b->b.steps().stream()).anyMatch(s->s.body().contains("incomplete-child-sentinel")));
        }
    }
    @Test void continuationUsesOnlyEligibleHostTargetAndExplicitCluesOverride() throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            String session=open(e),project=e.sessions.snapshot("i").projectId();
            var submission=e.repository.submit(project,e.submitEvent("i",session),e.runtime.snapshot().epoch());
            Identity id=submission.operation().orElseThrow().identity();Input input=new Input("3",List.of());
            var oldId=new Identity(project,"i",session,"old-turn","old-operation","old-attempt",id.runtimeEpoch());
            var oldInput=new Input("Change the selected method",List.of());
            var target=new Target("file:///src/X.java","src/X.java","m","demo.X#f(java.util.Map<java.lang.String,java.lang.Integer>,int)","vfs:1");
            var protocol=List.of(new Message("assistant","","",List.of(new Call("old-resolve","resolveEditTarget","{}")),""),
                    new Message("tool",ReactContext.json(Map.of("status","UNIQUE","target",target)),"",List.of(),"old-resolve"),
                    Message.text("assistant","Choose 3: use a local variable."));
            var step=new Step("final",1,StepStatus.FINISHED,"Choose 3: use a local variable.","",List.of(),"stop",Optional.empty(),List.of());
            var branch=new Branch("old-branch",BranchStatus.COMPLETED,RetrievalStatus.NO_INDEX,"",List.of(step),protocol);
            for(boolean eligible:List.of(true,false))for(String path:List.of("","other/X.java"))for(String type:List.of("X","","Other"))for(String method:List.of("f","")) {
                var old=new Snapshot(oldId,oldInput,1,1,eligible?AttemptStatus.SUCCEEDED:AttemptStatus.STOPPED,true,"old-branch","final",List.of(branch),List.of(),"");
                AtomicReference<TargetQuery> seen=new AtomicReference<>();
                var adapter=new CodeEditTools(id,input,(request,query)->{seen.set(query);assertTrue(request.captureId().isEmpty());return CompletableFuture.completedFuture(new TargetResolution(ResolutionStatus.NOT_FOUND,List.of(),Optional.empty()));},e.repository.edits(),1024,List.of(new HistoryTurn(oldInput,List.of(old))));
                var call=new Call("resolve","resolveEditTarget",ReactContext.json(Map.of("path",path,"class",type,"method",method,"parameters",List.of("int"))));
                adapter.execute(ToolBoundary.Capability.TARGET,id,"branch",call,()->true).get(5,TimeUnit.SECONDS);
                assertEquals(eligible&&path.isEmpty()&&!type.equals("Other")?"src/X.java":path,seen.get().pathHint());
                if(eligible&&path.isEmpty()&&type.isEmpty()&&method.isEmpty()) {
                    assertEquals("f",seen.get().methodHint());assertEquals(List.of("java.util.Map<java.lang.String,java.lang.Integer>","int"),seen.get().parameterHints());
                }
            }
            var fileTarget=new Target(target.fileRef(),target.relativePath(),target.module(),"file",target.fileIdentity());
            var fileProtocol=protocol.stream().map(m->m.role().equals("tool")?new Message("tool",ReactContext.json(Map.of("status","UNIQUE","target",fileTarget)),"",List.of(),m.callId()):m).toList();
            var fileBranch=new Branch("old-branch",BranchStatus.COMPLETED,RetrievalStatus.NO_INDEX,"",List.of(step),fileProtocol);
            var fileOld=new Snapshot(oldId,oldInput,1,1,AttemptStatus.SUCCEEDED,true,"old-branch","final",List.of(fileBranch),List.of(),"");
            var fileAdapter=new CodeEditTools(id,input,(r,q)->{throw new AssertionError("Must not widen an unspecified previous selection to the entire file");},e.repository.edits(),1024,List.of(new HistoryTurn(oldInput,List.of(fileOld))));
            String unresolved=fileAdapter.execute(ToolBoundary.Capability.TARGET,id,"branch",new Call("r","resolveEditTarget",ReactContext.json(Map.of("path","","class","","method","","parameters",List.of()))),()->true).get();
            assertEquals("INCOMPLETE",EditPatch.JSON.readTree(unresolved).get("status").asString());
            var classTarget=new Target(target.fileRef(),target.relativePath(),target.module(),"demo.X",target.fileIdentity());
            var classProtocol=protocol.stream().map(m->m.role().equals("tool")?new Message("tool",ReactContext.json(Map.of("status","UNIQUE","target",classTarget)),"",List.of(),m.callId()):m).toList();
            var classBranch=new Branch("old-branch",BranchStatus.COMPLETED,RetrievalStatus.NO_INDEX,"",List.of(step),classProtocol);
            var classOld=new Snapshot(oldId,oldInput,1,1,AttemptStatus.SUCCEEDED,true,"old-branch","final",List.of(classBranch),List.of(),"");
            var classAdapter=new CodeEditTools(id,input,(r,q)->{assertEquals("demo.X",q.classHint());assertEquals("src/X.java",q.pathHint());return CompletableFuture.completedFuture(new TargetResolution(ResolutionStatus.NOT_FOUND,List.of(),Optional.empty()));},e.repository.edits(),1024,List.of(new HistoryTurn(oldInput,List.of(classOld))));
            classAdapter.execute(ToolBoundary.Capability.TARGET,id,"branch",new Call("r","resolveEditTarget",ReactContext.json(Map.of("path","","class","","method","","parameters",List.of()))),()->true).get();
            var foreign=new Snapshot(new Identity("foreign","i",session,"t","o","a",0),oldInput,1,1,AttemptStatus.SUCCEEDED,true,"old-branch","final",List.of(branch),List.of(),"");
            assertThrows(ProtocolFailure.class,()->new CodeEditTools(id,input,(r,q)->null,e.repository.edits(),1024,List.of(new HistoryTurn(oldInput,List.of(foreign)))));
        }
    }
    @Test void legacyRetryBeforeAnyEditToolKeepsF0ButCannotStartF2()throws Exception {
        try(var e=new SessionTestEnvironment(root)) {
            String session=open(e);e.model.set((request,listener)->{listener.failed(new IllegalStateException("before tools"));return ()->{};});
            e.submit("i",session);e.idle("i",session);String project=e.sessions.snapshot("i").projectId();var attempt=e.repository.history(project,"i",session).getFirst().attempts().getLast();
            e.model.set((request,listener)->{assertTrue(request.tools().isEmpty());SessionTestEnvironment.finish(listener,"F0 retry answer");return ()->{};});
            assertEquals(Outcome.APPLIED,e.dispatch(new RetryAttempt(e.meta("i",session),attempt.identity().turnId(),attempt.identity().attemptId())).outcome());
            e.idle("i",session);assertEquals(2,e.requests.size());assertTrue(e.repository.edits().views(project,"i",session).isEmpty());
        }
    }
}
