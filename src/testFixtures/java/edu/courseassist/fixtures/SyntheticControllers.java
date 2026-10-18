package edu.courseassist.fixtures;

import edu.courseassist.contract.ApplicationController;
import edu.courseassist.contract.ApplicationContracts.ApplicationEvent;
import edu.courseassist.contract.ApplicationContracts.ApplicationViewState;
import edu.courseassist.contract.ChatContracts.ProjectChatViewState;
import edu.courseassist.contract.ChatContracts.ViewEvent;
import edu.courseassist.contract.CommonContracts.CommandResult;
import edu.courseassist.contract.CommonContracts.Outcome;
import edu.courseassist.contract.CommonContracts.Rejection;
import edu.courseassist.contract.SessionController;
import edu.courseassist.contract.ViewSubscription;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * SYNTHETIC command recorders for rendering/event wiring. They deliberately reject commands as
 * NOT_SUPPORTED and never pretend to implement core persistence, state transitions, or models.
 * Tests drive new snapshots explicitly with publish().
 */
public final class SyntheticControllers {
    private SyntheticControllers() {}
    public static SessionFixture session(ProjectChatViewState initial) { return new SessionFixture(initial); }
    public static ApplicationFixture application(ApplicationViewState initial) { return new ApplicationFixture(initial); }

    public static final class SessionFixture implements SessionController {
        private volatile ProjectChatViewState state;
        private final List<ViewEvent> recorded = new CopyOnWriteArrayList<>();
        private final List<Consumer<ProjectChatViewState>> listeners = new CopyOnWriteArrayList<>();
        private SessionFixture(ProjectChatViewState initial) { state = Objects.requireNonNull(initial, "initial"); }
        public List<ViewEvent> recordedEvents() { return List.copyOf(recorded); }
        public void publish(ProjectChatViewState next) {
            if (!state.projectInstanceId().equals(next.projectInstanceId())) throw new IllegalArgumentException("Fixture project changed");
            state = next;
            listeners.forEach(listener -> listener.accept(next));
        }
        @Override public CompletionStage<CommandResult> dispatch(ViewEvent event) {
            recorded.add(Objects.requireNonNull(event, "event"));
            return CompletableFuture.completedFuture(rejected(event.meta().commandId(), state.revision()));
        }
        @Override public ViewSubscription observe(String projectInstanceId, Consumer<ProjectChatViewState> listener) {
            if (!state.projectInstanceId().equals(projectInstanceId)) throw new IllegalArgumentException("Wrong fixture project");
            listeners.add(Objects.requireNonNull(listener, "listener"));
            listener.accept(state);
            return () -> listeners.remove(listener);
        }
    }

    public static final class ApplicationFixture implements ApplicationController {
        private volatile ApplicationViewState state;
        private final List<ApplicationEvent> recorded = new CopyOnWriteArrayList<>();
        private final List<Consumer<ApplicationViewState>> listeners = new CopyOnWriteArrayList<>();
        private ApplicationFixture(ApplicationViewState initial) { state = Objects.requireNonNull(initial, "initial"); }
        public List<ApplicationEvent> recordedEvents() { return List.copyOf(recorded); }
        public void publish(ApplicationViewState next) { state = Objects.requireNonNull(next, "next"); listeners.forEach(listener -> listener.accept(next)); }
        @Override public CompletionStage<CommandResult> dispatch(ApplicationEvent event) {
            recorded.add(Objects.requireNonNull(event, "event"));
            return CompletableFuture.completedFuture(rejected(event.meta().commandId(), state.revision()));
        }
        @Override public ViewSubscription observe(Consumer<ApplicationViewState> listener) {
            listeners.add(Objects.requireNonNull(listener, "listener")); listener.accept(state);
            return () -> listeners.remove(listener);
        }
    }

    private static CommandResult rejected(String commandId, long revision) {
        return new CommandResult(commandId, Outcome.REJECTED, revision, Optional.of(Rejection.NOT_SUPPORTED), Optional.empty());
    }
}
