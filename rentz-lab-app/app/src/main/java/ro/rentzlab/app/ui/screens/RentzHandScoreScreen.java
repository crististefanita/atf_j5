package ro.rentzlab.app.ui.screens;

import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.util.List;
import ro.cardtable.ui.screens.ScreenViews;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.app.ui.score.RentzHandDetails;
import ro.rentzlab.app.ui.score.RentzScoreContent;

/** A completed hand gets its own score page, using the same facts as the history dialog. */
public final class RentzHandScoreScreen {
    private final ScreenViews views;

    public RentzHandScoreScreen(ScreenViews views) { this.views = views; }

    public void show(RentzUiText copy, RentzScoreContent.Row row, List<String> names, int hands,
            List<RentzScoreContent.Standing> standings,
            Runnable next, boolean canContinue, Runnable history, Runnable table, Runnable menu) {
        views.applyStandardWindowChrome();
        var page = views.gamePage();
        page.setBackgroundColor(ScreenViews.BACKGROUND);
        views.addText(page, copy.handProgress(row.hand(), hands), 13, ScreenViews.ACCENT, true, 12, 0);
        views.addText(page, row.label(), 13, ScreenViews.ACCENT, false, 0, 4);
        views.addText(page, copy.text("Hand score", "Punctajul mâinii"), 24, ScreenViews.PINE, true, 0, 8);
        page.addView(new RentzHandDetails(views.activity(), row, names, copy, false, standings),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (next != null) views.addButton(page, copy.text("Next hand", "Mâna următoare"), "rentz-next-hand", canContinue, next, true);
        var actions = new LinearLayout(views.activity());
        addAction(actions, copy.history(), history);
        addAction(actions, copy.text("View table", "Vezi masa"), table);
        addAction(actions, copy.mainMenu(), menu);
        page.addView(actions, views.matchWrap());
    }

    private void addAction(LinearLayout actions, String label, Runnable action) {
        var button = views.actionButton(label, label, true, action, false, 14);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setStateListAnimator(null);
        button.setElevation(0);
        actions.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }
}
