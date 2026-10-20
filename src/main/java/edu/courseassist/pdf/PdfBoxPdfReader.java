package edu.courseassist.pdf;

import edu.courseassist.contract.PdfContracts.PageTextStatus;
import edu.courseassist.contract.PdfContracts.PdfPage;
import edu.courseassist.contract.PdfContracts.PdfReadRequest;
import edu.courseassist.contract.PdfContracts.PdfReadResult;
import edu.courseassist.contract.PdfReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDPageLabels;
import org.apache.pdfbox.text.PDFTextStripper;

/** Reads a fixed managed file; the caller owns scheduling and source identity. */
public final class PdfBoxPdfReader implements PdfReader {
    /** Public no-argument construction supports the existing plugin extension point. */
    public PdfBoxPdfReader() {}

    @Override
    public PdfReadResult read(PdfReadRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        // Own the source separately so that it also closes when loading fails.
        try (var source = new RandomAccessReadBufferedFile(request.managedSnapshot().toFile());
             PDDocument document = Loader.loadPDF(source)) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw new IOException("PDF does not permit text extraction");
            }
            int count = document.getNumberOfPages();
            PDPageLabels labels = document.getDocumentCatalog().getPageLabels();
            String[] printedLabels = labels == null ? null : labels.getLabelsByPageIndices();
            PDFTextStripper stripper = new PDFTextStripper();
            // Retain PDF content-stream order. These are extraction settings, not post-processing.
            stripper.setSortByPosition(false);
            stripper.setLineSeparator("\n");
            stripper.setPageEnd("\n");
            var pages = new ArrayList<PdfPage>(count);
            for (int page = 1; page <= count; page++) {
                // PDFBox's text pipeline can log and skip an undecodable page stream.
                // Decode it explicitly first so a broken page cannot become a successful EMPTY.
                var streams = document.getPage(page - 1).getContentStreams();
                while (streams.hasNext()) {
                    try (var input = streams.next().createInputStream()) {
                        input.transferTo(OutputStream.nullOutputStream());
                    }
                }
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                Optional<String> label = printedLabels == null ? Optional.empty()
                        : Optional.ofNullable(printedLabels[page - 1]);
                pages.add(new PdfPage(page, text, text.isBlank() ? PageTextStatus.EMPTY
                        : PageTextStatus.TEXT, label));
            }
            return new PdfReadResult(request, count, pages);
        }
    }
}
