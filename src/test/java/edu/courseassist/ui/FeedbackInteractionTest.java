package edu.courseassist.ui;

import edu.courseassist.fixtures.*;
import java.awt.*;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackInteractionTest {
    @Test void removeAndReplaceCredentialsCannotBeSelectedTogether() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class<?> type = Class.forName("edu.courseassist.ui.ApplicationWorkspace$ProviderFields");
                var ctor = type.getDeclaredConstructors()[0]; ctor.setAccessible(true);
                Object fields = ctor.newInstance("test", Optional.empty());
                var k = type.getDeclaredField("key"); k.setAccessible(true);
                var r = type.getDeclaredField("removeKey"); r.setAccessible(true);
                var key = (JPasswordField) k.get(fields); var remove = (JCheckBox) r.get(fields);
                key.setText("synthetic-only");
                assertFalse(remove.isEnabled(), "New key must prevent accidental removal");
                key.setText(""); remove.doClick();
                assertFalse(key.isEnabled(), "Removal must disable replacement field");
                remove.doClick(); assertTrue(key.isEnabled());
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }
    @Test void draftCanResizeAndContainsVerticalCapturesAndSend() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var state = SyntheticStates.project();
            var view = new CourseAssistantView(state.projectInstanceId(), SyntheticControllers.session(state), SyntheticControllers.application(SyntheticStates.application()));
            try {
                var split = (JSplitPane) find(view.component(), "chatDraftSplit");
                assertNotNull(split); assertTrue(split.isContinuousLayout());
                var composer = find(view.component(), "composer");
                assertNotNull(find(composer, "draftPreview")); assertNotNull(find(composer, "submitDraft"));
                assertInstanceOf(MixedDraftEditor.class,find(composer,"draftPreview"));
                assertNull(find(composer,"draftCaptures"),"No separated capture region");
                view.component().setSize(620,850); FeedbackPreviewTest.layout(view.component());
                split.setDividerLocation(400); FeedbackPreviewTest.layout(view.component());
                int height = composer.getHeight(); split.setDividerLocation(500); FeedbackPreviewTest.layout(view.component());
                assertTrue(composer.getHeight() < height);
            } finally { view.close(); }
        });
    }
    @Test void streamingRefreshPreservesUserScrollAndBottomButtonResumesFollowing() throws Exception {
        var timeline = new java.util.concurrent.atomic.AtomicReference<ChatTimeline>();
        SwingUtilities.invokeAndWait(() -> { timeline.set(new ChatTimeline(null, (id,citation)->{})); timeline.get().component().setSize(600,400); timeline.get().show(longSession(30)); });
        try {
            awaitRows(timeline.get(), 30);
            SwingUtilities.invokeAndWait(() -> {
                var scroll = (JScrollPane) find(timeline.get().component(), "chatScroll");
                FeedbackPreviewTest.layout(timeline.get().component());
                var bubble = find(timeline.get().component(), "userBubble");
                assertTrue(bubble.getX() > 80, "Bubble must be right aligned");
                var bar = scroll.getVerticalScrollBar(); assertTrue(bar.getMaximum() > bar.getVisibleAmount());
                bar.setValueIsAdjusting(true); bar.setValue(80); bar.setValueIsAdjusting(false);
                timeline.get().show(longSession(31));
            });
            Thread.sleep(200);
            SwingUtilities.invokeAndWait(() -> assertEquals(30,countNamed(timeline.get().component(),"userBubble"),"Reading viewport stays stable while new content accumulates"));
            SwingUtilities.invokeAndWait(() -> {
                var scroll = (JScrollPane) find(timeline.get().component(), "chatScroll");
                assertEquals(80, scroll.getVerticalScrollBar().getValue());
                var button = (JButton) find(timeline.get().component(), "scrollToBottom");
                assertTrue(button.isVisible()); button.doClick();
                var bar = scroll.getVerticalScrollBar(); assertEquals(bar.getMaximum()-bar.getVisibleAmount(), bar.getValue());
            });
            awaitRows(timeline.get(),31);
        } finally { SwingUtilities.invokeAndWait(() -> timeline.get().close()); }
    }
    @Test void draggingAcrossAnAppliedSnapshotWinsOverItsQueuedRestore() throws Exception {
        var ref = new java.util.concurrent.atomic.AtomicReference<ChatTimeline>();
        SwingUtilities.invokeAndWait(() -> { ref.set(new ChatTimeline(null,(id,c)->{}));ref.get().component().setSize(600,400);ref.get().show(longSession(30)); });
        try {
            awaitRows(ref.get(),30);
            SwingUtilities.invokeAndWait(() -> {
                var timeline=ref.get(); var bar=((JScrollPane)find(timeline.component(),"chatScroll")).getVerticalScrollBar();
                // Deterministic event ordering: press, snapshot application, drag, queued restore.
                for (var listener : bar.getMouseListeners()) if (listener.getClass().getEnclosingClass() == ChatTimeline.class) listener.mousePressed(new java.awt.event.MouseEvent(bar,java.awt.event.MouseEvent.MOUSE_PRESSED,1,0,1,1,1,false));
                bar.setValue(80);
                try {
                    var prepare=ChatTimeline.class.getDeclaredMethod("prepare",edu.courseassist.contract.ChatContracts.SessionViewState.class);prepare.setAccessible(true);
                    var apply=ChatTimeline.class.getDeclaredMethod("apply",String.class,java.util.List.class);apply.setAccessible(true);
                    var next=longSession(31);apply.invoke(timeline,next.sessionId(),prepare.invoke(timeline,next));
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                FeedbackPreviewTest.layout(timeline.component());
                bar.setValue(180);
                for (var listener : bar.getMouseMotionListeners()) if (listener.getClass().getEnclosingClass() == ChatTimeline.class) listener.mouseDragged(new java.awt.event.MouseEvent(bar,java.awt.event.MouseEvent.MOUSE_DRAGGED,2,java.awt.event.InputEvent.BUTTON1_DOWN_MASK,1,20,0,false));
            });
            SwingUtilities.invokeAndWait(() -> assertEquals(180,((JScrollPane)find(ref.get().component(),"chatScroll")).getVerticalScrollBar().getValue()));
        } finally { SwingUtilities.invokeAndWait(() -> ref.get().close()); }
    }
    @Test void wheelInsideNestedMessageAlsoSuspendsStreamingRebuild() throws Exception {
        var ref=new java.util.concurrent.atomic.AtomicReference<ChatTimeline>();
        SwingUtilities.invokeAndWait(()->{ref.set(new ChatTimeline(null,(id,c)->{}));ref.get().component().setSize(600,400);ref.get().show(longSession(30));});
        try {
            awaitRows(ref.get(),30);
            SwingUtilities.invokeAndWait(()->{
                Component child=find(ref.get().component(),"submittedMixedMessage");
                child.dispatchEvent(new java.awt.event.MouseWheelEvent(child,java.awt.event.MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,1,1,0,false,java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,3,-1));
                ref.get().show(longSession(31));
            });
            Thread.sleep(200);
            SwingUtilities.invokeAndWait(()->assertEquals(30,countNamed(ref.get().component(),"userBubble")));
        }finally{SwingUtilities.invokeAndWait(()->ref.get().close());}
    }
    @Test void switchingSessionInvalidatesDeferredContentBeforeNextRender() throws Exception {
        var ref=new java.util.concurrent.atomic.AtomicReference<ChatTimeline>();
        SwingUtilities.invokeAndWait(()->{ref.set(new ChatTimeline(null,(id,c)->{}));ref.get().component().setSize(600,400);ref.get().show(longSession(30));});
        try {
            awaitRows(ref.get(),30);
            SwingUtilities.invokeAndWait(()->{var bar=((JScrollPane)find(ref.get().component(),"chatScroll")).getVerticalScrollBar();bar.setValueIsAdjusting(true);bar.setValue(30);bar.setValueIsAdjusting(false);ref.get().show(longSession(31));});
            Thread.sleep(200);
            SwingUtilities.invokeAndWait(()->{
                var original=longSession(1);
                var other=new edu.courseassist.contract.ChatContracts.SessionViewState("other-session",original.title(),1,original.lifecycle(),original.draft(),original.turns(),original.activeOperation(),false,original.allowedActions(),original.notices());
                ref.get().show(other);
                ((JButton)find(ref.get().component(),"scrollToBottom")).doClick();
                assertEquals(0,countNamed(ref.get().component(),"userBubble"),"Old deferred session must not reappear");
            });
            awaitRows(ref.get(),1);
        }finally{SwingUtilities.invokeAndWait(()->ref.get().close());}
    }
    @Test void streamingKeepsUnchangedRowsAndBottomAnchoredDuringLayout() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try(var timeline=new ChatTimeline(null,(id,c)->{})) {
                timeline.component().setSize(600,400);applyNow(timeline,longSession(30));FeedbackPreviewTest.layout(timeline.component());
                var original=find(timeline.component(),"userBubble");
                for(int count=31;count<36;count++) {
                    applyNow(timeline,longSession(count));FeedbackPreviewTest.layout(timeline.component());
                    assertSame(original,find(timeline.component(),"userBubble"),"Historical messages are not rebuilt per token");
                    var scroll=(JScrollPane)find(timeline.component(),"chatScroll");
                    assertEquals(Math.max(0,scroll.getViewport().getViewSize().height-scroll.getViewport().getExtentSize().height),scroll.getViewport().getViewPosition().y);
                    assertFalse(find(timeline.component(),"scrollToBottom").isVisible(),"Following mode must not flash the bottom button");
                }
            }
        });
    }
    @Test void disclosureArrowMatchesStateAndBottomOverlayLeavesSidesUncovered() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try(var timeline=new ChatTimeline(null,(id,c)->{})) {
                timeline.component().setSize(600,400);applyNow(timeline,longSession(30));FeedbackPreviewTest.layout(timeline.component());
                var toggle=(JButton)find(timeline.component(),"processToggle");assertTrue(toggle.getText().startsWith("▶"));
                toggle.doClick();assertTrue(toggle.getText().startsWith("▼"));toggle.doClick();assertTrue(toggle.getText().startsWith("▶"));
                var scroll=(JScrollPane)find(timeline.component(),"chatScroll");var bar=scroll.getVerticalScrollBar();bar.setValueIsAdjusting(true);bar.setValue(50);bar.setValueIsAdjusting(false);
                FeedbackPreviewTest.layout(timeline.component());var button=(JButton)find(timeline.component(),"scrollToBottom");
                assertTrue(button.isVisible());var layer=(JLayeredPane)find(timeline.component(),"chatOverlay");
                assertEquals(layer.getHeight(),scroll.getHeight());assertSame(scroll,layer.getComponentAt(2,button.getY()+2));
                assertTrue(button.getWidth()<layer.getWidth()/2);
            }
        });
    }
    @Test void citationLabelsUseFileAndPhysicalPagesWithoutChangingCode() {
        var citation=new edu.courseassist.contract.ChatContracts.Citation("fixed-id","doc","source","Generics & Types.pdf",3,4);
        var rendered=ChatTimeline.markdown("See [[course:fixed-id]].\n\n```text\n[[course:fixed-id]]\n```",java.util.List.of(citation));
        assertTrue(rendered.getFirst().source().contains("Generics &amp; Types.pdf · PDF 3–4 页"));
        assertFalse(rendered.getFirst().source().contains("fixed-id"));assertTrue(rendered.getLast().source().contains("[[course:fixed-id]]"));
        assertFalse(ChatTimeline.markdown("unfinished [[course:some-par",java.util.List.of()).getFirst().source().contains("some-par"));
    }
    @Test void growingCurrentAnswerAnchorsBeforePaintAndKeepsUserMessage() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try(var timeline=new ChatTimeline(null,(id,c)->{})) {
                timeline.component().setSize(600,400);Component user=null;
                var source=longSession(1);var turn=source.turns().getFirst();var attempt=turn.attempts().getFirst();var branch=attempt.branches().getFirst();var step=branch.steps().getLast();
                for(int n=20;n<30;n++) {
                    var changed=new edu.courseassist.contract.ChatContracts.ModelStepView(step.stepId(),n,n,step.status(),"paragraph text\n\n".repeat(n),step.thinkingText(),step.tools(),step.finishReason(),step.usage());
                    var nextBranch=new edu.courseassist.contract.ChatContracts.BranchView(branch.branchId(),branch.status(),branch.basis(),java.util.List.of(changed));
                    var nextAttempt=new edu.courseassist.contract.ChatContracts.AttemptView(attempt.attemptId(),attempt.operationId(),n,attempt.status(),attempt.saveState(),attempt.contextEligibility(),attempt.effectiveBranchId(),attempt.finalStepId(),java.util.List.of(nextBranch));
                    var nextTurn=new edu.courseassist.contract.ChatContracts.TurnView(turn.turnId(),turn.requestId(),turn.userMessage(),java.util.List.of(nextAttempt));
                    var state=new edu.courseassist.contract.ChatContracts.SessionViewState(source.sessionId(),source.title(),n,source.lifecycle(),source.draft(),java.util.List.of(nextTurn),source.activeOperation(),false,source.allowedActions(),source.notices());
                    applyNow(timeline,state);FeedbackPreviewTest.layout(timeline.component());
                    if(user==null)user=find(timeline.component(),"userBubble");else assertSame(user,find(timeline.component(),"userBubble"));
                    var view=((JScrollPane)find(timeline.component(),"chatScroll")).getViewport();
                    assertEquals(Math.max(0,view.getViewSize().height-view.getExtentSize().height),view.getViewPosition().y);
                    assertFalse(find(timeline.component(),"scrollToBottom").isVisible());
                }
            }
        });
    }
    private static void applyNow(ChatTimeline timeline,edu.courseassist.contract.ChatContracts.SessionViewState state) {
        try {
            var prepare=ChatTimeline.class.getDeclaredMethod("prepare",edu.courseassist.contract.ChatContracts.SessionViewState.class);prepare.setAccessible(true);
            var apply=ChatTimeline.class.getDeclaredMethod("apply",String.class,java.util.List.class);apply.setAccessible(true);
            apply.invoke(timeline,state.sessionId(),prepare.invoke(timeline,state));
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static edu.courseassist.contract.ChatContracts.SessionViewState longSession(int count) {
        var original = SyntheticStates.project(SyntheticStates.Scenario.COMPLETED).sessions().getFirst();
        var turn = original.turns().getFirst();
        var turns = new java.util.ArrayList<edu.courseassist.contract.ChatContracts.TurnView>();
        for(int i=0;i<count;i++) turns.add(new edu.courseassist.contract.ChatContracts.TurnView("turn"+i,"request"+i,turn.userMessage(),turn.attempts()));
        return new edu.courseassist.contract.ChatContracts.SessionViewState(original.sessionId(), original.title(),count,original.lifecycle(),original.draft(),turns,original.activeOperation(),false,original.allowedActions(),original.notices());
    }
    private static void awaitRows(ChatTimeline timeline, int count) throws Exception {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        while(!ready.get() && System.nanoTime()<until) {
            SwingUtilities.invokeAndWait(() -> { FeedbackPreviewTest.layout(timeline.component()); ready.set(countNamed(timeline.component(),"userBubble")==count); });
            Thread.sleep(20);
        }
        assertTrue(ready.get()); SwingUtilities.invokeAndWait(() -> FeedbackPreviewTest.layout(timeline.component()));
    }
    private static int countNamed(Component c,String name) { int n=name.equals(c.getName())?1:0;if(c instanceof Container box)for(Component child:box.getComponents())n+=countNamed(child,name);return n; }
    static Component find(Component c, String name) { if(name.equals(c.getName()))return c; if(c instanceof Container box)for(Component child:box.getComponents()){var found=find(child,name);if(found!=null)return found;}return null; }
}
