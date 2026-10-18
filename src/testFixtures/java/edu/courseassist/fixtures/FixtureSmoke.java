package edu.courseassist.fixtures;

import edu.courseassist.contract.ApplicationContracts.DisablePlugin;
import edu.courseassist.contract.ApplicationContracts.ApplicationEventMeta;
import edu.courseassist.contract.ChatContracts.CloseSessionTab;
import edu.courseassist.contract.ChatContracts.ContextEligibility;
import edu.courseassist.contract.ChatContracts.EventMeta;
import edu.courseassist.contract.CommonContracts.Rejection;
import edu.courseassist.contract.CredentialUpdate;
import edu.courseassist.contract.PdfContracts.PdfReadRequest;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone no-dependency fixture smoke; run with assertions enabled. */
public final class FixtureSmoke {
    private FixtureSmoke() {}
    public static void main(String[] args) {
        var initial = SyntheticStates.project();
        var fixture = SyntheticControllers.session(initial);
        var observations = new AtomicInteger();
        var subscription = fixture.observe(SyntheticStates.PROJECT_INSTANCE_ID, ignored -> observations.incrementAndGet());
        var event = new CloseSessionTab(new EventMeta("synthetic-command-1", SyntheticStates.PROJECT_INSTANCE_ID,
                Optional.of(SyntheticStates.SESSION_ID)));
        var result = fixture.dispatch(event).toCompletableFuture().join();
        assert result.rejection().orElseThrow() == Rejection.NOT_SUPPORTED;
        assert fixture.recordedEvents().equals(List.of(event));
        fixture.publish(SyntheticStates.project(SyntheticStates.Scenario.INTERRUPTED));
        assert observations.get() == 2;
        subscription.close();
        subscription.close();
        fixture.publish(initial);
        assert observations.get() == 2 : "Closed view must not receive more state";
        for (var scenario : SyntheticStates.Scenario.values()) {
            var state = SyntheticStates.project(scenario);
            var attempt = state.sessions().get(0).turns().get(0).attempts().get(0);
            assert !attempt.branches().get(0).steps().get(1).markdown().isEmpty();
            assert (attempt.contextEligibility() == ContextEligibility.ELIGIBLE_DURABLE_SUCCESS)
                    == (scenario == SyntheticStates.Scenario.COMPLETED);
        }
        var source = new PdfReadRequest("synthetic-document-1", "synthetic-source-v1",
                Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("synthetic.pdf"), "0123456789abcdef0123456789abcdef");
        var pages = SyntheticStates.threePagePdf(source);
        assert pages.pages().get(1).physicalPage() == 2 && pages.pages().get(1).text().isEmpty();
        assert pages.pages().get(2).physicalPage() == 3;
        var app = SyntheticControllers.application(SyntheticStates.application());
        var appResult = app.dispatch(new DisablePlugin(new ApplicationEventMeta("synthetic-disable", 4))).toCompletableFuture().join();
        assert appResult.rejection().orElseThrow() == Rejection.NOT_SUPPORTED;
        char[] input = {'s', 'e', 'c', 'r', 'e', 't'};
        try (var update = new CredentialUpdate(List.of(new CredentialUpdate.Change(CredentialUpdate.Slot.GENERATION,
                CredentialUpdate.Action.REPLACE, input)))) {
            Arrays.fill(input, '\0');
            assert !update.toString().contains("secret");
            assert !update.changes().get(0).toString().contains("secret");
            char[] received = update.changes().get(0).copyValue();
            assert received[0] == 's';
            Arrays.fill(received, '\0');
        }
        System.out.println("PASS: synthetic scenarios, typed capture, blank physical page, observer lifecycle, event recording, credential redaction");
    }
}
