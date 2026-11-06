package atf.testkit.assertions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import java.util.function.Supplier;

/** Audits the JUnit overloads used by consumers while delegating their original verdict and exception. */
public final class AuditedAssertions {
    private AuditedAssertions() { }

    public static void assertEquals(Object expected, Object actual) {
        AuditedAssertionScope.check("assertEquals", expected, actual, null, () -> Assertions.assertEquals(expected, actual));
    }
    public static void assertEquals(Object expected, Object actual, String message) {
        AuditedAssertionScope.check("assertEquals", expected, actual, message, () -> Assertions.assertEquals(expected, actual, message));
    }
    public static void assertEquals(Object expected, Object actual, Supplier<String> message) {
        AuditedAssertionScope.check("assertEquals", expected, actual, null, () -> Assertions.assertEquals(expected, actual, message));
    }
    public static void assertEquals(int expected, int actual) {
        AuditedAssertionScope.check("assertEquals", expected, actual, null, () -> Assertions.assertEquals(expected, actual));
    }
    public static void assertEquals(int expected, int actual, String message) {
        AuditedAssertionScope.check("assertEquals", expected, actual, message, () -> Assertions.assertEquals(expected, actual, message));
    }
    public static void assertEquals(int expected, Integer actual) {
        AuditedAssertionScope.check("assertEquals", expected, actual, null, () -> Assertions.assertEquals(expected, actual));
    }
    public static void assertEquals(Integer expected, int actual, String message) {
        AuditedAssertionScope.check("assertEquals", expected, actual, message, () -> Assertions.assertEquals(expected, actual, message));
    }
    public static void assertEquals(long expected, long actual) {
        AuditedAssertionScope.check("assertEquals", expected, actual, null, () -> Assertions.assertEquals(expected, actual));
    }
    public static void assertEquals(long expected, long actual, String message) {
        AuditedAssertionScope.check("assertEquals", expected, actual, message, () -> Assertions.assertEquals(expected, actual, message));
    }
    public static void assertEquals(double expected, double actual, double delta) {
        AuditedAssertionScope.check("assertEquals", expected + " ± " + delta, actual, null,
                () -> Assertions.assertEquals(expected, actual, delta));
    }
    public static void assertEquals(double expected, double actual, double delta, String message) {
        AuditedAssertionScope.check("assertEquals", expected + " ± " + delta, actual, message,
                () -> Assertions.assertEquals(expected, actual, delta, message));
    }
    public static void assertNotEquals(Object expected, Object actual) {
        AuditedAssertionScope.check("assertNotEquals", AssertionValueDisplay.prefix("not ", expected), actual, null,
                () -> Assertions.assertNotEquals(expected, actual));
    }
    public static void assertTrue(boolean actual) {
        AuditedAssertionScope.check("assertTrue", true, actual, null, () -> Assertions.assertTrue(actual));
    }
    public static void assertTrue(boolean actual, String message) {
        AuditedAssertionScope.check("assertTrue", true, actual, message, () -> Assertions.assertTrue(actual, message));
    }
    public static void assertTrue(boolean actual, Supplier<String> message) {
        AuditedAssertionScope.check("assertTrue", true, actual, null, () -> Assertions.assertTrue(actual, message));
    }
    public static void assertFalse(boolean actual) {
        AuditedAssertionScope.check("assertFalse", false, actual, null, () -> Assertions.assertFalse(actual));
    }
    public static void assertFalse(boolean actual, String message) {
        AuditedAssertionScope.check("assertFalse", false, actual, message, () -> Assertions.assertFalse(actual, message));
    }
    public static void assertNull(Object actual) {
        AuditedAssertionScope.check("assertNull", "null", actual, null, () -> Assertions.assertNull(actual));
    }
    public static void assertNotNull(Object actual) {
        AuditedAssertionScope.check("assertNotNull", "not null", actual, null, () -> Assertions.assertNotNull(actual));
    }
    public static void assertNotNull(Object actual, String message) {
        AuditedAssertionScope.check("assertNotNull", "not null", actual, message, () -> Assertions.assertNotNull(actual, message));
    }
    public static void assertSame(Object expected, Object actual) {
        AuditedAssertionScope.check("assertSame", AssertionValueDisplay.identity(expected), AssertionValueDisplay.identity(actual),
                null, () -> Assertions.assertSame(expected, actual));
    }
    public static void assertNotSame(Object expected, Object actual) {
        AuditedAssertionScope.check("assertNotSame", AssertionValueDisplay.prefix("not ", AssertionValueDisplay.identity(expected)),
                AssertionValueDisplay.identity(actual), null,
                () -> Assertions.assertNotSame(expected, actual));
    }
    public static void assertArrayEquals(Object[] expected, Object[] actual) {
        AuditedAssertionScope.check("assertArrayEquals", expected, actual, null, () -> Assertions.assertArrayEquals(expected, actual));
    }
    public static <T> T assertInstanceOf(Class<T> expected, Object actual) {
        return AuditedAssertionScope.value("assertInstanceOf", AssertionValueDisplay.type(expected),
                actual == null ? "null" : actual.getClass().getName(), null,
                () -> Assertions.assertInstanceOf(expected, actual), null, false);
    }
    public static <T extends Throwable> T assertThrows(Class<T> expected, Executable executable) {
        return AuditedAssertionScope.value("assertThrows", AssertionValueDisplay.type(expected), "no matching exception", null,
                () -> Assertions.assertThrows(expected, executable), error -> error.getClass().getName(), true);
    }
    public static <T extends Throwable> T assertThrows(Class<T> expected, Executable executable, String message) {
        return AuditedAssertionScope.value("assertThrows", AssertionValueDisplay.type(expected), "no matching exception", message,
                () -> Assertions.assertThrows(expected, executable, message), error -> error.getClass().getName(), true);
    }
    public static void assertDoesNotThrow(Executable executable) {
        AuditedAssertionScope.value("assertDoesNotThrow", "no exception", "no exception", null,
                () -> { Assertions.assertDoesNotThrow(executable); return "no exception"; }, value -> value, true);
    }
    public static void assertDoesNotThrow(Executable executable, String message) {
        AuditedAssertionScope.value("assertDoesNotThrow", "no exception", "no exception", message,
                () -> { Assertions.assertDoesNotThrow(executable, message); return "no exception"; }, value -> value, true);
    }
    public static void assertAll(Executable... checks) {
        AuditedAssertionScope.group(null, () -> Assertions.assertAll(checks));
    }
    public static void assertAll(String message, Executable... checks) {
        AuditedAssertionScope.group(message, () -> Assertions.assertAll(message, checks));
    }
}
