package atf.testkit.assertions;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class AssertionProjectionTest {
    @Test void sanitizesTheCompleteValueBeforeApplyingTheCharacterLimit() {
        String secret = "{\"password\":\"synthetic-canary-" + "q".repeat(280) + "\"}";
        var seen = new java.util.ArrayList<String>();
        var display = new AssertionValueDisplay(240, (field, text) -> {
            seen.add(text);
            return text.equals(secret) ? "{\"password\":\"REDACTED\"}" : text;
        });
        var rows = new RecordedAssertions(display);
        rows.equals("json", secret, secret);
        assertTrue(seen.contains(secret));
        assertEquals("{\"password\":\"REDACTED\"}", rows.checks().getFirst().actual());
        assertEquals(240, display.display(null, "x".repeat(241)).length());
        assertEquals(1, new AssertionValueDisplay(1, (field, text) -> text).display(null, "long").length());
    }

    @Test void recordsTheSingleJUnitEvaluationAndKeepsBrokenDisplayOutOfItsVerdict() {
        var comparisons = new AtomicInteger();
        Object expected = new Object() {
            @Override public boolean equals(Object actual) { return comparisons.incrementAndGet() == 1; }
            @Override public int hashCode() { return 1; }
            @Override public String toString() { throw new IllegalStateException("synthetic display failure"); }
        };
        var rows = new RecordedAssertions();
        rows.equals("single evaluation", expected, new Object());
        assertEquals(1, comparisons.get());
        assertTrue(rows.checks().getFirst().passed());
        assertEquals("<display unavailable>", rows.checks().getFirst().expected());
        Object broken = new Object() { @Override public String toString() { throw new AssertionError("display"); } };
        try (var scope = AuditedAssertionScope.open(getClass().getName(),
                "recordsTheSingleJUnitEvaluationAndKeepsBrokenDisplayOutOfItsVerdict", rows)) {
            AuditedAssertions.assertEquals(broken, broken);
            AuditedAssertions.assertNotEquals(broken, new Object());
        }
        assertTrue(rows.checks().stream().allMatch(RecordedAssertions.Check::passed));
        assertDoesNotThrow(() -> AuditedAssertions.assertNotEquals(broken, new Object()));
    }

    @Test void explicitRecorderAndFacadeShareExpectedFailureSuppression() {
        var rows = new RecordedAssertions();
        try (var scope = AuditedAssertionScope.open(getClass().getName(),
                "explicitRecorderAndFacadeShareExpectedFailureSuppression", rows)) {
            AuditedAssertions.assertThrows(AssertionError.class, () -> rows.equals("negative", 1, 2));
            AuditedAssertions.assertDoesNotThrow(() -> rows.equals("positive", 1, 1));
            AuditedAssertions.assertThrows(AssertionError.class, () -> AuditedAssertions.assertAll(
                    () -> rows.isTrue("nested negative", false), () -> AuditedAssertions.assertFalse(true)));
        }
        assertEquals(3, rows.checks().size());
        assertTrue(rows.checks().stream().allMatch(RecordedAssertions.Check::passed));
    }

    @Test void aDetachedRecorderAlsoFeedsTheBoundAuditThroughItsSafeDisplayPolicy() {
        var bound = new RecordedAssertions(new AssertionValueDisplay(240,
                (field, text) -> text.equals("synthetic secret") ? "REDACTED" : text));
        var detached = new RecordedAssertions();
        try (var scope = AuditedAssertionScope.open(getClass().getName(),
                "aDetachedRecorderAlsoFeedsTheBoundAuditThroughItsSafeDisplayPolicy", bound)) {
            detached.equals("value", "synthetic secret", "synthetic secret");
        }
        assertEquals(1, detached.checks().size());
        assertEquals(1, bound.checks().size());
        assertEquals("REDACTED", bound.checks().getFirst().actual());
    }
}
