package edu.courseassist.ui.f2;

import com.intellij.diff.*;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.diff.util.*;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import edu.courseassist.contract.EditContracts.*;
import edu.courseassist.contract.EditPreviewPort;
import java.awt.BorderLayout;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Native two-sided Diff backed exclusively by read-only snapshot documents. */
public final class NativeEditPreviewPort implements EditPreviewPort {
    private final Project project;
    public NativeEditPreviewPort(Project project){this.project=project;}
    @Override public CompletionStage<Display> open(EditProposal proposal) {
        var result=new CompletableFuture<Display>();
        ApplicationManager.getApplication().invokeLater(()->{
            try {
                if(project.isDisposed())throw new IllegalStateException("Project closed");
                SnapshotDiff diff=new SnapshotDiff(project,proposal);
                DialogWrapper dialog=new DialogWrapper(project,false) {
                    {setTitle("审核 Java 修改 · "+proposal.snapshot().target().relativePath());setModal(false);init();}
                    @Override protected JComponent createCenterPanel(){return diff.component();}
                    @Override protected Action[] createActions(){return new Action[0];}
                    @Override protected void dispose(){diff.onClose=()->{};diff.close();super.dispose();}
                };
                diff.onClose=()->dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
                dialog.show();diff.ready().whenComplete((ignored,error)->{if(error!=null){diff.close();result.completeExceptionally(error);}else if(!result.complete(diff))diff.close();});
            }catch(Exception failure){result.completeExceptionally(failure);}
        });return result;
    }
    /** Also used by the actual IDE smoke probe: creates the same native request panel and buttons. */
    public static final class SnapshotDiff implements Display {
        private final EditProposal proposal;
        private final com.intellij.openapi.Disposable lifetime=Disposer.newDisposable("F2 read-only Diff");
        private final DiffRequestProcessor viewer;
        private final CompletableFuture<Void> ready=new CompletableFuture<>();
        private final DocumentContent before,after;
        private final JPanel root=new JPanel(new BorderLayout());
        private final JButton approve=new JButton("同意并应用"),reject=new JButton("拒绝");
        private boolean closed,decided;
        private Runnable onClose=()->{};
        private Runnable dismissed=()->{};
        private Runnable closedCallback=()->{};
        public SnapshotDiff(Project project,EditProposal proposal) {
            this.proposal=proposal;
            before=DiffContentFactory.getInstance().create(project,proposal.snapshot().before(),JavaFileType.INSTANCE);
            after=DiffContentFactory.getInstance().create(project,proposal.after(),JavaFileType.INSTANCE);
            before.getDocument().setReadOnly(true);after.getDocument().setReadOnly(true);
            var request=new SimpleDiffRequest("Java 修改",before,after,"修改前（快照）","候选（只读）");
            request.putUserData(DiffUserDataKeys.FORCE_READ_ONLY,true);
            request.putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS,new boolean[]{true,true});
            viewer=new DiffRequestProcessor(project) {
                {putContextUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL,SimpleDiffTool.INSTANCE);}
                @Override public void updateRequest(boolean force,DiffUserDataKeysEx.ScrollToPolicy policy){applyRequest(request,force,policy);}
            };
            Disposer.register(lifetime,viewer);
            viewer.addListener((com.intellij.diff.impl.DiffRequestProcessorListener)()->confirmDisplay(),lifetime);
            viewer.updateRequest(true);confirmDisplay();
            root.add(viewer.getComponent(),BorderLayout.CENTER);
            JPanel buttons=new JPanel();buttons.add(approve);buttons.add(reject);root.add(buttons,BorderLayout.SOUTH);
            root.setPreferredSize(new java.awt.Dimension(1000,650));
            approve.setName("approveEditProposal");reject.setName("rejectEditProposal");
            approve.setEnabled(false);reject.setEnabled(false);
        }
        public JComponent component(){return root;}
        public CompletionStage<Void> ready(){return ready;}
        public SimpleDiffViewer nativeViewer(){return viewer.getActiveViewer() instanceof SimpleDiffViewer diff?diff:null;}
        private void confirmDisplay() {
            var diff=nativeViewer();
            if(diff!=null&&diff.getEditor1().getDocument()==before.getDocument()&&diff.getEditor2().getDocument()==after.getDocument()
                    &&diff.getEditor1().isViewer()&&diff.getEditor2().isViewer())ready.complete(null);
        }
        public DocumentContent before(){return before;}
        public DocumentContent after(){return after;}
        @Override public void whenClosed(Runnable callback){ApplicationManager.getApplication().invokeLater(()->{if(closed)callback.run();else closedCallback=callback;});}
        @Override public void enable(PreviewToken token,Consumer<ReviewDecision> decisions,Runnable dismissed) {
            ApplicationManager.getApplication().invokeLater(()->{
                if(closed||decided){dismissed.run();return;}
                this.dismissed=dismissed;
                if(!proposal.ref().equals(token.proposal())||!before.getDocument().getText().equals(proposal.snapshot().before())
                        ||!after.getDocument().getText().equals(proposal.after())) {close();return;}
                approve.setEnabled(true);reject.setEnabled(true);
                approve.addActionListener(e->decide(token,true,decisions));reject.addActionListener(e->decide(token,false,decisions));
            });
        }
        private void decide(PreviewToken token,boolean accept,Consumer<ReviewDecision> decisions) {
            if(closed||decided)return;decided=true;approve.setEnabled(false);reject.setEnabled(false);
            decisions.accept(new ReviewDecision(UUID.randomUUID().toString(),token,accept));
            onClose.run();
        }
        @Override public void close() {
            if(!SwingUtilities.isEventDispatchThread()){ApplicationManager.getApplication().invokeLater(this::close);return;}
            if(closed)return;closed=true;approve.setEnabled(false);reject.setEnabled(false);ready.completeExceptionally(new CancellationException("Diff closed"));Disposer.dispose(lifetime);dismissed.run();closedCallback.run();onClose.run();
        }
    }
}
