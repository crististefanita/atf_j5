package ro.rentzlab.app.ui.score;

import android.content.Context;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import ro.cardtable.ui.theme.TablePalette;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.Contract;

/** Renders public settlement facts. A single scroll contains the profile and every player. */
public final class RentzHandDetails extends ScrollView {
    public RentzHandDetails(Context context, RentzScoreContent.Row hand, List<String> players, RentzUiText copy,
            String scoreProfileId, List<RentzScoreContent.Standing> standings) {
        super(context);
        setId(ro.rentzlab.app.R.id.history_details);
        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        content.setPadding(padding, dp(8), padding, dp(8));
        addView(content);
        if (!standings.isEmpty()) {
            line(content, copy.text("Final standings", "Clasament final"), true);
            for (var standing : standings) {
                line(content, standing.place() + ". " + players.get(standing.seat()) + "   ·   Σ " + standing.total(), false);
            }
            line(content, copy.text("Last hand details", "Detaliile ultimei mâini"), true);
        }
        String chooser = copy.text("Chosen by ", "Ales de ") + players.get(hand.chooser());
        if (scoreProfileId != null) {
            chooser += "\n" + copy.text("Scoring profile · ", "Profil de punctaj · ") + scoreProfileId;
        }
        line(content, chooser, false);
        var details = RentzScoreContent.details(hand);
        if (details.isEmpty()) line(content, copy.text("Scoring details unavailable", "Detaliul punctajului este indisponibil"), false);
        for (var player : details) {
            String place = player.place() == 0 ? "" : player.place() + ". ";
            line(content, place + players.get(player.seat()), true);
            line(content, "Δ " + player.score().delta() + "   ·   Σ " + player.score().total(), false);
            for (var contribution : player.contributions()) {
                String label = contribution.category() == Contract.RENTZ
                        ? copy.text("Finishing award", "Premiu de sosire")
                        : copy.contract(contribution.category()) + " × " + contribution.count();
                line(content, label + "   ·   " + contribution.points(), false);
            }
        }
        if (!details.isEmpty() && details.get(0).place() > 0) {
            line(content, copy.text("Last place is assigned to the player still holding cards.",
                    "Ultimul loc revine jucătorului care mai are cărți."), false);
        }
    }

    private void line(LinearLayout content, String value, boolean heading) {
        var text = new TextView(getContext());
        text.setText(value);
        text.setTextSize(heading ? 16 : 14);
        text.setTextColor(TablePalette.ACTIVE_SEAT_TEXT);
        text.setPadding(0, dp(heading ? 12 : 2), 0, dp(2));
        if (heading) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            if (android.os.Build.VERSION.SDK_INT >= 28) text.setAccessibilityHeading(true);
        }
        content.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
