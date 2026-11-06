package ro.rentzlab.app;

import android.app.Application;
import ro.rentzlab.app.lifecycle.FileSessionStorage;
import ro.rentzlab.app.lifecycle.SessionRepository;

/** One repository per app process; Activities receive distinct owner handles. */
public final class RentzApplication extends Application {
    private SessionRepository sessions;
    @Override public void onCreate(){super.onCreate();sessions=new SessionRepository(new FileSessionStorage(getFilesDir().toPath(),getCacheDir().toPath().resolve("session-writes")));}
    public SessionRepository sessions(){return sessions;}
}
