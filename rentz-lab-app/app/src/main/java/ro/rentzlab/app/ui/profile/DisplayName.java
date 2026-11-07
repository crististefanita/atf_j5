package ro.rentzlab.app.ui.profile;

import java.text.Normalizer;

/** Rentz display preference; seat identity and the saved game journal remain independent. */
public final class DisplayName {
    public static final int MAX_LENGTH = 10;
    private DisplayName() { }
    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Name required");
        String name = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_LENGTH
                || name.codePoints().anyMatch(c -> !Character.isLetterOrDigit(c) && c != ' ' && c != '-' && c != '\''))
            throw new IllegalArgumentException("Use 1–10 letters, digits, spaces, apostrophes or hyphens");
        return name;
    }
}
