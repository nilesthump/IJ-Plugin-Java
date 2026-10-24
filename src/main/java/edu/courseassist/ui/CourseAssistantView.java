package edu.courseassist.ui;

import com.intellij.openapi.project.Project;
import edu.courseassist.contract.ApplicationController;
import edu.courseassist.contract.ApplicationContracts.ApplicationViewState;
import edu.courseassist.contract.ChatContracts.AttemptView;
import edu.courseassist.contract.ChatContracts.BranchView;
import edu.courseassist.contract.ChatContracts.CaptureState;
import edu.courseassist.contract.ChatContracts.Citation;
import edu.courseassist.contract.ChatContracts.CodePart;
import edu.courseassist.contract.ChatContracts.CloseSessionTab;
import edu.courseassist.contract.ChatContracts.CreateSession;
import edu.courseassist.contract.ChatContracts.DeleteSession;
import edu.courseassist.contract.ChatContracts.DraftCapture;
import edu.courseassist.contract.ChatContracts.EventMeta;
import edu.courseassist.contract.ChatContracts.ManualCompact;
import edu.courseassist.contract.ChatContracts.ModelStepView;
import edu.courseassist.contract.ChatContracts.MessagePart;
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
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BoxLayout;
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
import javax.swing.JEditorPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A first state-driven view for the public project and application controllers. */
public final class CourseAssistantView implements AutoCloseable {
    private final String projectInstanceId;
    private final SessionController sessions;
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JTabbedPane tabs = new JTabbedPane();
    private final DefaultListModel<SessionViewState> sessionModel = new DefaultListModel<>();
    private final JList<SessionViewState> sessionList = new JList<>(sessionModel);
    private final DefaultListModel<DraftCapture> captureModel = new DefaultListModel<>();
    private final JList<DraftCapture> captureList = new JList<>(captureModel);
    private final JTextArea draft = area("draftPreview");
    private final JTextArea capture = area("capturePreview");
    private final JTextArea historyCode = area("historyCode");
    private final JTextArea historyQuestions = area("historyQuestions");
    private final JPanel highlightedCaptures = new JPanel();
    private final JPanel highlightedHistory = new JPanel();
    private final IdeCodeViewer codeViewer;
    private final IdeCodeViewer historyCodeViewer;
    private final JTextArea answer = area("answerPreview");
    private final JButton copyAnswerSource = button("copyAnswerSource", "复制最近回答原文");
    private final JEditorPane markdown = new JEditorPane();
    private final MarkdownRenderQueue markdownQueue;
    private final JPanel highlightedMarkdown = new JPanel();
    private final IdeCodeViewer markdownCodeViewer;
    private final JTextArea process = area("processPreview");
    private final DefaultListModel<Citation> citationModel = new DefaultListModel<>();
    private final JList<Citation> citations = new JList<>(citationModel);
    private final JButton openCitation = button("openCitation", "打开引用页");
    private final JToggleButton processToggle = new JToggleButton("过程与思考 ▼", true);
    private final JPanel processBody = new JPanel(new BorderLayout());
    private final ApplicationWorkspace applicationWorkspace;
    private final JButton stop = new JButton("停止当前回答");
    private final JButton create = button("createSession", "新会话");
    private final JButton open = button("openSession", "打开");
    private final JButton closeTab = button("closeTab", "关闭标签");
    private final JButton delete = button("deleteSession", "删除会话");
    private final JButton saveDraft = button("saveDraft", "保存草稿");
    private final JButton submit = button("submitDraft", "发送");
    private final JButton removeCapture = button("removeCapture", "移除代码组");
    private final JButton retry = button("retryAttempt", "重试");
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
    private boolean updatingTabs;
    private boolean updatingDraft;
    private long renderEpoch;
    private String captureRenderKey;
    private String historyRenderKey;
    private volatile boolean closed;
    private String latestAnswerSource;

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
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("CourseAssistantView must be created on EDT");
        }
        this.projectInstanceId = Objects.requireNonNull(projectInstanceId, "projectInstanceId");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(application, "application");
        codeViewer = project == null ? null : new IdeCodeViewer(project);
        historyCodeViewer = project == null ? null : new IdeCodeViewer(project);
        markdownCodeViewer = project == null ? null : new IdeCodeViewer(project);
        markdownQueue = new MarkdownRenderQueue(failure ->
                status.setText("Markdown 渲染失败：" + failure.getClass().getSimpleName()));
        applicationWorkspace = new ApplicationWorkspace(application, credentials, message -> {
            if (!closed) status.setText(message);
        }, () -> !closed);

        tabs.setName("sessionTabs");
        sessionList.setName("sessionList");
        sessionList.setCellRenderer((list, value, index, selected, focused) -> {
            var label = (JLabel) new javax.swing.DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, selected, focused);
            if (value != null) label.setText("会话：" + value.title() + " · " + value.lifecycle());
            return label;
        });
        sessionList.addListSelectionListener(event -> updateActions());
        captureList.setName("captureList");
        captureList.setCellRenderer((list, value, index, selected, focused) -> {
            var label = (JLabel) new javax.swing.DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, selected, focused);
            if (value != null) label.setText("捕获：" + value.captureId() + " · " + value.state());
            return label;
        });
        captureList.addListSelectionListener(event -> updateActions());
        draft.setEditable(true);
        draft.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { draftChanged(); }
            @Override public void removeUpdate(DocumentEvent event) { draftChanged(); }
            @Override public void changedUpdate(DocumentEvent event) { draftChanged(); }
        });
        stop.setName("stopOperation");
        stop.setEnabled(false);
        stop.addActionListener(event -> stopActiveOperation());
        create.addActionListener(event -> createSession());
        open.addActionListener(event -> openSelectedSession());
        closeTab.addActionListener(event -> {
            String id = selectedSessionId();
            if (id != null) dispatch(new CloseSessionTab(meta(id)));
        });
        delete.addActionListener(event -> deleteSelectedSession());
        saveDraft.addActionListener(event -> saveDraft());
        submit.addActionListener(event -> submitDraft());
        removeCapture.addActionListener(event -> removeCapture());
        retry.addActionListener(event -> retryLatest());
        compact.addActionListener(event -> {
            String id = selectedSessionId();
            if (id != null) dispatch(new ManualCompact(meta(id)));
        });
        recover.addActionListener(event -> {
            String id = selectedSessionId();
            if (id != null) dispatch(new RecoverSession(meta(id)));
        });
        tabs.addChangeListener(event -> {
            if (updatingTabs || closed || projectState == null) return;
            String selected = selectedSessionId();
            if (selected == null) return;
            showSession(selected);
            if (!projectState.activeSessionId().filter(selected::equals).isPresent()) {
                sessions.dispatch(new edu.courseassist.contract.ChatContracts.SelectSession(meta(selected)));
            }
        });

        JPanel compose = new JPanel(new GridLayout(2, 1, 0, 8));
        compose.add(section("草稿文字", draft));
        JPanel code = new JPanel(new BorderLayout(4, 4));
        code.add(new JScrollPane(captureList), BorderLayout.NORTH);
        JTabbedPane codeTabs = new JTabbedPane();
        if (codeViewer != null) {
            highlightedCaptures.setLayout(new BoxLayout(highlightedCaptures, BoxLayout.Y_AXIS));
            codeTabs.addTab("IDEA 高亮", new JScrollPane(highlightedCaptures));
        }
        codeTabs.addTab("原始快照", section("只读代码组", capture));
        if (historyCodeViewer != null) {
            highlightedHistory.setLayout(new BoxLayout(highlightedHistory, BoxLayout.Y_AXIS));
            codeTabs.addTab("历史代码高亮", new JScrollPane(highlightedHistory));
        }
        codeTabs.addTab("历史代码原文", section("已提交代码", historyCode));
        code.add(codeTabs, BorderLayout.CENTER);
        code.add(removeCapture, BorderLayout.SOUTH);
        compose.add(code);
        JPanel reply = new JPanel(new GridLayout(2, 1, 0, 8));
        markdown.setName("markdownPreview");
        markdown.setContentType("text/html");
        markdown.setEditable(false);
        JTabbedPane answerTabs = new JTabbedPane();
        if (markdownCodeViewer == null) {
            answerTabs.addTab("渲染", new JScrollPane(markdown));
        } else {
            highlightedMarkdown.setLayout(new BoxLayout(highlightedMarkdown, BoxLayout.Y_AXIS));
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    new JScrollPane(markdown), new JScrollPane(highlightedMarkdown));
            split.setResizeWeight(0.7);
            answerTabs.addTab("渲染与 IDEA 高亮", split);
        }
        answerTabs.addTab("完整回答档案", new JScrollPane(answer));
        answerTabs.addTab("用户消息", new JScrollPane(historyQuestions));
        JPanel answerSection = new JPanel(new BorderLayout());
        answerSection.add(new JLabel("回答正文"), BorderLayout.NORTH);
        answerSection.add(answerTabs, BorderLayout.CENTER);
        copyAnswerSource.addActionListener(event -> {
            if (latestAnswerSource != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(latestAnswerSource), null);
            }
        });
        answerSection.add(copyAnswerSource, BorderLayout.SOUTH);
        reply.add(answerSection);
        process.setFont(process.getFont().deriveFont(Math.max(10f, process.getFont().getSize2D() - 2f)));
        citations.setName("citationList");
        citations.setCellRenderer((list, value, index, selected, focused) -> {
            var label = (JLabel) new javax.swing.DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, selected, focused);
            if (value != null) label.setText("资料：" + value.materialName() + " · PDF 第"
                    + value.firstPhysicalPage() + (value.lastPhysicalPage() == value.firstPhysicalPage()
                    ? "" : "-" + value.lastPhysicalPage()) + "页");
            return label;
        });
        citations.addListSelectionListener(event -> updateActions());
        openCitation.addActionListener(event -> openSelectedCitation());
        processBody.add(new JScrollPane(process), BorderLayout.CENTER);
        JPanel evidence = new JPanel(new BorderLayout());
        evidence.add(new JScrollPane(citations), BorderLayout.CENTER);
        evidence.add(openCitation, BorderLayout.SOUTH);
        processBody.add(evidence, BorderLayout.EAST);
        processToggle.addActionListener(event -> processBody.setVisible(processToggle.isSelected()));
        JPanel processSection = new JPanel(new BorderLayout());
        processSection.add(processToggle, BorderLayout.NORTH);
        processSection.add(processBody, BorderLayout.CENTER);
        reply.add(processSection);
        JTabbedPane sections = new JTabbedPane();
        sections.setName("workspaceSections");
        sections.addTab("草稿与代码", compose);
        sections.addTab("聊天回答", reply);
        sections.addTab("全部会话", section("会话列表", sessionList));
        sections.addTab("资料与配置", applicationWorkspace.component());

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(tabs, BorderLayout.NORTH);
        JPanel toolbar = new JPanel(new GridLayout(0, 3, 4, 4));
        for (JButton button : List.of(create, open, closeTab, delete, saveDraft, submit, retry, compact, recover)) {
            toolbar.add(button);
        }
        top.add(toolbar, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);
        root.add(sections, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(status, BorderLayout.CENTER);
        footer.add(stop, BorderLayout.EAST);
        notices.setRows(3);
        footer.add(new JScrollPane(notices), BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
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
            renderNotices();
            localDrafts.keySet().removeIf(id -> findSession(next, id) == null);
            String selectedInList = sessionList.getSelectedValue() == null ? null : sessionList.getSelectedValue().sessionId();
            sessionModel.clear();
            next.sessions().forEach(sessionModel::addElement);
            if (selectedInList != null) {
                for (int i = 0; i < sessionModel.size(); i++) {
                    if (selectedInList.equals(sessionModel.get(i).sessionId())) {
                        sessionList.setSelectedIndex(i);
                        break;
                    }
                }
            }
            updateTabs(next);
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

    private void updateTabs(ProjectChatViewState state) {
        updatingTabs = true;
        try {
            String previouslySelected = selectedSessionId();
            tabs.removeAll();
            for (String id : state.openSessionIds()) {
                SessionViewState session = findSession(state, id);
                if (session != null) tabs.addTab("会话：" + session.title(), tabIdentity(id));
            }
            String desired = state.activeSessionId().orElse(previouslySelected);
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (Objects.equals(desired, tabs.getComponentAt(i).getName())) {
                    tabs.setSelectedIndex(i);
                    break;
                }
            }
        } finally {
            updatingTabs = false;
        }
    }

    private void showSession(String id) {
        SessionViewState session = findSession(projectState, id);
        if (session == null) { clearSession(); return; }
        LocalDraft edit = localDrafts.get(id);
        if (edit == null || !edit.draftId.equals(session.draft().draftId())) {
            edit = new LocalDraft(session.draft().draftId(), session.draft().revision(), session.draft().text());
            localDrafts.put(id, edit);
        } else if (session.draft().revision() > edit.baseRevision) {
            edit.baseRevision = session.draft().revision();
            if (!edit.dirty || edit.text.equals(session.draft().text())) {
                edit.text = session.draft().text();
                edit.dirty = false;
            }
            edit.pending = false;
        }
        updatingDraft = true;
        try {
            if (!draft.getText().equals(edit.text)) draft.setText(edit.text);
        } finally {
            updatingDraft = false;
        }
        draft.setEditable(!edit.submitting && session.allowedActions().contains(SessionAction.EDIT_DRAFT));
        StringBuilder codes = new StringBuilder();
        String newCaptureKey = id + ":" + session.draft().draftId() + ":" + session.draft().captures().hashCode();
        boolean rebuildHighlights = codeViewer != null && !newCaptureKey.equals(captureRenderKey);
        if (rebuildHighlights) {
            highlightedCaptures.removeAll();
            codeViewer.clear();
        }
        String selectedCapture = captureList.getSelectedValue() == null ? null : captureList.getSelectedValue().captureId();
        captureModel.clear();
        for (DraftCapture item : session.draft().captures()) {
            captureModel.addElement(item);
            codes.append("捕获 ").append(item.captureId()).append(" · ").append(item.state()).append('\n');
            if (item.state() == CaptureState.READY) {
                item.captured().ifPresent(group -> {
                    codes.append("选区:\n").append(group.start().selectedText()).append("\n\n");
                    if (rebuildHighlights) addHighlighted("选区", group.start().selectedText());
                    group.automaticContexts().forEach(context -> {
                        codes.append(context.kind()).append(" · ").append(context.status()).append(":\n");
                        context.text().ifPresent(codes::append);
                        codes.append("\n\n");
                        if (rebuildHighlights) context.text().ifPresent(value ->
                                addHighlighted(context.kind().toString(), value));
                    });
                });
            } else item.statusMessage().ifPresent(message -> codes.append(message).append('\n'));
        }
        if (rebuildHighlights) {
            captureRenderKey = newCaptureKey;
            highlightedCaptures.revalidate();
            highlightedCaptures.repaint();
        }
        if (selectedCapture != null) {
            for (int i = 0; i < captureModel.size(); i++) {
                if (selectedCapture.equals(captureModel.get(i).captureId())) {
                    captureList.setSelectedIndex(i);
                    break;
                }
            }
        }
        capture.setText(codes.toString());

        StringBuilder bodies = new StringBuilder();
        StringBuilder steps = new StringBuilder();
        StringBuilder questions = new StringBuilder();
        StringBuilder committedCode = new StringBuilder();
        latestAnswerSource = null;
        String newHistoryKey = id + ":" + session.turns().stream()
                .map(turn -> turn.userMessage().messageId()).toList().hashCode();
        boolean rebuildHistory = historyCodeViewer != null && !newHistoryKey.equals(historyRenderKey);
        if (rebuildHistory) {
            highlightedHistory.removeAll();
            historyCodeViewer.clear();
        }
        String selectedEvidence = citations.getSelectedValue() == null ? null : citations.getSelectedValue().evidenceId();
        citationModel.clear();
        for (TurnView turn : session.turns()) {
            questions.append("提问 ").append(turn.turnId()).append(":\n");
            for (MessagePart part : turn.userMessage().parts()) {
                if (part instanceof TextPart textPart) {
                    questions.append(textPart.text()).append("\n\n");
                } else if (part instanceof CodePart codePart) {
                    String label = codePart.kind() + " · " + codePart.source().fileRef();
                    committedCode.append(label).append("\n").append(codePart.text()).append("\n\n");
                    if (rebuildHistory) {
                        highlightedHistory.add(new JLabel("代码：" + label));
                        highlightedHistory.add(historyCodeViewer.create(codePart.text(),
                                codePart.source().languageId()));
                    }
                }
            }
            for (AttemptView attempt : turn.attempts()) {
                bodies.append("尝试 ").append(attempt.attemptId()).append(" · ").append(attempt.status()).append('\n');
                for (BranchView branch : attempt.branches()) {
                    bodies.append("分支 ").append(branch.branchId()).append(" · ").append(branch.status())
                            .append(" · ").append(branch.basis().mode()).append(" / ")
                            .append(branch.basis().retrievalStatus()).append('\n')
                            .append(branch.basis().explanation()).append('\n');
                    if (attempt.effectiveBranchId().filter(branch.branchId()::equals).isPresent()
                            && branch.status() != edu.courseassist.contract.ChatContracts.BranchStatus.INVALIDATED) {
                        branch.basis().citations().forEach(citationModel::addElement);
                    }
                    Optional<ModelStepView> displayed = displayedStep(attempt, branch);
                    displayed.ifPresent(step -> bodies.append(step.status()).append(":\n")
                            .append(step.markdown()).append("\n\n"));
                    if (displayed.isPresent()) latestAnswerSource = displayed.orElseThrow().markdown();
                    for (ModelStepView step : branch.steps()) {
                        steps.append(step.stepId()).append(" · ").append(step.status()).append('\n');
                        if (isToolStep(step) && !step.markdown().isEmpty()) {
                            steps.append(step.markdown()).append('\n');
                        }
                        step.thinkingText().ifPresent(thinking -> steps.append(thinking).append('\n'));
                        step.tools().forEach(tool -> {
                            steps.append(tool.name()).append(" · ").append(tool.status()).append('\n');
                            tool.observationText().ifPresent(observation -> steps.append(observation).append('\n'));
                        });
                    }
                }
            }
        }
        historyQuestions.setText(questions.toString());
        historyCode.setText(committedCode.toString());
        if (rebuildHistory) {
            historyRenderKey = newHistoryKey;
            highlightedHistory.revalidate();
            highlightedHistory.repaint();
        }
        if (selectedEvidence != null) {
            for (int i = 0; i < citationModel.size(); i++) {
                if (selectedEvidence.equals(citationModel.get(i).evidenceId())) {
                    citations.setSelectedIndex(i);
                    break;
                }
            }
        }
        answer.setText(bodies.toString());
        copyAnswerSource.setEnabled(latestAnswerSource != null);
        markdownQueue.submit(bodies.toString(), markdownCodeViewer != null, ++renderEpoch, rendered -> {
            markdown.setText(rendered.html());
            if (markdownCodeViewer != null) {
                highlightedMarkdown.removeAll();
                markdownCodeViewer.clear();
                int number = 1;
                for (SafeMarkdown.CodeBlock block : rendered.codeBlocks()) {
                    highlightedMarkdown.add(new JLabel("代码块 " + number++));
                    highlightedMarkdown.add(markdownCodeViewer.create(block.source(), block.language()));
                }
                highlightedMarkdown.revalidate();
                highlightedMarkdown.repaint();
            }
        });
        process.setText(steps.toString());
        status.setText("会话：" + session.title() + " · " + session.lifecycle()
                + (session.writesBlocked() ? " · 写入受阻" : ""));
        stop.setEnabled(session.activeOperation().isPresent()
                && session.allowedActions().contains(edu.courseassist.contract.ChatContracts.SessionAction.STOP));
        updateActions();
    }

    private static Optional<ModelStepView> displayedStep(AttemptView attempt, BranchView branch) {
        Optional<ModelStepView> finalStep = attempt.finalStepId().flatMap(id ->
                branch.steps().stream().filter(step -> id.equals(step.stepId())).findFirst());
        if (finalStep.isPresent()) return finalStep;
        List<ModelStepView> steps = branch.steps();
        if (steps.isEmpty() || isToolStep(steps.get(steps.size() - 1))) return Optional.empty();
        return Optional.of(steps.get(steps.size() - 1));
    }

    private static boolean isToolStep(ModelStepView step) {
        return step.status() == edu.courseassist.contract.ChatContracts.StepStatus.TOOL_CALLS_READY
                || step.status() == edu.courseassist.contract.ChatContracts.StepStatus.TOOLS_RUNNING
                || step.finishReason().filter("tool_calls"::equals).isPresent();
    }

    private void addHighlighted(String label, String source) {
        highlightedCaptures.add(new JLabel(label));
        highlightedCaptures.add(codeViewer.create(source, "java"));
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
        String title = JOptionPane.showInputDialog(root, "会话名称", "新会话");
        if (title == null || title.isBlank()) return;
        dispatch(new CreateSession(new EventMeta(UUID.randomUUID().toString(), projectInstanceId,
                Optional.empty()), title.strip()));
    }

    private void openSelectedSession() {
        if (closed || projectState == null || sessionList.getSelectedValue() == null) return;
        String id = sessionList.getSelectedValue().sessionId();
        if (projectState.openSessionIds().contains(id)) dispatch(new edu.courseassist.contract.ChatContracts.SelectSession(meta(id)));
        else dispatch(new OpenSessionTab(meta(id)));
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
        edit.text = draft.getText();
        edit.dirty = !edit.text.equals(session.draft().text());
        updateActions();
    }

    private void saveDraft() {
        String id = selectedSessionId();
        if (id == null || projectState == null) return;
        SessionViewState session = findSession(projectState, id);
        LocalDraft edit = localDrafts.get(id);
        if (session == null || edit == null || !edit.dirty || edit.pending
                || !session.allowedActions().contains(SessionAction.EDIT_DRAFT)) return;
        edit.pending = true;
        updateActions();
        UpdateDraftText command = new UpdateDraftText(meta(id), edit.draftId,
                session.draft().revision(), edit.text);
        sessions.dispatch(command).whenComplete((result, failure) -> onEdt(() -> {
            if (closed) return;
            LocalDraft current = localDrafts.get(id);
            if (current != edit) return;
            if (failure != null || result.outcome() == Outcome.REJECTED) {
                edit.pending = false;
                showFeedback(result, failure);
            } else {
                status.setText("草稿已接收，等待新状态确认");
            }
            updateActions();
        }));
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
                    } else status.setText("提交已接收，等待新草稿状态");
                    updateActions();
                }));
    }

    private void removeCapture() {
        String id = selectedSessionId();
        DraftCapture selected = captureList.getSelectedValue();
        if (id == null || selected == null || projectState == null) return;
        SessionViewState session = findSession(projectState, id);
        if (session == null || !session.allowedActions().contains(SessionAction.REMOVE_CAPTURE)) return;
        dispatch(new RemoveDraftCapture(meta(id), session.draft().draftId(),
                session.draft().revision(), selected.captureId()));
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

    private void openSelectedCitation() {
        String id = selectedSessionId();
        Citation citation = citations.getSelectedValue();
        if (id != null && citation != null && projectState != null) {
            dispatch(new OpenCitation(meta(id), citation));
        }
    }

    private void updateActions() {
        String id = selectedSessionId();
        SessionViewState session = id == null || projectState == null ? null : findSession(projectState, id);
        LocalDraft edit = id == null ? null : localDrafts.get(id);
        create.setEnabled(!closed && projectState != null);
        open.setEnabled(!closed && sessionList.getSelectedValue() != null);
        closeTab.setEnabled(session != null && session.allowedActions().contains(SessionAction.CLOSE_TAB));
        delete.setEnabled(session != null && session.allowedActions().contains(SessionAction.DELETE));
        saveDraft.setEnabled(session != null && edit != null && edit.dirty && !edit.pending
                && session.allowedActions().contains(SessionAction.EDIT_DRAFT));
        submit.setEnabled(session != null && edit != null && !edit.dirty && !edit.pending && !edit.submitting
                && session.allowedActions().contains(SessionAction.SUBMIT));
        removeCapture.setEnabled(session != null && captureList.getSelectedValue() != null
                && session.allowedActions().contains(SessionAction.REMOVE_CAPTURE));
        retry.setEnabled(session != null && session.allowedActions().contains(SessionAction.RETRY)
                && !session.turns().isEmpty() && !session.turns().get(session.turns().size() - 1).attempts().isEmpty());
        compact.setEnabled(session != null && session.allowedActions().contains(SessionAction.COMPACT));
        recover.setEnabled(session != null && session.allowedActions().contains(SessionAction.RECOVER));
        openCitation.setEnabled(session != null && citations.getSelectedValue() != null);
        stop.setEnabled(session != null && session.activeOperation().isPresent()
                && session.allowedActions().contains(SessionAction.STOP));
    }

    private void dispatch(ViewEvent command) {
        sessions.dispatch(command).whenComplete((result, failure) -> onEdt(() -> {
            if (!closed) showFeedback(result, failure);
        }));
    }

    private void showFeedback(CommandResult result, Throwable failure) {
        if (failure != null) status.setText("操作失败：" + failure.getClass().getSimpleName());
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
    }

    private static void appendNotice(StringBuilder text, Notice notice) {
        text.append(notice.severity()).append(" · ").append(notice.message()).append('\n');
    }

    private EventMeta meta(String sessionId) {
        return new EventMeta(UUID.randomUUID().toString(), projectInstanceId, Optional.of(sessionId));
    }

    private String selectedSessionId() {
        return tabs.getSelectedComponent() == null ? null : tabs.getSelectedComponent().getName();
    }

    private static JPanel tabIdentity(String id) {
        JPanel marker = new JPanel();
        marker.setName(id);
        return marker;
    }

    private static SessionViewState findSession(ProjectChatViewState state, String id) {
        return state.sessions().stream().filter(session -> id.equals(session.sessionId())).findFirst().orElse(null);
    }

    private void clearSession() {
        updatingDraft = true;
        try { draft.setText(""); } finally { updatingDraft = false; }
        draft.setEditable(false);
        captureModel.clear();
        citationModel.clear();
        if (codeViewer != null) {
            highlightedCaptures.removeAll();
            codeViewer.clear();
            captureRenderKey = null;
        }
        capture.setText(""); answer.setText(""); process.setText("");
        latestAnswerSource = null;
        copyAnswerSource.setEnabled(false);
        historyCode.setText(""); historyQuestions.setText("");
        if (historyCodeViewer != null) {
            highlightedHistory.removeAll();
            historyCodeViewer.clear();
            historyRenderKey = null;
        }
        renderEpoch++;
        markdown.setText("");
        if (markdownCodeViewer != null) {
            highlightedMarkdown.removeAll();
            markdownCodeViewer.clear();
        }
        status.setText("没有打开的会话");
        updateActions();
    }

    private static JButton button(String name, String label) {
        JButton value = new JButton(label);
        value.setName(name);
        return value;
    }

    private static JPanel section(String title, JList<?> body) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(new JScrollPane(body), BorderLayout.CENTER);
        return panel;
    }

    private static final class LocalDraft {
        final String draftId;
        long baseRevision;
        String text;
        boolean dirty;
        boolean pending;
        boolean submitting;
        LocalDraft(String draftId, long baseRevision, String text) {
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

    private static JPanel section(String title, JTextArea body) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(new JScrollPane(body), BorderLayout.CENTER);
        return panel;
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        sessionSubscription.close();
        applicationSubscription.close();
        markdownQueue.close();
        onEdt(() -> {
            if (codeViewer != null) codeViewer.close();
            if (historyCodeViewer != null) historyCodeViewer.close();
            if (markdownCodeViewer != null) markdownCodeViewer.close();
        });
    }
}
