package atf.testkit.assertions.requirements;

import atf.testkit.assertions.AssertionValueDisplay;
import atf.testkit.assertions.ComponentAssertionAudit;
import atf.testkit.assertions.ComponentAuditAdapter;
import atf.testkit.assertions.ComponentAuditExtension;
import atf.testkit.assertions.RecordedAssertions;

import atf.testkit.assertions.*;

import atf.testkit.AtfComponentTest;
import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import java.util.ArrayList;
import java.util.List;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest @Execution(ExecutionMode.SAME_THREAD)
class TestBodyAssertionRequirementComponentTest {
    private static final List<ComponentAssertionAudit.Result> RESULTS = new ArrayList<>();

    @Test void realJUnitRejectsPlainAndSetupOnlyBodiesButKeepsTheAuditedInvocation() {
        var summary = run(BodyFixture.class);
        assertEquals(1, summary.getTestsSucceededCount());
        assertEquals(2, summary.getTestsFailedCount());
        assertEquals(2, RESULTS.stream().filter(result -> result.failure() != null
                && result.failure().getMessage().contains("TEST BODY passed without")).count());
    }

    @Test void everyTemplateInvocationHasItsOwnCoverageDecision() {
        var summary = run(TemplateFixture.class);
        assertEquals(1, summary.getTestsSucceededCount());
        assertEquals(1, summary.getTestsFailedCount());
        assertEquals(2, RESULTS.size());
    }

    @Test void lifecycleAbortKeepsItsOriginalOutcomeAndNoBodyPhase() {
        var summary = run(AbortFixture.class);
        assertEquals(1, summary.getTestsAbortedCount());
        assertEquals(ComponentAssertionAudit.Outcome.ABORTED, RESULTS.getFirst().outcome());
        assertTrue(RESULTS.getFirst().phases().stream().noneMatch(phase -> phase.phase() == ComponentAssertionAudit.Phase.TEST_BODY));
    }

    private static org.junit.platform.launcher.listeners.TestExecutionSummary run(Class<?> type) {
        RESULTS.clear();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(type)).build(), listener);
        return listener.getSummary();
    }

    public static class StrictExtension implements ComponentAuditAdapter {
        private final ComponentAuditExtension audit = new ComponentAuditExtension(StrictExtension.class, context -> new ComponentAuditExtension.Publication() {
            public AssertionValueDisplay display() { return new AssertionValueDisplay(240, (field, text) -> text); }
            public ComponentAssertionAudit.AssertionRequirement assertionRequirement() { return ComponentAssertionAudit.AssertionRequirement.TEST_BODY; }
            public void publish(ComponentAssertionAudit.Result result) { RESULTS.add(result); }
            public void close() { }
        });
        @Override public ComponentAuditExtension auditExtension() { return audit; }
    }

    @AtfUnitTest @ExtendWith(StrictExtension.class)
    static class BodyFixture {
        @BeforeEach void setup(RecordedAssertions rows) { rows.equals("setup marker", 1, 1); }
        @Test void missing() { }
        @Test void plainJUnit() { org.junit.jupiter.api.Assertions.assertEquals(1, 1); }
        @Test void audited(RecordedAssertions rows) { rows.equals("body comparison", "actual", "actual"); }
    }
    @AtfUnitTest @ExtendWith(StrictExtension.class)
    static class TemplateFixture {
        @BeforeEach void setup(RecordedAssertions rows) { rows.equals("setup marker", 1, 1); }
        @RepeatedTest(2) void repeated(RepetitionInfo repetition, RecordedAssertions rows) {
            if (repetition.getCurrentRepetition() == 1) rows.equals("first invocation", 1, 1);
        }
    }
    @AtfUnitTest @ExtendWith(StrictExtension.class)
    static class AbortFixture {
        @BeforeEach void unavailable() { Assumptions.assumeTrue(false, "controlled prerequisite"); }
        @Test void notExecuted() { throw new AssertionError("must not execute"); }
    }
}
