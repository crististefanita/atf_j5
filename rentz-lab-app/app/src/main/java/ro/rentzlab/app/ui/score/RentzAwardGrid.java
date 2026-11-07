package ro.rentzlab.app.ui.score;

import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.util.List;
import ro.cardtable.ui.screens.ScreenViews;
import ro.rentzlab.app.R;
import ro.rentzlab.app.ui.copy.RentzUiText;

/** A row per table size and a column per finishing place. */
public final class RentzAwardGrid {
    private RentzAwardGrid() { }

    public static View create(ScreenViews views, RentzUiText copy, List<List<Integer>> awards) {
        var scroll = new HorizontalScrollView(views.activity());
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        var grid = new TableLayout(views.activity());
        grid.setStretchAllColumns(true);
        scroll.addView(grid, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int places = awards.get(awards.size() - 1).size();
        var header = new TableRow(views.activity());
        header.setBackgroundColor(0xffdfeada);
        header.addView(cell(views, copy.text("Players", "Jucători"), true));
        for (int place = 1; place <= places; place++) {
            var label = cell(views, Integer.toString(place), true);
            label.setContentDescription(copy.text("Place ", "Locul ") + place);
            header.addView(label);
        }
        grid.addView(header);
        for (var points : awards) {
            var row = new TableRow(views.activity());
            row.setBackgroundColor(points.size() % 2 == 0 ? ScreenViews.SURFACE : ScreenViews.BACKGROUND);
            String table = points.size() + copy.text(" players", " jucători");
            var count = cell(views, Integer.toString(points.size()), true);
            count.setContentDescription(table);
            row.addView(count);
            for (int place = 1; place <= places; place++) {
                var value = cell(views, place <= points.size() ? points.get(place - 1).toString() : "—", false);
                if (place <= points.size()) {
                    value.setId(R.id.rentz_finishing_award);
                    value.setContentDescription(table + copy.text(", place ", ", locul ") + place
                            + ", " + value.getText() + copy.text(" points", " puncte"));
                } else {
                    value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                }
                row.addView(value);
            }
            grid.addView(row);
        }
        return scroll;
    }

    private static TextView cell(ScreenViews views, String text, boolean heading) {
        var cell = views.text(text, 14, ScreenViews.INK, heading);
        cell.setGravity(Gravity.CENTER);
        cell.setSingleLine(true);
        cell.setPadding(views.dp(6), views.dp(5), views.dp(6), views.dp(5));
        if (heading && Build.VERSION.SDK_INT >= 28) cell.setAccessibilityHeading(true);
        return cell;
    }
}
