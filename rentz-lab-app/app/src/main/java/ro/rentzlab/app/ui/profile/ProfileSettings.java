package ro.rentzlab.app.ui.profile;

import ro.rentzlab.app.ui.copy.RentzUiText.Language;
import java.util.Objects;

/** Injected durable preference port; failed writes never change the effective profile. */
public final class ProfileSettings {
    public interface Store { String get(String key, String fallback); void put(String key, String value); }
    private final Store store;
    public ProfileSettings(Store store) { this.store = Objects.requireNonNull(store); }
    public String preferredName() { return store.get("name", ""); }
    public Language language() {
        try { return Language.valueOf(store.get("language", "EN")); }
        catch (IllegalArgumentException invalid) { return Language.EN; }
    }
    public void name(String value) { store.put("name", value.isEmpty() ? "" : DisplayName.normalize(value)); }
    public void language(Language language) { store.put("language", Objects.requireNonNull(language).name()); }
}
