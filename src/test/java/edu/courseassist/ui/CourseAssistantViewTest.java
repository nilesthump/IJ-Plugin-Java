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
    @Test void noticesUseTransientDisplayWithoutComposerStripOrRefreshReplay() throws Exception {
        var controller=new ApplyingController();List<List<Notice>> shown=new ArrayList<>();
        var view=edt(()->new CourseAssistantView(controller.state.projectInstanceId(),controller,
                SyntheticControllers.application(SyntheticStates.application()),null,null,new java.util.HashMap<>(),shown::add));
        try {edt(()->{
            shown.clear();var old=controller.state;var s=old.sessions().getFirst();
            for(int n=0;n<3;n++) {
                var notice=new Notice("projection-"+n,Severity.WARNING,"EDIT_SOURCE_CHANGED","文件已被更改",Optional.of(s.sessionId()),Optional.of("open-1"));
                var changed=new SessionViewState(s.sessionId(),s.title(),s.revision()+n+1,s.lifecycle(),s.draft(),s.turns(),s.activeOperation(),s.writesBlocked(),s.allowedActions(),List.of(notice));
                controller.observer.accept(new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision()+n+1,List.of(changed),old.openSessionIds(),old.activeSessionId(),List.of()));
            }
            assertNull(find(view.component(),"notices"));assertEquals(1,shown.size());
            assertTrue(shown.getFirst().getFirst().message().contains("文件已被更改"));
            assertTrue(shown.getFirst().getFirst().message().contains(s.title()));
            assertEquals(s.draft().text(),((javax.swing.text.JTextComponent)find(view.component(),"draftPreview")).getText());
            assertTrue(controller.events.isEmpty());return null;
        });}finally {edt(()->{view.close();return null;});}
    }
    @Test void emptyForegroundRetainsComposerAndSendsWithoutNewButton() throws Exception {
        var controller=new ApplyingController();var old=controller.state;
        controller.state=new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision(),old.sessions(),List.of(),Optional.empty(),List.of());
        controller.reject=true;
        List<List<Notice>> shown=new ArrayList<>();
        var view=edt(()->new CourseAssistantView(old.projectInstanceId(),controller,SyntheticControllers.application(SyntheticStates.application()),null,null,new java.util.HashMap<>(),shown::add));
        try {
            edt(()->{
                var input=(javax.swing.text.JTextComponent)find(view.component(),"draftPreview");
                assertTrue(input.isEditable());input.setText("没有前台会话也能发送");
                controller.observer.accept(new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision()+1,old.sessions(),List.of(),Optional.empty(),List.of()));
                assertEquals("没有前台会话也能发送",input.getText());
                var send=(JButton)find(view.component(),"submitDraft");assertTrue(send.isEnabled());send.doClick();
                assertEquals("没有前台会话也能发送",input.getText());assertTrue(input.isEditable());
                assertFalse(((JLabel)find(view.component(),"sessionStatus")).getText().contains("STALE_REVISION"));
                assertTrue(shown.stream().flatMap(List::stream).anyMatch(n->n.message().contains("状态已变化")));return null;
            });
            var event=assertInstanceOf(SubmitNewSession.class,controller.events.getFirst());
            assertTrue(event.meta().sessionId().isEmpty());assertEquals("没有前台会话也能发送",event.text());
        } finally {edt(()->{view.close();return null;});}
    }
    @Test void emptyComposerTextMovesOnlyIntoNewDraftNotExistingHistory() throws Exception {
        var controller=new ApplyingController();var old=controller.state;var first=old.sessions().getFirst();
        controller.state=new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision(),old.sessions(),List.of(),Optional.empty(),List.of());
        var view=edt(()->new CourseAssistantView(old.projectInstanceId(),controller,SyntheticControllers.application(SyntheticStates.application())));
        try {edt(()->{
            var input=(javax.swing.text.JTextComponent)find(view.component(),"draftPreview");input.setText("捕获前文字");
            controller.observer.accept(old); // Old history must keep its own draft.
            assertEquals(first.draft().text(),input.getText());
            controller.observer.accept(controller.state);
            assertEquals("捕获前文字",input.getText());
            var fresh=new SessionViewState("new-capture","新会话",0,Lifecycle.LIVE,new DraftView("fresh-draft",0,"",first.draft().captures(),SaveState.SAVED),List.of(),Optional.empty(),false,first.allowedActions(),List.of());
            controller.state=new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision()+1,List.of(fresh),List.of(fresh.sessionId()),Optional.of(fresh.sessionId()),List.of());
            controller.observer.accept(controller.state);
            assertEquals("捕获前文字",input.getText());return null;
        });}finally {edt(()->{view.close();return null;});}
    }
    @Test void historyAndSearchExposeRenameForChosenSession() throws Exception {
        var controller=new ApplyingController();
        var view=edt(()->new CourseAssistantView(controller.state.projectInstanceId(),controller,SyntheticControllers.application(SyntheticStates.application())));
        try {edt(()->{
            for(boolean searching:List.of(false,true)) {
                var panel=view.sessionPicker(searching);var list=(JList<?>)find(panel,"sessionList");
                assertNotNull(list.getComponentPopupMenu());list.setSelectedIndex(0);
                controller.observer.accept(new ProjectChatViewState(controller.state.projectId(),controller.state.projectInstanceId(),controller.state.revision()+1,controller.state.sessions(),controller.state.openSessionIds(),controller.state.activeSessionId(),List.of()));
                assertNotNull(list.getSelectedValue(),"Refreshing state must not drop the rename target");
                assertEquals("重命名",((JMenuItem)list.getComponentPopupMenu().getComponent(0)).getText());
            }
            view.renameSession(controller.state.sessions().getFirst().sessionId(),"我的课程问题");return null;
        });assertInstanceOf(RenameSession.class,controller.events.getFirst());}
        finally {edt(()->{view.close();return null;});}
    }
    @Test void rightClickResolvesTargetBeforeSwingShowsMenuAndClickRenamesThatTarget() throws Exception {
        var controller=new ApplyingController();var old=controller.state;var first=old.sessions().getFirst();
        var second=new SessionViewState("other-session","第二个会话",0,Lifecycle.LIVE,first.draft(),first.turns(),Optional.empty(),false,first.allowedActions(),List.of());
        controller.state=new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision(),List.of(first,second),old.openSessionIds(),old.activeSessionId(),List.of());
        var view=edt(()->new CourseAssistantView(old.projectInstanceId(),controller,SyntheticControllers.application(SyntheticStates.application())));
        try {edt(()->{
            for(boolean searching:List.of(false,true)) {
                var prompted=new ArrayList<String>();
                var panel=view.sessionPicker(searching,chosen->{prompted.add(chosen.sessionId());return "重命名结果";});
                var list=(JList<?>)find(panel,"sessionList");list.setFixedCellHeight(24);list.setSize(320,180);
                var menu=list.getComponentPopupMenu();var rename=(JMenuItem)menu.getComponent(0);
                var visibleTargets=new ArrayList<String>();
                // Exercise the actual installed Swing global mouse listener. Only the window
                // placement is replaced; production menu visibility and action callbacks run.
                list.setComponentPopupMenu(new JPopupMenu(){
                    @Override public void show(Component invoker,int x,int y) {
                        for(var listener:menu.getPopupMenuListeners())listener.popupMenuWillBecomeVisible(new javax.swing.event.PopupMenuEvent(menu));
                        visibleTargets.add(rename.isEnabled()?((SessionViewState)list.getSelectedValue()).sessionId():"disabled");
                    }
                });
                for(int row:List.of(0,1,4)) {
                    if(row==1)list.setSelectedIndex(0);
                    var event=new java.awt.event.MouseEvent(list,java.awt.event.MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,5,row*24+5,1,true,java.awt.event.MouseEvent.BUTTON3);
                    list.dispatchEvent(event);
                    assertTrue(event.isConsumed(),"Swing's global popup handler must consume the trigger");
                    if(row<2) {
                        String expected=row==0?first.sessionId():second.sessionId();
                        assertEquals(expected,visibleTargets.getLast(),"Target must be selected before menu visibility evaluates enabled state");
                        assertTrue(rename.isEnabled());
                        // A refresh or later selection must not redirect an already open menu.
                        controller.observer.accept(controller.state);list.setSelectedIndex(1-row);
                        int count=controller.events.size();rename.doClick();
                        assertEquals(expected,prompted.getLast());
                        var eventSent=assertInstanceOf(RenameSession.class,controller.events.get(count));
                        assertEquals(Optional.of(expected),eventSent.meta().sessionId());assertEquals("重命名结果",eventSent.title());
                    } else {
                        assertEquals("disabled",visibleTargets.getLast());assertFalse(rename.isEnabled());
                        int count=controller.events.size();rename.doClick();assertEquals(count,controller.events.size());
                    }
                }
            }
            return null;
        });}finally {edt(()->{view.close();return null;});}
    }
    @Test void successfulAnswerWithEmptyDraftCannotOfferOrDispatchRetry() throws Exception {
        var controller = new ApplyingController();
        var old = controller.state; var first = old.sessions().getFirst();
        var empty = new DraftView(first.draft().draftId(), first.draft().revision(), "", List.of(), SaveState.SAVED);
        var session = new SessionViewState(first.sessionId(),first.title(),first.revision(),first.lifecycle(),empty,first.turns(),Optional.empty(),false,first.allowedActions(),List.of());
        controller.state = new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision(),List.of(session),List.of(session.sessionId()),Optional.of(session.sessionId()),List.of());
        var view = edt(() -> new CourseAssistantView(old.projectInstanceId(),controller,SyntheticControllers.application(SyntheticStates.application())));
        try { edt(() -> {
            var button=(JButton)find(view.component(),"submitDraft");
            assertEquals("发送",button.getToolTipText()); assertFalse(button.isEnabled()); button.doClick();
            assertTrue(controller.events.isEmpty()); return null;
        }); } finally { edt(() -> {view.close();return null;}); }
    }
    @Test void eachDiffStaysWithItsOriginalAnswerWhenAnotherTurnArrives() throws Exception {
        var controller=new ApplyingController();var old=SyntheticStates.project(SyntheticStates.Scenario.COMPLETED);var base=old.sessions().getFirst();
        var first=base.turns().getFirst();var a=first.attempts().getFirst();var b=a.branches().getFirst();
        var b2=new BranchView("branch-2",b.status(),b.basis(),b.steps());
        var a2=new AttemptView("attempt-2","operation-2",a.viewRevision(),a.status(),a.saveState(),a.contextEligibility(),Optional.of(b2.branchId()),a.finalStepId(),List.of(b2));
        var second=new TurnView("turn-2","request-2",first.userMessage(),List.of(a2));
        var ref1=new edu.courseassist.contract.EditContracts.ProposalRef("old-proposal",0,"b1","a1");
        var ref2=new edu.courseassist.contract.EditContracts.ProposalRef("new-proposal",0,"b2","a2");
        var p1=new edu.courseassist.contract.EditContracts.ProposalView(ref1,edu.courseassist.contract.EditContracts.ReviewState.REJECTED,Optional.empty(),"X.java",first.turnId(),a.attemptId(),b.branchId());
        var p2=new edu.courseassist.contract.EditContracts.ProposalView(ref2,edu.courseassist.contract.EditContracts.ReviewState.REVIEWABLE,Optional.empty(),"X.java",second.turnId(),a2.attemptId(),b2.branchId());
        var session=new SessionViewState(base.sessionId(),base.title(),base.revision(),base.lifecycle(),base.draft(),List.of(first,second),Optional.empty(),false,base.allowedActions(),List.of(),List.of(p2,p1));
        controller.state=new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision(),List.of(session),List.of(session.sessionId()),Optional.of(session.sessionId()),List.of());
        var view=edt(()->new CourseAssistantView(old.projectInstanceId(),controller,SyntheticControllers.application(SyntheticStates.application())));
        try {
            List<String> expected=List.of(first.turnId(),a.attemptId()+":"+b.branchId(),"edit:old-proposal","turn-2","attempt-2:branch-2","edit:new-proposal");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(edt(()->timelineIds(view.component())).size()<6&&System.nanoTime()<deadline)Thread.sleep(20);
            assertEquals(expected,edt(()->timelineIds(view.component())));
            var third=new TurnView("turn-3","request-3",first.userMessage(),List.of());
            var updated=new SessionViewState(base.sessionId(),base.title(),base.revision()+1,base.lifecycle(),base.draft(),List.of(first,second,third),Optional.empty(),false,base.allowedActions(),List.of(),List.of(p2,p1));
            edt(()->{controller.observer.accept(new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision()+1,List.of(updated),List.of(updated.sessionId()),Optional.of(updated.sessionId()),List.of()));return null;});
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(edt(()->timelineIds(view.component())).size()<7&&System.nanoTime()<deadline)Thread.sleep(20);
            var all=new ArrayList<>(expected);all.add("turn-3");assertEquals(all,edt(()->timelineIds(view.component())));
        } finally {edt(()->{view.close();return null;});}
    }
    private static List<String> timelineIds(Component component) {
        List<String> result=new ArrayList<>();
        if(component instanceof JComponent c&&c.getClientProperty("timelineRowId") instanceof String id)result.add(id);
        if(component instanceof Container c)for(Component child:c.getComponents())result.addAll(timelineIds(child));
        return result;
    }
    @Test void appliedReceiptShowsStateWithoutManualInspectionButton() throws Exception {
        for(boolean blocked:List.of(false,true)) {
        var controller = new ApplyingController();var old=controller.state;var first=old.sessions().getFirst();
        var ref=new edu.courseassist.contract.EditContracts.ProposalRef("proposal",0,"before","after");
        var receipt=new edu.courseassist.contract.EditContracts.ApplyReceipt("apply",ref,
            blocked?edu.courseassist.contract.EditContracts.ApplyState.APPLYING:edu.courseassist.contract.EditContracts.ApplyState.APPLIED_TO_DOCUMENT,
            blocked?edu.courseassist.contract.EditContracts.DiskState.UNKNOWN:edu.courseassist.contract.EditContracts.DiskState.SAVED,blocked?Optional.empty():Optional.of(9L));
        var proposal=new edu.courseassist.contract.EditContracts.ProposalView(ref,edu.courseassist.contract.EditContracts.ReviewState.APPROVED,Optional.of(receipt),"src/X.java");
        var session=new SessionViewState(first.sessionId(),first.title(),first.revision(),first.lifecycle(),first.draft(),first.turns(),Optional.empty(),blocked,first.allowedActions(),List.of(),List.of(proposal));
        controller.state=new ProjectChatViewState(old.projectId(),old.projectInstanceId(),old.revision(),List.of(session),List.of(session.sessionId()),Optional.of(session.sessionId()),List.of());
        var view=edt(()->new CourseAssistantView(old.projectInstanceId(),controller,SyntheticControllers.application(SyntheticStates.application())));
        try {edt(()->{return null;});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(edt(()->find(view.component(),"editProposalState"))==null&&System.nanoTime()<deadline)Thread.sleep(20);
            edt(()->{assertNotNull(find(view.component(),"editProposalState"));return null;});
            edt(()->{if(blocked)assertEquals("重试状态恢复",((JButton)find(view.component(),"openEditPreview")).getText());
                else assertNull(find(view.component(),"openEditPreview"));assertTrue(controller.events.isEmpty());return null;});}
        finally {edt(()->{view.close();return null;});}
        }
    }
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
