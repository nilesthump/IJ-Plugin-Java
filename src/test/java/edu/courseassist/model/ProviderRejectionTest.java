package edu.courseassist.model;

import com.openai.core.http.Headers;
import com.openai.errors.PermissionDeniedException;
import com.openai.models.ErrorObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProviderRejectionTest {
    private PermissionDeniedException denial(String contentType, String code, String message) {
        return PermissionDeniedException.builder().headers(Headers.builder().put("Content-Type", contentType).build())
                .error(ErrorObject.builder().code(code).message(message).param(java.util.Optional.empty()).type("synthetic-type").build()).build();
    }
    @Test void actualSdkPreservesHtmlAndJsonDenialClassification() throws Exception {
        for (boolean html : new boolean[]{true, false}) {
            var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/embeddings", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] body=(html ? "<html>synthetic-private-gateway-body</html>"
                        : "{\"error\":{\"code\":\"model_not_allowed\",\"message\":\"synthetic-private-model\",\"type\":\"permission_denied\"}}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",html ? "text/html" : "application/json");
                exchange.sendResponseHeaders(403,body.length);
                try(exchange) {exchange.getResponseBody().write(body);}
            });
            server.start();
            var endpoint=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
            var profile=new edu.courseassist.contract.ApplicationContracts.ProviderProfile("fixture","local",endpoint,"synthetic",java.util.Optional.of(3));
            var vault=new edu.courseassist.core.session.CredentialVault() {
                public char[] read(edu.courseassist.contract.CredentialUpdate.Slot slot) {return "synthetic-only".toCharArray();}
                public void write(edu.courseassist.contract.CredentialUpdate.Slot slot,char[] value) {fail("No writes");}
            };
            try (var runtime=new ConfiguredRuntimeFactory().create(new edu.courseassist.contract.ApplicationContracts.ProviderConfiguration(profile,profile),vault)) {
                var failure=assertThrows(Exception.class,()->runtime.embeddings().document("synthetic input"));
                assertTrue(ProviderFailure.message(failure).contains(html ? "HTML" : "模型访问未授权"),ProviderFailure.message(failure));
                assertFalse(ProviderFailure.message(failure).contains("synthetic-private"));
                assertFalse(ProviderFailure.safeDiagnostic(failure).contains("synthetic-private"));
            } finally {server.stop(0);}
        }
    }
    @Test void htmlRejectionDoesNotClaimTokenPermissionAndLeaksNoBody() {
        var error=denial("text/html; charset=UTF-8", "private-code", "synthetic-secret-body");
        assertTrue(ProviderFailure.message(error).contains("HTML"));
        assertFalse(ProviderFailure.message(error).contains("synthetic"));
        assertFalse(ProviderFailure.safeDiagnostic(error).contains("private-code"));
    }
    @Test void explicitModelDenialIsTranslatedWithoutEchoingNamesOrCredentials() {
        var error=denial("application/json", "model_not_allowed", "synthetic-secret-body");
        assertTrue(ProviderFailure.message(error).contains("模型访问未授权"));
        assertTrue(ProviderFailure.safeDiagnostic(error).contains("MODEL_ACCESS"));
        assertFalse(ProviderFailure.message(error).contains("synthetic"));
    }
    @Test void exactTokenDenialPhraseIsClassifiedWithoutCopyingResponse() {
        var error=denial("application/json", "", "令牌无权访问该模型: synthetic-secret-model");
        assertTrue(ProviderFailure.message(error).contains("模型访问未授权"));
        assertFalse(ProviderFailure.message(error).contains("synthetic"));
    }
    @Test void unknownCodesAndIpTextNeverEscapeIntoViewOrLog() {
        var error=denial("application/json", "synthetic-secret-code", "IP 不在白名单中: 192.0.2.42 synthetic-secret");
        assertTrue(ProviderFailure.message(error).contains("IP"));
        assertFalse(ProviderFailure.message(error).contains("192.0.2.42"));
        assertFalse(ProviderFailure.safeDiagnostic(error).contains("synthetic"));
        var unknown=denial("application/json", "synthetic-secret-code", "synthetic-secret-body");
        assertTrue(ProviderFailure.message(unknown).contains("未识别"));
        assertFalse(ProviderFailure.safeDiagnostic(unknown).contains("synthetic"));
    }
}
