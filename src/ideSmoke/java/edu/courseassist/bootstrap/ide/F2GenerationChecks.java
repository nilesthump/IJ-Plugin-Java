package edu.courseassist.bootstrap.ide;

import com.sun.net.httpserver.HttpServer;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.core.edit.EditPatch;
import edu.courseassist.core.react.ReactContext;
import edu.courseassist.core.session.SessionData.Location;
import edu.courseassist.model.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;

/** Loopback-only real Spring AI F2 protocol and guarded H2 promotion, reused by JUnit and IDEA. */
public final class F2GenerationChecks {
    private F2GenerationChecks(){}
    public static void run(Path root)throws Exception {
        String before="class X { int n=1; }";AtomicInteger calls=new AtomicInteger();AtomicReference<Throwable> failure=new AtomicReference<>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat",exchange->{
            try {
                var input=EditPatch.JSON.readTree(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));int n=calls.incrementAndGet();String delta,finish;
                if(n==1||n==2) {
                    require(input.get("tools").size()==2,"Only target and proposal tools without course library");
                    String args;
                    if(n==1)args="{\"path\":\"X.java\",\"class\":\"\",\"method\":\"\",\"parameters\":[]}";
                    else {
                        String ref=null;for(var message:input.get("messages"))if(message.hasNonNull("tool_call_id")&&message.get("tool_call_id").asString().equals("resolve"))ref=EditPatch.JSON.readTree(message.get("content").asString()).get("snapshotRef").asString();
                        require(ref!=null,"Paired target observation supplies host reference");args=ReactContext.json(Map.of("ref",ref));
                    }
                    delta=ReactContext.json(Map.of("tool_calls",List.of(Map.of("index",0,"id",n==1?"resolve":"proposal","type","function","function",Map.of("name",n==1?"resolveEditTarget":"proposeCodeEdit","arguments",args)))));finish="tool_calls";
                } else if(n==3) {
                    require(!input.hasNonNull("tools")||input.get("tools").isEmpty(),"Child has no tools");
                    require(input.get("tool_choice").asString().equals("none"),"Child forbids recursive tools");
                    int at=before.indexOf("1");String patch=ReactContext.json(Map.of("changes",List.of(Map.of("start",at,"end",at+1,"before","1","after","2"))));
                    delta=ReactContext.json(Map.of("content",patch));finish="stop";
                } else {
                    require(n==4,"Exactly one bounded child subscription");require(input.get("tools").size()==2,"Parent resumes its own capabilities");
                    delta=ReactContext.json(Map.of("content","Candidate prepared, not applied."));finish="stop";
                }
                byte[] bytes=(ReactRuntimeChecks.event(delta,finish)+"data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            }catch(Throwable ex){failure.set(ex);exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}
        });server.start();
        try(var e=new SessionTestEnvironment(root.resolve("run-"+UUID.randomUUID()));var transport=new SpringAiTransport(Duration.ofSeconds(3),Duration.ofSeconds(5))) {
            e.configureAndEnable();
            var binding=e.sessions.open(new Location("C:/synthetic/f2-http","f2-http"),"i",()->start->CompletableFuture.completedFuture(SessionTestEnvironment.group(start)),true,
                    Optional.of((identity,input,permitted)->(request,query)->{
                        var target=new Target("file:///X.java","X.java","fixture","X");
                        return CompletableFuture.completedFuture(new TargetResolution(ResolutionStatus.UNIQUE,List.of(target),Optional.of(new EditSnapshot(1,"snapshot",request,target,1,before,List.of(new Span(0,before.length()))))));
                    })).toCompletableFuture().get(15,TimeUnit.SECONDS);
            String session=e.create("i");e.text("i",session,"Change n to 2 in X.java");
            e.model.set(new SpringAiCourseModel(transport.webClient().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).build(),"/chat","local-fixture",Optional.empty(),true));
            e.submit("i",session);e.idle("i",session);
            if(failure.get()!=null)throw new IllegalStateException("Loopback F2 contract",failure.get());
            require(calls.get()==4,"Three parent steps plus one child");require(e.queries.isEmpty(),"No embedding call without course library");
            var views=e.repository.edits().views(binding.projectId(),"i",session);require(views.size()==1&&views.getFirst().review()==ReviewState.REVIEWABLE,"Durable parent promotes exact candidate");
            var proposal=e.repository.edits().load(binding.projectId(),"i",session,views.getFirst().proposal());require(proposal.after().equals(before.replace("1","2")),"Exact structured replacement");
            require(!ReactContext.history(binding.projectId(),session,e.repository.history(binding.projectId(),"i",session)).toString().contains("\"changes\""),"Child raw output excluded from future protocol");
        }finally{server.stop(0);}
    }
}
