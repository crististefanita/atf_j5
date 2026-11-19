package ro.rentzlab.app.ui.screens;

import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
        var page = views.page(null, null);
        page.setBackgroundColor(ScreenViews.BACKGROUND);
        views.addText(page, copy.handProgress(row.hand(), hands), 13, ScreenViews.ACCENT, false, 12, 0);
        views.addText(page, row.label(), 13, ScreenViews.ACCENT, false, 0, 4);
        views.addText(page, copy.text("Hand score", "Punctajul mâinii"), 24, ScreenViews.PINE, false, 0, 8);
        var progress = new FrameLayout(views.activity());
        page.addView(progress, views.matchWrap());
        var content = new LinearLayout(views.activity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(new RentzSettlementTable(views.activity(), row, names, copy), views.matchWrap());
        views.addText(content, RentzScoreContent.legend(copy), 12, ScreenViews.PINE, false, 6, 8);
        page.addView(content, views.matchWrap());
        var details = views.scoreActionButton(copy.text("Scoring details", "Detalii de punctaj"),
                "hand-score-details", true, () -> new AlertDialog.Builder(views.activity())
                        .setTitle(copy.text("Scoring details", "Detalii de punctaj"))
                        .setView(new RentzHandDetails(views.activity(), row, names, copy, null, standings))
                        .setPositiveButton(copy.text("Close", "Închide"), null).show(), false);
        page.addView(details, views.matchWrap());
        if (next != null) views.addScoreAction(page, copy.text("Next hand", "Mâna următoare"), "rentz-next-hand", canContinue, next, true);
        var actions = new LinearLayout(views.activity());
        actions.setOrientation(LinearLayout.VERTICAL);
        addAction(actions, copy.history(), history);
        addAction(actions, copy.text("View table", "Vezi masa"), table);
        addAction(actions, copy.mainMenu(), menu);
        page.addView(actions, views.matchWrap());
        return progress;
    }

    private void addAction(LinearLayout actions, String label, Runnable action) {
        var button = views.scoreActionButton(label, label, true, action, false);
        actions.addView(button, views.matchWrap());
    }
}
