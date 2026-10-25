package edu.courseassist.ui;

import edu.courseassist.contract.*;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.*;
import edu.courseassist.fixtures.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CourseAssistantViewTest {
    @Test void singleComposerStopsOriginalActiveOperationAndHasOnlyFourTopActions() throws Exception {
        var state = SyntheticStates.project(); var sessions = SyntheticControllers.session(state);
        var app = SyntheticControllers.application(SyntheticStates.application());
        var view = edt(() -> new CourseAssistantView(state.projectInstanceId(), sessions, app));
        try {
            edt(() -> {
                assertNotNull(find(view.component(), "chatTimeline"));
                assertNull(find(view.component(), "workspaceSections")); assertNull(find(view.component(), "saveDraft"));
                for (String name : List.of("createSession", "historySessions", "searchSessions", "settings")) assertNotNull(find(view.component(), name));
                var primary = (JButton) find(view.component(), "submitDraft"); assertEquals("暂停生成", primary.getToolTipText()); primary.doClick();
                return null;
            });
            var stop = assertInstanceOf(StopOperation.class, sessions.recordedEvents().getFirst());
            assertEquals(SyntheticStates.SESSION_ID, stop.meta().sessionId().orElseThrow());
        } finally { edt(() -> { view.close(); return null; }); }
    }
    @Test void dirtyDraftSavesAndSubmitsInOneClickWithExactOriginalTargetAndRevision() throws Exception {
        var controller = new ApplyingController();
        var view = edt(() -> new CourseAssistantView(controller.state.projectInstanceId(), controller,
                SyntheticControllers.application(SyntheticStates.application())));
        try {
            edt(() -> {
                ((javax.swing.text.JTextComponent) find(view.component(), "draftPreview")).setText("  exact new question\n");
                ((JButton) find(view.component(), "submitDraft")).doClick(); return null;
            });
            assertEquals(2, controller.events.size());
            var update = assertInstanceOf(UpdateDraftText.class, controller.events.get(0));
            var submit = assertInstanceOf(SubmitDraft.class, controller.events.get(1));
            assertEquals("  exact new question\n", update.text());
            assertEquals(update.expectedDraftRevision() + 1, submit.expectedDraftRevision());
            assertEquals(update.meta().sessionId(), submit.meta().sessionId());
        } finally { edt(() -> { view.close(); return null; }); }
    }
    @Test void rejectedAutosaveKeepsTextAndDoesNotSubmitOrRetrySilently() throws Exception {
        var controller = new ApplyingController(); controller.reject = true;
        var view = edt(() -> new CourseAssistantView(controller.state.projectInstanceId(), controller,
                SyntheticControllers.application(SyntheticStates.application())));
        try {
            edt(() -> { ((javax.swing.text.JTextComponent) find(view.component(), "draftPreview")).setText("unsaved sentinel"); ((JButton) find(view.component(), "submitDraft")).doClick(); return null; });
            Thread.sleep(700);
            edt(() -> { assertEquals("unsaved sentinel", ((javax.swing.text.JTextComponent) find(view.component(), "draftPreview")).getText()); return null; });
            assertEquals(1, controller.events.size()); assertInstanceOf(UpdateDraftText.class, controller.events.getFirst());
        } finally { edt(() -> { view.close(); return null; }); }
    }
    @Test void oldStateCannotOverwriteNewerOrLocallyEditedDraft() throws Exception {
        var initial = SyntheticStates.project(); var sessions = SyntheticControllers.session(initial);
        var view = edt(() -> new CourseAssistantView(initial.projectInstanceId(), sessions, SyntheticControllers.application(SyntheticStates.application())));
        try {
            edt(() -> { ((javax.swing.text.JTextComponent) find(view.component(), "draftPreview")).setText("local new text"); return null; });
            sessions.publish(initial);
            edt(() -> { assertEquals("local new text", ((javax.swing.text.JTextComponent) find(view.component(), "draftPreview")).getText()); return null; });
        } finally { edt(() -> { view.close(); return null; }); }
    }
    @Test void closeDrainsNewerTypingWhileFirstAutosaveIsPending() throws Exception {
        var controller = new ApplyingController(); controller.delay = true;
        var view = edt(() -> new CourseAssistantView(controller.state.projectInstanceId(), controller,
                SyntheticControllers.application(SyntheticStates.application())));
        edt(() -> { ((javax.swing.text.JTextComponent) find(view.component(), "draftPreview")).setText("first"); return null; });
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (controller.delayed == null && System.nanoTime() < until) Thread.sleep(20);
        assertNotNull(controller.delayed);
        edt(() -> {
            ((javax.swing.text.JTextComponent) find(view.component(), "draftPreview")).setText("newest while saving");
            view.close();
            assertNotNull(controller.observer);
            controller.completeDelayed();
            assertEquals("newest while saving", controller.state.sessions().getFirst().draft().text());
            assertNull(controller.observer);
            return null;
        });
        assertEquals(2, controller.events.size());
    }
    @Test void rejectedSaveDuringCloseReleasesObserverAndRecoversLatestTextOnReopen() throws Exception {
        var controller = new ApplyingController(); controller.delay = true;
        var recovery = new java.util.HashMap<String, String>();
        var app = SyntheticControllers.application(SyntheticStates.application());
        var view = edt(() -> new CourseAssistantView(controller.state.projectInstanceId(), controller, app, null, null, recovery));
        edt(() -> {
            ((javax.swing.text.JTextComponent) find(view.component(), "draftPreview")).setText("retained after rejection");
            ((JButton) find(view.component(), "submitDraft")).doClick();
            view.close(); controller.reject = true; controller.completeDelayed();
            assertNull(controller.observer); assertFalse(recovery.isEmpty());
            return null;
        });
        var reopened = edt(() -> new CourseAssistantView(controller.state.projectInstanceId(), controller, app, null, null, recovery));
        edt(() -> { assertEquals("retained after rejection", ((javax.swing.text.JTextComponent) find(reopened.component(), "draftPreview")).getText()); reopened.close(); return null; });
    }
    @Test void oldClosingViewCannotEraseNewViewsRecoveryText() throws Exception {
        var oldController = new ApplyingController(); oldController.delay = true;
        var newController = new ApplyingController(); newController.reject = true;
        var recovery = new java.util.HashMap<String, String>();
        var app = SyntheticControllers.application(SyntheticStates.application());
        edt(() -> {
            var oldView = new CourseAssistantView(oldController.state.projectInstanceId(), oldController, app, null, null, recovery);
            ((javax.swing.text.JTextComponent) find(oldView.component(), "draftPreview")).setText("old view save"); oldView.close();
            var newView = new CourseAssistantView(newController.state.projectInstanceId(), newController, app, null, null, recovery);
            ((javax.swing.text.JTextComponent) find(newView.component(), "draftPreview")).setText("new view unsaved");
            oldController.completeDelayed();
            assertFalse(recovery.isEmpty()); newView.close();
            var check=new CourseAssistantView(newController.state.projectInstanceId(),newController,app,null,null,recovery);
            assertEquals("new view unsaved",((javax.swing.text.JTextComponent)find(check.component(),"draftPreview")).getText());check.close();
            return null;
        });
    }
    @Test void markdownRendersFormattingInsteadOfRawMarkers() {
        var parts = ChatTimeline.markdown("# Heading\n\n**bold** and `inline`\n\n- one\n- two");
        String html = parts.stream().map(ChatTimeline.Segment::source).collect(java.util.stream.Collectors.joining());
        assertTrue(html.contains("<h1>Heading</h1>")); assertTrue(html.contains("<strong>bold</strong>"));
        assertTrue(html.contains("<code>inline</code>")); assertTrue(html.contains("<ul>"));
        assertFalse(html.contains("**bold**")); assertFalse(html.contains("# Heading"));
    }
    @Test void markdownKeepsTextCodeTextOrderAndUnclosedFenceWithoutLoadingImages() {
        var parts = ChatTimeline.markdown("Before\n\n```java\nint a = 1;\n```\n\nAfter\n\n![hidden](https://example.invalid/private.png)");
        assertEquals(4, parts.size()); assertTrue(parts.get(0).source().contains("Before"));
        assertTrue(parts.get(1).code()); assertEquals("int a = 1;\n", parts.get(1).source());
        assertTrue(parts.get(2).source().contains("After")); assertFalse(parts.get(3).source().contains("<img"));
        var partial = ChatTimeline.markdown("```java\nint a"); assertTrue(partial.getFirst().code()); assertEquals("int a\n", partial.getFirst().source());
    }
    @Test void closingUnsubscribesWithoutStopOrDelete() throws Exception {
        var state = SyntheticStates.project(); var sessions = SyntheticControllers.session(state);
        var view = edt(() -> new CourseAssistantView(state.projectInstanceId(), sessions, SyntheticControllers.application(SyntheticStates.application())));
        edt(() -> { view.close(); return null; }); assertTrue(sessions.recordedEvents().isEmpty());
    }
    private static final class ApplyingController implements SessionController {
        ProjectChatViewState state; Consumer<ProjectChatViewState> observer; boolean reject;
        final List<ViewEvent> events = new ArrayList<>();
        boolean delay;
        volatile CompletableFuture<CommandResult> delayed;
        ViewEvent delayedEvent;
        void completeDelayed() {
            delay = false;
            var result = apply(delayedEvent).toCompletableFuture().join();
            delayed.complete(result);
        }
        ApplyingController() {
            var original = SyntheticStates.project(SyntheticStates.Scenario.COMPLETED); var first = original.sessions().getFirst();
            var session = new SessionViewState(first.sessionId(), first.title(), first.revision(), first.lifecycle(), first.draft(), first.turns(), Optional.empty(), false,
                    java.util.Set.of(SessionAction.EDIT_DRAFT, SessionAction.SUBMIT, SessionAction.RETRY), List.of());
            state = new ProjectChatViewState(original.projectId(), original.projectInstanceId(), original.revision(), List.of(session), List.of(session.sessionId()), Optional.of(session.sessionId()), List.of());
        }
        public ViewSubscription observe(String id, Consumer<ProjectChatViewState> listener) { observer = listener; listener.accept(state); return () -> observer = null; }
        public CompletionStage<CommandResult> dispatch(ViewEvent event) {
            events.add(event);
            if (delay && event instanceof UpdateDraftText) { delayedEvent=event; delayed=new CompletableFuture<>(); return delayed; }
            return apply(event);
        }
        private CompletionStage<CommandResult> apply(ViewEvent event) {
            if (reject) return CompletableFuture.completedFuture(new CommandResult(event.meta().commandId(), Outcome.REJECTED, state.revision(), Optional.of(Rejection.STALE_REVISION), Optional.empty()));
            if (event instanceof UpdateDraftText update) {
                var first = state.sessions().getFirst();
                var draft = new DraftView(first.draft().draftId(), first.draft().revision() + 1, update.text(), first.draft().captures(), first.draft().saveState(),update.captureOffsets().orElse(first.draft().captureOffsets()),update.captureOrder().orElse(first.draft().captureOrder()));
                var session = new SessionViewState(first.sessionId(), first.title(), first.revision()+1, first.lifecycle(), draft, first.turns(), first.activeOperation(), false, first.allowedActions(), List.of());
                state = new ProjectChatViewState(state.projectId(), state.projectInstanceId(), state.revision()+1, List.of(session), state.openSessionIds(), state.activeSessionId(), List.of());
                observer.accept(state);
            }
            return CompletableFuture.completedFuture(new CommandResult(event.meta().commandId(), Outcome.APPLIED, state.revision(), Optional.empty(), Optional.empty()));
        }
    }
    static Component find(Component c, String name) { if (name.equals(c.getName())) return c; if (c instanceof Container box) for (Component child : box.getComponents()) { var found = find(child, name); if (found != null) return found; } return null; }
    private static <T> T edt(Callable<T> action) throws Exception { AtomicReference<T> result=new AtomicReference<>(); AtomicReference<Throwable> failure=new AtomicReference<>(); SwingUtilities.invokeAndWait(() -> { try { result.set(action.call()); } catch(Throwable e) { failure.set(e); } }); if(failure.get()!=null) throw new AssertionError(failure.get()); return result.get(); }
}
