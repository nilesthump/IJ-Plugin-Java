package edu.courseassist.contract;
import java.util.concurrent.CompletionStage;
import edu.courseassist.contract.EditContracts.*;
import java.util.function.Consumer;
/** Read-only snapshot viewer. Completion proves display, not approval or application. */
@FunctionalInterface public interface EditPreviewPort {
    CompletionStage<Display> open(EditProposal proposal);
    interface Display extends AutoCloseable {
        void whenClosed(Runnable callback);
        /** Enables local buttons only while this exact displayed pair still exists. */
        void enable(PreviewToken token, Consumer<ReviewDecision> decisions, Runnable dismissed);
        @Override void close();
    }
}
