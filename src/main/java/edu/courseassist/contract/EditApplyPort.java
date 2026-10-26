package edu.courseassist.contract;
import java.util.concurrent.CompletionStage;
import edu.courseassist.contract.EditContracts.*;
import java.util.Optional;
/** Host-only IDE adapter; final ownership/before recheck and Undo belong inside its write command. */
@FunctionalInterface public interface EditApplyPort {
    CompletionStage<ApplyReceipt> apply(ApplyRequest approved, WritePermit permit);
    default CompletionStage<Long> validate(EditProposal proposal) { return java.util.concurrent.CompletableFuture.completedFuture(proposal.snapshot().documentStamp()); }
    /** Serializes the final memory-only cancellation check with the actual IDE mutation. No DB under IDE locks. */
    @FunctionalInterface interface WritePermit { boolean commit(Runnable write); }
    final class NotApplied extends RuntimeException { public NotApplied(Throwable cause){super("F2 write rejected before mutation",cause);} }
    default void forgetSession(String sessionId) {}
    /** Observation only. Missing process-local evidence must remain unknown; never infer a write from matching text. */
    default CompletionStage<Optional<ApplyReceipt>> inspect(ApplyRequest request) {
        return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
    }
    default CompletionStage<Optional<ApplyReceipt>> inspect(ApplyRequest request,Optional<ApplyReceipt> durable) {return inspect(request);}
}
