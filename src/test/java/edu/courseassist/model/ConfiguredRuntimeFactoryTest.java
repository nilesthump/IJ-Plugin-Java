package edu.courseassist.model;

import com.sun.net.httpserver.HttpServer;
import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CredentialUpdate.Slot;
import edu.courseassist.core.react.ReactData.*;
import edu.courseassist.core.session.CredentialVault;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguredRuntimeFactoryTest {
    @Test void rejectsChatOnlyDeepSeekEmbeddingBeforeCredentialsOrNetwork() {
        var g = new ProviderProfile("g", "deepseek", URI.create("https://api.deepseek.com"), "deepseek-flash", Optional.empty());
        var e = new ProviderProfile("e", "deepseek", URI.create("https://api.deepseek.com"), "deepseek-flash", Optional.of(1024));
        CredentialVault vault = new CredentialVault() {
            public char[] read(Slot slot) { fail("invalid embedding must be rejected before reading credentials"); return null; }
            public void write(Slot slot, char[] value) { fail("no write"); }
        };
        var failure = assertThrows(IllegalArgumentException.class, () -> new ConfiguredRuntimeFactory().create(new ProviderConfiguration(g,e), vault));
        assertTrue(failure.getMessage().contains("嵌入"));
    }
    @Test void firstCredentialCopyIsClearedWhenReadingSecondSlotFails() {
        char[] copied="synthetic-sensitive-copy".toCharArray();
        CredentialVault vault=new CredentialVault(){public char[] read(Slot slot){if(slot==Slot.EMBEDDING)throw new IllegalStateException("synthetic vault failure");return copied;}
            public void write(Slot slot,char[] value){throw new UnsupportedOperationException();}};
        var profile=new ProviderProfile("p","synthetic",URI.create("https://example.invalid"),"explicit",Optional.of(3));
        assertThrows(IllegalStateException.class,()->new ConfiguredRuntimeFactory().create(new ProviderConfiguration(profile,profile),vault));
        assertArrayEquals(new char[copied.length],copied);
    }
    @Test void productionFactoryConstructsLocallyAndUsesOnlyExplicitLoopbackEndpoints() throws Exception {
        AtomicInteger requests=new AtomicInteger();var chatBody=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",exchange->{requests.incrementAndGet();chatBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body="data: {\"id\":\"configured-local\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"configured answer\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,body.length);try(exchange){exchange.getResponseBody().write(body);}});
        server.createContext("/embeddings",exchange->{requests.incrementAndGet();exchange.getRequestBody().readAllBytes();
            byte[] body="{\"object\":\"list\",\"model\":\"configured-embedding\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[1,0,0]}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);try(exchange){exchange.getResponseBody().write(body);}});
        server.start();
        CredentialVault vault=new CredentialVault(){public char[] read(Slot slot){return "synthetic-loopback-only".toCharArray();}public void write(Slot slot,char[] value){throw new UnsupportedOperationException();}};
        URI endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
        var generation=new ProviderProfile("g","synthetic",endpoint,"configured-generation",Optional.empty());
        var embedding=new ProviderProfile("e","synthetic",endpoint,"configured-embedding",Optional.of(3));
        try(var configured=new ConfiguredRuntimeFactory().create(new ProviderConfiguration(generation,embedding),vault)) {
            assertEquals(32768,configured.limits().outputTokens());
            assertEquals(163840,configured.limits().contextTokens());
            assertEquals(131072,configured.context().inputLimit());
            assertEquals(98304,configured.context().autoTrigger());
            assertEquals(8192,configured.context().summaryOutput());
            assertEquals(32768,configured.context().maxSummaryChars());
            assertTrue(configured.limits().contextTokens()-configured.limits().outputTokens()>=configured.context().inputLimit());
            assertTrue(configured.limits().maxOutputChars()>=configured.limits().outputTokens()*4);
            assertEquals(0,requests.get());assertArrayEquals(new float[]{1,0,0},configured.embeddings().query("exact query"));
            CountDownLatch complete=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();List<Frame> frames=new CopyOnWriteArrayList<>();
            Cancel cancel=configured.model().subscribe(new Request(List.of(Message.text("user","question")),List.of(),
                    configured.limits().outputTokens()),new Listener(){
                public void frame(Frame frame){frames.add(frame);}public void failed(Throwable failure){error.set(failure);complete.countDown();}public void complete(){complete.countDown();}});
            assertTrue(complete.await(10,TimeUnit.SECONDS));cancel.cancel();assertNull(error.get());assertTrue(frames.contains(new Body("configured answer")));assertEquals(2,requests.get());
            assertNotNull(chatBody.get());assertTrue(chatBody.get().contains("\"max_tokens\":32768"),chatBody.get());
        }finally{server.stop(0);}
    }
}
