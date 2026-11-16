package ro.cardtable.names;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/** Confirms the WEB-only TeaVM shim preserves the name API shipped to the Android APK. */
public final class PlayerNamesParityTest {
    private static int checks;

    private PlayerNamesParityTest() { }

    public static void main(String[] args) throws Exception {
        String library = System.getProperty("rentz.names.nativeJar");
        if (library == null || library.isBlank()) throw new IllegalArgumentException("Missing native names JAR");
        URL jar = Path.of(library).toUri().toURL();
        try (var nativeLoader = new URLClassLoader(new URL[] { jar }, ClassLoader.getPlatformClassLoader())) {
            Class<?> nativeNames = Class.forName("ro.cardtable.names.PlayerNames", true, nativeLoader);
            equal("MAX_LENGTH matches APK contract", nativeNames.getField("MAX_LENGTH").get(null), PlayerNames.MAX_LENGTH);
            equal("DEFAULT_HUMAN matches APK contract", nativeNames.getField("DEFAULT_HUMAN").get(null), PlayerNames.DEFAULT_HUMAN);
            sameNormalize(nativeNames, "  Ana  ");
            sameNormalize(nativeNames, "Ana\tMara");
            sameNormalize(nativeNames, "Jose\u0301");
            sameNormalize(nativeNames, "\uD801\uDC00x"); // supplementary-plane letter, counted as one code point
            sameNormalize(nativeNames, "O'Neil-2");
            sameNormalize(nativeNames, "A".repeat(10));
            sameNormalize(nativeNames, "\uD801\uDC00".repeat(10));
            sameNormalize(nativeNames, "\uD801\uDC00".repeat(11));
            sameNormalize(nativeNames, null);
            sameNormalize(nativeNames, " \t ");
            sameNormalize(nativeNames, "A".repeat(11));
            sameNormalize(nativeNames, "Ana_1");
            sameNormalize(nativeNames, "Ana🙂");
            sameNormalize(nativeNames, "\u00a0Ana\u00a0");
            sameMatch(nativeNames, 3, "Ana", 11L);
            sameMatch(nativeNames, 4, "ana", 20261116L);
            sameMatch(nativeNames, 5, "José", -1L);
            sameMatch(nativeNames, 6, "O'Neil", Long.MIN_VALUE);
            sameMatch(nativeNames, 2, "Ana", 1L);
            sameMatch(nativeNames, 7, "Ana", 1L);
            compareDisplayName(nativeLoader, "  Ana  ");
            compareDisplayName(nativeLoader, "Ana\tMara");
            compareDisplayName(nativeLoader, "Jose\u0301");
            compareDisplayName(nativeLoader, "\uD801\uDC00".repeat(10));
            compareDisplayName(nativeLoader, null);
            compareDisplayName(nativeLoader, "Ana🙂");
        }
        System.out.println("PlayerNames APK parity checks passed: " + checks);
    }

    private static void sameNormalize(Class<?> nativeNames, String value) throws Exception {
        Object expected = invoke(nativeNames, "normalize", new Class<?>[] { String.class }, value);
        Object actual = invoke(PlayerNames.class, "normalize", new Class<?>[] { String.class }, value);
        equal("normalize(" + value + ")", expected, actual);
    }

    private static void sameMatch(Class<?> nativeNames, int seats, String name, long seed) throws Exception {
        Object expected = invoke(nativeNames, "forMatch", new Class<?>[] { int.class, String.class, long.class }, seats, name, seed);
        Object actual = invoke(PlayerNames.class, "forMatch", new Class<?>[] { int.class, String.class, long.class }, seats, name, seed);
        equal("forMatch(" + seats + ", " + name + ", " + seed + ")", expected, actual);
        if (actual instanceof List<?> names && seats >= 3 && seats <= 6) {
            equal("seat count", seats, names.size());
            equal("human name remains first", PlayerNames.normalize(name), names.get(0));
        }
    }

    private static void compareDisplayName(ClassLoader nativeLoader, String value) throws Exception {
        String classes = System.getProperty("rentz.native.appClasses");
        if (classes == null || classes.isBlank()) throw new IllegalArgumentException("Missing native app classes");
        try (var appLoader = new URLClassLoader(new URL[] { Path.of(classes).toUri().toURL() }, nativeLoader)) {
            Class<?> nativeDisplayName = Class.forName("ro.rentzlab.app.ui.profile.DisplayName", true, appLoader);
            Object expected = invoke(nativeDisplayName, "normalize", new Class<?>[] { String.class }, value);
            Object actual = invoke(ro.rentzlab.app.ui.profile.DisplayName.class, "normalize",
                    new Class<?>[] { String.class }, value);
            equal("DisplayName.normalize(" + value + ")", expected, actual);
        }
    }

    private static Object invoke(Class<?> type, String method, Class<?>[] parameters, Object... values) throws Exception {
        try {
            return type.getMethod(method, parameters).invoke(null, values);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) return runtime.getClass().getName() + ":" + runtime.getMessage();
            throw failure;
        }
    }

    private static void equal(String description, Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(description + " expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
