package ro.rentzlab.app.ui.profile;

import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.ui.copy.RentzUiText.Language;
import org.junit.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;

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
    @Test public void opponentNamesKeepTheirSeatsAndDoNotSaveTheHumanName() throws Exception {
        for (int seats = 3; seats <= 6; seats++) {
            var baseline = new GameSession(seats, 31, RentzPlayer.Difficulty.NORMAL);
            String reserved = baseline.opponentNames().get(0);
            var session = new GameSession(seats, 31, RentzPlayer.Difficulty.NORMAL, reserved);
            audit.equal("One unique opponent per seat " + seats, seats - 1, new HashSet<>(session.opponentNames()).size());
            audit.truth("Reserve the chosen human name " + seats, session.opponentNames().stream().noneMatch(reserved::equalsIgnoreCase));
            var legacy = SessionJson.saved(baseline);
            audit.truth("Original journal schema remains readable " + seats, !legacy.has("opponents"));
            audit.equal("Legacy session reconstructs the seed roster " + seats, baseline.opponentNames(),
                    SessionJson.restore(legacy.toString()).opponentNames());
            var saved = SessionJson.saved(session);
            var restored = SessionJson.restore(saved.toString());
            audit.equal("Persist the opponents by seat " + seats, session.opponentNames(), restored.opponentNames());
            audit.truth("Reservation persists only opponent labels " + seats, !saved.toString().contains("\"" + reserved + "\""));
            audit.equal("Changing the displayed human name keeps opponents " + seats, session.opponentNames(),
                    restored.displayNames("Alt nume").subList(1, seats));
            audit.equal("Roster leaves the deal unchanged " + seats, baseline.view(), restored.view());
        }
        var invalid = SessionJson.saved(new GameSession(3, 31, RentzPlayer.Difficulty.NORMAL));
        invalid.put("opponents", new JSONArray(List.of("Ana", "ANA")));
        audit.rejects("Reject duplicate saved names", IllegalArgumentException.class, () -> SessionJson.restore(invalid.toString()));
        invalid.put("opponents", new JSONArray(List.of("Ana", 42)));
        audit.rejects("Reject nontext saved names", IllegalArgumentException.class, () -> SessionJson.restore(invalid.toString()));
    }
    private ProfileSettings.Store store(HashMap<String, String> data, boolean fail) {
        return new ProfileSettings.Store() {
            public String get(String key, String fallback) { return data.getOrDefault(key, fallback); }
            public void put(String key, String value) { if (fail) throw new IllegalStateException("storage"); data.put(key, value); }
        };
    }
}
