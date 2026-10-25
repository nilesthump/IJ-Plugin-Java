package edu.courseassist.model;

import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CredentialUpdate.Slot;
import edu.courseassist.core.session.CredentialVault;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnableDiagnosticsTest {
    private ProviderConfiguration profiles(Optional<Integer> dimensions) {
        return new ProviderConfiguration(new ProviderProfile("g","deepseek",URI.create("https://api.deepseek.com"),"deepseek-flash",Optional.empty()),
            new ProviderProfile("e","course",URI.create("https://agi.tongji.edu.cn"),"Qwen3-Embedding",dimensions));
    }
    private CredentialVault vault(Slot missing) {
        return new CredentialVault() {
            public char[] read(Slot slot) { return slot==missing ? new char[0] : "synthetic-no-network".toCharArray(); }
            public void write(Slot slot,char[] value) { fail("no credential mutation"); }
        };
    }
    @Test void missingGenerationKeyIsNamedWithoutGenericPdfAdvice() {
        var error=assertThrows(IllegalArgumentException.class,()->new ConfiguredRuntimeFactory().create(profiles(Optional.of(1024)),vault(Slot.GENERATION)));
        assertTrue(ProviderFailure.message(error).contains("生成 API key"));
    }
    @Test void missingEmbeddingKeyIsNamed() {
        var error=assertThrows(IllegalArgumentException.class,()->new ConfiguredRuntimeFactory().create(profiles(Optional.of(1024)),vault(Slot.EMBEDDING)));
        assertTrue(ProviderFailure.message(error).contains("嵌入 API key"));
    }
    @Test void missingDimensionIsNamed() {
        var error=assertThrows(IllegalArgumentException.class,()->new ConfiguredRuntimeFactory().create(profiles(Optional.empty()),vault(null)));
        assertTrue(ProviderFailure.message(error).contains("嵌入维度"));
    }
    @Test void suppliedNonSecretModelConfigurationConstructsWithoutCallingProviders() throws Exception {
        try(var resources=new ConfiguredRuntimeFactory().create(profiles(Optional.of(1024)),vault(null))) {
            assertEquals("Qwen3-Embedding",resources.embeddings().space().model());
        }
    }
    @Test void syntheticNonAsciiKeyReproducesOriginalHeaderFailureBeforeNetwork() throws Exception {
        var client=new com.openai.client.OpenAIClientImpl(com.openai.core.ClientOptions.builder()
                .baseUrl("http://127.0.0.1:1").apiKey("synthetic-非法")
                .maxRetries(0).httpClient(org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient.builder().build()).build());
        try {
            var model=org.springframework.ai.openai.OpenAiEmbeddingModel.builder().openAiClient(client)
                    .options(org.springframework.ai.openai.OpenAiEmbeddingOptions.builder().model("synthetic").dimensions(3).build()).build();
            var error=assertThrows(IllegalArgumentException.class,()->model.call(new org.springframework.ai.embedding.EmbeddingRequest(java.util.List.of("synthetic"),org.springframework.ai.embedding.EmbeddingOptions.builder().build())));
            assertTrue(java.util.Arrays.stream(error.getStackTrace()).anyMatch(f->f.getClassName().equals("okhttp3.Headers$Companion")&&f.getMethodName().equals("checkValue")));
            assertTrue(ProviderFailure.message(error).contains("请求头"));assertFalse(ProviderFailure.message(error).contains("synthetic-非法"));
        } finally {client.close();}
    }
    @Test void invalidEmbeddingCredentialIsRejectedBeforeAnyRequestWithoutLeakingValue() {
        var invalid=new CredentialVault() {
            public char[] read(Slot slot){return (slot==Slot.EMBEDDING?"synthetic-隐藏\n":"synthetic-valid").toCharArray();}
            public void write(Slot slot,char[] value){fail("No writes");}
        };
        var error=assertThrows(IllegalArgumentException.class,()->new ConfiguredRuntimeFactory().create(profiles(Optional.of(1024)),invalid));
        String notice=ProviderFailure.message(error);assertTrue(notice.contains("嵌入 API key"));assertTrue(notice.contains("字符"));assertFalse(notice.contains("synthetic"));
    }
    @Test void unknownFailureIncludesTypeButNeverRawMessage() {
        String view=ProviderFailure.message(new IllegalStateException("synthetic-secret-do-not-display"));
        assertTrue(view.contains("IllegalStateException")); assertFalse(view.contains("synthetic-secret"));
        assertFalse(view.contains("原始原因保留在任务诊断中"));
    }
}
