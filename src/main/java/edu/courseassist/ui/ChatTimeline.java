package edu.courseassist.ui;

import com.intellij.openapi.project.Project;
import edu.courseassist.contract.ChatContracts.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/** One chronological transcript. Raw message/step sources remain in the controller archive. */
final class ChatTimeline implements AutoCloseable {
    private final JPanel rows = new TranscriptPanel();
    private final JScrollPane scroll = new JScrollPane(rows);
    private final JPanel root = new JPanel(new BorderLayout());
    private final JButton bottom = new JButton("↓");
    private boolean followBottom = true;
    private boolean applying;
    private String displayedSession;
    private List<RenderedRow> renderedRows=List.of();
    private final JComponent tail=(JComponent)Box.createVerticalGlue();
    private record RenderedRow(Row value,JComponent component,JComponent gap) {}
    private List<Row> deferredRows;
    private String deferredSession;

    // Read-only IDE code viewers can consume wheel events before the outer scroll pane.
    private final java.awt.event.AWTEventListener nestedReadingIntent = event -> {
        if(event instanceof java.awt.event.MouseWheelEvent wheel && wheel.getWheelRotation()<0
                && SwingUtilities.isDescendingFrom(wheel.getComponent(),rows)) {
            followBottom=false;updateBottomButton();
        }
    };
    private final IdeCodeViewer viewer;
    private final java.util.function.BiConsumer<String, Citation> openCitation;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("chat-timeline").factory());
    private final Map<String, Boolean> expanded = new HashMap<>();
    private final java.util.concurrent.atomic.AtomicReference<SessionViewState> pending = new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile SessionViewState latest;
    private volatile boolean closed;

    ChatTimeline(Project project, java.util.function.BiConsumer<String, Citation> openCitation) {
        viewer = project == null ? null : new IdeCodeViewer(project);
        this.openCitation = openCitation;
        Toolkit.getDefaultToolkit().addAWTEventListener(nestedReadingIntent,AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        rows.setName("chatTimeline"); rows.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setName("chatScroll"); bottom.setName("scrollToBottom"); bottom.setToolTipText("回到最新内容");
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        JViewport viewport=new JViewport() {
            @Override public void doLayout() {
                super.doLayout();
                if(followBottom && getView()!=null) {
                    Point position=getViewPosition();
                    int y=Math.max(0,getViewSize().height-getExtentSize().height);
                    if(position.y!=y) setViewPosition(new Point(position.x,y));
                }
                updateBottomButton();
            }
        };
        viewport.setView(rows);scroll.setViewport(viewport);
        JLayeredPane layer=new JLayeredPane() {
            @Override public void doLayout() {
                scroll.setBounds(0,0,getWidth(),getHeight());
                Dimension size=bottom.getPreferredSize();
                bottom.setBounds(Math.max(0,(getWidth()-size.width)/2),Math.max(0,getHeight()-size.height-10),size.width,size.height);
            }
            @Override public Dimension getPreferredSize() { return scroll.getPreferredSize(); }
        };
        layer.setName("chatOverlay");layer.add(scroll,JLayeredPane.DEFAULT_LAYER);layer.add(bottom,JLayeredPane.PALETTE_LAYER);
        bottom.setVisible(false);root.add(layer,BorderLayout.CENTER);
        bottom.addActionListener(e -> { followBottom = true; resumeLatest(); scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()); updateBottomButton(); });
        scroll.addMouseWheelListener(e -> {
            followBottom = e.getWheelRotation() >= 0 && atBottom();
            if(followBottom)resumeLatest(); updateBottomButton();
        });
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (e.getValueIsAdjusting() && !applying) { followBottom = atBottom(); }
            updateBottomButton();
        });
        scroll.getVerticalScrollBar().addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent e) { followBottom = false; updateBottomButton(); }
        });
        scroll.getVerticalScrollBar().addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) { followBottom = false; }
            public void mouseReleased(java.awt.event.MouseEvent e) { followBottom = atBottom(); if(followBottom)resumeLatest(); updateBottomButton(); }
        });
    }
    JComponent component() { return root; }
    private boolean atBottom() { var bar = scroll.getVerticalScrollBar(); return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4; }
    private void updateBottomButton() { bottom.setVisible(!followBottom && (deferredRows != null || !atBottom())); }
    private void resumeLatest() {
        if(deferredRows==null || latest==null || !latest.sessionId().equals(deferredSession)){deferredRows=null;deferredSession=null;updateBottomButton();return;}
        var content=deferredRows;var id=deferredSession;deferredRows=null;deferredSession=null;apply(id,content);
    }
    void show(SessionViewState session) {
        if (session.equals(latest)) return;
        if (latest == null || !latest.sessionId().equals(session.sessionId())) clear();
        latest = session; pending.set(session); start();
    }
    private void start() { if (!closed && running.compareAndSet(false, true)) worker.execute(this::drain); }
    private void drain() {
        try {
            SessionViewState session;
            while (!closed && (session = pending.getAndSet(null)) != null) {
                var captured = session;
                var rendered = prepare(session);
                SwingUtilities.invokeLater(() -> { if (!closed && latest == captured) apply(captured.sessionId(), rendered); });
            }
        } finally { running.set(false); if (pending.get() != null) start(); }
    }
    private List<Row> prepare(SessionViewState session) {
        List<Row> result = new ArrayList<>();
        for (TurnView turn : session.turns()) {
            List<Segment> user = new ArrayList<>();
            for (MessagePart part : turn.userMessage().parts()) {
                if (part instanceof TextPart text) user.add(new Segment(text.text(), "", false, true));
                else if (part instanceof CodePart code && code.kind()==PartKind.SELECTION) {
                    String file=code.source().fileRef();file=file.substring(Math.max(file.lastIndexOf('/'),file.lastIndexOf('\\'))+1);
                    user.add(new Segment(code.text(),file+" · "+code.range().startLine()+"–"+code.range().endLine(),true,false));
                }
            }
            result.add(new Row(true, turn.turnId(), "", "", user, List.of()));
            for (AttemptView attempt : turn.attempts()) for (BranchView branch : attempt.branches()) {
                StringBuilder process = new StringBuilder();
                List<Segment> answer = new ArrayList<>();
                for (ModelStepView step : branch.steps()) {
                    boolean tool = step.status() == StepStatus.TOOL_CALLS_READY || step.status() == StepStatus.TOOLS_RUNNING || step.finishReason().filter("tool_calls"::equals).isPresent();
                    process.append(step.status()).append('\n');
                    step.thinkingText().ifPresent(t -> process.append(t).append('\n'));
                    step.tools().forEach(t -> { process.append(t.name()).append(" · ").append(t.status()).append('\n'); t.observationText().ifPresent(o -> process.append(o).append('\n')); });
                    if (tool) process.append(step.markdown()).append('\n');
                    else if (attempt.finalStepId().filter(step.stepId()::equals).isPresent() ||
                            (branch.steps().stream().noneMatch(candidate -> attempt.finalStepId().filter(candidate.stepId()::equals).isPresent()) && step == branch.steps().getLast())) answer.addAll(markdown(step.markdown(), branch.basis().citations()));
                }
                String state = switch (attempt.status()) {
                    case RUNNING -> "正在生成"; case SAVING -> "正在保存"; case SUCCEEDED -> "已完成";
                    case STOPPED -> "已暂停 · 继续将从原问题重新生成"; case INTERRUPTED -> "已中断";
                    case LIMITED -> "达到限制"; case FAILED -> "生成失败，请检查服务配置";
                };
                if (branch.status() == BranchStatus.INVALIDATED) state += " · 旧分支已失效";
                state += " · " + branch.basis().explanation();
                var refs = attempt.contextEligibility()==ContextEligibility.ELIGIBLE_DURABLE_SUCCESS && attempt.effectiveBranchId().filter(branch.branchId()::equals).isPresent() && branch.status() != BranchStatus.INVALIDATED ? branch.basis().citations() : List.<Citation>of();
                result.add(new Row(false, attempt.attemptId() + ":" + branch.branchId(), state, process.toString(), answer, refs));
            }
        }
        return result;
    }
    // Parse once off EDT and preserve text/code order, including an unfinished streaming fence.
    static List<Segment> markdown(String source) { return markdown(source,List.of()); }
    static List<Segment> markdown(String source,List<Citation> citations) {
        var extensions = List.<org.commonmark.Extension>of(org.commonmark.ext.gfm.tables.TablesExtension.create());
        Node document = Parser.builder().extensions(extensions).build().parse(source);
        HtmlRenderer html = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true).build();
        List<Segment> result = new ArrayList<>();
        scrubImages(document);
        displayReferences(document,citations);
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof FencedCodeBlock code) result.add(new Segment(code.getLiteral(), code.getInfo().strip().split("\\s+", 2)[0], true, false));
            else if (node instanceof IndentedCodeBlock code) result.add(new Segment(code.getLiteral(), "", true, false));
            else result.add(new Segment(html.render(node), "", false, false));
        }
        return List.copyOf(result);
    }
    private static void scrubImages(Node parent) {
        for (Node node = parent.getFirstChild(); node != null;) {
            Node next = node.getNext();
            if (node instanceof org.commonmark.node.Image) { node.insertBefore(new Text("[图片未加载]")); node.unlink(); }
            else scrubImages(node);
            node = next;
        }
    }
    private static void displayReferences(Node parent,List<Citation> citations) {
        for(Node node=parent.getFirstChild();node!=null;node=node.getNext()) {
            if(node instanceof Text text) {
                var matcher=java.util.regex.Pattern.compile("\\[\\[course:([^\\]]*)\\]\\]|\\[\\[course:[^\\]]*$").matcher(text.getLiteral());
                StringBuilder result=new StringBuilder();
                while(matcher.find()) {
                    String id=matcher.group(1);
                    String label=citations.stream().filter(c->c.evidenceId().equals(id)).findFirst()
                            .map(c->"【"+c.materialName()+" · PDF "+c.firstPhysicalPage()+(c.lastPhysicalPage()==c.firstPhysicalPage()?"":"–"+c.lastPhysicalPage())+" 页】")
                            .orElse("【课程引用待确认】");
                    matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(label));
                }
                matcher.appendTail(result);text.setLiteral(result.toString());
            } else if(!(node instanceof Code) && !(node instanceof FencedCodeBlock) && !(node instanceof IndentedCodeBlock)) displayReferences(node,citations);
        }
    }
    private void apply(String sessionId, List<Row> content) {
        if(sessionId.equals(displayedSession) && !followBottom) {
            deferredSession=sessionId;deferredRows=content;updateBottomButton();return;
        }
        displayedSession=sessionId;deferredRows=null;deferredSession=null;
        if(content.equals(renderedRows.stream().map(RenderedRow::value).toList()) && !content.isEmpty()) { updateBottomButton();return; }
        applying = true;
        List<RenderedRow> nextRows=new ArrayList<>();
        if(renderedRows.isEmpty()) rows.removeAll();
        if (content.isEmpty()) {
            rows.removeAll();
            JPanel welcome = vertical(); welcome.setBorder(BorderFactory.createEmptyBorder(60, 20, 60, 20));
            welcome.add(new JLabel("◉  Course Assistant")); welcome.add(Box.createVerticalStrut(12));
            welcome.add(new JLabel("提问课程内容，或从编辑器添加 Java 代码")); rows.add(welcome);
        }
        for (Row row : content) {
            int index=nextRows.size();
            if(index<renderedRows.size() && renderedRows.get(index).value().equals(row)) { nextRows.add(renderedRows.get(index));continue; }
            JPanel message = row.user() ? new BubblePanel() : vertical();
            message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS)); message.setAlignmentX(Component.LEFT_ALIGNMENT);
            message.setName(row.user() ? "userBubble" : "agentMessage");
            message.setBorder(BorderFactory.createEmptyBorder(8, 10, 12, 10));
            if (row.user()) {
                message.setBackground(Optional.ofNullable(UIManager.getColor("TextField.background")).orElse(Color.GRAY));
            } else {
                JLabel avatar = new JLabel("◉  Agent"); avatar.setFont(avatar.getFont().deriveFont(Font.BOLD)); message.add(avatar);
                JLabel state = new JLabel(row.state()); state.setToolTipText(row.state()); message.add(state);
                if (!row.process().isBlank()) {
                    JTextArea details = text(row.process()); details.setName("processPreview");
                    details.setFont(details.getFont().deriveFont(11f));
                    details.setVisible(expanded.getOrDefault(row.id(), false));
                    JButton toggle = new JButton((details.isVisible()?"▼":"▶")+" 过程与思考"); toggle.setName("processToggle");
                    toggle.addActionListener(e -> { boolean open = !details.isVisible(); expanded.put(row.id(), open); details.setVisible(open); toggle.setText((open?"▼":"▶")+" 过程与思考"); rows.revalidate(); });
                    message.add(toggle); message.add(details);
                }
            }
            if(row.user()) {
                JTextPane input=new JTextPane();input.setEditable(false);input.setOpaque(false);input.setName("submittedMixedMessage");
                ((javax.swing.text.DefaultCaret)input.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
                try {
                    for(Segment segment:row.body()) {
                        if(segment.code()) {
                            var attributes=new javax.swing.text.SimpleAttributeSet();
                            JLabel chip=new JLabel("  "+segment.language()+"  ");chip.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                            javax.swing.text.StyleConstants.setComponent(attributes,chip);
                            input.getStyledDocument().insertString(input.getDocument().getLength(),"\uFFFC",attributes);
                        } else input.getStyledDocument().insertString(input.getDocument().getLength(),segment.source(),null);
                    }
                }catch(javax.swing.text.BadLocationException failure){throw new IllegalStateException(failure);}
                message.add(input);
            }
            for (Segment segment : row.user()?List.<Segment>of():row.body()) {
                if (segment.code()) {
                    JPanel block = new JPanel(new BorderLayout());
                    JPanel header = new JPanel(new BorderLayout()); header.add(new JLabel(segment.language().isEmpty() ? "代码" : segment.language()), BorderLayout.WEST);
                    JButton copy = new JButton("复制"); copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(segment.source()), null));
                    header.add(copy, BorderLayout.EAST); block.add(header, BorderLayout.NORTH);
                    JComponent body = viewer == null ? text(segment.source()) : viewer.create(segment.source(), segment.language());
                    body.setPreferredSize(new Dimension(300, Math.min(360, Math.max(60, (segment.source().split("\n", -1).length + 1) * 20))));
                    block.add(body, BorderLayout.CENTER); message.add(block);
                } else if (segment.plain()) message.add(text(segment.source()));
                else {
                    JEditorPane html = new JEditorPane("text/html", ""); html.setEditable(false); html.setOpaque(false);
                    html.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
                    Color color = UIManager.getColor("Label.foreground");
                    String rgb = color == null ? "#dddddd" : String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                    ((javax.swing.text.DefaultCaret) html.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
                    html.setText("<html><head><style>body{color:" + rgb + ";font-family:sans-serif;} pre{white-space:pre-wrap;} td{padding:4px;}</style></head><body>" + segment.source() + "</body></html>");
                    message.add(html);
                }
            }
            for (Citation citation : row.citations()) {
                JButton link = new JButton(citation.materialName() + " · PDF " + citation.firstPhysicalPage() + "–" + citation.lastPhysicalPage() + " 页");
                link.setName("citation-" + citation.evidenceId()); link.addActionListener(e -> openCitation.accept(sessionId, citation)); message.add(link);
            }
            for (Component child : message.getComponents()) if (child instanceof JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel aligned;
            if (row.user()) {
                aligned = new JPanel(null) {
                    @Override public Dimension getPreferredSize() {
                        int available = Math.max(100, rows.getWidth() - 24);
                        int width = Math.max(80, (int)(available * .78));
                        message.setSize(width, Short.MAX_VALUE);
                        return new Dimension(available, message.getPreferredSize().height);
                    }
                    @Override public void doLayout() {
                        int width = Math.max(80, (int)(getWidth() * .78));
                        message.setBounds(getWidth() - width, 0, width, getHeight()); message.doLayout();
                    }
                    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
                };
                aligned.add(message);
            } else {
                aligned = new JPanel(new BorderLayout()) {
                    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
                };
                aligned.add(Box.createHorizontalStrut(25), BorderLayout.EAST);
                aligned.add(message, BorderLayout.CENTER);
            }
            aligned.setOpaque(false); aligned.setAlignmentX(Component.LEFT_ALIGNMENT);
            nextRows.add(new RenderedRow(row,aligned,(JComponent)Box.createVerticalStrut(16)));
        }
        for(var previous:renderedRows) if(!nextRows.contains(previous) && viewer!=null) viewer.releaseInside(previous.component());
        if(!content.isEmpty()) {
            List<Component> desired=new ArrayList<>();
            for(var row:nextRows) { desired.add(row.component());desired.add(row.gap()); }desired.add(tail);
            for(int i=0;i<desired.size();i++) {
                if(i<rows.getComponentCount() && rows.getComponent(i)==desired.get(i)) continue;
                if(i<rows.getComponentCount())rows.remove(i);
                rows.add(desired.get(i),i);
            }
            while(rows.getComponentCount()>desired.size())rows.remove(rows.getComponentCount()-1);
        }
        renderedRows=List.copyOf(nextRows);
        applying=false;
        rows.revalidate(); root.validate(); rows.repaint();updateBottomButton();
    }

    private static final class BubblePanel extends JPanel {
        BubblePanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D paint = (Graphics2D) g.create();
            paint.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = Optional.ofNullable(UIManager.getColor("Panel.background")).orElse(Color.DARK_GRAY);
            boolean dark = base.getRed() + base.getGreen() + base.getBlue() < 384;
            paint.setColor(dark ? base.brighter() : base.darker());
            paint.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); paint.dispose();
            super.paintComponent(g);
        }
    }
    private static final class TranscriptPanel extends JPanel implements Scrollable {
        TranscriptPanel() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
        public Dimension getPreferredScrollableViewportSize() { return new Dimension(500, 500); }
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return r.height - 18; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    private static JPanel vertical() { JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
    private static JTextArea text(String source) { JTextArea a = new JTextArea(source); a.setEditable(false); a.setLineWrap(true); a.setWrapStyleWord(true); a.setOpaque(false); ((javax.swing.text.DefaultCaret) a.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE); return a; }
    void clear() { renderedRows=List.of();displayedSession=null;deferredRows=null;deferredSession=null;applying = false; followBottom = true; latest = null; pending.set(null); rows.removeAll(); if (viewer != null) viewer.clear(); updateBottomButton(); }
    public void close() { Toolkit.getDefaultToolkit().removeAWTEventListener(nestedReadingIntent); closed = true; pending.set(null); worker.shutdownNow(); if (viewer != null) viewer.close(); }
    record Segment(String source, String language, boolean code, boolean plain) {}
    private record Row(boolean user, String id, String state, String process, List<Segment> body, List<Citation> citations) {}
}
