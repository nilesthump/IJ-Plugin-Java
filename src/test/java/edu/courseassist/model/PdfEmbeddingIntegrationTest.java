package edu.courseassist.model;

import com.sun.net.httpserver.HttpServer;
import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CredentialUpdate.Slot;
import edu.courseassist.core.corpus.*;
import edu.courseassist.core.session.CredentialVault;
import edu.courseassist.storage.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PdfEmbeddingIntegrationTest {
    @TempDir Path temp;
    @Test void actualPdfThroughConfiguredEmbeddingHttpAndDurableIndex() throws Exception { exercise(false); }
    @Test void mismatchedServiceDimensionsIdentifyConfiguredAndActualWithoutPublishing() throws Exception { exercise(true); }
    @Test void deniedEmbeddingAllowsLocalRemovalAndExplicitRetryWithCorrectRouteAndCredentialSlot() throws Exception {
        var denied = new java.util.concurrent.atomic.AtomicBoolean(true);
        var calls = new java.util.concurrent.CopyOnWriteArrayList<List<String>>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.add(List.of(exchange.getRequestURI().toString(), exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            boolean reject = denied.get();
            byte[] body = (reject
                    ? "{\"error\":{\"message\":\"synthetic-private-provider-body\",\"type\":\"permission_denied\"}}"
                    : "{\"object\":\"list\",\"model\":\"Qwen3-Embedding\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[1,0,0]}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reject ? 403 : 200, body.length);
            try(exchange) { exchange.getResponseBody().write(body); }
        });
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        var g = new ProviderProfile("g", "local", endpoint, "generation-fixture", Optional.empty());
        var e = new ProviderProfile("e", "local", endpoint, "Qwen3-Embedding", Optional.of(3));
        CredentialVault vault = new CredentialVault() {
            public char[] read(Slot slot) { return (slot == Slot.EMBEDDING ? "synthetic-embedding-only" : "synthetic-generation-only").toCharArray(); }
            public void write(Slot slot, char[] value) { throw new UnsupportedOperationException(); }
        };
        try (var resources = new ConfiguredRuntimeFactory().create(new ProviderConfiguration(g,e),vault);
             var workers = Executors.newCachedThreadPool(); var store = new H2Store(temp.resolve("denied-db"));
             var library = new CourseLibrary(new CourseRepository(store),new ManagedPdfStore(temp.resolve("denied-pdf")),edu.courseassist.pdf.PdfBoxPdfReader::new,workers)) {
            library.initialize().get(10, TimeUnit.SECONDS);
            Path file = Path.of("spec/tasks/dev-manager/blackbox-fixtures/course-a-v1.pdf").toAbsolutePath();
            var initial = library.importPdf(file, "denied", library.snapshot().get().base(), resources.corpus(), resources.embeddings(), () -> true);
            var failure = assertThrows(ExecutionException.class, () -> initial.completion().toCompletableFuture().get(20, TimeUnit.SECONDS));
            initial.settled().toCompletableFuture().get();
            assertTrue(ProviderFailure.message(failure).contains("HTTP 403"));
            assertFalse(ProviderFailure.message(failure).contains("synthetic-private"));
            assertFalse(ProviderFailure.safeDiagnostic(failure).contains("synthetic-private"));
            assertEquals(1, calls.size(), "No automatic retry after permission denial");
            var before = library.snapshot().get(); var row = before.materials().getFirst();
            var target = new MaterialTarget(row.documentId(), row.sourceVersionId(), row.materialRevision());
            var replacement = library.replacePdf(file, "retry", before.base(), target, resources.corpus(), resources.embeddings(), () -> true);
            var replaceFailure = assertThrows(ExecutionException.class, () -> replacement.completion().toCompletableFuture().get(20, TimeUnit.SECONDS));
            replacement.settled().toCompletableFuture().get();
            assertTrue(ProviderFailure.message(replaceFailure).contains("HTTP 403"));
            assertEquals(2, calls.size());
            var removal = library.remove(before.base(), target, () -> true);
            removal.completion().toCompletableFuture().get(10, TimeUnit.SECONDS); removal.settled().toCompletableFuture().get();
            assertEquals(2, calls.size(), "Removal must work while embedding access is still denied");
            denied.set(false);
            var retry = library.importPdf(file, "retry", library.snapshot().get().base(), resources.corpus(), resources.embeddings(), () -> true);
            assertEquals(CorpusData.Disposition.PUBLISHED, retry.completion().toCompletableFuture().get(20, TimeUnit.SECONDS).disposition());
            retry.settled().toCompletableFuture().get();
            assertEquals(1, library.snapshot().get().materials().stream().filter(m -> m.status() == MaterialStatus.READY).count());
            for (var call : calls) {
                assertEquals("/v1/embeddings", call.get(0));
                assertEquals("Bearer synthetic-embedding-only", call.get(1));
                assertTrue(call.get(2).contains("Qwen3-Embedding"));
                assertTrue(call.get(2).contains("\"encoding_format\":\"float\""), call.get(2));
            }
        } finally { server.stop(0); }
    }
    private void exercise(boolean mismatch) throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            requests.incrementAndGet(); String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(request.contains("local-embedding"));
            String vector = request.contains("F0-ALPHA") ? "[0,1,0]" : "[1,0,0]";
            byte[] body = ("{\"object\":\"list\",\"model\":\"local-embedding\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":"+vector+"}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            try(exchange) { exchange.getResponseBody().write(body); }
        });
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:"+server.getAddress().getPort());
        var g = new ProviderProfile("g","local",endpoint,"local-generation",Optional.empty());
        var e = new ProviderProfile("e","local",endpoint,"local-embedding",Optional.of(mismatch ? 1024 : 3));
        CredentialVault vault = new CredentialVault() { public char[] read(Slot slot) { return "synthetic-only".toCharArray(); } public void write(Slot slot,char[] v) { throw new UnsupportedOperationException(); } };
        try (var resources = new ConfiguredRuntimeFactory().create(new ProviderConfiguration(g,e),vault);
             var workers = Executors.newCachedThreadPool(); var store = new H2Store(temp.resolve("db"));
             var library = new CourseLibrary(new CourseRepository(store),new ManagedPdfStore(temp.resolve("pdf")),edu.courseassist.pdf.PdfBoxPdfReader::new,workers)) {
            library.initialize().get(10,TimeUnit.SECONDS);
            var job = library.importPdf(Path.of("spec/tasks/dev-manager/blackbox-fixtures/course-a-v1.pdf").toAbsolutePath(),"A-v1",library.snapshot().get().base(),resources.corpus(),resources.embeddings(),()->true);
            if (mismatch) {
                var failed = assertThrows(ExecutionException.class, () -> job.completion().toCompletableFuture().get(20,TimeUnit.SECONDS));
                String notice = ProviderFailure.message(failed);
                assertTrue(notice.contains("1024"), notice); assertTrue(notice.contains("3"), notice);
                job.settled().toCompletableFuture().get();
                assertTrue(library.snapshot().get().base().generationId().isEmpty());
                return;
            }
            var imported = job.completion().toCompletableFuture().get(20,TimeUnit.SECONDS); job.settled().toCompletableFuture().get();
            var material = library.snapshot().get().materials().getFirst(); assertEquals(3,material.physicalPageCount().orElseThrow());
            assertTrue(imported.base().generationId().isPresent()); assertTrue(requests.get() >= 2);
            try (var lease = library.openAttempt()) {
                var hits = lease.search("F0-ALPHA",resources.embeddings(),new CorpusData.SearchSettings(1,0.5f),()->true).get(10,TimeUnit.SECONDS);
                assertEquals(3,hits.getFirst().physicalPage());
            }
        } finally { server.stop(0); }
    }
}
