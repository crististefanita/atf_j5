package atf.testkit.assertions;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class AuditedAssertionsTest {
    @Test
    void recordsExpectedExceptionsAsPassingChecksWithoutTheirInnerFailures() {
        var rows = new RecordedAssertions();
        try (var scope = AuditedAssertionScope.open(getClass().getName(),
                "recordsExpectedExceptionsAsPassingChecksWithoutTheirInnerFailures", rows)) {
            var failure = AuditedAssertions.assertThrows(AssertionError.class,
                    () -> AuditedAssertions.assertTrue(false));
            assertNotNull(failure);
        }
        assertEquals(1, rows.checks().size());
        assertTrue(rows.checks().getFirst().passed());
        assertEquals("org.opentest4j.AssertionFailedError", rows.checks().getFirst().actual());
    }

    @Test
    void delegatesNumericOverloadsAndKeepsMessageSuppliersLazy() {
        var rows = new RecordedAssertions();
        var evaluations = new AtomicInteger();
        try (var scope = AuditedAssertionScope.open(getClass().getName(),
                "delegatesNumericOverloadsAndKeepsMessageSuppliersLazy", rows)) {
            AuditedAssertions.assertEquals(16, 16L);
            AuditedAssertions.assertEquals(2, Integer.valueOf(2));
            AuditedAssertions.assertEquals(1.0, 1.01, 0.1);
            AuditedAssertions.assertTrue(true, () -> "unused " + evaluations.incrementAndGet());
            assertEquals(0, evaluations.get());
            assertThrows(AssertionError.class, () -> AuditedAssertions.assertTrue(false,
                    () -> "evaluated " + evaluations.incrementAndGet()));
        }
        assertEquals(1, evaluations.get());
        assertEquals(5, rows.checks().size());
        assertFalse(rows.checks().getLast().passed());
    }

    @Test
    void keepsAllEvaluatedRowsAndTheOriginalJUnitAggregateFailure() {
        var rows = new RecordedAssertions();
        try (var scope = AuditedAssertionScope.open(getClass().getName(),
                "keepsAllEvaluatedRowsAndTheOriginalJUnitAggregateFailure", rows)) {
            var failure = assertThrows(MultipleFailuresError.class, () -> AuditedAssertions.assertAll(
                    () -> AuditedAssertions.assertEquals(1, 2),
                    () -> AuditedAssertions.assertFalse(true)));
            assertEquals(2, failure.getFailures().size());
        }
        assertEquals(2, rows.checks().size());
        assertTrue(rows.checks().stream().noneMatch(RecordedAssertions.Check::passed));
    }

    @Test
    void comparesTheFullValueAndKeepsTheConfiguredCharacterLimitIncludingEllipsis() {
        var rows = new RecordedAssertions();
        String prefix = "x".repeat(240);
        assertThrows(AssertionError.class, () -> rows.equals("different suffix", prefix + "a", prefix + "b"));
        assertFalse(rows.checks().getFirst().passed());
        assertEquals(240, rows.checks().getFirst().actual().length());
        assertTrue(rows.checks().getFirst().actual().endsWith("…"));
        var custom = new RecordedAssertions(10);
        custom.equals("custom", "x".repeat(11), "x".repeat(11));
        assertEquals(10, custom.checks().getFirst().actual().length());
        assertTrue(custom.checks().getFirst().actual().endsWith("…"));
        rows.equals("exact boundary", prefix, prefix);
        assertEquals(prefix, rows.checks().getLast().actual());
    }
    @Test
    void namedToleranceKeepsNumericFactsAndTheOriginalJUnitVerdict() {
        var rows = new RecordedAssertions();
        try (var scope = AuditedAssertionScope.open(getClass().getName(), "namedToleranceKeepsNumericFactsAndTheOriginalJUnitVerdict", rows)) {
            AuditedAssertions.assertEquals(100.0, 101.5, 2.0, "PCM frames");
            assertThrows(AssertionError.class, () ->
                    AuditedAssertions.assertEquals(100.0, 105.0, 2.0, "outside frame tolerance"));
        }
        assertEquals(2, rows.checks().size());
        var accepted = rows.checks().getFirst();
        assertTrue(accepted.name().contains("PCM frames"));
        assertEquals("100.0 ± 2.0", accepted.expected());
        assertEquals("101.5", accepted.actual());
        assertTrue(accepted.passed());
        assertFalse(rows.checks().getLast().passed());
        assertEquals("105.0", rows.checks().getLast().actual());
    }
}
