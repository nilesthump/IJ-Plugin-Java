package edu.courseassist.contract;

import edu.courseassist.contract.PdfContracts.PdfReadRequest;
import edu.courseassist.contract.PdfContracts.PdfReadResult;
import java.io.IOException;

/** Implemented by dev-b. Core schedules this blocking operation on its background executor. */
@FunctionalInterface
public interface PdfReader {
    /** Any unreadable page propagates its original cause; never return partial success. */
    PdfReadResult read(PdfReadRequest request) throws IOException;
}
