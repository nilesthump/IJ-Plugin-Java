package edu.courseassist.contract;
import java.util.concurrent.CompletionStage;
import edu.courseassist.contract.EditContracts.*;
/** Read-only, bounded lookup inside the request's original project. */
@FunctionalInterface public interface EditTargetPort {
    CompletionStage<TargetResolution> resolveAndCapture(EditRequest request, TargetQuery query);
}
