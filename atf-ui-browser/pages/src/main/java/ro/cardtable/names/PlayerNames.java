package ro.cardtable.names;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * TeaVM-only source replacement for the shared name contract. The Android APK continues to use
 * the published card-table-names library. Browser Unicode normalization delegates to the same
 * Unicode NFC operation exposed by ECMAScript; the JVM fallback keeps this source directly
 * comparable with the library in component tests.
 */
public final class PlayerNames {
    public static final int MAX_LENGTH = 10;
    public static final String DEFAULT_HUMAN = "You";
    private static final List<String> POOL = List.of("Ana", "Mara", "Radu", "Iris", "Luca", "Nora",
            "Teo", "Ion", "Vlad", "Dora", "Emil", "Dinu", "Alex", "Oana", "Lia", "Eva", "Paul", "Toma");

    private PlayerNames() { }

    public static String normalize(String value) {
        return BrowserNameRules.normalize(value, "Name is required");
    }

    public static List<String> forMatch(int seats, String humanName, long seed) {
        if (seats < 3 || seats > 6) throw new IllegalArgumentException("Expected 3–6 players");
        String human = normalize(humanName);
        var candidates = new ArrayList<>(POOL);
        candidates.removeIf(name -> rootLower(name).equals(rootLower(human)));
        Collections.shuffle(candidates, new Random(seed ^ 0x4e414d45535f4149L));
        var result = new ArrayList<String>();
        result.add(human);
        result.addAll(candidates.subList(0, seats - 1));
        return Collections.unmodifiableList(result);
    }

    private static String rootLower(String value) {
        try {
            return browserLower(value);
        } catch (UnsatisfiedLinkError runningOnJvm) {
            return value.toLowerCase(Locale.ROOT);
        }
    }

    @org.teavm.jso.JSBody(params = "value", script = "return value.toLowerCase();")
    private static native String browserLower(String value);
}
