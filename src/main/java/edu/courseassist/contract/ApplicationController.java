package edu.courseassist.contract;

import edu.courseassist.contract.ApplicationContracts.ApplicationEvent;
import edu.courseassist.contract.ApplicationContracts.ApplicationViewState;
import edu.courseassist.contract.CommonContracts.CommandResult;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface ApplicationController {
    CompletionStage<CommandResult> dispatch(ApplicationEvent event);

    /** Current full state then cumulative updates; callback does not imply EDT affinity. */
    ViewSubscription observe(Consumer<ApplicationViewState> listener);
}
