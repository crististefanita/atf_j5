package ro.rentzlab.app.ui.profile;

import ro.rentzlab.app.ui.copy.RentzUiText.Language;
import java.util.Objects;
import java.time.ZoneId;
import java.util.UUID;
import ro.rentzlab.app.profile.LocalProfile;

/** Injected durable preference port; failed writes never change the effective profile. */
public final class ProfileSettings {
    public interface Store { String get(String key, String fallback); void put(String key, String value); }
    private final Store store;
    public ProfileSettings(Store store) { this.store = Objects.requireNonNull(store); }
    public String preferredName() { return store.get("name", ""); }
    public LocalProfile localProfile() {
        String identity = store.get("local-profile-v1", "");
        if (identity.isEmpty()) {
            var profile = new LocalProfile(UUID.randomUUID(), ZoneId.systemDefault());
            store.put("local-profile-v1", profile.id() + "|" + profile.zone().getId());
            return profile;
        }
        String[] fields = identity.split("\\|", -1);
        if (fields.length != 2) throw new IllegalStateException("Invalid local profile");
        return new LocalProfile(UUID.fromString(fields[0]), ZoneId.of(fields[1]));
    }
    public Language language() {
        try { return Language.valueOf(store.get("language", "EN")); }
        catch (IllegalArgumentException invalid) { return Language.EN; }
    }
    public void name(String value) { store.put("name", value.isEmpty() ? "" : DisplayName.normalize(value)); }
    public void language(Language language) { store.put("language", Objects.requireNonNull(language).name()); }
}
