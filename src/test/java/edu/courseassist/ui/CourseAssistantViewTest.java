package edu.courseassist.ui;

import edu.courseassist.contract.ChatContracts.DraftView;
import edu.courseassist.contract.ChatContracts.ProjectChatViewState;
import edu.courseassist.contract.ChatContracts.SessionViewState;
import edu.courseassist.contract.ChatContracts.StopOperation;
import edu.courseassist.contract.ChatContracts.OpenCitation;
import edu.courseassist.contract.ChatContracts.CloseSessionTab;
import edu.courseassist.contract.ChatContracts.DeleteSession;
import edu.courseassist.contract.ChatContracts.OpenSessionTab;
import edu.courseassist.contract.ChatContracts.UpdateDraftText;
import edu.courseassist.contract.ApplicationContracts.ApplicationViewState;
import edu.courseassist.contract.ApplicationContracts.MaterialView;
import edu.courseassist.contract.ApplicationContracts.RemoveCourseMaterial;
import edu.courseassist.fixtures.SyntheticControllers;
import edu.courseassist.fixtures.SyntheticStates;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CourseAssistantViewTest {
    @Test
    void rendersTwoControllersAndTargetsStopAtTheActiveSession() throws Exception {
        var project = SyntheticStates.project();
        var session = SyntheticControllers.session(project);
        var application = SyntheticControllers.application(SyntheticStates.application());
        CourseAssistantView view = onEdt(() -> new CourseAssistantView(project.projectInstanceId(), session, application));
        try {
            onEdt(() -> {
                assertEquals(2, ((JTabbedPane) named(view.component(), "sessionTabs")).getTabCount());
                assertTrue(((JTextArea) named(view.component(), "draftPreview")).getText().contains("这段代码"));
                assertTrue(((JTextArea) named(view.component(), "capturePreview")).getText().contains("return value + 1;"));
                assertTrue(((JTextArea) named(view.component(), "answerPreview")).getText().contains("```java"));
                assertFalse(((JTextArea) named(view.component(), "answerPreview")).getText().contains("需要补查课程片段"));
                assertTrue(((JTextArea) named(view.component(), "processPreview")).getText().contains("需要补查课程片段"));
                assertEquals("合成资料", ((edu.courseassist.contract.ApplicationContracts.MaterialView)
                        ((JList<?>) named(view.component(), "materialList")).getModel().getElementAt(0)).name());
                ((JButton) named(view.component(), "stopOperation")).doClick();
                ((JList<?>) named(view.component(), "citationList")).setSelectedIndex(0);
                ((JButton) named(view.component(), "openCitation")).doClick();
                return null;
            });
            assertEquals(2, session.recordedEvents().size());
            StopOperation stop = assertInstanceOf(StopOperation.class, session.recordedEvents().get(0));
            assertEquals(SyntheticStates.SESSION_ID, stop.meta().sessionId().orElseThrow());
            assertEquals("synthetic-operation-1", stop.operationId());
            OpenCitation citation = assertInstanceOf(OpenCitation.class, session.recordedEvents().get(1));
            assertEquals("synthetic-source-v1", citation.citation().sourceVersionId());
        } finally {
            view.close();
        }
    }

    @Test
    void ignoresOlderSnapshotsAndReleasesObserversOnClose() throws Exception {
        var initial = SyntheticStates.project();
        var session = SyntheticControllers.session(initial);
        var application = SyntheticControllers.application(SyntheticStates.application());
        CourseAssistantView view = onEdt(() -> new CourseAssistantView(initial.projectInstanceId(), session, application));
        var first = initial.sessions().get(0);
        var changedDraft = new DraftView(first.draft().draftId(), first.draft().revision() + 1,
                "较新的草稿", first.draft().captures(), first.draft().saveState());
        var changed = new SessionViewState(first.sessionId(), first.title(), first.revision() + 1,
                first.lifecycle(), changedDraft, first.turns(), first.activeOperation(), first.writesBlocked(),
                first.allowedActions(), first.notices());
        var newer = new ProjectChatViewState(initial.projectId(), initial.projectInstanceId(),
                initial.revision() + 1, List.of(changed, initial.sessions().get(1)), initial.openSessionIds(),
                initial.activeSessionId(), initial.notices());

        session.publish(newer);
        session.publish(initial);
        onEdt(() -> {
            assertEquals("较新的草稿", ((JTextArea) named(view.component(), "draftPreview")).getText());
            return null;
        });

        view.close();
        session.publish(initial);
        onEdt(() -> {
            assertEquals("较新的草稿", ((JTextArea) named(view.component(), "draftPreview")).getText());
            return null;
        });
    }

    @Test
    void keepsUnconfirmedDraftWhenAnOlderServerTextArrives() throws Exception {
        var initial = SyntheticStates.project();
        var session = SyntheticControllers.session(initial);
        var application = SyntheticControllers.application(SyntheticStates.application());
        CourseAssistantView view = onEdt(() -> new CourseAssistantView(initial.projectInstanceId(), session, application));
        try {
            onEdt(() -> {
                JTextArea draft = (JTextArea) named(view.component(), "draftPreview");
                draft.append(" 新问题");
                assertFalse(((JButton) named(view.component(), "submitDraft")).isEnabled());
                assertTrue(((JButton) named(view.component(), "saveDraft")).isEnabled());
                return null;
            });
            session.publish(initial);
            onEdt(() -> {
                assertTrue(((JTextArea) named(view.component(), "draftPreview")).getText().endsWith("新问题"));
                ((JButton) named(view.component(), "saveDraft")).doClick();
                return null;
            });
            UpdateDraftText update = assertInstanceOf(UpdateDraftText.class, session.recordedEvents().get(0));
            assertEquals(initial.sessions().get(0).draft().revision(), update.expectedDraftRevision());
            assertEquals(SyntheticStates.SESSION_ID, update.meta().sessionId().orElseThrow());
            onEdt(() -> {
                assertTrue(((JTextArea) named(view.component(), "draftPreview")).getText().endsWith("新问题"));
                return null;
            });
        } finally {
            view.close();
        }
    }

    @Test
    void materialRemovalKeepsTheTargetSelectedAtClickTime() throws Exception {
        var initial = SyntheticStates.application();
        var another = new MaterialView("other-document", "另一份资料", 1, Optional.of("other-v1"),
                initial.materials().get(0).status(), Optional.of(2), SyntheticStates.TIME, Optional.empty());
        var twoMaterials = new ApplicationViewState(initial.revision() + 1, initial.workState(),
                initial.configRevision(), initial.runtimeEpoch(), initial.generationProfile(),
                initial.embeddingProfile(), initial.corpusState(), initial.corpusBase(),
                List.of(initial.materials().get(0), another), initial.hasUnsavedState(), initial.notices());
        var project = SyntheticStates.project();
        var session = SyntheticControllers.session(project);
        var application = SyntheticControllers.application(twoMaterials);
        CourseAssistantView view = onEdt(() -> new CourseAssistantView(project.projectInstanceId(), session, application));
        try {
            onEdt(() -> {
                JList<?> list = (JList<?>) named(view.component(), "materialList");
                list.setSelectedIndex(0);
                ((JButton) named(view.component(), "removeMaterial")).doClick();
                list.setSelectedIndex(1);
                return null;
            });
            RemoveCourseMaterial remove = assertInstanceOf(RemoveCourseMaterial.class,
                    application.recordedEvents().get(0));
            assertEquals(initial.materials().get(0).documentId(), remove.target().documentId());
            assertEquals(initial.materials().get(0).materialRevision(), remove.target().expectedMaterialRevision());
            assertEquals(twoMaterials.corpusBase(), remove.expectedCorpus());
        } finally {
            view.close();
        }
    }

    @Test
    void closeAndDeleteUseDifferentCommandsAndClosedSessionCanReopen() throws Exception {
        var initial = SyntheticStates.project();
        var session = SyntheticControllers.session(initial);
        var application = SyntheticControllers.application(SyntheticStates.application());
        CourseAssistantView view = onEdt(() -> new CourseAssistantView(initial.projectInstanceId(), session, application));
        try {
            onEdt(() -> {
                ((JButton) named(view.component(), "closeTab")).doClick();
                view.deleteSessionConfirmed();
                return null;
            });
            assertInstanceOf(CloseSessionTab.class, session.recordedEvents().get(0));
            assertInstanceOf(DeleteSession.class, session.recordedEvents().get(1));

            var afterClose = new ProjectChatViewState(initial.projectId(), initial.projectInstanceId(),
                    initial.revision() + 1, initial.sessions(), List.of("synthetic-session-2"),
                    Optional.of("synthetic-session-2"), initial.notices());
            session.publish(afterClose);
            onEdt(() -> {
                JList<?> allSessions = (JList<?>) named(view.component(), "sessionList");
                allSessions.setSelectedIndex(0);
                ((JButton) named(view.component(), "openSession")).doClick();
                return null;
            });
            OpenSessionTab reopen = assertInstanceOf(OpenSessionTab.class, session.recordedEvents().get(2));
            assertEquals(SyntheticStates.SESSION_ID, reopen.meta().sessionId().orElseThrow());
        } finally {
            view.close();
        }
    }

    @Test
    void keepsInterruptedAnswerAndProcessVisible() throws Exception {
        var project = SyntheticStates.project(SyntheticStates.Scenario.INTERRUPTED);
        var session = SyntheticControllers.session(project);
        var application = SyntheticControllers.application(SyntheticStates.application());
        CourseAssistantView view = onEdt(() -> new CourseAssistantView(project.projectInstanceId(), session, application));
        try {
            onEdt(() -> {
                String archive = ((JTextArea) named(view.component(), "answerPreview")).getText();
                assertTrue(archive.contains("INTERRUPTED"));
                assertTrue(archive.contains("INCOMPLETE"));
                assertTrue(archive.contains("return value + 1;"));
                assertTrue(((JTextArea) named(view.component(), "processPreview"))
                        .getText().contains("合成供应商思考字段"));
                assertTrue(((JButton) named(view.component(), "copyAnswerSource")).isEnabled());
                return null;
            });
        } finally {
            view.close();
        }
    }

    private static Component named(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component found = named(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(work.call()); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }
}
