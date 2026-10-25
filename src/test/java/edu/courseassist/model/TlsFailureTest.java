package edu.courseassist.model;

import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CredentialUpdate.Slot;
import edu.courseassist.core.corpus.CourseLibrary;
import edu.courseassist.core.session.CredentialVault;
import edu.courseassist.storage.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TlsFailureTest {
    @TempDir Path temp;

    @Test void closedTlsHandshakeIsNetworkFailureAndNeverPublishesPdf() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             var peer = Executors.newSingleThreadExecutor(); var workers = Executors.newCachedThreadPool();
             var store = new H2Store(temp.resolve("db"));
             var library = new CourseLibrary(new CourseRepository(store), new ManagedPdfStore(temp.resolve("pdf")),
                     edu.courseassist.pdf.PdfBoxPdfReader::new, workers)) {
            server.setSoTimeout(10000);
            var firstByte = peer.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    var input = new java.io.DataInputStream(socket.getInputStream());
                    int recordType = input.readUnsignedByte();
                    input.readUnsignedShort();
                    int length = input.readUnsignedShort();
                    if (input.readNBytes(length).length != length) throw new java.io.EOFException("Incomplete synthetic ClientHello");
                    socket.shutdownOutput(); // Clean FIN after the complete ClientHello, before any HTTP.
                    while (input.read() != -1) { /* Drain the client's TLS alert before closing. */ }
                    return recordType;
                }
            });
            URI endpoint = URI.create("https://127.0.0.1:" + server.getLocalPort() + "/v1");
            var generation = new ProviderProfile("g", "local", endpoint, "synthetic-chat", Optional.empty());
            var embedding = new ProviderProfile("e", "local", endpoint, "synthetic-embedding", Optional.of(3));
            CredentialVault vault = new CredentialVault() {
                public char[] read(Slot slot) { return "synthetic-only".toCharArray(); }
                public void write(Slot slot, char[] value) { fail("No credential writes"); }
            };
            try (var resources = new ConfiguredRuntimeFactory().create(new ProviderConfiguration(generation, embedding), vault)) {
                library.initialize().get(10, TimeUnit.SECONDS);
                var before = library.snapshot().get().base();
                var job = library.importPdf(Path.of("spec/tasks/dev-manager/blackbox-fixtures/course-a-v1.pdf").toAbsolutePath(),
                        "TLS fixture", before, resources.corpus(), resources.embeddings(), () -> true);
                var failure = assertThrows(ExecutionException.class,
                        () -> job.completion().toCompletableFuture().get(20, TimeUnit.SECONDS));
                job.settled().toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertEquals(22, firstByte.get(10, TimeUnit.SECONDS)); // TLS handshake record, not an HTTP request.
                assertTrue(ProviderFailure.safeDiagnostic(failure).contains(SSLHandshakeException.class.getName()), ProviderFailure.safeDiagnostic(failure));
                String message = ProviderFailure.message(failure);
                assertTrue(message.contains("TLS"), message);
                assertFalse(message.contains("本地处理失败"), message);
                assertFalse(message.contains("synthetic"), message);
                assertEquals(before, library.snapshot().get().base());
                assertEquals(MaterialStatus.FAILED, library.snapshot().get().materials().getFirst().status());
            }
        }
    }

    @Test void ordinaryLocalEofIsNotMislabelledAsNetworkAndMessagesRemainPrivate() {
        var local = new java.io.EOFException("synthetic-private-path");
        assertTrue(ProviderFailure.message(local).contains("本地处理失败（EOFException）"));
        var tls = new SSLHandshakeException("synthetic-private-host"); tls.initCause(local);
        String message = ProviderFailure.message(new CompletionException(tls));
        assertTrue(message.contains("TLS"));
        assertFalse(message.contains("synthetic"));
        assertFalse(ProviderFailure.safeDiagnostic(tls).contains("synthetic"));
    }
}
