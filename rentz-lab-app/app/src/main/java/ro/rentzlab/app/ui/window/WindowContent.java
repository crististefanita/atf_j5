package ro.rentzlab.app.ui.window;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/** Consumer-owned window boundary, injected into the shared screen factory. */
public final class WindowContent {
    private WindowContent() { }
    public static void protect(View root) {
        if (Build.VERSION.SDK_INT < 35) return;
        int left = root.getPaddingLeft(), top = root.getPaddingTop(), right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return WindowInsets.CONSUMED;
        });
        root.requestApplyInsets();
    }
    public static int widthDp(Context context) { return dimension(context, true); }
    public static int heightDp(Context context) { return dimension(context, false); }
    private static int dimension(Context context, boolean horizontal) {
        if (Build.VERSION.SDK_INT < 30) {
            var configuration = context.getResources().getConfiguration();
            return horizontal ? configuration.screenWidthDp : configuration.screenHeightDp;
        }
        var metrics = context.getSystemService(WindowManager.class).getCurrentWindowMetrics();
        var bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int pixels = horizontal ? metrics.getBounds().width() - bars.left - bars.right : metrics.getBounds().height() - bars.top - bars.bottom;
        return Math.max(1, Math.round(pixels / context.getResources().getDisplayMetrics().density));
    }
}
