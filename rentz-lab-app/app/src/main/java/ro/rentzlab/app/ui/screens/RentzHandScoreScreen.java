package ro.rentzlab.app.ui.screens;

import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.app.AlertDialog;
import java.util.List;
import ro.cardtable.ui.screens.ScreenViews;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.app.ui.score.RentzHandDetails;
import ro.rentzlab.app.ui.score.RentzScoreContent;
import ro.rentzlab.app.ui.score.RentzSettlementTable;

/** A completed hand gets its own score page, using the same facts as the history dialog. */
public final class RentzHandScoreScreen {
    private final ScreenViews views;

    public RentzHandScoreScreen(ScreenViews views) { this.views = views; }

    public FrameLayout show(RentzUiText copy, RentzScoreContent.Row row, List<String> names, int hands,
            List<RentzScoreContent.Standing> standings,
            Runnable next, boolean canContinue, Runnable history, Runnable table, Runnable menu) {
        views.applyStandardWindowChrome();
        var page = views.gamePage();
        page.setBackgroundColor(ScreenViews.BACKGROUND);
        views.addText(page, copy.handProgress(row.hand(), hands), 13, ScreenViews.ACCENT, false, 12, 0);
        views.addText(page, row.label(), 13, ScreenViews.ACCENT, false, 0, 4);
        views.addText(page, copy.text("Hand score", "Punctajul mâinii"), 24, ScreenViews.PINE, false, 0, 8);
        var progress = new FrameLayout(views.activity());
        page.addView(progress, views.matchWrap());
        var scroll = new ScrollView(views.activity());
        var content = new LinearLayout(views.activity());
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        content.addView(new RentzSettlementTable(views.activity(), row, names, copy), views.matchWrap());
        views.addText(content, RentzScoreContent.legend(copy), 12, ScreenViews.PINE, false, 6, 8);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        var details = views.actionButton(copy.text("Scoring details", "Calculul punctajului"),
                "hand-score-details", true, () -> new AlertDialog.Builder(views.activity())
                        .setTitle(copy.text("Scoring details", "Calculul punctajului"))
                        .setView(new RentzHandDetails(views.activity(), row, names, copy, null, standings))
                        .setPositiveButton(copy.text("Close", "Închide"), null).show(), false, 14);
        details.setBackgroundColor(Color.TRANSPARENT);
        details.setStateListAnimator(null);
        details.setElevation(0);
        page.addView(details, views.matchWrap());
        if (next != null) views.addButton(page, copy.text("Next hand", "Mâna următoare"), "rentz-next-hand", canContinue, next, true);
        var actions = new LinearLayout(views.activity());
        addAction(actions, copy.history(), history);
        addAction(actions, copy.text("View table", "Vezi masa"), table);
        addAction(actions, copy.mainMenu(), menu);
        page.addView(actions, views.matchWrap());
        return progress;
    }

    private void addAction(LinearLayout actions, String label, Runnable action) {
        var button = views.actionButton(label, label, true, action, false, 14);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setStateListAnimator(null);
        button.setElevation(0);
        actions.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }
}
