package ro.rentzlab.app.ui.profile;

import ro.cardtable.names.BrowserNameRules;

/** TeaVM-only source replacement; native Android continues to compile the app's original class. */
public final class DisplayName {
    public static final int MAX_LENGTH = 10;

    private DisplayName() { }

    public static String normalize(String value) {
        return BrowserNameRules.normalize(value, "Name required");
    }
}
