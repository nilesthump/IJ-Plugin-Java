package edu.courseassist.bootstrap;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import edu.courseassist.core.react.ReactEngine;
import edu.courseassist.core.session.SessionData.Binding;
import edu.courseassist.core.session.SessionData.IdentityUncertain;
import edu.courseassist.ui.CourseAssistantView;
import java.awt.BorderLayout;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;

/** Production view wiring. Storage and identity discovery never block the EDT. */
public final class BootstrapToolWindowFactory implements ToolWindowFactory {
    @Override public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        Host host = new Host(project);
        Content content = ContentFactory.getInstance().createContent(host.panel, "课程助教", false);
        content.setDisposer(host);
        toolWindow.getContentManager().addContent(content);
        host.open(false);
    }

    private static final class Host implements Disposable {
        private final Project project;
        private final CourseAssistantProject service;
        private final JPanel panel = new JPanel(new BorderLayout());
        private CourseAssistantView view;
        private boolean closed;
        private long request;
        private boolean associating;

        Host(Project project) { this.project = project; service = CourseAssistantProject.getInstance(project); }

        private void open(boolean asNew) { associating = false; load(service.openSessions(asNew)); }

        private void load(CompletableFuture<Binding> opening) {
            long token = ++request;
            show(new JLabel("正在恢复课程库与项目会话…"));
            opening.thenCompose(ignored -> service.application().controllers()).whenComplete((controllers, failure) ->
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (closed || project.isDisposed() || token != request) return;
                        if (failure != null) { failed(ReactEngine.unwrap(failure)); return; }
                        try {
                            view = new CourseAssistantView(service.projectInstanceId(), controllers.sessions(),
                                    controllers.application(), controllers.application(), project);
                            show(view.component());
                        } catch (RuntimeException viewFailure) { failed(viewFailure); }
                    }));
        }

        private void failed(Throwable failure) {
            if (!(failure instanceof IdentityUncertain)) com.intellij.openapi.diagnostic.Logger.getInstance(BootstrapToolWindowFactory.class)
                    .warn("F0 project view initialization failed", failure);
            JPanel message = new JPanel(new BorderLayout(4, 4));
            message.add(new JLabel(failure instanceof IdentityUncertain
                    ? "项目身份待确认：新项目不会继承其他项目会话。"
                    : "初始化失败（" + failure.getClass().getSimpleName() + "），原有记录已保留。"), BorderLayout.NORTH);
            JPanel actions = new JPanel();
            if (service.application().initializationFailed()) {
                actions.add(new JLabel("应用存储初始化失败，请排除故障后重启 IJ；不会清空原有数据。"));
            } else {
                JButton retry = new JButton(associating ? "重新选择关联项目" : "重试恢复");
                retry.addActionListener(event -> { if (associating) chooseAssociation(); else open(false); });
                actions.add(retry);
            }
            if (failure instanceof IdentityUncertain) {
                JButton fresh = new JButton("作为新项目打开");
                fresh.addActionListener(event -> open(true));
                actions.add(fresh);
                JButton associate = new JButton("关联已有项目会话");
                associate.addActionListener(event -> chooseAssociation());
                actions.add(associate);
            }
            message.add(actions, BorderLayout.SOUTH);
            show(message);
        }

        private void chooseAssociation() {
            associating = true;
            long token = ++request;
            show(new JLabel("正在读取可关联项目…"));
            service.application().controllers().thenCompose(c -> c.sessions().availableBindings())
                    .whenComplete((bindings, failure) -> ApplicationManager.getApplication().invokeLater(() -> {
                        if (closed || project.isDisposed() || request != token) return;
                        if (failure != null) { failed(ReactEngine.unwrap(failure)); return; }
                        JPanel chooser = new JPanel(new BorderLayout(4, 4));
                        chooser.add(new JLabel("仅关联未被打开的旧项目；不会合并已有会话。"), BorderLayout.NORTH);
                        JComboBox<Binding> choices = new JComboBox<>(bindings.toArray(Binding[]::new));
                        choices.setRenderer((list, value, index, selected, focused) -> {
                            JLabel label = (JLabel) new DefaultListCellRenderer().getListCellRendererComponent(list, value, index, selected, focused);
                            label.setText(value == null ? "无可关联项目" : value.location().path());
                            return label;
                        });
                        chooser.add(choices, BorderLayout.CENTER);
                        JPanel actions = new JPanel();
                        JButton confirm = new JButton("确认关联所选项目");
                        confirm.setEnabled(!bindings.isEmpty());
                        confirm.addActionListener(event -> {
                            Binding selected = (Binding) choices.getSelectedItem();
                            if (selected != null) load(service.associate(selected));
                        });
                        JButton back = new JButton("返回");
                        back.addActionListener(event -> open(false));
                        actions.add(confirm); actions.add(back);
                        chooser.add(actions, BorderLayout.SOUTH);
                        show(chooser);
                    }));
        }

        private void show(java.awt.Component component) {
            panel.removeAll(); panel.add(component, BorderLayout.CENTER); panel.revalidate(); panel.repaint();
        }

        @Override public void dispose() { closed = true; ++request; if (view != null) view.close(); }
    }
}
