package edu.courseassist.contract;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static edu.courseassist.contract.CommonContracts.requireId;

public final class PdfContracts {
    private PdfContracts() {}

    /** A fixed, core-managed copy. The adapter must never reopen the user's external original. */
    public record PdfReadRequest(String documentId, String sourceVersionId, Path managedSnapshot,
                                 String contentMd5) {
        public PdfReadRequest {
            requireId(documentId, "documentId");
            requireId(sourceVersionId, "sourceVersionId");
            Objects.requireNonNull(managedSnapshot, "managedSnapshot");
            if (!managedSnapshot.isAbsolute()) throw new IllegalArgumentException("Managed path must be absolute");
            if (!Objects.requireNonNull(contentMd5, "contentMd5").matches("[0-9a-fA-F]{32}")) {
                throw new IllegalArgumentException("Expected a 32-digit MD5 of the fixed source");
            }
        }
    }

    public enum PageTextStatus { TEXT, EMPTY }

    /** Physical page is one-based. Printed labels do not change its identity. No OCR is implied. */
    public record PdfPage(int physicalPage, String text, PageTextStatus status, Optional<String> printedLabel) {
        public PdfPage {
            if (physicalPage < 1) throw new IllegalArgumentException("Physical page must be positive");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(status, "status");
            printedLabel = Objects.requireNonNull(printedLabel, "printedLabel");
            if ((status == PageTextStatus.EMPTY) != text.isBlank()) {
                throw new IllegalArgumentException("Text status must match extracted text without rewriting it");
            }
        }
    }

    public record PdfReadResult(PdfReadRequest source, int physicalPageCount, List<PdfPage> pages) {
        public PdfReadResult {
            Objects.requireNonNull(source, "source");
            pages = List.copyOf(pages);
            if (physicalPageCount < 0 || physicalPageCount != pages.size()) {
                throw new IllegalArgumentException("Every physical page must be present");
            }
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i).physicalPage() != i + 1) {
                    throw new IllegalArgumentException("Physical pages must be contiguous and ordered");
                }
            }
        }
    }
}
