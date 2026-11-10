package edu.courseassist.core.react;

import edu.courseassist.contract.ChatContracts.FailureCode;
import edu.courseassist.contract.ChatContracts.FailurePhase;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import static org.junit.jupiter.api.Assertions.*;

class GenerationDiagnosticsTest {
    @Test void httpProviderJsonCancellationAndUnknownAreClassifiedWithoutCopyingMessages() {
        String sentinel="provider-private-body-sentinel";
        var http=WebClientResponseException.create(503,"Service Unavailable",HttpHeaders.EMPTY,
                sentinel.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);
        var diagnosis=GenerationDiagnostics.diagnose(http);
        assertEquals(FailureCode.PROVIDER_FAILURE,diagnosis.failure().code());
        assertEquals(FailurePhase.MODEL_GENERATION,diagnosis.failure().phase());
        assertFalse(GenerationDiagnostics.message(diagnosis.failure()).contains(sentinel));
        assertFalse(GenerationDiagnostics.notice(http).orElseThrow().contains(sentinel));
        assertTrue(GenerationDiagnostics.notice(http).orElseThrow().contains("PROVIDER_FAILURE"));

        Throwable json=assertThrows(RuntimeException.class,()->tools.jackson.databind.json.JsonMapper.builder().build().readTree("{"));
        assertEquals(FailureCode.MODEL_PROTOCOL,GenerationDiagnostics.diagnose(json).failure().code());

        var cancelled=GenerationDiagnostics.diagnose(new CancellationException(sentinel));
        assertEquals(FailureCode.CANCELLED,cancelled.failure().code());
        assertEquals(FailurePhase.LIFECYCLE,cancelled.failure().phase());

        var unknown=GenerationDiagnostics.diagnose(new IllegalStateException(sentinel));
        assertEquals(FailureCode.INTERNAL,unknown.failure().code());
        assertEquals(FailurePhase.INTERNAL,unknown.failure().phase());
        assertTrue(GenerationDiagnostics.notice(new IllegalStateException(sentinel)).isEmpty());
    }
}
