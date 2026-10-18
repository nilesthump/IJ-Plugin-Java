package edu.courseassist.contract;

import edu.courseassist.contract.ChatContracts.ProjectChatViewState;
import edu.courseassist.contract.ChatContracts.ViewEvent;
import edu.courseassist.contract.CommonContracts.CommandResult;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Core-owned project/session service. UI never subscribes directly to model streams. */
public interface SessionController {
    CompletionStage<CommandResult> dispatch(ViewEvent event);

    /** Publishes the current full state followed by cumulative snapshots. No EDT guarantee. */
    ViewSubscription observe(String projectInstanceId, Consumer<ProjectChatViewState> listener);
}
