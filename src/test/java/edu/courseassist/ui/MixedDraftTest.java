package edu.courseassist.ui;

import edu.courseassist.bootstrap.ide.SessionTestEnvironment;
import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.contract.CommonContracts.SaveState;
import edu.courseassist.fixtures.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MixedDraftTest {
    @TempDir Path root;
    @Test void inputMethodCompositionAfterInlineCaptureCommitsWithoutPinyinResidue() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var capture=SyntheticStates.project().sessions().getFirst().draft().captures().getFirst();
            var editor=new MixedDraftEditor();editor.render("",List.of(capture),Map.of(capture.captureId(),0),id->{});
            ime(editor,"jie",0);
            assertEquals("jie",editor.getText());
            ime(editor,"jie shi",0);assertEquals("jie shi",editor.getText());
            ime(editor,"解释",2);assertEquals("解释",editor.getText());
            ime(editor,"zhe ge",0);ime(editor,"这个",2);assertEquals("解释这个",editor.getText());
            assertEquals(1,editor.captureOffsets().size());assertEquals(0,editor.captureOffsets().get(capture.captureId()));
        });
    }
    @Test void inlineImeSurvivesAutosaveAcknowledgementAndStillProtectsCapture() throws Exception {
        try(var env=new SessionTestEnvironment(root)) {
            env.configureAndEnable();env.open("ime","ime",false);String session=env.create("ime");
            env.sessions.beginCapture(SessionTestEnvironment.selection("ime-code","ime"));env.sessions.captureCompletion("ime-code").toCompletableFuture().get();
            env.sessions.refreshAll().toCompletableFuture().get();
            var view=new java.util.concurrent.atomic.AtomicReference<CourseAssistantView>();
            SwingUtilities.invokeAndWait(()->view.set(new CourseAssistantView("ime",env.sessions,env.app)));
            try {
                SwingUtilities.invokeAndWait(()->ime((MixedDraftEditor)FeedbackInteractionTest.find(view.get().component(),"draftPreview"),"jie",0));
                SessionTestEnvironment.await(()->env.view("ime",session).draft().text().equals("jie"));SwingUtilities.invokeAndWait(()->{});
                SwingUtilities.invokeAndWait(()->{
                    var editor=(MixedDraftEditor)FeedbackInteractionTest.find(view.get().component(),"draftPreview");
                    ime(editor,"jie shi",0);ime(editor,"解释",2);assertEquals("解释",editor.getText());
                    editor.setCaretPosition(0);editor.moveCaretPosition(1);editor.replaceSelection("");
                    assertEquals(1,editor.captureOffsets().size(),"Actual chip still requires its close action");
                    editor.setCaretPosition(editor.getDocument().getLength());
                    editor.replaceSelection("\n");ime(editor,"zheng chang",0);ime(editor,"正常",2);
                    assertEquals("解释\n正常",editor.getText());
                });
                SessionTestEnvironment.await(()->env.view("ime",session).draft().text().equals("解释\n正常"));
                SwingUtilities.invokeAndWait(()->assertEquals("解释\n正常",((MixedDraftEditor)FeedbackInteractionTest.find(view.get().component(),"draftPreview")).getText()));
            }finally{SwingUtilities.invokeAndWait(()->view.get().close());}
        }
    }
    @Test void imeBetweenTwoCapturesAndCancellationKeepMixedOrder() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var a=SyntheticStates.project().sessions().getFirst().draft().captures().getFirst();
            var group=SessionTestEnvironment.group(SessionTestEnvironment.selection("ime-second","ime"));
            var b=new DraftCapture("ime-second",CaptureState.READY,group.start().source(),Optional.of(group),Optional.empty());
            var editor=new MixedDraftEditor();editor.render("",List.of(a,b),Map.of(a.captureId(),0,b.captureId(),0),id->{});
            editor.setCaretPosition(1);ime(editor,"jie shi",0);ime(editor,"解释",2);
            assertEquals(List.of(a.captureId(),b.captureId()),editor.captureOrder());assertEquals(2,editor.captureOffsets().get(b.captureId()));
            editor.setCaretPosition(editor.getDocument().getLength());ime(editor,"qu xiao",0);ime(editor,"",0);
            ime(editor,"fang fa",0);ime(editor,"方法",2);assertEquals("解释方法",editor.getText());
            assertEquals(2,editor.captureOffsets().get(b.captureId()));
        });
    }
    private static void ime(MixedDraftEditor editor,String value,int committed) {
        var text=new java.text.AttributedString(value);
        if(committed<value.length())text.addAttribute(java.awt.font.TextAttribute.INPUT_METHOD_HIGHLIGHT,java.awt.im.InputMethodHighlight.SELECTED_RAW_TEXT_HIGHLIGHT,committed,value.length());
        editor.dispatchEvent(new java.awt.event.InputMethodEvent(editor,java.awt.event.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                text.getIterator(),committed,java.awt.font.TextHitInfo.leading(value.length()-committed),null));
    }
    @Test void consecutiveTypingAdvancesCaretAndRefreshPreservesSelection() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var editor=new MixedDraftEditor();
            editor.replaceSelection("a");editor.replaceSelection("b");editor.replaceSelection("中文");
            assertEquals("ab中文",editor.getText());assertEquals(editor.getDocument().getLength(),editor.getCaretPosition());
            editor.select(1,3);
            var capture=SyntheticStates.project().sessions().getFirst().draft().captures().getFirst();
            editor.render(editor.getText(),List.of(capture),Map.of(capture.captureId(),4),id->{});
            assertEquals("b中",editor.getSelectedText());
            editor.setCaretPosition(editor.getDocument().getLength());editor.replaceSelection(" tail");
            assertEquals("ab中文 tail",editor.getText());
        });
    }
    @Test void inlineCapturesPreserveExactTextAndTypingDoesNotBecomeHiddenCode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var capture=SyntheticStates.project().sessions().getFirst().draft().captures().getFirst();
            var editor=new MixedDraftEditor();editor.render("AB",List.of(capture),Map.of(capture.captureId(),1),id->{});
            assertEquals("AB",editor.getText());assertEquals(1,editor.captureOffsets().get(capture.captureId()));
            editor.setCaretPosition(2);editor.replaceSelection(" middle ");
            assertEquals("A middle B",editor.getText());assertEquals(1,editor.captureOffsets().get(capture.captureId()));
            editor.setCaretPosition(0);editor.replaceSelection("prefix");assertEquals(7,editor.captureOffsets().get(capture.captureId()));
        });
    }
    @Test void insertingBeforeExistingChipAtSameTextOffsetPreservesOrder() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var a=SyntheticStates.project().sessions().getFirst().draft().captures().getFirst();
            var g=SessionTestEnvironment.group(SessionTestEnvironment.selection("second","a"));
            var b=new DraftCapture("second",CaptureState.READY,g.start().source(),Optional.of(g),Optional.empty());
            var editor=new MixedDraftEditor();editor.render("tail",List.of(a),Map.of(a.captureId(),0),id->{});editor.setCaretPosition(0);
            var order=editor.incomingOrder(List.of(a,b),List.of(a.captureId(),b.captureId()),true);
            assertEquals(List.of(b.captureId(),a.captureId()),order);
            editor.render("tail",List.of(a,b),Map.of(a.captureId(),0,b.captureId(),0),order,id->{});
            assertEquals(order,editor.captureOrder());assertEquals("tail",editor.getText());
        });
    }
    @Test void deletingUnpersistedChipLayoutCannotResurrectSubmittedTextOnReopen() throws Exception {
        try(var env=new SessionTestEnvironment(root)) {
            env.configureAndEnable();env.open("mixed","mixed",false);String session=env.create("mixed");env.text("mixed",session,"saved question");
            env.sessions.beginCapture(SessionTestEnvironment.selection("capture","mixed"));env.sessions.captureCompletion("capture").toCompletableFuture().get();env.sessions.refreshAll().toCompletableFuture().get();
            var recovery=new HashMap<String,String>();var ref=new java.util.concurrent.atomic.AtomicReference<CourseAssistantView>();
            SwingUtilities.invokeAndWait(()->ref.set(new CourseAssistantView("mixed",env.sessions,env.app,null,null,recovery)));
            SwingUtilities.invokeAndWait(()->{
                var editor=(MixedDraftEditor)FeedbackInteractionTest.find(ref.get().component(),"draftPreview");
                editor.render("saved question",env.view("mixed",session).draft().captures(),Map.of("capture",0),id->{});
                try {var field=CourseAssistantView.class.getDeclaredField("autosave");field.setAccessible(true);((javax.swing.Timer)field.get(ref.get())).stop();}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
                assertFalse(recovery.isEmpty());
            });
            var draft=env.view("mixed",session).draft();env.dispatch(new RemoveDraftCapture(env.meta("mixed",session),draft.draftId(),draft.revision(),"capture"));
            SwingUtilities.invokeAndWait(()->assertTrue(recovery.isEmpty(),"Normalized saved draft clears only its old recovery entry"));
            SwingUtilities.invokeAndWait(()->((JButton)FeedbackInteractionTest.find(ref.get().component(),"submitDraft")).doClick());
            SessionTestEnvironment.await(()->!env.requests.isEmpty());env.idle("mixed",session);
            SwingUtilities.invokeAndWait(()->{
                ref.get().close();var reopened=new CourseAssistantView("mixed",env.sessions,env.app,null,null,recovery);ref.set(reopened);
            });
            SwingUtilities.invokeAndWait(()->{assertEquals("",((MixedDraftEditor)FeedbackInteractionTest.find(ref.get().component(),"draftPreview")).getText());ref.get().close();});
        }
    }
    @Test void autosavedDraftCanSubmitTwiceWithoutRestart() throws Exception {
        try(var env=new SessionTestEnvironment(root)) {
            env.configureAndEnable();env.open("mixed","mixed",false);String session=env.create("mixed");
            var ref=new java.util.concurrent.atomic.AtomicReference<CourseAssistantView>();
            SwingUtilities.invokeAndWait(()->ref.set(new CourseAssistantView("mixed",env.sessions,env.app)));
            try {
                for(int n=1;n<=2;n++) {
                    env.text("mixed",session,"question "+n);SwingUtilities.invokeAndWait(()->{});
                    SwingUtilities.invokeAndWait(()->{var button=(JButton)FeedbackInteractionTest.find(ref.get().component(),"submitDraft");assertTrue(button.isEnabled());button.doClick();});
                    int count=n;SessionTestEnvironment.await(()->env.requests.size()>=count);env.idle("mixed",session);
                    SwingUtilities.invokeAndWait(()->assertTrue(((MixedDraftEditor)FeedbackInteractionTest.find(ref.get().component(),"draftPreview")).isEditable()));
                }
                assertEquals(2,env.view("mixed",session).turns().size());assertEquals(2,env.requests.size());
            } finally {SwingUtilities.invokeAndWait(()->ref.get().close());}
        }
    }
}
