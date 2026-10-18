package edu.courseassist.bootstrap;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Temporary registration owned by dev-manager; replaced when dev-c's view is integrated. */
public final class BootstrapToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("F0 开发基线：公共契约已提供，聊天与资料功能待各任务实现。"), BorderLayout.NORTH);
        Content content = ContentFactory.getInstance().createContent(panel, "开发入口", false);
        toolWindow.getContentManager().addContent(content);
    }
}
