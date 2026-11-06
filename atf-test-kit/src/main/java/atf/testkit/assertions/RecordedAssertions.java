package atf.testkit.assertions;

import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

/** JUnit comparisons with immutable, renderer-independent evidence of evaluated checks. */
public final class RecordedAssertions {
    public static final int DEFAULT_MAX_DISPLAYED_CHARACTERS = AssertionValueDisplay.DEFAULT_LIMIT;
    public record Check(String name, String expected, String actual, boolean passed) { }

    private final List<Check> checks = new ArrayList<>();
    private final AssertionValueDisplay display;
    private Throwable failure;

    public RecordedAssertions() { this(DEFAULT_MAX_DISPLAYED_CHARACTERS); }

    public RecordedAssertions(int maxDisplayedCharacters) {
        this(new AssertionValueDisplay(maxDisplayedCharacters, (field, value) -> value));
    }

    public RecordedAssertions(AssertionValueDisplay display) {
        this.display = java.util.Objects.requireNonNull(display, "display");
    }

    void record(String name, Object expected, Object actual, boolean passed) {
        record(name, expected, actual, passed, null);
    }

    void record(String name, Object expected, Object actual, boolean passed, Throwable error) {
        if (AuditedAssertionScope.isSuppressed()) return;
        checks.add(new Check(display.display(null, name), display.display(name, expected),
                display.display(name, actual), passed));
        if (!passed && failure == null) failure = error;
    }

    public void equals(String name, Object expected, Object actual) {
        AuditedAssertionScope.explicit(this, name, expected, actual,
                () -> Assertions.assertEquals(expected, actual, name));
    }

    public void isTrue(String name, boolean actual) {
        AuditedAssertionScope.explicit(this, name, true, actual, () -> Assertions.assertTrue(actual, name));
    }

    public void isFalse(String name, boolean actual) {
        AuditedAssertionScope.explicit(this, name, false, actual, () -> Assertions.assertFalse(actual, name));
    }

    public List<Check> checks() { return List.copyOf(checks); }
    Throwable failure() { return failure; }
}
