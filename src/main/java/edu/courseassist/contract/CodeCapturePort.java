package edu.courseassist.contract;

import edu.courseassist.contract.CaptureContracts.CapturedCodeGroup;
import edu.courseassist.contract.CaptureContracts.SelectionStart;
import java.util.concurrent.CompletionStage;

/** Implemented by dev-a. Core calls it after the ticket's required initialization is saved. */
@FunctionalInterface
public interface CodeCapturePort {
    CompletionStage<CapturedCodeGroup> capture(SelectionStart start);
}
