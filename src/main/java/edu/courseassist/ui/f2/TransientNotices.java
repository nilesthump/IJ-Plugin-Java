package edu.courseassist.ui.f2;

import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import edu.courseassist.contract.CommonContracts.Notice;
import java.awt.Point;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;

/** View-lifetime, nonmodal notices. Snapshot refreshes must not replay a persistent fault. */
public final class TransientNotices implements AutoCloseable {
    private final Consumer<List<Notice>> display;
    private Set<Key> current=Set.of();
    private Balloon balloon;
    private JComponent anchor;
    private java.awt.event.HierarchyListener showingListener;
    private List<Notice> pending=List.of();
    private final List<Notice> pendingCommands=new ArrayList<>();
    private boolean closed;
    private record Key(String code,String message,Optional<String> session,Optional<String> operation) {}
    public TransientNotices(JComponent anchor) {
        this.anchor=anchor;display=notices->show(anchor,notices);
        showingListener=event->{if(!closed&&anchor.isShowing()&&(!pending.isEmpty()||!pendingCommands.isEmpty()))show(anchor,pending);};
        anchor.addHierarchyListener(showingListener);
    }
    public TransientNotices(Consumer<List<Notice>> display) {this.display=Objects.requireNonNull(display);}
    public void show(Notice notice) {
        if(closed)return;
        if(anchor!=null&&!anchor.isShowing())pendingCommands.add(notice);
        else display.accept(List.of(notice));
    }
    public void update(List<Notice> notices) {
        if(closed)return;
        Set<Key> next=new HashSet<>();List<Notice> fresh=new ArrayList<>();
        for(var notice:notices) {
            var key=new Key(notice.code(),notice.message(),notice.sessionId(),notice.operationId());
            if(next.add(key)&&!current.contains(key))fresh.add(notice);
        }
        current=Set.copyOf(next);
        pending=pending.stream().filter(n->current.contains(new Key(n.code(),n.message(),n.sessionId(),n.operationId()))).toList();
        if(!fresh.isEmpty())display.accept(List.copyOf(fresh));
    }
    private void show(JComponent anchor,List<Notice> notices) {
        if(!anchor.isShowing()){var waiting=new ArrayList<>(pending);waiting.addAll(notices);pending=List.copyOf(waiting);return;}
        var messages=new ArrayList<>(pendingCommands);messages.addAll(notices);pendingCommands.clear();
        pending=List.of();
        if(balloon!=null)balloon.hide();
        JTextArea text=new JTextArea(String.join("\n",messages.stream().map(Notice::message).toList()));
        text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setColumns(30);
        text.setOpaque(false);text.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
        text.setFont(UIManager.getFont("Label.font"));
        text.getAccessibleContext().setAccessibleName("Course Assistant 提示");
        balloon=JBPopupFactory.getInstance().createBalloonBuilder(text)
                .setFadeoutTime(5000).setHideOnKeyOutside(false).setHideOnClickOutside(true)
                .setCloseButtonEnabled(true).setRequestFocus(false).createBalloon();
        balloon.show(new RelativePoint(anchor,new Point(Math.max(16,anchor.getWidth()-16),
                Math.max(16,anchor.getHeight()-64))),Balloon.Position.atLeft);
    }
    @Override public void close(){closed=true;current=Set.of();pending=List.of();pendingCommands.clear();
        if(anchor!=null)anchor.removeHierarchyListener(showingListener);
        if(balloon!=null){balloon.hide();balloon=null;}}
}
