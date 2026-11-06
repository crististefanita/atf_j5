package atf.testkit.assertions.requirements;

import atf.testkit.assertions.AssertionValueDisplay;
import atf.testkit.assertions.ComponentAssertionAudit;

import atf.testkit.assertions.*;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import static atf.testkit.assertions.ComponentAssertionAudit.*;
import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class TestBodyAssertionRequirementTest {
    private static ComponentAssertionAudit strict() {
        return new ComponentAssertionAudit(new AssertionValueDisplay(240, (field, text) -> text), System::nanoTime, AssertionRequirement.TEST_BODY);
    }

    @Test void setupAndTeardownCannotStandInForBodyAssertions() {
        var audit = strict();
        audit.recorder(Phase.SETUP).equals("setup prerequisite", 1, 1);
        audit.start(Phase.TEST_BODY);
        audit.recorder(Phase.TEARDOWN).equals("cleanup prerequisite", 1, 1);
        var result = audit.finish(null);
        assertEquals(Outcome.FAIL, result.outcome());
        assertTrue(result.propagate());
        var body = result.phases().stream().filter(phase -> phase.phase() == Phase.TEST_BODY).findFirst().orElseThrow();
        assertEquals(Outcome.FAIL, body.outcome());
        assertEquals(1, body.checks().size());
        var row = body.checks().getFirst();
        assertEquals("Audited assertion coverage / TEST BODY", row.name());
        assertEquals("at least 1 evaluated assertion", row.expected());
        assertEquals("0", row.actual());
        assertFalse(row.passed());
    }

    @Test void aRealBodyComparisonSatisfiesTheRequirement() {
        var audit = strict();
        audit.start(Phase.TEST_BODY);
        audit.recorder(Phase.TEST_BODY).equals("independent reference", "expected", "expected");
        assertEquals(Outcome.PASS, audit.finish(null).outcome());
    }

    @Test void anOriginalBodyFailureIsNeverReplacedByCoverage() {
        var audit = strict();
        var primary = new IllegalStateException("original decoder failure");
        audit.start(Phase.TEST_BODY); audit.failed(Phase.TEST_BODY, primary);
        var result = audit.finish(primary);
        assertSame(primary, result.failure());
        assertEquals(Outcome.FAIL, result.outcome());
        assertTrue(result.phases().getFirst().checks().isEmpty());
    }

    @Test void setupAbortDoesNotInventBodyExecutionOrAnAssertion() {
        var audit = strict();
        var abort = new TestAbortedException("prerequisite unavailable");
        audit.start(Phase.SETUP); audit.failed(Phase.SETUP, abort);
        var result = audit.finish(abort);
        assertSame(abort, result.failure());
        assertEquals(Outcome.ABORTED, result.outcome());
        assertEquals(1, result.phases().size());
        assertEquals(Phase.SETUP, result.phases().getFirst().phase());
        assertTrue(result.phases().getFirst().checks().isEmpty());
    }

    @Test void bodyAbortRemainsSkipAndCaughtAuditFailureRemainsFail() {
        var audit = strict(); audit.start(Phase.TEST_BODY);
        var abort = new TestAbortedException("controlled skip"); audit.failed(Phase.TEST_BODY, abort);
        assertEquals(Outcome.ABORTED, audit.finish(abort).outcome());
        var failed = strict(); failed.start(Phase.TEST_BODY);
        AssertionError primary = assertThrows(AssertionError.class, () -> failed.recorder(Phase.TEST_BODY).equals("actual mismatch", 1, 2));
        var result = failed.finish(abort);
        assertEquals(Outcome.FAIL, result.outcome()); assertSame(primary, result.failure());
    }

    @Test void legacyPolicyAndExplicitNullRejectionRemainStable() {
        var legacy = new ComponentAssertionAudit(new AssertionValueDisplay(240, (field, text) -> text));
        legacy.recorder(Phase.SETUP).equals("legacy lifecycle proof", 1, 1);
        assertEquals(Outcome.PASS, legacy.finish(null).outcome());
        assertThrows(NullPointerException.class, () -> new ComponentAssertionAudit(new AssertionValueDisplay(240, (f, t) -> t), System::nanoTime, null));
    }

    @Test void repeatedFinishKeepsTheOriginalCoverageFailureAndOneRow() {
        var audit = strict(); audit.start(Phase.TEST_BODY);
        var first = audit.finish(null); var second = audit.finish(null);
        assertSame(first.failure(), second.failure());
        assertEquals(1, second.phases().getFirst().checks().size());
    }
}
