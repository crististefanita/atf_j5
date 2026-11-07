package ro.rentzlab.app.ui.window;

import android.app.Activity;
import android.os.Build;

/** Activity-owned modern back registration; older devices continue through key-up dispatch. */
public final class BackNavigation {
    private BackNavigation() { }
    public static Runnable register(Activity activity, Runnable action) {
        return Build.VERSION.SDK_INT >= 33 ? Api33.register(activity, action) : () -> { };
    }
    @android.annotation.TargetApi(33)
    private static final class Api33 {
        static Runnable register(Activity activity, Runnable action) {
            android.window.OnBackInvokedCallback callback = action::run;
            var dispatcher = activity.getOnBackInvokedDispatcher();
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }
}
