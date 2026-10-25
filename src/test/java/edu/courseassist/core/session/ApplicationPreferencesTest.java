package edu.courseassist.core.session;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.PdfContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.storage.ApplicationRepository;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static edu.courseassist.bootstrap.ide.SessionTestEnvironment.await;

class ApplicationPreferencesTest {
    @TempDir Path root;
    @Test void explicitPreferenceSurvivesShutdownButNoOldModelRequestsResume() throws Exception {
        var first=new SessionTestEnvironment(root);
        assertEquals(WorkState.OFF,first.app.snapshot().workState());
        first.configureAndEnable();first.closeForRestart();
        try(var second=new SessionTestEnvironment(root)) {
            assertEquals(WorkState.ON,second.app.snapshot().workState());assertTrue(second.requests.isEmpty());
            assertEquals(Outcome.APPLIED,second.app.dispatch(new DisablePlugin(second.appMeta())).toCompletableFuture().get().outcome());
        }
        try(var third=new SessionTestEnvironment(root)){assertEquals(WorkState.OFF,third.app.snapshot().workState());}
    }
    @Test void legacyProfileSchemaRetainsConfigAndDefaultsOff() throws Exception {
        try(var store=new edu.courseassist.storage.H2Store(root)) {
            store.transaction(c->{try(var s=c.createStatement()){s.execute("CREATE TABLE ca_app(singleton INT PRIMARY KEY,schema_version INT NOT NULL,revision BIGINT NOT NULL,payload CLOB)");s.execute("INSERT INTO ca_app VALUES(1,1,7,NULL)");}return null;}).join();
            var repo=new ApplicationRepository(store);assertEquals(7,repo.initialize().revision());assertFalse(repo.enabled());repo.enabled(true);assertTrue(repo.enabled());assertEquals(7,repo.initialize().revision());
        }
    }
    @Test void batchPublishesEachPdfBeforeStartingNextWithoutRevisionConflict() throws Exception {
        var reads=new AtomicInteger();
        try(var e=new SessionTestEnvironment(root,request->{reads.incrementAndGet();return new PdfReadResult(request,1,List.of(new PdfPage(1,Files.readString(request.managedSnapshot()),PageTextStatus.TEXT,Optional.empty())));})) {
            e.configureAndEnable();var files=List.of(Files.writeString(root.resolve("one.pdf"),"first document"),Files.writeString(root.resolve("two.pdf"),"second document"),Files.writeString(root.resolve("three.pdf"),"third document"));
            assertEquals(Outcome.APPLIED,e.app.dispatch(new ImportCoursePdfs(e.appMeta(),files,e.app.snapshot().corpusBase())).toCompletableFuture().get().outcome());
            await(()->e.app.snapshot().materials().size()==3&&e.app.snapshot().materials().stream().allMatch(m->m.status()==MaterialStatus.READY));
            assertEquals(3,reads.get());assertTrue(e.app.snapshot().notices().isEmpty());assertEquals(3,e.app.snapshot().corpusBase().revision());
        }
    }
    @Test void failedFirstPdfStopsBatchAndReleasesLibraryForRemoval() throws Exception {
        var reads=new AtomicInteger();
        try(var e=new SessionTestEnvironment(root,request->{reads.incrementAndGet();throw new java.io.EOFException("synthetic PDF failure");})) {
            e.configureAndEnable();var files=List.of(Files.writeString(root.resolve("bad.pdf"),"bad"),Files.writeString(root.resolve("later.pdf"),"later"));
            e.app.dispatch(new ImportCoursePdfs(e.appMeta(),files,e.app.snapshot().corpusBase())).toCompletableFuture().get();
            await(()->e.app.snapshot().notices().stream().anyMatch(n->n.code().equals("BATCH_STOPPED")));
            assertEquals(1,reads.get());assertEquals(1,e.app.snapshot().materials().size());var m=e.app.snapshot().materials().getFirst();
            assertEquals(Outcome.APPLIED,e.app.dispatch(new RemoveCourseMaterial(e.appMeta(),new MaterialTarget(m.documentId(),m.sourceVersionId(),m.materialRevision()),e.app.snapshot().corpusBase())).toCompletableFuture().get().outcome());
            await(()->e.app.snapshot().materials().isEmpty());
        }
    }
    @Test void disableCancelsRemainingBatchAndLateCompletionCannotResumeIt() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var reads=new AtomicInteger();
        try(var e=new SessionTestEnvironment(root,request->{reads.incrementAndGet();entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.IOException(interrupted);}return new PdfReadResult(request,1,List.of(new PdfPage(1,"text",PageTextStatus.TEXT,Optional.empty())));})) {
            try {
                e.configureAndEnable();var files=List.of(Files.writeString(root.resolve("first.pdf"),"first"),Files.writeString(root.resolve("later.pdf"),"later"));
                e.app.dispatch(new ImportCoursePdfs(e.appMeta(),files,e.app.snapshot().corpusBase())).toCompletableFuture().get();assertTrue(entered.await(10,TimeUnit.SECONDS));
                assertEquals(Rejection.BUSY,e.app.dispatch(new ImportCoursePdf(e.appMeta(),files.getLast(),e.app.snapshot().corpusBase())).toCompletableFuture().get().rejection().orElseThrow());
                e.app.dispatch(new DisablePlugin(e.appMeta())).toCompletableFuture().get();e.app.dispatch(new EnablePlugin(e.appMeta())).toCompletableFuture().get();release.countDown();
                await(()->e.app.snapshot().notices().stream().anyMatch(n->n.code().equals("BATCH_STOPPED")));
                assertEquals(1,reads.get());assertEquals(1,e.app.snapshot().materials().size());
            } finally {release.countDown();}
        }
    }

}
