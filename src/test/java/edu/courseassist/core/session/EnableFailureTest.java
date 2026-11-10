package edu.courseassist.core.session;

import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CredentialUpdate.Slot;
import edu.courseassist.core.RuntimeGate;
import edu.courseassist.core.corpus.CourseLibrary;
import edu.courseassist.storage.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EnableFailureTest {
    @TempDir Path root;
    @Test void failedEnableReturnsOffRetainsOriginalCauseAndPublishesSafeDiagnosticId() throws Exception {
        verifyFailure(new IllegalStateException("synthetic-private-response-must-not-leak"));
    }
    @Test void linkageFailureAlsoReturnsOffWithSafeDiagnostic() throws Exception {
        verifyFailure(new NoClassDefFoundError("synthetic-private-response-must-not-leak"));
    }
    @Test void failedRestoreKeepsConfigurationAndOffSettingsAvailable() throws Exception { verifyFailure(new IllegalStateException("synthetic restore failure"),true); }
    private void verifyFailure(Throwable failure) throws Exception { verifyFailure(failure,false); }
    @Test void failedStartupViewCredentialReadAlsoRollsBackToOff() throws Exception { verifyFailure(new IllegalStateException("synthetic credential service failure"),true,true); }
    private void verifyFailure(Throwable failure, boolean restore) throws Exception { verifyFailure(failure,restore,false); }
    private void verifyFailure(Throwable failure, boolean restore, boolean failViewRead) throws Exception {
        var failRead=new java.util.concurrent.atomic.AtomicBoolean(failViewRead);
        var vault = new CredentialVault() {
            public char[] read(Slot slot) { if(failRead.getAndSet(false))throw (RuntimeException)failure;return "synthetic-only".toCharArray(); }
            public void write(Slot slot,char[] value) { fail("No credential writes"); }
        };
        try (var workers=Executors.newCachedThreadPool(); var store=new H2Store(root);
             var library=new CourseLibrary(new CourseRepository(store),new ManagedPdfStore(root.resolve("pdf")),
                     () -> request -> { throw new AssertionError("No PDF work during enable"); },workers);
             var runtime=new RuntimeGate();
             var app=new ApplicationCoordinator(new ApplicationRepository(store),runtime,library,vault,
                     (profiles,keys)->{if(failure instanceof LinkageError linkage)throw linkage;throw (Exception)failure;},workers,error->{throw new AssertionError(error);})) {
            if(restore){var repo=new ApplicationRepository(store);repo.initialize();var profile=new ProviderProfile("restore","synthetic",URI.create("https://example.invalid"),"model",Optional.of(3));repo.save(0,new ProviderConfiguration(profile,profile));repo.enabled(true);}
            app.initialize().toCompletableFuture().get(10,TimeUnit.SECONDS);
            if(restore){assertEquals(WorkState.OFF,app.snapshot().workState());assertTrue(app.snapshot().generationProfile().isPresent());assertTrue(app.snapshot().notices().stream().anyMatch(n->n.code().equals("RESTORE_ENABLE_FAILED")));assertTrue(new ApplicationRepository(store).enabled());app.shutdown().get(10,TimeUnit.SECONDS);return;}
            var p=new ProviderProfile("local","synthetic",URI.create("https://example.invalid"),"model",Optional.of(3));
            app.dispatch(new UpdateProviderConfig(new ApplicationEventMeta("configure",app.snapshot().revision()),
                    app.snapshot().configRevision(),new ProviderConfiguration(p,p),Optional.empty())).toCompletableFuture().get();
            var command=new EnablePlugin(new ApplicationEventMeta("enable-diagnostic-test",app.snapshot().revision()));
            assertSame(failure,assertThrows(ExecutionException.class,()->app.dispatch(command).toCompletableFuture().get()).getCause());
            assertEquals(WorkState.OFF,app.snapshot().workState());
            assertSame(failure,app.failure("enable-diagnostic-test").orElseThrow());
            String notice=app.snapshot().notices().getLast().message();
            assertTrue(notice.contains("启用失败"));assertTrue(notice.contains("enable-diagnostic-test"));
            assertTrue(notice.contains(failure.getClass().getSimpleName()));assertFalse(notice.contains("synthetic-private"));
            String log=edu.courseassist.model.ProviderFailure.safeDiagnostic(failure);
            assertTrue(log.contains("EnableFailureTest"));assertFalse(log.contains("synthetic-private"));
            app.shutdown().get(10,TimeUnit.SECONDS);
        }
    }
}
