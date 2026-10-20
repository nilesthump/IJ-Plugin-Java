package edu.courseassist.pdf;

import edu.courseassist.contract.PdfContracts.PageTextStatus;
import edu.courseassist.contract.PdfContracts.PdfReadRequest;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDPageLabelRange;
import org.apache.pdfbox.pdmodel.common.PDPageLabels;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class PdfBoxPdfReaderTest {
    @TempDir Path temp;
    private final PdfBoxPdfReader reader = new PdfBoxPdfReader();

    @Test
    void readsThreePhysicalPagesIncludingBlankAndKeepsExactSource() throws Exception {
        Path file = textPdf("three.pdf", "First physical page.", "", "Third physical page.");
        var request = request(file);
        var result = reader.read(request);
        assertSame(request, result.source());
        assertEquals(3, result.physicalPageCount());
        assertEquals(java.util.List.of(1, 2, 3), result.pages().stream().map(p -> p.physicalPage()).toList());
        assertTrue(result.pages().get(0).text().contains("First physical page."));
        assertFalse(result.pages().get(0).text().contains("Third"));
        assertEquals(PageTextStatus.TEXT, result.pages().get(0).status());
        assertTrue(result.pages().get(1).text().isBlank());
        assertEquals(PageTextStatus.EMPTY, result.pages().get(1).status());
        assertTrue(result.pages().get(2).text().contains("Third physical page."));
        assertFalse(result.pages().get(2).text().contains("First"));
        assertTrue(result.pages().stream().allMatch(p -> p.printedLabel().isEmpty()));
        assertThrows(UnsupportedOperationException.class, () -> result.pages().clear());
    }

    @Test
    void printedLabelsNeverReplacePhysicalNumbers() throws Exception {
        Path file = temp.resolve("labels.pdf");
        try (var document = new PDDocument()) {
            addTextPage(document, "Front matter.");
            addTextPage(document, "Printed page 7.");
            addTextPage(document, "Printed page 8.");
            var labels = new PDPageLabels(document);
            var roman = new PDPageLabelRange();
            roman.setStyle(PDPageLabelRange.STYLE_ROMAN_LOWER);
            labels.setLabelItem(0, roman);
            var decimal = new PDPageLabelRange();
            decimal.setStyle(PDPageLabelRange.STYLE_DECIMAL);
            decimal.setStart(7);
            labels.setLabelItem(1, decimal);
            document.getDocumentCatalog().setPageLabels(labels);
            document.save(file.toFile());
        }
        var pages = reader.read(request(file)).pages();
        assertEquals(java.util.List.of(1, 2, 3), pages.stream().map(p -> p.physicalPage()).toList());
        assertEquals(java.util.List.of(Optional.of("i"), Optional.of("7"), Optional.of("8")),
                pages.stream().map(p -> p.printedLabel()).toList());
    }

    @Test
    void imageOnlyAndBlankPagesRemainEmptyWithoutOcr() throws Exception {
        Path file = temp.resolve("image.pdf");
        try (var document = new PDDocument()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.drawImage(LosslessFactory.createFromImage(document,
                        new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)), 50, 50);
            }
            document.addPage(new PDPage());
            document.save(file.toFile());
        }
        var result = reader.read(request(file));
        assertEquals(2, result.physicalPageCount());
        assertTrue(result.pages().stream().allMatch(p -> p.status() == PageTextStatus.EMPTY && p.text().isBlank()));
    }

    @Test
    void readsOnlyManagedCopyAndDoesNotRewriteBytesOrBusinessIdentity() throws Exception {
        Path original = textPdf("original.pdf", "Original fixed content.");
        Path managed = Files.copy(original, temp.resolve("managed.pdf"));
        byte[] before = Files.readAllBytes(managed);
        Files.delete(original);
        var first = new PdfReadRequest("document-A", "version-old", managed, "ABCDEF0123456789ABCDEF0123456789");
        var second = new PdfReadRequest("document-B", "version-new", managed, "0123456789abcdef0123456789abcdef");
        assertSame(first, reader.read(first).source());
        assertSame(second, reader.read(second).source());
        assertTrue(reader.read(first).pages().getFirst().text().contains("Original fixed content."));
        assertArrayEquals(before, Files.readAllBytes(managed));
    }

    @Test
    void keepsExtractedWhitespaceAndUsesLfSeparators() throws Exception {
        var page = reader.read(request(textPdf("spaces.pdf", "  indented text  "))).pages().getFirst();
        assertTrue(page.text().contains("  indented text  "));
        assertTrue(page.text().endsWith("\n"));
        assertFalse(page.text().contains("\r"));
    }

    @Test
    void sourceHandlesAreReleasedAfterRepeatedSuccessfulReads() throws Exception {
        Path file = textPdf("repeat.pdf", "Repeated read.");
        for (int i = 0; i < 20; i++) {
            assertEquals(1, reader.read(request(file)).physicalPageCount());
            Path moved = temp.resolve("moved.pdf");
            Files.move(file, moved);
            Files.move(moved, file);
        }
        Files.delete(file);
        assertFalse(Files.exists(file));
    }

    @Test
    void missingFilePropagatesIOException() {
        IOException error = assertThrows(IOException.class, () -> reader.read(request(temp.resolve("missing.pdf"))));
        assertNotNull(error.getMessage());
    }

    @Test
    void invalidAndTruncatedFilesFailAndReleaseHandles() throws Exception {
        for (String content : new String[]{"This is not a PDF.", "%PDF-1.7\n1 0 obj\n<< /Type /Catalog"}) {
            Path file = temp.resolve("broken.pdf");
            Files.writeString(file, content, StandardCharsets.US_ASCII);
            IOException error = assertThrows(IOException.class, () -> reader.read(request(file)));
            assertNotNull(error.getMessage());
            Path moved = temp.resolve("broken-moved.pdf");
            Files.move(file, moved);
            Files.delete(moved);
        }
    }

    @Test
    void failureOnLaterPageRejectsWholeReadAndReleasesHandle() throws Exception {
        Path file = temp.resolve("later-page-fails.pdf");
        try (var document = new PDDocument()) {
            addTextPage(document, "This first page is readable.");
            var page = new PDPage();
            document.addPage(page);
            var contents = new PDStream(document);
            try (var output = contents.getCOSObject().createRawOutputStream()) {
                output.write("unreadable synthetic stream".getBytes(StandardCharsets.US_ASCII));
            }
            contents.getCOSObject().setItem(COSName.FILTER, COSName.getPDFName("UnsupportedSyntheticFilter"));
            page.setContents(contents);
            document.save(file.toFile());
        }
        IOException error = assertThrows(IOException.class, () -> reader.read(request(file)));
        assertTrue(error.getMessage().contains("UnsupportedSyntheticFilter"));
        Path moved = Files.move(file, temp.resolve("later-page-released.pdf"));
        Files.delete(moved);
    }

    @Test
    void passwordRequiredPropagatesOriginalExceptionAndReleasesHandle() throws Exception {
        Path file = encryptedPdf("password.pdf", "reader-password", new AccessPermission());
        assertThrows(InvalidPasswordException.class, () -> reader.read(request(file)));
        Path moved = Files.move(file, temp.resolve("password-moved.pdf"));
        Files.delete(moved);
    }

    @Test
    void refusesExtractionWhenPermissionsDenyItAndReleasesHandle() throws Exception {
        var permissions = new AccessPermission();
        permissions.setCanExtractContent(false);
        Path file = encryptedPdf("restricted.pdf", "", permissions);
        IOException error = assertThrows(IOException.class, () -> reader.read(request(file)));
        assertTrue(error.getMessage().contains("does not permit text extraction"));
        Files.delete(file);
    }

    @Test
    void independentCallsCanReadSameSnapshotConcurrently() throws Exception {
        var request = request(textPdf("parallel.pdf", "Concurrent content."));
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> reader.read(request))).toList();
            for (var task : tasks) {
                var result = task.get(15, java.util.concurrent.TimeUnit.SECONDS);
                assertSame(request, result.source());
                assertTrue(result.pages().getFirst().text().contains("Concurrent content."));
            }
        }
        Files.delete(request.managedSnapshot());
    }

    private Path encryptedPdf(String name, String userPassword, AccessPermission permissions) throws IOException {
        Path file = temp.resolve(name);
        try (var document = new PDDocument()) {
            addTextPage(document, "Protected text.");
            var policy = new StandardProtectionPolicy("owner-password", userPassword, permissions);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(file.toFile());
        }
        return file;
    }

    private Path textPdf(String name, String... texts) throws IOException {
        Path file = temp.resolve(name);
        try (var document = new PDDocument()) {
            for (String text : texts) addTextPage(document, text);
            document.save(file.toFile());
        }
        return file;
    }

    private static void addTextPage(PDDocument document, String text) throws IOException {
        var page = new PDPage();
        document.addPage(page);
        if (text.isEmpty()) return;
        try (var stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(50, 700);
            stream.showText(text);
            stream.endText();
        }
    }

    private static PdfReadRequest request(Path file) {
        return new PdfReadRequest("synthetic-document", "synthetic-version", file.toAbsolutePath(),
                "0123456789abcdef0123456789abcdef");
    }
}
