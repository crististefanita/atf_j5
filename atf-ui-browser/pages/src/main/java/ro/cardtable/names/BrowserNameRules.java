package ro.cardtable.names;

import java.text.Normalizer;
import org.teavm.jso.JSBody;

/** Unicode operations implemented by the browser runtime but parity-tested on the JVM. */
public final class BrowserNameRules {
    private BrowserNameRules() { }

    public static String normalize(String value, String requiredMessage) {
        if (value == null) throw new IllegalArgumentException(requiredMessage);
        String name = canonicalForm(value);
        if (name.isEmpty() || name.codePointCount(0, name.length()) > PlayerNames.MAX_LENGTH
                || name.codePoints().anyMatch(codePoint -> !allowed(codePoint))) {
            throw new IllegalArgumentException("Use 1–10 letters, digits, spaces, apostrophes or hyphens");
        }
        return name;
    }

    private static String canonicalForm(String value) {
        try {
            return browserCanonicalForm(value);
        } catch (UnsatisfiedLinkError runningOnJvm) {
            return Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        }
    }

    private static boolean allowed(int codePoint) {
        try {
            return browserAllowed(codePoint);
        } catch (UnsatisfiedLinkError runningOnJvm) {
            return Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '-' || codePoint == '\'';
        }
    }

    @JSBody(params = "value", script = """
            let start = 0;
            let end = value.length;
            while (start < end && value.charCodeAt(start) <= 0x20) start++;
            while (end > start && value.charCodeAt(end - 1) <= 0x20) end--;
            return value.slice(start, end).normalize('NFC');
            """)
    private static native String browserCanonicalForm(String value);

    @JSBody(params = "codePoint", script = """
            if (codePoint === 0x20 || codePoint === 0x2d || codePoint === 0x27) return true;
            return /^[\\p{L}\\p{N}]$/u.test(String.fromCodePoint(codePoint));
            """)
    private static native boolean browserAllowed(int codePoint);
}
