package ro.rentzlab.app.ui.screens;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import ro.cardtable.ui.screens.ScreenViews;
import ro.rentzlab.app.lifecycle.SessionController.AchievementNotice;
import ro.rentzlab.app.ui.copy.RentzUiText;

/** Transient score-page feedback for a single saved hand. */
public final class AchievementPopup {
    private AchievementPopup() { }

    public static View show(ScreenViews views, FrameLayout slot, RentzUiText copy, AchievementNotice notice,
            Runnable dismiss) {
        var card = new LinearLayout(views.activity());
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = views.dp(16);
        card.setPadding(padding, views.dp(10), padding, views.dp(8));
        card.setBackground(views.roundedBackground(ScreenViews.PINE, ScreenViews.ACCENT, 16));
        String title = notice.matchEnd() ? copy.text("Match achievement progress", "Progres după partidă")
                : copy.text("Achievement progress", "Progres la realizări");
        views.addText(card, title, 15, Color.WHITE, true, 0, 3);
        int visible = Math.min(2, notice.changes().size());
        for (var change : notice.changes().subList(0, visible)) {
            String name = copy.text(change.badge().english(), change.badge().romanian());
            String status = change.unlocked() ? copy.text("Unlocked", "Deblocată")
                    : change.count() + "/" + change.badge().goal();
            views.addText(card, name + " · " + status, 13, Color.WHITE, false, 2, 0);
        }
        int more = notice.changes().size() - visible;
        if (more > 0) views.addText(card, "+" + more + copy.text(" more updated", " în plus"), 12, Color.WHITE, false, 3, 0);
        String closeProgress = copy.text("Close achievement progress", "Închide progresul la realizări");
        var close = views.actionButton(copy.close(), closeProgress, true, dismiss, false, 12);
        card.addView(close, views.matchWrap());
        card.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        var layout = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.setMargins(views.dp(12), 0, views.dp(12), views.dp(8));
        slot.addView(card, layout);
        card.post(() -> { if (card.isAttachedToWindow()) card.announceForAccessibility(title); });
        return card;
    }
}
