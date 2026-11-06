package atf.testkit.assertions;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiFunction;

/** Safe projection only: sanitize the complete value before enforcing the display budget. */
public final class AssertionValueDisplay {
    public static final int DEFAULT_LIMIT = 240;
    private final int limit;
    private final BiFunction<String, String, String> sanitizer;

    @FunctionalInterface
    interface Value { String text(); }

    public AssertionValueDisplay(int limit, BiFunction<String, String, String> sanitizer) {
        if (limit <= 0) throw new IllegalArgumentException("display limit must be positive");
        this.limit = limit;
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    public String display(String field, Object value) {
        String safe;
        try {
            String text = value instanceof Value lazy ? lazy.text()
                    : value instanceof Object[] array ? Arrays.deepToString(array) : String.valueOf(value);
            safe = Objects.requireNonNull(sanitizer.apply(field, text), "sanitized value");
        } catch (RuntimeException | AssertionError | LinkageError | StackOverflowError unavailable) {
            // Never retry a broken toString/sanitizer or expose its potentially sensitive message.
            safe = "<display unavailable>";
        }
        return safe.length() <= limit ? safe : safe.substring(0, limit - 1) + "…";
    }

    static Object prefix(String prefix, Object value) {
        return (Value) () -> prefix + (value instanceof Value lazy ? lazy.text() : String.valueOf(value));
    }

    static Object identity(Object value) {
        return (Value) () -> value == null ? "null"
                : value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    static Object type(Class<?> value) { return (Value) () -> value == null ? "null" : value.getName(); }
}
