package ro.rentzlab.app.ui.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import ro.cardtable.ui.theme.TablePalette;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.app.ui.score.RentzHandDetails;
import ro.rentzlab.app.ui.score.RentzHistoryGrid;
import ro.rentzlab.app.ui.score.RentzScoreContent;
import ro.rentzlab.app.ui.window.WindowContent;

/** Dialog composition and player naming remain at the Rentz screen boundary. */
public final class RentzHistoryView {
    private RentzHistoryView() { }

    public static AlertDialog show(Context context, GameSession session, RentzUiText copy, String name, Runnable dismissed) {
        var container = new FrameLayout(context);
        var dialog = new AlertDialog.Builder(context).setTitle(copy.history()).setView(container)
                .setPositiveButton(copy.close(), null).create();
        var history = session == null ? java.util.Collections.<RentzScoreContent.Row>emptyList()
                : RentzScoreContent.rows(session.history(), copy);
        if (history.isEmpty()) container.addView(empty(context, copy));
        else {
            var players = new ArrayList<String>();
            for (int seat = 0; seat < history.get(0).values().size(); seat++) players.add(copy.name(seat, name));
            var grid = new RentzHistoryGrid(context, history, players, copy, hand -> {
                if (container.getChildCount() != 1) return;
                container.getChildAt(0).setVisibility(View.GONE);
                container.addView(new RentzHandDetails(context, hand, players, copy));
                dialog.setTitle(copy.text("Hand ", "Mâna ") + hand.hand() + " · " + hand.label());
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(copy.text("Back to history", "Înapoi la istoric"));
            });
            container.addView(grid);
        }
        dialog.setOnDismissListener(ignored -> dismissed.run());
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            if (!returnToGrid(dialog, container, copy)) dialog.dismiss();
        });
        dialog.setOnKeyListener((ignored, key, event) -> key == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled() && returnToGrid(dialog, container, copy));
        int width = Math.max(1, Math.min(720, WindowContent.widthDp(context) - 16));
        dialog.getWindow().setLayout(dp(context, width), ViewGroup.LayoutParams.WRAP_CONTENT);
        return dialog;
    }

    private static boolean returnToGrid(AlertDialog dialog, FrameLayout container, RentzUiText copy) {
        if (container.getChildCount() != 2) return false;
        container.removeViewAt(1);
        container.getChildAt(0).setVisibility(View.VISIBLE);
        dialog.setTitle(copy.history());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(copy.close());
        return true;
    }

    private static View empty(Context context, RentzUiText copy) {
        var empty = new TextView(context);
        int padding = dp(context, 16);
        empty.setPadding(padding, padding, padding, padding);
        empty.setTextColor(TablePalette.ACTIVE_SEAT_TEXT);
        empty.setText(copy.text("No completed hands yet", "Nicio mână încheiată"));
        empty.setTag("score-history-empty");
        return empty;
    }

    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
