package edu.courseassist.ui;

import edu.courseassist.fixtures.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackPreviewTest {
    @Test void paintsUnifiedTranscriptAtSidebarWidth() throws Exception {
        AtomicReference<CourseAssistantView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            var state = SyntheticStates.project(SyntheticStates.Scenario.COMPLETED);
            view.set(new CourseAssistantView(state.projectInstanceId(), SyntheticControllers.session(state), SyntheticControllers.application(SyntheticStates.application())));
        });
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                AtomicReference<Boolean> ready = new AtomicReference<>(false);
                SwingUtilities.invokeAndWait(() -> ready.set(findAgent(view.get().component())));
                if (ready.get()) break;
                Thread.sleep(20);
            }
            SwingUtilities.invokeAndWait(() -> {
                JPanel root = view.get().component(); assertTrue(findAgent(root)); root.setSize(620, 850); root.addNotify(); layout(root);
                BufferedImage image = new BufferedImage(620, 850, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics(); root.printAll(graphics); graphics.dispose();
                try { Files.createDirectories(Path.of("build/reports")); ImageIO.write(image, "png", Path.of("build/reports/feedback-ui-preview.png").toFile()); }
                catch (java.io.IOException error) { throw new RuntimeException(error); }
                assertEquals(4, countTopButtons(((JPanel)((BorderLayout)root.getLayout()).getLayoutComponent(BorderLayout.NORTH))));
            });
        } finally { SwingUtilities.invokeAndWait(() -> view.get().close()); }
    }
    static int countTopButtons(Container c) { int n=0; for(Component child:c.getComponents()) { if(child instanceof JButton)n++; else if(child instanceof Container nested)n+=countTopButtons(nested); } return n; }
    static boolean findAgent(Component c) { if(c instanceof JLabel label && label.getText().contains("Agent")) return true; if(c instanceof Container box) for(Component child:box.getComponents()) if(findAgent(child))return true; return false; }
    static void layout(Container c) { c.doLayout(); for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested); }
}
