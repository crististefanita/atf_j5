package ro.rentzlab.app.ui.profile;

import android.content.Context;
import android.content.SharedPreferences;

/** Only UI preferences; game checkpoints continue through SessionRepository. */
public final class AndroidPreferences implements ProfileSettings.Store {
    private final SharedPreferences preferences;
    public AndroidPreferences(Context context) { preferences = context.getSharedPreferences("rentz-ui", Context.MODE_PRIVATE); }
    public String get(String key, String fallback) { return preferences.getString(key, fallback); }
    public void put(String key, String value) {
        if (!preferences.edit().putString(key, value).commit()) throw new IllegalStateException("UI preference was not saved");
    }
}
