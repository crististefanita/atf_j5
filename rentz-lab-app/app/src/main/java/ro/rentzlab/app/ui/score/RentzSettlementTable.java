package ro.rentzlab.app.ui.score;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import ro.cardtable.ui.table.ScoreGridStyle;
import ro.rentzlab.app.ui.copy.RentzUiText;

/** Compact settlement using the shared Whist grid dimensions and palette. */
public final class RentzSettlementTable extends LinearLayout {
    public RentzSettlementTable(Context context, RentzScoreContent.Row hand, List<String> names, RentzUiText copy) {
        super(context);
        setOrientation(VERTICAL);
        setTag("hand-score-table");
        var shape = new GradientDrawable();
        shape.setColor(ScoreGridStyle.PAPER);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(1), ScoreGridStyle.RULE);
        setBackground(shape);
        var data = RentzScoreContent.settlement(hand, names, copy);
        var weights = List.of(ScoreGridStyle.COLUMN_WEIGHTS.get(0),
                ScoreGridStyle.COLUMN_WEIGHTS.get(3), ScoreGridStyle.COLUMN_WEIGHTS.get(4));
        for (int index = 0; index < data.size(); index++) {
            boolean header = index == 0;
            var row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), 0, dp(8), 0);
            row.setBackgroundColor(header ? ScoreGridStyle.HEADER : index == 1 ? ScoreGridStyle.HUMAN_ROW
                    : index % 2 == 0 ? ScoreGridStyle.STRIPE : ScoreGridStyle.PAPER);
            row.setContentDescription(String.join(" · ", data.get(index)));
            for (int column = 0; column < 3; column++) {
                var cell = new TextView(context);
                cell.setText(data.get(index).get(column));
                cell.setTextSize(header ? 11 : 13);
                cell.setTextColor(header ? ScoreGridStyle.MUTED : ScoreGridStyle.INK);
                cell.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                cell.setGravity(column == 0 ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER);
                cell.setSingleLine(true);
                cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(cell, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weights.get(column)));
            }
            addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(header ? ScoreGridStyle.HEADER_DP : ScoreGridStyle.ROW_DP)));
            if (index + 1 < data.size()) {
                var divider = new View(context);
                divider.setBackgroundColor(ScoreGridStyle.RULE);
                addView(divider, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
