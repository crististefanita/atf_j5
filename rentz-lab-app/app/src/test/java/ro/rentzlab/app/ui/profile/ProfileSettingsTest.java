package ro.rentzlab.app.ui.profile;

import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.ui.copy.RentzUiText.Language;
import org.junit.*;
import java.util.HashMap;

@AndroidComponentLayer
public final class ProfileSettingsTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void namesNormalizeWithoutEnteringTheGameJournal() {
        audit.equal("NFC and trimming", "Ștefan", DisplayName.normalize("  S\u0326tefan  "));
        audit.equal("Apostrophe and hyphen", "Ana-M'", DisplayName.normalize("Ana-M'"));
        for (String invalid : new String[]{"", "12345678901", "Ana🙂", "a/b"}) audit.rejects("Invalid display name " + invalid, IllegalArgumentException.class, () -> DisplayName.normalize(invalid));
        audit.rejects("Missing display name", IllegalArgumentException.class, () -> DisplayName.normalize(null));
    }
    @Test public void preferencesPersistLanguageAndResetWithoutChangingSeatIdentity() {
        var data = new HashMap<String, String>(); var profile = new ProfileSettings(store(data, false));
        audit.equal("First launch language", Language.EN, profile.language());
        profile.name(" Ana "); profile.language(Language.RO);
        var restored = new ProfileSettings(store(data, false));
        audit.equal("Restored display name", "Ana", restored.preferredName()); audit.equal("Restored language", Language.RO, restored.language());
        profile.name(""); audit.equal("Reset preference", "", restored.preferredName());
    }
    @Test public void failedWritesDoNotChangeTheEffectivePreferenceOrRetry() {
        var data = new HashMap<String, String>(); data.put("name", "Ana");
        var profile = new ProfileSettings(store(data, true));
        audit.rejects("Storage rejection is visible", IllegalStateException.class, () -> profile.name("Mara"));
        audit.equal("Previous durable name", "Ana", profile.preferredName());
        audit.rejects("Language write rejection", IllegalStateException.class, () -> profile.language(Language.RO));
        audit.equal("Previous effective language", Language.EN, profile.language());
    }
    private ProfileSettings.Store store(HashMap<String, String> data, boolean fail) {
        return new ProfileSettings.Store() {
            public String get(String key, String fallback) { return data.getOrDefault(key, fallback); }
            public void put(String key, String value) { if (fail) throw new IllegalStateException("storage"); data.put(key, value); }
        };
    }
}
