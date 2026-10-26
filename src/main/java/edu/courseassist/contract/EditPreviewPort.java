package edu.courseassist.contract;
import java.util.concurrent.CompletionStage;
import edu.courseassist.contract.EditContracts.*;
/** Read-only snapshot viewer. Completion proves display, not approval or application. */
@FunctionalInterface public interface EditPreviewPort {
    CompletionStage<Void> open(EditProposal proposal);
}
