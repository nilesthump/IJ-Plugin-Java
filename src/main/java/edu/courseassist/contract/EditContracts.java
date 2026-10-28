package edu.courseassist.contract;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static edu.courseassist.contract.CommonContracts.*;

/** F2 v1 values. Text remains exact UTF-16; paths are clues, never write authorization. */
public final class EditContracts {
    private EditContracts() {}
    public static final int FORMAT = 1;
    public static final int MAX_DOCUMENT_CHARS = 1_000_000;
    public enum ResolutionStatus { UNIQUE, AMBIGUOUS, NOT_FOUND, NOT_READY, INCOMPLETE, STALE, OUTSIDE_SOURCE_ROOT }
    public enum ReviewState { STAGED, REVIEWABLE, APPROVED, REJECTED, INVALIDATED }
    public enum ApplyState { INTENT, APPLYING, APPLIED_TO_DOCUMENT, FAILED_NOT_APPLIED, OUTCOME_UNKNOWN, RECEIPT_PENDING }
    public enum DiskState { UNKNOWN, UNSAVED, SAVED }
    public enum DocumentState { UNKNOWN, APPLIED, UNDONE, CHANGED }
    public record Origin(String projectId, String instanceId, String sessionId, String turnId,
                         String attemptId, String operationId, long epoch) {
        public Origin {
            for (String id : List.of(projectId, instanceId, sessionId, turnId, attemptId, operationId)) requireId(id,"origin");
            requireRevision(epoch);
        }
    }
    public record EditRequest(String requestId, Origin origin, String instructionRef, long instructionRevision,
                              String instruction, Optional<String> captureId) {
        public EditRequest {
            requireId(requestId,"request"); Objects.requireNonNull(origin); requireId(instructionRef,"instruction");
            requireRevision(instructionRevision); requireId(instruction,"instruction text");
            captureId=Objects.requireNonNull(captureId); captureId.ifPresent(x->requireId(x,"capture"));
        }
    }
    public record TargetQuery(String pathHint, String classHint, String methodHint, List<String> parameterHints) {
        public TargetQuery { Objects.requireNonNull(pathHint); Objects.requireNonNull(classHint); Objects.requireNonNull(methodHint); parameterHints=List.copyOf(parameterHints); }
    }
    public record Target(String fileRef, String relativePath, String module, String signature, String fileIdentity) {
        public Target(String fileRef,String relativePath,String module,String signature){this(fileRef,relativePath,module,signature,"");}
        public Target { requireId(fileRef,"file"); requireId(relativePath,"path"); Objects.requireNonNull(module); Objects.requireNonNull(signature); fileIdentity=fileIdentity==null?"":fileIdentity; }
    }
    /** Zero-based UTF-16 offsets, start inclusive/end exclusive. */
    public record Span(int start, int end) {
        public Span { if(start<0||end<start)throw new IllegalArgumentException("Invalid span"); }
    }
    public record EditSnapshot(int format, String snapshotId, EditRequest request, Target target,
                               long documentStamp, String before, List<Span> allowedRanges) {
        public EditSnapshot {
            version(format); requireId(snapshotId,"snapshot"); Objects.requireNonNull(request); Objects.requireNonNull(target);
            requireRevision(documentStamp); text(before); allowedRanges=List.copyOf(allowedRanges);
            if(allowedRanges.isEmpty())throw new IllegalArgumentException("Missing edit scope");
            int end=-1;
            for(Span span:allowedRanges) { if(span.start()<end||span.end()>before.length())throw new IllegalArgumentException("Overlapping/outside scope"); end=span.end(); }
        }
        public String beforeHash() { return hash(before); }
    }
    public record TargetResolution(ResolutionStatus status, List<Target> candidates, Optional<EditSnapshot> snapshot) {
        public TargetResolution {
            Objects.requireNonNull(status); candidates=List.copyOf(candidates); snapshot=Objects.requireNonNull(snapshot);
            if(status==ResolutionStatus.UNIQUE) {
                if(candidates.size()!=1||snapshot.isEmpty()||!snapshot.get().target().equals(candidates.getFirst()))throw new IllegalArgumentException("Unique requires complete capture");
            } else if(snapshot.isPresent())throw new IllegalArgumentException("Incomplete resolution has no capture");
        }
    }
    public record EditProposal(int format, String proposalId, long revision, EditSnapshot snapshot,
                               String branchId, String toolCallId, String after) {
        public EditProposal {
            version(format); requireId(proposalId,"proposal"); requireRevision(revision); Objects.requireNonNull(snapshot);
            requireId(branchId,"branch"); requireId(toolCallId,"tool call"); text(after);
            if(after.equals(snapshot.before()))throw new IllegalArgumentException("Unchanged candidate");
        }
        public ProposalRef ref() { return new ProposalRef(proposalId,revision,snapshot.beforeHash(),hash(after)); }
    }
    public record ProposalRef(String proposalId, long revision, String beforeHash, String afterHash) {
        public ProposalRef { requireId(proposalId,"proposal"); requireRevision(revision); requireId(beforeHash,"before hash"); requireId(afterHash,"after hash"); }
    }
    /** Issued by the host only after a real viewer displays this exact immutable pair. */
    public record PreviewToken(String tokenId, ProposalRef proposal, String instanceId,long documentStamp) {
        public PreviewToken(String tokenId,ProposalRef proposal,String instanceId){this(tokenId,proposal,instanceId,0);}
        public PreviewToken { requireId(tokenId,"preview"); Objects.requireNonNull(proposal); requireId(instanceId,"instance");requireRevision(documentStamp); }
    }
    public record ProposalView(ProposalRef proposal, ReviewState review, Optional<ApplyReceipt> receipt,String targetPath,String turnId,String attemptId,String branchId) {
        public ProposalView(ProposalRef proposal,ReviewState review,Optional<ApplyReceipt> receipt){this(proposal,review,receipt,"","","","");}
        public ProposalView(ProposalRef proposal, ReviewState review, Optional<ApplyReceipt> receipt,String targetPath){this(proposal,review,receipt,targetPath,"","","");}
        public ProposalView { turnId=turnId==null?"":turnId;attemptId=attemptId==null?"":attemptId;branchId=branchId==null?"":branchId; Objects.requireNonNull(proposal); Objects.requireNonNull(review); receipt=Objects.requireNonNull(receipt);Objects.requireNonNull(targetPath); }
    }
    public record ReviewDecision(String commandId, PreviewToken preview, boolean approve) {
        public ReviewDecision { requireId(commandId,"command"); Objects.requireNonNull(preview); }
    }
    /** Host-created after durable approval/intent. Never exposed as a model tool. */
    public record ApplyRequest(String applyId, Origin operation, EditProposal proposal,long documentStamp) {
        public ApplyRequest(String applyId,Origin operation,EditProposal proposal){this(applyId,operation,proposal,proposal.snapshot().documentStamp());}
        public ApplyRequest { requireId(applyId,"apply"); Objects.requireNonNull(operation); Objects.requireNonNull(proposal);requireRevision(documentStamp); }
    }
    public record ApplyReceipt(String applyId, ProposalRef proposal, ApplyState state, DiskState disk,
                               Optional<Long> documentStamp, DocumentState documentState) {
        public ApplyReceipt(String applyId, ProposalRef proposal, ApplyState state, DiskState disk, Optional<Long> stamp) {
            this(applyId,proposal,state,disk,stamp,state==ApplyState.APPLIED_TO_DOCUMENT?DocumentState.APPLIED:DocumentState.UNKNOWN);
        }
        public ApplyReceipt {
            requireId(applyId,"apply"); Objects.requireNonNull(proposal); Objects.requireNonNull(state); Objects.requireNonNull(disk);
            documentStamp=Objects.requireNonNull(documentStamp); documentStamp.ifPresent(CommonContracts::requireRevision);
            documentState=documentState==null?DocumentState.UNKNOWN:documentState;
            if(state==ApplyState.APPLIED_TO_DOCUMENT&&documentStamp.isEmpty())throw new IllegalArgumentException("Applied result needs observed stamp");
            if(state!=ApplyState.APPLIED_TO_DOCUMENT&&disk!=DiskState.UNKNOWN)throw new IllegalArgumentException("No observed disk result");
        }
    }
    public static void version(int format) { if(format!=FORMAT)throw new IllegalArgumentException("Unsupported F2 format: "+format); }
    private static void text(String text) { Objects.requireNonNull(text); if(text.length()>MAX_DOCUMENT_CHARS)throw new IllegalArgumentException("Document exceeds F2 limit"); }
    /** Hash exact Java chars, including unpaired surrogates; independent of disk encoding/newlines. */
    public static String hash(String text) {
        ByteBuffer bytes=ByteBuffer.allocate(Math.multiplyExact(text.length(),2));
        for(int i=0;i<text.length();i++)bytes.putChar(text.charAt(i));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array())); }
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
