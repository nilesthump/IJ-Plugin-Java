package edu.courseassist.model;

import com.sun.net.httpserver.HttpServer;
import edu.courseassist.bootstrap.ide.ReactRuntimeChecks;
import edu.courseassist.core.react.ReactData.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SpringAiCourseModelTest {
    @TempDir Path scratch;
    @Test void springAiStrictStreamDrivesDurableFiniteReact() throws Exception { ReactRuntimeChecks.run(scratch); }
    @Test void malformedFramesEofHttpAndUnknownFinishesRetainFailureWithoutRetry() throws Exception {
        List<String> streams=List.of(
                ReactRuntimeChecks.event("{\"content\":\"accepted-prefix\"}",null)+"data: {broken-json}\n\n",
                ReactRuntimeChecks.event("{\"content\":\"accepted-prefix\"}","stop"),
                "data: {\"choices\":[{\"index\":1,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":1}}\n\ndata: [DONE]\n\n",
                "http-error",
                ReactRuntimeChecks.event("{\"content\":\"body\"}","future_reason")+"data: [DONE]\n\n");
        for(int i=0;i<streams.size();i++) {
            String stream=streams.get(i);var requests=new AtomicInteger();
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/chat",e->{requests.incrementAndGet();e.getRequestBody().readAllBytes();
                byte[] bytes=stream.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","text/event-stream");
                e.sendResponseHeaders(stream.equals("http-error")?503:200,bytes.length);try(e){e.getResponseBody().write(bytes);}});server.start();
            try(var transport=new SpringAiTransport(Duration.ofSeconds(2),Duration.ofSeconds(3))) {
                var model=new SpringAiCourseModel(transport.webClient().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).build(),"/chat","fixture",Optional.empty(),true);
                var frames=new CopyOnWriteArrayList<Frame>();var complete=new CompletableFuture<Void>();
                var cancel=model.subscribe(new Request(List.of(Message.text("user","question")),List.of(),32),new Listener(){
                    public void frame(Frame frame){frames.add(frame);}public void failed(Throwable failure){complete.completeExceptionally(failure);}public void complete(){complete.complete(null);}});
                try {
                    if(i==5) {complete.get(5,TimeUnit.SECONDS);assertTrue(frames.contains(new End("future_reason")));}
                    else {var failure=assertThrows(ExecutionException.class,()->complete.get(5,TimeUnit.SECONDS));assertNotNull(failure.getCause());}
                    if(i<2) assertTrue(frames.contains(new Body("accepted-prefix")));
                    assertEquals(1,requests.get());
                } finally {cancel.cancel();}
            } finally {server.stop(0);}
        }
    }
}
