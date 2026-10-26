package edu.courseassist.contract;
import java.util.concurrent.CompletionStage;
import edu.courseassist.contract.EditContracts.*;
/** Host-only IDE adapter; final ownership/before recheck and Undo belong inside its write command. */
@FunctionalInterface public interface EditApplyPort {
    CompletionStage<ApplyReceipt> apply(ApplyRequest approved);
}
