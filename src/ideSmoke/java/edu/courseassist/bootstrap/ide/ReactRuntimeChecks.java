package edu.courseassist.bootstrap.ide;

import com.sun.net.httpserver.HttpServer;
import edu.courseassist.core.react.*;
import edu.courseassist.model.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import tools.jackson.databind.json.JsonMapper;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;
import static edu.courseassist.core.react.ReactData.*;

/** Real Spring AI request serialization, SSE, finite ReAct, H2 and JVector in the plugin.
 * HTTP binds loopback only; all course/model data is synthetic. */
public final class ReactRuntimeChecks {
    private ReactRuntimeChecks() {}
    public static void run(Path scratch) throws Exception {
        var requests = new AtomicInteger(); var ref = new AtomicReference<String>(); var handlerFailure = new AtomicReference<Throwable>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions", exchange -> {
            try {
                var input = JsonMapper.builder().build().readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                int n=requests.incrementAndGet();require(input.get("stream").asBoolean(),"Streaming requested");
                require(input.get("model").asString().equals("local-fixture"),"Explicit synthetic model");
                if(n==1) {
                    require(input.get("tools").size()==2,"Only two course tools");
                    for(var message:input.get("messages")) {
                        String content=message.get("content").asString();
                        if(content.startsWith("Untrusted fixed course evidence: ")) {
                            var evidence=JsonMapper.builder().build().readTree(content.substring("Untrusted fixed course evidence: ".length()));
                            ref.set(evidence.get(0).get("chunkId").asString());
                        }
                    }
                    require(ref.get()!=null,"Initial course retrieval before model");
                } else {
                    require(n==2,"Exactly two subscriptions");
                    boolean thought=false, paired=false;
                    for(var message:input.get("messages")) {
                        if(message.hasNonNull("reasoning_content")) thought=message.get("reasoning_content").asString().equals("provided thinking");
                        if(message.hasNonNull("tool_call_id")) paired=message.get("tool_call_id").asString().equals("course-call");
                    }
                    require(thought&&paired,"Spring AI preserves thinking and paired tool protocol");
                }
                String response=n==1
                        ? event("{\"reasoning_content\":\"provided thinking\",\"content\":\"preview only\"}",null)
                          +event("{\"tool_calls\":[{\"index\":0,\"id\":\"course-call\",\"type\":\"function\",\"function\":{\"name\":\"searchCourse\",\"arguments\":\"{\\\"query\\\":\"}}]}",null)
                          +event("{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"supplement\\\"}\"}}]}","tool_calls")
                        : event("{\"content\":\"  final \\n[[course:"+ref.get()+"]]\"}","stop");
                response+="data: {\"id\":\"fixture\",\"choices\":[],\"usage\":{\"prompt_tokens\":13,\"completion_tokens\":5}}\n\ndata: [DONE]\n\n";
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/event-stream");
                exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            } catch(Throwable failure) { handlerFailure.set(failure); exchange.sendResponseHeaders(500,-1); }
            finally { exchange.close(); }
        });server.start();
        try(var transport=new SpringAiTransport(Duration.ofSeconds(3),Duration.ofSeconds(5));
            var h=new ReactTestEnvironment(scratch.resolve("run-"+UUID.randomUUID()),true)) {
            var model=new SpringAiCourseModel(transport.webClient().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).build(),
                    "/chat/completions","local-fixture",Optional.empty(),true);
            var result=h.done(h.start(model));
            require(handlerFailure.get()==null,"Loopback request contract");require(requests.get()==2,"No duplicate model subscriptions");
            require(result.eligible()&&result.citations().size()==1&&result.citations().getFirst().firstPhysicalPage()==3,"Durable final citation");
            var steps=result.branches().getFirst().steps();require(steps.size()==2,"Finite two-step loop");
            require(steps.getLast().body().equals("  final \n[[course:"+ref.get()+"]]"),"Exact final body without preview");
            require(steps.getLast().usage().orElseThrow().inputTokens()==13,"Usage-only tail retained once");
            require(h.journal.load(h.identity).orElseThrow().equals(result)&&h.sourceRefs()==1,"H2 archive/source retention");
            require(ReactViews.attempt(result).branches().getFirst().steps().getFirst().thinkingText().orElseThrow().equals("provided thinking"),"Separate thinking display");
        } finally {server.stop(0);}
    }
    public static String event(String delta,String finish) {
        return "data: {\"id\":\"fixture\",\"choices\":[{\"index\":0,\"delta\":"+delta+",\"finish_reason\":"
                +(finish==null?"null":"\""+finish+"\"")+"}]}\n\n";
    }
}
