package edu.courseassist.ui;

import edu.courseassist.contract.ChatContracts.DraftCapture;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.text.*;

/** One editable document. Embedded capture chips are read-only; offsets count only original text. */
public final class MixedDraftEditor extends JTextPane {
    private static final String CAPTURE = "courseassist.capture";
    private List<DraftCapture> captures = List.of();
    private Consumer<String> remove = id -> {};
    private boolean loading;
    private List<String> order = List.of();
    public MixedDraftEditor() {
        setName("draftPreview"); setOpaque(false);
        ((DefaultCaret)getCaret()).setUpdatePolicy(DefaultCaret.UPDATE_WHEN_ON_EDT);
        ((AbstractDocument)getDocument()).setDocumentFilter(new DocumentFilter() {
            private boolean hasCapture(int offset,int length) {
                for(int i=offset;i<offset+length;i++)if(captureAt(i)!=null)return true;
                return false;
            }
            private AttributeSet safe(AttributeSet attrs) { var clean=new SimpleAttributeSet(attrs==null?SimpleAttributeSet.EMPTY:attrs);clean.removeAttribute(CAPTURE);clean.removeAttribute(StyleConstants.ComponentAttribute);clean.removeAttribute("$ename");return clean; }
            public void insertString(FilterBypass fb,int offset,String text,AttributeSet attrs) throws BadLocationException { super.insertString(fb,offset,text,loading?attrs:safe(attrs)); }
            public void remove(FilterBypass fb,int offset,int length) throws BadLocationException { if(loading||!hasCapture(offset,length))super.remove(fb,offset,length); }
            public void replace(FilterBypass fb,int offset,int length,String text,AttributeSet attrs) throws BadLocationException { if(loading||!hasCapture(offset,length))super.replace(fb,offset,length,text,loading?attrs:safe(attrs)); }
        });
    }
    /** Swing merges the previous run's attributes into IME preedit text. A copied capture
     * attribute is not a chip: only the component leaf owns capture identity. */
    private Object captureAt(int position) {
        var element=getStyledDocument().getCharacterElement(position);
        return StyleConstants.ComponentElementName.equals(element.getName())
                ? element.getAttributes().getAttribute(CAPTURE) : null;
    }
    @Override public void setEditable(boolean editable) {
        super.setEditable(editable);
        if(getDocument()==null)return;
        for(int i=0;i<getDocument().getLength();i++) {
            Component component=StyleConstants.getComponent(getStyledDocument().getCharacterElement(i).getAttributes());
            if(component instanceof Container box)for(Component child:box.getComponents())if(child instanceof JButton button)button.setEnabled(editable);
        }
    }
    @Override public String getText() {
        if(getDocument()==null)return "";
        StringBuilder text=new StringBuilder();
        try { for(int i=0;i<getDocument().getLength();i++)if(captureAt(i)==null)text.append(getDocument().getText(i,1)); }
        catch(BadLocationException e){throw new IllegalStateException(e);}return text.toString();
    }
    public Map<String,Integer> captureOffsets() {
        Map<String,Integer> offsets=new LinkedHashMap<>();int position=0;
        for(int i=0;i<getDocument().getLength();i++) {
            Object id=captureAt(i);
            if(id!=null)offsets.put(id.toString(),position);else position++;
        }return Collections.unmodifiableMap(offsets);
    }
    public int textCaret() {
        int count=0;for(int i=0;i<getCaretPosition();i++)if(captureAt(i)==null)count++;return count;
    }
    @Override public void setText(String text) {
        if(captures==null){super.setText(text);return;}
        Map<String,Integer> offsets=new LinkedHashMap<>();captureOffsets().forEach((id,offset)->offsets.put(id,Math.min(offset,text.length())));
        render(text,captures,offsets,order,remove);
    }
    public List<String> captureOrder() { return List.copyOf(captureOffsets().keySet()); }
    public List<String> incomingOrder(List<DraftCapture> next,List<String> saved,boolean atCaret) {
        var result=new ArrayList<>(edu.courseassist.contract.ChatContracts.orderedCaptures(next.stream().map(DraftCapture::captureId).toList(),saved));
        if(!atCaret)return result;
        var known=captures.stream().map(DraftCapture::captureId).toList();
        var added=next.stream().map(DraftCapture::captureId).filter(id->!known.contains(id)).toList();
        result.removeAll(added);String after=null;
        for(int i=getCaretPosition();i<getDocument().getLength();i++) {
            Object id=captureAt(i);
            if(id!=null){after=id.toString();break;}
        }
        int at=after==null?result.size():result.indexOf(after);result.addAll(at<0?result.size():at,added);return List.copyOf(result);
    }
    public void render(String text,List<DraftCapture> next,Map<String,Integer> offsets,Consumer<String> onRemove) { render(text,next,offsets,next.stream().map(DraftCapture::captureId).toList(),onRemove); }
    public void render(String text,List<DraftCapture> next,Map<String,Integer> offsets,List<String> nextOrder,Consumer<String> onRemove) {
        if(text.equals(getText())&&next.equals(captures)&&offsets.equals(captureOffsets())&&nextOrder.equals(order)){remove=onRemove;return;}
        int oldLength=getDocument().getLength();
        boolean atEnd=getCaret().getDot()==oldLength && getCaret().getMark()==oldLength;
        int dot=remapCaret(getCaret().getDot(),text,next,offsets,nextOrder);
        int mark=remapCaret(getCaret().getMark(),text,next,offsets,nextOrder);
        captures=List.copyOf(next);order=List.copyOf(nextOrder);remove=onRemove;loading=true;
        try {
            super.setText("");
            var ordered=next.stream().sorted(Comparator.<DraftCapture>comparingInt(c->offsets.getOrDefault(c.captureId(),text.length())).thenComparingInt(c->nextOrder.indexOf(c.captureId()))).toList();
            int cursor=0;
            for(var capture:ordered) {
                int at=Math.max(cursor,Math.min(text.length(),offsets.getOrDefault(capture.captureId(),text.length())));
                getStyledDocument().insertString(getDocument().getLength(),text.substring(cursor,at),null);cursor=at;
                SimpleAttributeSet attr=new SimpleAttributeSet();attr.addAttribute(CAPTURE,capture.captureId());
                StyleConstants.setComponent(attr,chip(capture));
                getStyledDocument().insertString(getDocument().getLength(),"\uFFFC",attr);
            }
            getStyledDocument().insertString(getDocument().getLength(),text.substring(cursor),null);
            if(atEnd) setCaretPosition(getDocument().getLength());
            else { setCaretPosition(Math.min(mark,getDocument().getLength())); moveCaretPosition(Math.min(dot,getDocument().getLength())); }
        }catch(BadLocationException e){throw new IllegalStateException(e);}finally{loading=false;}
    }
    private int remapCaret(int position,String text,List<DraftCapture> next,Map<String,Integer> offsets,List<String> nextOrder) {
        int textPosition=0;Set<String> before=new HashSet<>();
        for(int i=0;i<position;i++) {
            Object capture=captureAt(i);
            if(capture==null)textPosition++;else before.add(capture.toString());
        }
        int target=Math.min(textPosition,text.length()),result=target;
        Set<String> known=new HashSet<>(captures.stream().map(DraftCapture::captureId).toList());
        for(var capture:next) {
            int at=offsets.getOrDefault(capture.captureId(),text.length());
            if(at<target || at==target && (before.contains(capture.captureId()) || !known.contains(capture.captureId()))) result++;
        }
        return result;
    }
    private JComponent chip(DraftCapture capture) {
        String source=capture.source().fileRef();String shortName=source.substring(Math.max(source.lastIndexOf('/'),source.lastIndexOf('\\'))+1);
        String range=capture.captured().map(g->" · "+g.start().selection().startLine()+"–"+g.start().selection().endLine()).orElse(" · "+capture.state());
        JPanel chip=new JPanel(new FlowLayout(FlowLayout.LEFT,3,0)){ @Override public Dimension getMaximumSize(){return getPreferredSize();} };chip.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JLabel label=new JLabel(shortName+range);label.setToolTipText(source);chip.add(label);
        JButton close=new JButton("×");close.setName("removeCapture-"+capture.captureId());close.setMargin(new Insets(0,3,0,3));
        close.addActionListener(e->remove.accept(capture.captureId()));chip.add(close);
        close.setEnabled(isEditable()); close.setVisible(false);
        var hover=new java.awt.event.MouseAdapter(){
            public void mouseEntered(java.awt.event.MouseEvent e){close.setVisible(true);chip.revalidate();}
            public void mouseExited(java.awt.event.MouseEvent e){if(!chip.contains(SwingUtilities.convertPoint(e.getComponent(),e.getPoint(),chip))){close.setEnabled(isEditable()); close.setVisible(false);chip.revalidate();}}
        };chip.addMouseListener(hover);label.addMouseListener(hover);close.addMouseListener(hover);
        return chip;
    }
}
