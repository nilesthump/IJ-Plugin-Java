package edu.courseassist.ui;

import com.intellij.openapi.project.Project;
import edu.courseassist.contract.ApplicationController;
import edu.courseassist.contract.ApplicationContracts.ApplicationViewState;
import edu.courseassist.contract.ChatContracts.AttemptView;
import edu.courseassist.contract.ChatContracts.CloseSessionTab;
import edu.courseassist.contract.ChatContracts.CreateSession;
import edu.courseassist.contract.ChatContracts.DeleteSession;
import edu.courseassist.contract.ChatContracts.DraftCapture;
import edu.courseassist.contract.ChatContracts.EventMeta;
import edu.courseassist.contract.ChatContracts.ManualCompact;
import edu.courseassist.contract.ChatContracts.OpenSessionTab;
import edu.courseassist.contract.ChatContracts.OpenCitation;
import edu.courseassist.contract.ChatContracts.ProjectChatViewState;
import edu.courseassist.contract.ChatContracts.RecoverSession;
import edu.courseassist.contract.ChatContracts.RemoveDraftCapture;
import edu.courseassist.contract.ChatContracts.RetryAttempt;
import edu.courseassist.contract.ChatContracts.SessionAction;
import edu.courseassist.contract.ChatContracts.SessionViewState;
import edu.courseassist.contract.ChatContracts.StopOperation;
import edu.courseassist.contract.ChatContracts.SubmitDraft;
import edu.courseassist.contract.ChatContracts.TextPart;
import edu.courseassist.contract.ChatContracts.TurnView;
import edu.courseassist.contract.ChatContracts.UpdateDraftText;
import edu.courseassist.contract.ChatContracts.ViewEvent;
import edu.courseassist.contract.CommonContracts.CommandResult;
import edu.courseassist.contract.CommonContracts.Notice;
import edu.courseassist.contract.CommonContracts.Outcome;
import edu.courseassist.contract.SessionController;
import edu.courseassist.contract.CredentialUpdateSink;
import edu.courseassist.contract.ViewSubscription;
import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A first state-driven view for the public project and application controllers. */
public final class CourseAssistantView implements AutoCloseable {
    private static final com.intellij.openapi.util.Key<Map<String, String>> RECOVERY = com.intellij.openapi.util.Key.create("courseassist.draftRecovery");
    private final Map<String, String> recovery;
    private final String projectInstanceId;
    private final SessionController sessions;
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final javax.swing.Timer autosave = new javax.swing.Timer(450, event -> saveDraft());
    private final JButton history = button("historySessions", "◷");
    private final JButton search = button("searchSessions", "⌕");
    private final JButton settings = button("settings", "⚙");
    private final ChatTimeline timeline;
    private com.intellij.openapi.ui.popup.JBPopup popup;
    private final MixedDraftEditor draft = new MixedDraftEditor();
    private String shownDraftSession;
    private final ApplicationWorkspace applicationWorkspace;
    private final JButton create = button("createSession", "新会话");
    private final JButton closeTab = button("closeTab", "关闭标签");
    private final JButton delete = button("deleteSession", "删除会话");
    private final JButton submit = button("submitDraft", "发送");
    private final JButton compact = button("compactSession", "整理历史");
    private final JButton recover = button("recoverSession", "恢复未保存");
    private final JLabel status = new JLabel("等待状态");
    private final JTextArea notices = area("notices");
    private final Map<String, LocalDraft> localDrafts = new HashMap<>();
    private final ViewSubscription sessionSubscription;
    private final ViewSubscription applicationSubscription;
    private ProjectChatViewState projectState;
    private long applicationRevision = -1;
    private List<Notice> applicationNotices = List.of();
    private boolean updatingDraft;
    private volatile boolean closed;
    private boolean closing;

    public CourseAssistantView(String projectInstanceId, SessionController sessions,
                               ApplicationController application) {
        this(projectInstanceId, sessions, application, null, null);
    }

    public CourseAssistantView(String projectInstanceId, SessionController sessions,
                               ApplicationController application, CredentialUpdateSink credentials) {
        this(projectInstanceId, sessions, application, credentials, null);
    }

    public CourseAssistantView(String projectInstanceId, SessionController sessions,
                               ApplicationController application, CredentialUpdateSink credentials,
                               Project project) {
        this(projectInstanceId, sessions, application, credentials, project, recoveryFor(project, sessions, projectInstanceId));
    }

    CourseAssistantView(String projectInstanceId, SessionController sessions, ApplicationController application,
                        CredentialUpdateSink credentials, Project project, Map<String, String> recovery) {
        this.recovery = recovery;
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("CourseAssistantView must be created on EDT");
        }
        this.projectInstanceId = Objects.requireNonNull(projectInstanceId, "projectInstanceId");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(application, "application");
        timeline = new ChatTimeline(project, (id, citation) -> dispatch(new OpenCitation(meta(id), citation)));
        autosave.setRepeats(false);
        applicationWorkspace = new ApplicationWorkspace(application, credentials, message -> {
            if (!closed) status.setText(message);
        }, () -> !closed);

        draft.setEditable(true);
        draft.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { draftChanged(); }
            @Override public void removeUpdate(DocumentEvent event) { draftChanged(); }
            @Override public void changedUpdate(DocumentEvent event) { draftChanged(); }
        });
        create.addActionListener(event -> createSession());
        closeTab.addActionListener(event -> {
            String id = selectedSessionId();
            if (id != null) dispatch(new CloseSessionTab(meta(id)));
        });
        delete.addActionListener(event -> deleteSelectedSession());
        submit.addActionListener(event -> primaryAction());
        compact.addActionListener(event -> {
            String id = selectedSessionId();
            if (id != null) dispatch(new ManualCompact(meta(id)));
        });
        recover.addActionListener(event -> {
            String id = selectedSessionId();
            if (id != null) dispatch(new RecoverSession(meta(id)));
        });
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 8));
        top.add(new JLabel("Course Assistant"), BorderLayout.WEST);
        JPanel toolbar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
        create.setText("＋"); create.setToolTipText("新建会话");
        history.setToolTipText("历史会话"); search.setToolTipText("搜索会话"); settings.setToolTipText("设置");
        int iconNumber = 0;
        for (JButton action : List.of(create, history, search, settings)) {
            action.setText(""); action.setIcon(new ToolbarIcon(iconNumber++));
            action.getAccessibleContext().setAccessibleName(action.getToolTipText());
            action.setMargin(new java.awt.Insets(2, 2, 2, 2));
            action.setPreferredSize(new java.awt.Dimension(34, 30)); toolbar.add(action);
        }
        history.addActionListener(event -> showSessions(false));
        search.addActionListener(event -> showSessions(true));
        settings.addActionListener(event -> showSettings());
        top.add(toolbar, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        JPanel composer = new JPanel(new BorderLayout(6, 6));
        composer.setName("composer");
        composer.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Separator.foreground")),
                javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        JScrollPane draftScroll = new JScrollPane(draft);
        draftScroll.setBorder(null); draft.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));
        draft.setOpaque(false); draftScroll.getViewport().setOpaque(false); draftScroll.setOpaque(false);
        composer.add(draftScroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.CENTER); bottom.add(submit, BorderLayout.EAST);
        composer.add(bottom, BorderLayout.SOUTH);
        JPanel footer = new JPanel(new BorderLayout(0, 4));
        notices.setRows(2); notices.setVisible(false);
        footer.add(notices, BorderLayout.NORTH); footer.add(composer, BorderLayout.CENTER);
        footer.setMinimumSize(new java.awt.Dimension(100, 150));
        footer.setPreferredSize(new java.awt.Dimension(400, 230));
        timeline.component().setMinimumSize(new java.awt.Dimension(100, 100));
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, timeline.component(), footer);
        split.setName("chatDraftSplit"); split.setResizeWeight(1.0); split.setContinuousLayout(true);
        split.setDividerSize(8); split.setBorder(null);
        root.add(split, BorderLayout.CENTER);
        updateActions();

        sessionSubscription = sessions.observe(projectInstanceId, this::receiveProject);
        applicationSubscription = application.observe(this::receiveApplication);
    }

    public JPanel component() { return root; }

    private void receiveProject(ProjectChatViewState next) {
        if (!projectInstanceId.equals(next.projectInstanceId())) return;
        onEdt(() -> {
            if (closed || projectState != null && next.revision() < projectState.revision()) return;
            projectState = next;
            recovery.keySet().removeIf(key -> next.sessions().stream().noneMatch(session ->
                    session.lifecycle() == edu.courseassist.contract.ChatContracts.Lifecycle.LIVE && key.equals(recoveryKey(session.sessionId(), session.draft().draftId()))));
            localDrafts.keySet().removeIf(id -> findSession(next, id) == null);
            next.sessions().forEach(this::reconcileDraft);
            if (closing) { finishCloseIfSettled(); return; }
            renderNotices();
            localDrafts.keySet().removeIf(id -> findSession(next, id) == null);
            String selected = selectedSessionId();
            if (selected != null) showSession(selected);
            else clearSession();
        });
    }

    private void receiveApplication(ApplicationViewState next) {
        onEdt(() -> {
            if (closed || next.revision() < applicationRevision) return;
            applicationRevision = next.revision();
            applicationNotices = next.notices();
            renderNotices();
            applicationWorkspace.apply(next);
        });
    }

    private LocalDraft reconcileDraft(SessionViewState session) {
        LocalDraft edit = localDrafts.get(session.sessionId());
        if (edit == null || !edit.draftId.equals(session.draft().draftId())) {
            edit = new LocalDraft(session.draft().draftId(), session.draft().revision(), session.draft().text(), session.draft().captureOffsets(),session.draft().captureOrder());
            String restored = recovery.get(recoveryKey(session.sessionId(), session.draft().draftId()));
            if (restored != null) { restoreDraft(edit,restored); edit.dirty = !edit.text.equals(session.draft().text()) || (!sameLayout(edit.offsets,session.draft().captureOffsets(),session.draft().captures(),edit.text.length()) || !edit.order.equals(session.draft().captureOrder())); }
            localDrafts.put(session.sessionId(), edit);
        } else if (session.draft().revision() > edit.baseRevision) {
            edit.baseRevision = session.draft().revision();
            if (!edit.dirty || edit.text.equals(session.draft().text()) && sameLayout(edit.offsets,session.draft().captureOffsets(),session.draft().captures(),edit.text.length()) && edit.order.equals(session.draft().captureOrder())) {
                edit.text = session.draft().text(); edit.offsets = new HashMap<>(session.draft().captureOffsets()); edit.order=session.draft().captureOrder();
                edit.dirty = false;
            }
        }
        if (!edit.dirty) recovery.remove(recoveryKey(session.sessionId(), edit.draftId), rememberDraft(edit));
        return edit;
    }

    private void showSession(String id) {
        SessionViewState session = findSession(projectState, id);
        if (session == null) { clearSession(); return; }
        LocalDraft edit = reconcileDraft(session);
        updatingDraft = true;
        try {
            String previousRecovery=rememberDraft(edit);
            Map<String,Integer> beforeLayout=Map.copyOf(edit.offsets); var beforeOrder=edit.order;
            edit.order=draft.incomingOrder(session.draft().captures(),edit.order,id.equals(shownDraftSession));
            edit.offsets.keySet().removeIf(capture->session.draft().captures().stream().noneMatch(c->c.captureId().equals(capture)));
            int insertion=id.equals(shownDraftSession)?Math.min(edit.text.length(),draft.textCaret()):edit.text.length();
            for(var capture:session.draft().captures()) edit.offsets.putIfAbsent(capture.captureId(),insertion);
            draft.render(edit.text,session.draft().captures(),edit.offsets,edit.order,capture -> {
                var latest=findSession(projectState,id);
                if(latest!=null)dispatch(new RemoveDraftCapture(meta(id),latest.draft().draftId(),latest.draft().revision(),capture));
            });
            if(!id.equals(shownDraftSession)) draft.setCaretPosition(draft.getDocument().getLength());
            shownDraftSession=id;
            edit.dirty=!edit.text.equals(session.draft().text()) || (!sameLayout(edit.offsets,session.draft().captureOffsets(),session.draft().captures(),edit.text.length()) || !edit.order.equals(session.draft().captureOrder()));
            if(edit.dirty){recovery.put(recoveryKey(id,edit.draftId),rememberDraft(edit));if(!beforeLayout.equals(edit.offsets)||!beforeOrder.equals(edit.order))autosave.restart();}
            else recovery.remove(recoveryKey(id,edit.draftId),previousRecovery);
        } finally {
            updatingDraft = false;
        }
        draft.setEditable(!edit.submitting && session.allowedActions().contains(SessionAction.EDIT_DRAFT));

        timeline.show(session);
        status.setText("会话：" + session.title() + " · " + session.lifecycle()
                + (session.writesBlocked() ? " · 写入受阻" : "") + (edit.dirty ? " · 未保存草稿" : ""));
        updateActions();
    }

    private void stopActiveOperation() {
        String id = selectedSessionId();
        if (closed || id == null || projectState == null) return;
        SessionViewState session = findSession(projectState, id);
        if (session == null || !session.allowedActions().contains(edu.courseassist.contract.ChatContracts.SessionAction.STOP)) return;
        session.activeOperation().ifPresent(operation ->
                dispatch(new StopOperation(meta(id), operation.operationId())));
    }

    private void createSession() {
        if (closed || projectState == null) return;
        String title = "新会话 " + (projectState.sessions().size() + 1);
        dispatch(new CreateSession(new EventMeta(UUID.randomUUID().toString(), projectInstanceId,
                Optional.empty()), title.strip()));
    }

    private void deleteSelectedSession() {
        String id = selectedSessionId();
        if (closed || id == null || projectState == null) return;
        if (JOptionPane.showConfirmDialog(root, "永久删除此会话及全部记录？", "删除会话",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            deleteSessionConfirmed();
        }
    }

    void deleteSessionConfirmed() {
        String id = selectedSessionId();
        if (closed || id == null || projectState == null) return;
        dispatch(new DeleteSession(meta(id)));
    }

    private void draftChanged() {
        if (updatingDraft || closed || projectState == null) return;
        String id = selectedSessionId();
        if (id == null) return;
        LocalDraft edit = localDrafts.get(id);
        SessionViewState session = findSession(projectState, id);
        if (edit == null || session == null) return;
        String previousText = rememberDraft(edit);
        edit.text = draft.getText(); edit.offsets = new HashMap<>(draft.captureOffsets()); edit.order=draft.captureOrder();
        edit.dirty = !edit.text.equals(session.draft().text()) || (!sameLayout(edit.offsets,session.draft().captureOffsets(),session.draft().captures(),edit.text.length()) || !edit.order.equals(session.draft().captureOrder()));
        if (edit.dirty) recovery.put(recoveryKey(id, edit.draftId), rememberDraft(edit));
        else recovery.remove(recoveryKey(id, edit.draftId), previousText);
        autosave.restart();
        updateActions();
    }

    private void saveDraft() {
        for (String id : List.copyOf(localDrafts.keySet())) saveDraft(id);
    }

    private void saveDraft(String id) {
        if (id == null || projectState == null) return;
        SessionViewState session = findSession(projectState, id);
        LocalDraft edit = localDrafts.get(id);
        if (session == null || edit == null || !edit.dirty || edit.pending || edit.submitting
                || !session.allowedActions().contains(SessionAction.EDIT_DRAFT)) return;
        edit.pending = true;
        updateActions();
        UpdateDraftText command = new UpdateDraftText(meta(id), edit.draftId,
                session.draft().revision(), edit.text, Optional.of(Map.copyOf(edit.offsets)),Optional.of(edit.order));
        sessions.dispatch(command).whenComplete((result, failure) -> onEdt(() -> {
            if (closed) return;
            LocalDraft current = localDrafts.get(id);
            if (current != edit) return;
            edit.pending = false;
            if (failure != null || result.outcome() == Outcome.REJECTED) {
                edit.pending = false;
                showFeedback(result, failure);
            } else {
                status.setText("草稿已接收，等待新状态确认");
            }
            if (failure == null && result.outcome() == Outcome.APPLIED && edit.dirty && !edit.pending) {
                if (closing) saveDraft(id); else autosave.restart();
            }
            if (closing) finishCloseIfSettled();
            updateActions();
        }));
    }

    private void primaryAction() {
        String id = selectedSessionId();
        SessionViewState session = id == null ? null : findSession(projectState, id);
        if (session == null) return;
        if (session.activeOperation().isPresent()) { stopActiveOperation(); return; }
        LocalDraft edit = localDrafts.get(id);
        if (edit == null || edit.pending || edit.submitting) return;
        if (edit.text.isBlank() && session.draft().captures().isEmpty()) { retryLatest(); return; }
        if (!session.allowedActions().contains(SessionAction.SUBMIT)) return;
        autosave.stop();
        if (!edit.dirty) { submitDraft(); return; }
        edit.submitting = true;
        String exact = edit.text;
        draft.setEditable(false); updateActions();
        sessions.dispatch(new UpdateDraftText(meta(id), edit.draftId, session.draft().revision(), exact, Optional.of(Map.copyOf(edit.offsets)),Optional.of(edit.order)))
            .whenComplete((saved, failure) -> onEdt(() -> {
                if (closed || localDrafts.get(id) != edit) return;
                SessionViewState current = findSession(projectState, id);
                if (failure != null || saved.outcome() == Outcome.REJECTED || current == null ||
                        !current.draft().draftId().equals(edit.draftId) || !current.draft().text().equals(exact)) {
                    edit.submitting = false; showFeedback(saved, failure);
                    if (closing) { finishCloseIfSettled(); return; }
                    if (id.equals(selectedSessionId())) showSession(id);
                    return;
                }
                // Preserve original session identity even if the user opened another history item.
                sessions.dispatch(new SubmitDraft(meta(id), edit.draftId, current.draft().revision()))
                    .whenComplete((result, error) -> onEdt(() -> {
                        if (closed) return;
                        edit.submitting = false; showFeedback(result, error);
                        if (closing) { finishCloseIfSettled(); return; }
                        if (id.equals(selectedSessionId())) showSession(id);
                    }));
            }));
    }

    private static String retryLabel(SessionViewState session) {
        var turns = session.turns();
        if (!turns.isEmpty()) {
            var attempts = turns.getLast().attempts();
            if (!attempts.isEmpty()) {
                var state = attempts.getLast().status();
                if (state == edu.courseassist.contract.ChatContracts.AttemptStatus.STOPPED ||
                        state == edu.courseassist.contract.ChatContracts.AttemptStatus.INTERRUPTED) return "继续生成";
            }
        }
        return "重试";
    }

    private void showSessions(boolean searching) {
        if (projectState == null) return;
        JPanel content = new JPanel(new BorderLayout(6, 6));
        var model = new DefaultListModel<SessionViewState>();
        var list = new JList<SessionViewState>(model); list.setName("sessionList");
        list.setCellRenderer((items, value, index, selected, focused) -> {
            var label = (JLabel) new javax.swing.DefaultListCellRenderer().getListCellRendererComponent(items, value, index, selected, focused);
            if (value != null) label.setText(value.title()); return label;
        });
        javax.swing.JTextField query = new javax.swing.JTextField(); query.setName("sessionSearch");
        Runnable filter = () -> {
            model.clear(); String needle = query.getText().strip().toLowerCase(java.util.Locale.ROOT);
            projectState.sessions().stream().filter(s -> s.title().toLowerCase(java.util.Locale.ROOT).contains(needle) ||
                s.turns().stream().flatMap(t -> t.userMessage().parts().stream()).anyMatch(p ->
                    p instanceof TextPart t && t.text().toLowerCase(java.util.Locale.ROOT).contains(needle))).forEach(model::addElement);
        };
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter.run(); }
            public void removeUpdate(DocumentEvent e) { filter.run(); }
            public void changedUpdate(DocumentEvent e) { filter.run(); }
        });
        if (searching) content.add(query, BorderLayout.NORTH);
        filter.run(); content.add(new JScrollPane(list), BorderLayout.CENTER);
        Runnable openChoice = () -> {
            var chosen = list.getSelectedValue(); if (chosen == null) return;
            if (popup != null) popup.cancel();
            dispatch(new OpenSessionTab(meta(chosen.sessionId())));
        };
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getButton() == 1) openChoice.run(); }
        });
        list.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "open");
        list.getActionMap().put("open", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { openChoice.run(); }
        });
        content.setPreferredSize(new java.awt.Dimension(searching ? 540 : 360, 420));
        showPopup(content, searching ? query : list, searching ? search : history);
    }

    private void showSettings() {
        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.add(applicationWorkspace.component(), BorderLayout.CENTER);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        actions.add(delete);
        content.add(actions, BorderLayout.SOUTH);
        content.setPreferredSize(new java.awt.Dimension(600, 450));
        showPopup(content, applicationWorkspace.component(), settings);
    }

    private void showPopup(javax.swing.JComponent content, javax.swing.JComponent focus, JButton anchor) {
        if (popup != null) popup.cancel();
        popup = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance().createComponentPopupBuilder(content, focus)
                .setRequestFocus(true).setFocusable(true).setCancelOnClickOutside(true).setCancelOnOtherWindowOpen(true)
                .setResizable(true).createPopup();
        popup.showUnderneathOf(anchor);
    }

    private void submitDraft() {
        String id = selectedSessionId();
        if (id == null || projectState == null) return;
        SessionViewState session = findSession(projectState, id);
        LocalDraft edit = localDrafts.get(id);
        if (session == null || edit == null || edit.dirty || edit.pending || edit.submitting
                || !session.allowedActions().contains(SessionAction.SUBMIT)) return;
        edit.submitting = true;
        draft.setEditable(false);
        updateActions();
        sessions.dispatch(new SubmitDraft(meta(id), edit.draftId, session.draft().revision()))
                .whenComplete((result, failure) -> onEdt(() -> {
                    if (closed || localDrafts.get(id) != edit) return;
                    if (failure != null || result.outcome() == Outcome.REJECTED) {
                        edit.submitting = false;
                        showFeedback(result, failure);
                        if (id.equals(selectedSessionId())) showSession(id);
                    } else {
                        edit.submitting=false;
                        if(id.equals(selectedSessionId()))showSession(id);
                    }
                    if (closing) finishCloseIfSettled();
                    updateActions();
                }));
    }

    private void retryLatest() {
        String id = selectedSessionId();
        if (id == null || projectState == null) return;
        SessionViewState session = findSession(projectState, id);
        if (session == null || !session.allowedActions().contains(SessionAction.RETRY)
                || session.turns().isEmpty()) return;
        TurnView turn = session.turns().get(session.turns().size() - 1);
        if (turn.attempts().isEmpty()) return;
        AttemptView attempt = turn.attempts().get(turn.attempts().size() - 1);
        dispatch(new RetryAttempt(meta(id), turn.turnId(), attempt.attemptId()));
    }

    private void updateActions() {
        String id = selectedSessionId();
        SessionViewState session = id == null || projectState == null ? null : findSession(projectState, id);
        LocalDraft edit = id == null ? null : localDrafts.get(id);
        create.setEnabled(!closed && projectState != null);
        closeTab.setEnabled(session != null && session.allowedActions().contains(SessionAction.CLOSE_TAB));
        delete.setEnabled(session != null && session.allowedActions().contains(SessionAction.DELETE));
        boolean running = session != null && session.activeOperation().isPresent();
        boolean hasInput = edit != null && (!edit.text.isBlank() || !session.draft().captures().isEmpty());
        boolean canRetry = session != null && session.allowedActions().contains(SessionAction.RETRY) && !session.turns().isEmpty();
        submit.setText(running ? "■" : hasInput ? "↑" : canRetry ? retryLabel(session) : "↑");
        submit.setToolTipText(running ? "暂停生成" : hasInput ? "发送" : canRetry ? retryLabel(session) : "发送");
        submit.setEnabled(session != null && edit != null && !edit.submitting &&
                (running ? session.allowedActions().contains(SessionAction.STOP) :
                 hasInput ? !edit.pending && session.allowedActions().contains(SessionAction.SUBMIT) : canRetry));
        compact.setEnabled(session != null && session.allowedActions().contains(SessionAction.COMPACT));
        recover.setEnabled(session != null && session.allowedActions().contains(SessionAction.RECOVER));
    }

    private void dispatch(ViewEvent command) {
        sessions.dispatch(command).whenComplete((result, failure) -> onEdt(() -> {
            if (!closed) showFeedback(result, failure);
        }));
    }

    private void showFeedback(CommandResult result, Throwable failure) {
        if (failure != null) status.setText("操作失败：" + failure.getClass().getSimpleName());
        else if (result == null) status.setText("草稿状态尚未确认，请重试");
        else if (result.outcome() == Outcome.REJECTED) status.setText("操作未应用：" + result.rejection().orElseThrow());
        else status.setText("操作已应用");
    }

    private void renderNotices() {
        StringBuilder text = new StringBuilder();
        if (projectState != null) {
            projectState.notices().forEach(notice -> appendNotice(text, notice));
            projectState.sessions().forEach(session ->
                    session.notices().forEach(notice -> appendNotice(text, notice)));
        }
        applicationNotices.forEach(notice -> appendNotice(text, notice));
        notices.setText(text.toString());
        notices.setVisible(!text.isEmpty());
    }

    private static void appendNotice(StringBuilder text, Notice notice) {
        text.append(notice.severity()).append(" · ").append(notice.message()).append('\n');
    }

    private EventMeta meta(String sessionId) {
        return new EventMeta(UUID.randomUUID().toString(), projectInstanceId, Optional.of(sessionId));
    }

    private String selectedSessionId() {
        return projectState == null ? null : projectState.activeSessionId().orElse(null);
    }

    private static SessionViewState findSession(ProjectChatViewState state, String id) {
        return state.sessions().stream().filter(session -> id.equals(session.sessionId())).findFirst().orElse(null);
    }

    private void clearSession() {
        timeline.clear();
        updatingDraft = true;
        try { draft.render("",List.of(),Map.of(),capture->{}); shownDraftSession=null; } finally { updatingDraft = false; }
        draft.setEditable(false);
        status.setText("没有打开的会话");
        updateActions();
    }

    private record ToolbarIcon(int kind) implements javax.swing.Icon {
        public int getIconWidth() { return 20; }
        public int getIconHeight() { return 20; }
        public void paintIcon(java.awt.Component c, java.awt.Graphics graphics, int x, int y) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            try {
                g.translate(x, y); g.setColor(c.getForeground()); g.setStroke(new java.awt.BasicStroke(1.6f));
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                if (kind == 0) { g.drawRoundRect(2, 2, 15, 13, 5, 5); g.drawLine(5, 15, 3, 18); g.drawLine(6, 8, 13, 8); g.drawLine(10, 5, 10, 12); }
                else if (kind == 1) { g.drawArc(3, 3, 14, 14, 35, 300); g.drawLine(2, 3, 2, 8); g.drawLine(2, 8, 7, 8); g.drawLine(10, 5, 10, 10); g.drawLine(10, 10, 13, 12); }
                else if (kind == 2) { g.drawOval(2, 2, 11, 11); g.drawLine(12, 12, 18, 18); }
                else { g.drawOval(5, 5, 10, 10); g.drawOval(8, 8, 4, 4); for (int i=0;i<8;i++) { double a=i*Math.PI/4; g.drawLine(10+(int)(6*Math.cos(a)),10+(int)(6*Math.sin(a)),10+(int)(9*Math.cos(a)),10+(int)(9*Math.sin(a))); } }
            } finally { g.dispose(); }
        }
    }

    private static JButton button(String name, String label) {
        JButton value = new JButton(label);
        value.setName(name);
        return value;
    }

    private static String recoveryKey(String session, String draft) { return session + "\u0000" + draft; }
    private static Map<String, String> recoveryFor(Project project, SessionController sessions, String instance) {
        if (project == null) return new HashMap<>();
        Map<String, String> value = project.getUserData(RECOVERY);
        if (value == null) {
            value = new HashMap<>(); project.putUserData(RECOVERY, value);
            Map<String, String> owned = value;
            ViewSubscription subscription = sessions.observe(instance, state -> onEdt(() ->
                owned.keySet().removeIf(key -> state.sessions().stream().noneMatch(session ->
                    session.lifecycle() == edu.courseassist.contract.ChatContracts.Lifecycle.LIVE &&
                    key.equals(recoveryKey(session.sessionId(), session.draft().draftId()))))));
            com.intellij.openapi.util.Disposer.register(project, () -> { subscription.close(); owned.clear(); });
        }
        return value;
    }

    private static boolean sameLayout(Map<String,Integer> a,Map<String,Integer> b,List<DraftCapture> captures,int length) {
        return captures.stream().allMatch(c->a.getOrDefault(c.captureId(),length).equals(b.getOrDefault(c.captureId(),length)));
    }
    private static String rememberDraft(LocalDraft draft) {
        if(draft.offsets.isEmpty())return draft.text;
        var encoder=java.util.Base64.getEncoder();
        StringBuilder out=new StringBuilder("\u0000F0_MIXED1:").append(encoder.encodeToString(draft.text.getBytes(java.nio.charset.StandardCharsets.UTF_8))).append('\n');
        draft.order.forEach(id->out.append(encoder.encodeToString(id.getBytes(java.nio.charset.StandardCharsets.UTF_8))).append(':').append(draft.offsets.getOrDefault(id,draft.text.length())).append('\n'));
        return out.toString();
    }
    private static void restoreDraft(LocalDraft draft,String source) {
        if(!source.startsWith("\u0000F0_MIXED1:")){draft.text=source;return;}
        String[] lines=source.substring(11).split("\n");var decoder=java.util.Base64.getDecoder();
        draft.text=new String(decoder.decode(lines[0]),java.nio.charset.StandardCharsets.UTF_8);draft.offsets=new java.util.LinkedHashMap<>();
        for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');draft.offsets.put(new String(decoder.decode(lines[i].substring(0,colon)),java.nio.charset.StandardCharsets.UTF_8),Integer.parseInt(lines[i].substring(colon+1)));}
        draft.order=List.copyOf(draft.offsets.keySet());
    }

    private static final class LocalDraft {
        final String draftId;
        long baseRevision;
        String text;
        Map<String,Integer> offsets;
        List<String> order;
        boolean dirty;
        boolean pending;
        boolean submitting;
        LocalDraft(String draftId, long baseRevision, String text, Map<String,Integer> offsets,List<String> order) {
            this.order=List.copyOf(order);
            this.offsets = new HashMap<>(offsets);
            this.draftId = draftId;
            this.baseRevision = baseRevision;
            this.text = text;
        }
    }

    private static JTextArea area(String name) {
        JTextArea value = new JTextArea();
        value.setName(name);
        value.setEditable(false);
        value.setLineWrap(true);
        value.setWrapStyleWord(true);
        return value;
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    @Override public void close() {
        onEdt(() -> {
            if (closed || closing) return;
            closing = true;
            autosave.stop();
            if (popup != null) popup.cancel();
            timeline.close();
            applicationSubscription.close();
            saveDraft();
            finishCloseIfSettled();
        });
    }
    private void finishCloseIfSettled() {
        if (localDrafts.values().stream().anyMatch(edit -> edit.pending || edit.submitting)) return;
        // Latest unsaved text remains in project-owned recovery until a matching durable
        // snapshot confirms it, the draft is consumed, or the session is deleted.
        if (localDrafts.values().stream().anyMatch(edit -> edit.dirty)) {
            status.setText("草稿未保存：本项目内重新打开侧栏可恢复文字；退出 IDEA 前请先恢复保存");
            if (com.intellij.openapi.application.ApplicationManager.getApplication() != null) {
                com.intellij.notification.NotificationGroupManager.getInstance().getNotificationGroup("Course Assistant")
                    .createNotification("草稿未保存", status.getText(), com.intellij.notification.NotificationType.WARNING).notify(null);
            }
        }
        closed = true;
        sessionSubscription.close();
    }
}
