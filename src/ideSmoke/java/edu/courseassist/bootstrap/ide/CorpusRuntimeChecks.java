package edu.courseassist.bootstrap.ide;

import edu.courseassist.contract.ApplicationContracts.MaterialTarget;
import edu.courseassist.contract.PdfContracts.*;
import edu.courseassist.core.corpus.*;
import edu.courseassist.model.CourseEmbeddings;
import edu.courseassist.storage.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static edu.courseassist.core.corpus.CorpusData.*;
import static edu.courseassist.bootstrap.ide.DependencyChecks.require;

/** Registered PDF adapter and real corpus persistence; embeddings remain synthetic. */
public final class CorpusRuntimeChecks {
    private CorpusRuntimeChecks() {}
    public static void run(Path scratch) throws Exception {
        run(scratch, new edu.courseassist.pdf.PdfBoxPdfReader());
    }
    public static void run(Path scratch, edu.courseassist.contract.PdfReader reader) throws Exception {
        scratch = scratch.resolve("run-" + java.util.UUID.randomUUID());
        Files.createDirectories(scratch);
        Path selected = scratch.resolve("selected-fixture.pdf");
        writePdf(selected);
        var space = new EmbeddingSpace("fixture", URI.create("https://example.invalid"), "fixture-model", 3, "", "", "v1");
        var settings = new Settings(space, new Processing("synthetic-reader-v1", 200, 10), new IndexSettings(2, 8, 1.5f, 1.2f));
        CourseEmbeddings embeddings = new CourseEmbeddings() {
            public EmbeddingSpace space() { return space; }
            public float[] document(String t) { return t.contains("physical three") ? new float[]{0, 1, 0} : new float[]{1, 0, 0}; }
            public float[] query(String t) { return new float[]{0, 1, 0}; }
        };
        String oldSource, generation;
        try (var executor = Executors.newCachedThreadPool(); var store = new H2Store(scratch.resolve("db"));
             var library = new CourseLibrary(new CourseRepository(store), new ManagedPdfStore(scratch.resolve("pdf")),
                     () -> reader, executor)) {
            library.initialize().get(10, TimeUnit.SECONDS);
            var change = library.importPdf(selected, "runtime fixture", library.snapshot().get(10, TimeUnit.SECONDS).base(), settings, embeddings, () -> true)
                    .completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            oldSource = change.sourceId().orElseThrow(); generation = change.base().generationId().orElseThrow();
            Files.delete(selected);
            try (var lease = library.openAttempt()) {
                var hits = lease.search("question", embeddings, new SearchSettings(1, 0.9f), () -> true).get(10, TimeUnit.SECONDS);
                require(hits.size() == 1 && hits.getFirst().physicalPage() == 3 && hits.getFirst().source().versionId().equals(oldSource), "Corpus ordinal to fixed physical page");
                require(Files.exists(library.sourcePath(oldSource).get(10, TimeUnit.SECONDS)), "Managed PDF survives selected file deletion");
            }
        }
        try (var executor = Executors.newCachedThreadPool(); var store = new H2Store(scratch.resolve("db"));
             var library = new CourseLibrary(new CourseRepository(store), new ManagedPdfStore(scratch.resolve("pdf")),
                     () -> request -> { throw new AssertionError("Recovery reread PDF"); }, executor)) {
            library.initialize().get(10, TimeUnit.SECONDS);
            try (var lease = library.openAttempt()) {
                require(lease.base().generationId().orElseThrow().equals(generation), "Recovery keeps generation identity");
                CourseEmbeddings queryOnly = new CourseEmbeddings() {
                    public EmbeddingSpace space() { return space; }
                    public float[] document(String t) { throw new AssertionError("Recovery re-embedded document"); }
                    public float[] query(String t) { return new float[]{0, 1, 0}; }
                };
                require(lease.search("question", queryOnly, new SearchSettings(1, 0.9f), () -> true).get(10, TimeUnit.SECONDS).getFirst().physicalPage() == 3, "Corpus local-vector recovery query");
                var state = library.snapshot().get(10, TimeUnit.SECONDS); var material = state.materials().getFirst();
                library.remove(state.base(), new MaterialTarget(material.documentId(), material.sourceVersionId(), material.materialRevision()), () -> true).completion().toCompletableFuture().get(10, TimeUnit.SECONDS);
                boolean revoked=false;
                try { lease.search("question", queryOnly, new SearchSettings(1, 0.9f), () -> true); }
                catch (java.util.concurrent.CancellationException expected) { revoked=true; }
                require(revoked, "Explicit deletion revokes old retrieval lease");
                require(library.snapshot().get(10, TimeUnit.SECONDS).materials().isEmpty(), "Hard deletion leaves no material row");
            }
        }
    }
    public static void writePdf(Path path) throws Exception {
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (String text : List.of("physical one", "", "physical three")) {
                var page = new org.apache.pdfbox.pdmodel.PDPage(); document.addPage(page);
                if (!text.isEmpty()) try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 720); stream.showText(text); stream.endText();
                }
            }
            document.save(path.toFile());
        }
    }
}
