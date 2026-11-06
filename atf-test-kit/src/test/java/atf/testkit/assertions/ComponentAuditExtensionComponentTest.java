package atf.testkit.assertions;

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

@AtfComponentTest
@Execution(ExecutionMode.SAME_THREAD)
class ComponentAuditExtensionComponentTest {
    private static final List<ComponentAssertionAudit.Result> RESULTS = new ArrayList<>();
    private static int closed;
    private static boolean publicationFails;
    private static boolean closeFails;
    private static final IllegalStateException PRIMARY = new IllegalStateException("controlled primary failure");
    private static final IllegalStateException PUBLICATION = new IllegalStateException("controlled publication failure");
    private static final IllegalStateException CLOSE = new IllegalStateException("controlled close failure");

    @Test void reconcilesExpectedCaughtEmptyAndAbortedInvocations() {
        var expected = run(ExpectedFailureFixture.class);
        assertEquals(1, expected.getTestsSucceededCount());
        assertEquals(1, RESULTS.getFirst().phases().getFirst().checks().size());
        assertEquals(ComponentAssertionAudit.Outcome.PASS, RESULTS.getFirst().outcome());
        assertEquals(1, run(CaughtFailureFixture.class).getTestsFailedCount());
        assertEquals(ComponentAssertionAudit.Outcome.FAIL, RESULTS.getFirst().outcome());
        assertEquals(1, run(EmptyFixture.class).getTestsFailedCount());
        assertTrue(RESULTS.getFirst().phases().getFirst().checks().isEmpty());
        assertEquals(1, run(AbortedFixture.class).getTestsAbortedCount());
        assertEquals(ComponentAssertionAudit.Outcome.ABORTED, RESULTS.getFirst().outcome());
        assertEquals(1, closed);
    }

    @Test void recordsInheritedLifecycleMethodsInTheirOwnPhases() {
        assertEquals(1, run(InheritedFixture.class).getTestsSucceededCount());
        var phases = RESULTS.getFirst().phases();
        assertEquals(List.of(ComponentAssertionAudit.Phase.SETUP, ComponentAssertionAudit.Phase.TEST_BODY,
                ComponentAssertionAudit.Phase.TEARDOWN), phases.stream().map(ComponentAssertionAudit.PhaseResult::phase).toList());
        assertTrue(phases.stream().allMatch(phase -> phase.checks().size() == 1));
        assertEquals(1, closed);
    }

    @Test void isolatesTemplateInvocationsAndKeepsLifecycleFailuresInTheirPhase() {
        assertEquals(2, run(TemplateFixture.class).getTestsSucceededCount());
        assertEquals(2, RESULTS.size());
        assertEquals(2, closed);
        assertEquals(1, run(SetupFailureFixture.class).getTestsFailedCount());
        assertEquals(ComponentAssertionAudit.Phase.SETUP, RESULTS.getFirst().phases().getFirst().phase());
        assertEquals(1, run(TeardownFailureFixture.class).getTestsFailedCount());
        assertEquals(ComponentAssertionAudit.Phase.TEARDOWN, RESULTS.getFirst().phases().getLast().phase());
        assertEquals(ComponentAssertionAudit.Outcome.FAIL, RESULTS.getFirst().phases().getLast().outcome());
    }

    @Test void closesAfterPublicationFailureWithoutReplacingTheOriginalCause() {
        publicationFails = true; closeFails = true;
        try {
            var result = run(PrimaryFailureFixture.class);
            assertEquals(1, result.getTestsFailedCount());
            assertSame(PRIMARY, result.getFailures().getFirst().getException());
            assertEquals(List.of(PUBLICATION), List.of(PRIMARY.getSuppressed()));
            assertEquals(List.of(CLOSE), List.of(PUBLICATION.getSuppressed()));
            assertEquals(1, closed);
        } finally { publicationFails = false; closeFails = false; }
    }

    private static org.junit.platform.launcher.listeners.TestExecutionSummary run(Class<?> fixture) {
        RESULTS.clear(); closed = 0;
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(fixture)).build(), listener);
        return listener.getSummary();
    }

    public static class ProbeExtension implements ComponentAuditAdapter {
        private final ComponentAuditExtension audit = new ComponentAuditExtension(ProbeExtension.class,
                context -> new ComponentAuditExtension.Publication() {
                    public AssertionValueDisplay display() { return new AssertionValueDisplay(240, (field, text) -> text); }
                    public void publish(ComponentAssertionAudit.Result result) {
                        RESULTS.add(result);
                        if (publicationFails) throw PUBLICATION;
                    }
                    public void close() { closed++; if (closeFails) throw CLOSE; }
                });
        @Override public ComponentAuditExtension auditExtension() { return audit; }
    }
    @AtfUnitTest @ExtendWith(ProbeExtension.class)
    static class ExpectedFailureFixture {
        @Test void expected(RecordedAssertions rows) {
            AuditedAssertions.assertThrows(AssertionError.class, () -> rows.equals("expected negative", 1, 2));
        }
    }
    @AtfUnitTest @ExtendWith(ProbeExtension.class)
    static class CaughtFailureFixture {
        @Test void caught() { try { AuditedAssertions.assertTrue(false); } catch (AssertionError expected) { } }
    }
    @AtfUnitTest @ExtendWith(ProbeExtension.class)
    static class EmptyFixture { @Test void empty() { } }
    @AtfUnitTest @ExtendWith(ProbeExtension.class)
    static class AbortedFixture { @Test void aborted() { Assumptions.assumeTrue(false, "controlled abort"); } }
    @AtfUnitTest @ExtendWith(ProbeExtension.class)
    static class LifecycleFixture {
        @BeforeEach void setup(RecordedAssertions rows) { rows.equals("setup", 1, 1); }
        @Test void body() { AuditedAssertions.assertTrue(true); }
        @AfterEach void teardown() { AuditedAssertions.assertFalse(false); }
    }
    @AtfUnitTest static class InheritedFixture extends LifecycleFixture { }
    @AtfUnitTest @ExtendWith(ProbeExtension.class)
    static class TemplateFixture {
        @RepeatedTest(2)
        void repeated(RepetitionInfo repetition) { AuditedAssertions.assertTrue(repetition.getCurrentRepetition() >= 1); }
    }
    @AtfUnitTest @ExtendWith(ProbeExtension.class)
    static class SetupFailureFixture {
        @BeforeEach void setup() { AuditedAssertions.assertTrue(false); }
        @Test void body() { throw new AssertionError("must not run"); }
    }
    @AtfUnitTest @ExtendWith(ProbeExtension.class)
    static class TeardownFailureFixture {
        @Test void body() { AuditedAssertions.assertTrue(true); }
        @AfterEach void teardown() { AuditedAssertions.assertFalse(true); }
    }
    @AtfUnitTest @ExtendWith(ProbeExtension.class)
    static class PrimaryFailureFixture { @Test void body() { throw PRIMARY; } }
}
