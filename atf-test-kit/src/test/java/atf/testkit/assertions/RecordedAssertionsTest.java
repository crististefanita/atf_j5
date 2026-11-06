package atf.testkit.assertions;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfUnitTest
class RecordedAssertionsTest {
    @Test
    void retainsTheFailingComparisonBeforeJUnitStopsExecution() {
        var assertions = new RecordedAssertions();
        assertions.equals("product", "hammer", "hammer");
        var beforeFailure = assertions.checks();

        assertThrows(AssertionError.class, () -> assertions.equals("quantity", 2, 3));

        assertEquals(1, beforeFailure.size());
        assertEquals(2, assertions.checks().size());
        assertEquals(new RecordedAssertions.Check("quantity", "2", "3", false),
                assertions.checks().getLast());
        assertThrows(UnsupportedOperationException.class, () -> assertions.checks().clear());
    }

    @Test
    void preservesJUnitEqualityAndBooleanVerdictsIncludingFailures() {
        var assertions = new RecordedAssertions();
        assertions.equals("nullable", null, null);
        assertions.isTrue("enabled", true);
        assertions.isFalse("disabled", false);
        assertThrows(AssertionError.class, () -> assertions.isTrue("missing", false));
        assertThrows(AssertionError.class, () -> assertions.isFalse("unexpected", true));

        assertTrue(assertions.checks().subList(0, 3).stream().allMatch(RecordedAssertions.Check::passed));
        assertFalse(assertions.checks().get(3).passed());
        assertFalse(assertions.checks().get(4).passed());
    }
}
