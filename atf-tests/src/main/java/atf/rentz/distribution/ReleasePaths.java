package atf.rentz.distribution;

import java.io.IOException;
import java.util.Set;

/** Canonical relative release paths; no platform-dependent escaping or traversal. */
public final class ReleasePaths {
    public static String relative(String value) throws IOException {
        require(value != null && !value.isBlank() && !value.contains("\\") && !value.matches(".*[\\p{Cntrl}:].*"), value);
        require(!value.startsWith("/") && !value.endsWith("/"), value);
        for (String part : value.split("/", -1)) require(!Set.of("", ".", "..").contains(part), value);
        return value;
    }
    private static void require(boolean valid, String value) throws IOException {
        if (!valid) throw new IOException("PATH: " + value);
    }
    private ReleasePaths() { }
}
