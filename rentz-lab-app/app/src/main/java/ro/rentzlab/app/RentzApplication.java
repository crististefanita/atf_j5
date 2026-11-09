package ro.rentzlab.app;

import android.app.Application;
import ro.rentzlab.app.lifecycle.FileSessionStorage;
import ro.rentzlab.app.lifecycle.SessionRepository;
import ro.rentzlab.app.ui.profile.AndroidPreferences;
import ro.rentzlab.app.ui.profile.ProfileSettings;
import java.time.Clock;

/** One repository per app process; Activities receive distinct owner handles. */
public final class RentzApplication extends Application {
    private SessionRepository sessions;
    private boolean initialActivityCreated;
    @Override public void onCreate(){
        super.onCreate();
        var profile = new ProfileSettings(new AndroidPreferences(this)).localProfile();
        sessions = new SessionRepository(new FileSessionStorage(getFilesDir().toPath(),
                getCacheDir().toPath().resolve("session-writes")), profile, Clock.systemUTC());
    }
    public SessionRepository sessions(){return sessions;}
    boolean claimColdPresentation() {
        if (initialActivityCreated) return false;
        initialActivityCreated = true;
        return true;
    }
}
