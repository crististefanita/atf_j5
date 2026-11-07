package ro.rentzlab.app.ui.score;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import ro.cardtable.ui.theme.TablePalette;
import ro.rentzlab.app.ui.copy.RentzUiText;

/** The row panes share a vertical scroll. The two score panes share a horizontal position. */
public final class RentzHistoryGrid extends ScrollView {
    private static final int HEADER = 0xffdfeada;
    private static final int LIGHT_ROW = 0xfffffef8;
    private static final int DARK_ROW = 0xfff1f5ee;
    private static final int DIVIDER = 0xffb4c3af;
    private final ScrollView rows;
    private final HorizontalScrollView headings, scores;
    private final LinearLayout header, scoreHeadings, scoreRows;
    private final TextView corner, legend;
    private final List<TextView> names = new ArrayList<>();
    private final List<TextView> deltas = new ArrayList<>();
    private final List<TextView> totals = new ArrayList<>();
    private final List<RowViews> rowViews = new ArrayList<>();
    private final int separator;
    private final int handMinimum, nameMinimum, deltaMinimum, totalMinimum;
    private int fittedWidth = -1, naturalRows, firstRow;

    public RentzHistoryGrid(Context context, List<RentzScoreContent.Row> history, List<String> players,
            RentzUiText copy, Consumer<RentzScoreContent.Row> openDetails) {
        super(context);
        separator = dp(1);
        setTag("score-history-table");
        setId(ro.rentzlab.app.R.id.history_content);
        var content = column();
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        header = new LinearLayout(context);
        header.setBackgroundColor(HEADER);
        corner = text(copy.text("Hand · game", "Mână · joc"), true, Gravity.START);
        heading(corner);
        header.addView(corner, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT));
        headings = horizontal();
        headings.setId(ro.rentzlab.app.R.id.history_headings);
        scoreHeadings = new LinearLayout(context);
        for (String player : players) {
            var name = text(player, true, Gravity.CENTER_HORIZONTAL);
            heading(name);
            names.add(name);
            var captions = scores("Δ", "Σ", null);
            var group = column();
            group.addView(name);
            group.addView(captions);
            addGroup(scoreHeadings, group, HEADER);
        }
        headings.addView(scoreHeadings, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        header.addView(headings, remainingWidth(LayoutParams.MATCH_PARENT));
        content.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0));

        rows = new ScrollView(context);
        rows.setId(ro.rentzlab.app.R.id.history_body);
        var body = new LinearLayout(context);
        rows.addView(body, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        var handLabels = column();
        body.addView(handLabels, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        scores = horizontal();
        scores.setId(ro.rentzlab.app.R.id.history_scores);
        scoreRows = column();
        for (var row : history) {
            int fill = row.hand() % 2 == 0 ? DARK_ROW : LIGHT_ROW;
            var hand = text(row.hand() + " · " + row.label() + "\n" + players.get(row.chooser()), false, Gravity.START);
            hand.setBackgroundColor(fill);
            hand.setPaintFlags(hand.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            hand.setOnClickListener(ignored -> openDetails.accept(row));
            hand.setFocusable(true);
            hand.setTooltipText(copy.text("Hand scoring details", "Detaliul punctajului mâinii"));
            handLabels.addView(hand);
            var numbers = new LinearLayout(context);
            numbers.setBackgroundColor(fill);
            for (int seat = 0; seat < players.size(); seat++) {
                var value = row.values().get(seat);
                String description = players.get(seat) + ". Δ " + value.delta() + ". Σ " + value.total()
                        + ". " + copy.text("Hand ", "Mâna ") + row.hand();
                addGroup(numbers, scores(Integer.toString(value.delta()), Integer.toString(value.total()), description), fill);
            }
            scoreRows.addView(numbers);
            rowViews.add(new RowViews(hand, numbers));
        }
        scores.addView(scoreRows, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        body.addView(scores, remainingWidth(LayoutParams.WRAP_CONTENT));
        content.addView(rows, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0));

        legend = text(copy.text("Δ: hand points · Σ: confirmed total", "Δ: punctele mâinii · Σ: total confirmat"), false, Gravity.START);
        content.addView(legend, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        headings.setOnScrollChangeListener((view, x, y, oldX, oldY) -> sync(scores, x));
        scores.setOnScrollChangeListener((view, x, y, oldX, oldY) -> sync(headings, x));
        headings.setScrollbarFadingEnabled(false);
        int handWidth = desiredWidth(corner);
        for (var row : rowViews) handWidth = Math.max(handWidth, desiredWidth(row.hand()));
        handMinimum = handWidth;
        nameMinimum = maxWidth(names);
        deltaMinimum = maxWidth(deltas);
        totalMinimum = maxWidth(totals);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = Math.max(2, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        if (width != fittedWidth) fit(width);
        measureNatural(legend, width);
        int available = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                : MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom();
        int height = HistoryGridMetrics.bodyHeight(available, header.getLayoutParams().height, legend.getMeasuredHeight(), naturalRows, firstRow);
        if (rows.getLayoutParams().height != height) {
            rows.getLayoutParams().height = height;
            rows.forceLayout();
            ((View) rows.getParent()).forceLayout();
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private void fit(int width) {
        fittedWidth = width;
        var grid = HistoryGridMetrics.fit(width, handMinimum, nameMinimum, deltaMinimum, totalMinimum, separator, names.size());
        size(corner, grid.hand(), LayoutParams.MATCH_PARENT);
        for (var name : names) size(name, grid.delta() + grid.total(), LayoutParams.WRAP_CONTENT);
        for (var delta : deltas) size(delta, grid.delta(), LayoutParams.MATCH_PARENT);
        for (var total : totals) size(total, grid.total(), LayoutParams.MATCH_PARENT);
        measureNatural(corner, grid.hand());
        measureNatural(scoreHeadings, grid.scoresWidth());
        size(header, LayoutParams.MATCH_PARENT, Math.max(corner.getMeasuredHeight(), scoreHeadings.getMeasuredHeight()));
        size(scoreHeadings, grid.scoresWidth(), LayoutParams.MATCH_PARENT);
        naturalRows = 0;
        for (var row : rowViews) {
            measureNatural(row.hand(), grid.hand());
            measureNatural(row.numbers(), grid.scoresWidth());
            int height = Math.max(row.hand().getMeasuredHeight(), row.numbers().getMeasuredHeight());
            size(row.hand(), grid.hand(), height);
            size(row.numbers(), grid.scoresWidth(), height);
            naturalRows += height;
        }
        firstRow = rowViews.get(0).hand().getLayoutParams().height;
        size(scoreRows, grid.scoresWidth(), LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout scores(String delta, String total, String description) {
        var pair = new LinearLayout(getContext());
        deltas.add(number(pair, delta, false, description == null));
        totals.add(number(pair, total, true, description == null));
        if (description != null) {
            pair.setFocusable(true);
            pair.setContentDescription(description);
        }
        return pair;
    }

    private TextView number(LinearLayout pair, String value, boolean bold, boolean accessible) {
        var view = text(value, bold, Gravity.END);
        view.setSingleLine(true);
        if (!accessible) view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        pair.addView(view);
        return view;
    }

    private void addGroup(LinearLayout line, LinearLayout group, int fill) {
        var border = new View(getContext());
        border.setBackgroundColor(DIVIDER);
        border.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.addView(border, new LinearLayout.LayoutParams(separator, LayoutParams.MATCH_PARENT));
        group.setBackgroundColor(fill);
        line.addView(group, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    }

    private TextView text(String value, boolean bold, int horizontalGravity) {
        var view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(13);
        view.setTextColor(TablePalette.ACTIVE_SEAT_TEXT);
        view.setGravity(horizontalGravity | Gravity.CENTER_VERTICAL);
        view.setPadding(dp(6), dp(5), dp(6), dp(5));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout column() {
        var layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private HorizontalScrollView horizontal() {
        var scroll = new HorizontalScrollView(getContext());
        scroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        return scroll;
    }

    private static void heading(View view) { if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true); }
    private static void sync(HorizontalScrollView target, int x) { if (target.getScrollX() != x) target.scrollTo(x, 0); }
    private static LinearLayout.LayoutParams remainingWidth(int height) { return new LinearLayout.LayoutParams(0, height, 1f); }
    private static int maxWidth(List<TextView> views) { return views.stream().mapToInt(RentzHistoryGrid::desiredWidth).max().orElseThrow(); }
    private static int desiredWidth(TextView view) { return (int) Math.ceil(Layout.getDesiredWidth(view.getText(), view.getPaint())) + view.getCompoundPaddingLeft() + view.getCompoundPaddingRight(); }
    private static void measureNatural(View view, int width) { view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)); }
    private static void size(View view, int width, int height) {
        var params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.forceLayout();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private record RowViews(TextView hand, LinearLayout numbers) { }
}
