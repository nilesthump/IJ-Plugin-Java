package edu.courseassist.ui.f2;

import edu.courseassist.contract.ChatContracts.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FailurePresentationTest {
    @Test void everyCodedTerminalFailureHasSpecificSafeText() {
        for (FailureCode code : FailureCode.values()) {
            FailureView failure = new FailureView(code, FailurePhase.MODEL_GENERATION);
            for (AttemptStatus status : new AttemptStatus[]{AttemptStatus.LIMITED, AttemptStatus.FAILED}) {
                String state = FailurePresentation.state(status, Optional.of(failure));
                assertNotEquals("达到限制", state);
                assertNotEquals("生成失败，请检查服务配置", state);
                assertTrue(state.contains(code.name()), state);
                assertTrue(state.contains(FailurePresentation.message(failure)), state);
            }
        }
    }
    @Test void legacyAttemptsWithoutCodeSayTheDiagnosticIsMissing() {
        assertTrue(FailurePresentation.state(AttemptStatus.LIMITED, Optional.empty()).contains("历史尝试无诊断码"));
        assertTrue(FailurePresentation.state(AttemptStatus.FAILED, Optional.empty()).contains("历史尝试无诊断码"));
    }
    @Test void cancellationAndCandidateRangeTextsNameTheActualCondition() {
        String cancelled = FailurePresentation.state(AttemptStatus.STOPPED,
                Optional.of(new FailureView(FailureCode.CANCELLED, FailurePhase.LIFECYCLE)));
        assertTrue(cancelled.contains("用户取消")); assertTrue(cancelled.contains("CANCELLED"));
        String outside = FailurePresentation.state(AttemptStatus.FAILED,
                Optional.of(new FailureView(FailureCode.CANDIDATE_RANGE_OUTSIDE_ALLOWED, FailurePhase.PATCH_VALIDATION)));
        assertTrue(outside.contains("允许范围")); assertTrue(outside.contains("未修改任何文件"));
        String before = FailurePresentation.state(AttemptStatus.FAILED,
                Optional.of(new FailureView(FailureCode.CANDIDATE_BEFORE_MISMATCH, FailurePhase.PATCH_VALIDATION)));
        assertTrue(before.contains("before")); assertTrue(before.contains("CANDIDATE_BEFORE_MISMATCH"));
    }
    @Test void outputPhaseTextDistinguishesParentGenerationFromCandidateGeneration() {
        String parent = FailurePresentation.message(new FailureView(FailureCode.MODEL_OUTPUT_LIMIT, FailurePhase.MODEL_GENERATION));
        String child = FailurePresentation.message(new FailureView(FailureCode.MODEL_OUTPUT_LIMIT, FailurePhase.CANDIDATE_GENERATION));
        assertTrue(parent.contains("模型输出达到上限"));
        assertTrue(child.contains("候选生成输出达到上限"));
        assertNotEquals(parent, child);
    }
}
