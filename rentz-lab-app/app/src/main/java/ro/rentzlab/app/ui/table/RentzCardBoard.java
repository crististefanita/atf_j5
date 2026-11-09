package ro.rentzlab.app.ui.table;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import ro.cardtable.ui.table.PlayedCardMetrics;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableSizing;
import ro.cardtable.ui.table.TableViewportPolicy;

import java.util.function.BiFunction;

/** Fits the center content to the same measured seat band throughout four-player play. */
final class RentzCardBoard extends ViewGroup {
    private final int players;
    private final int footerDp;
    private final TableViewportPolicy viewport;
    private final PlayedCardMetrics.Layout trick;
    private final BiFunction<Integer, Integer, View> render;
    private final FrameLayout center;
    private RentzCardSpace.Plan plan;
    private int previousWidth = -1;
    private int previousBudget = -1;

    RentzCardBoard(Context context, int players, int footerDp, TableViewportPolicy viewport, PlayedCardMetrics.Layout trick,
                   BiFunction<Integer, Integer, View> render) {
        super(context);
        this.players = players;
        this.footerDp = footerDp;
        this.viewport = viewport;
        this.trick = trick;
        this.render = render;
        center = new FrameLayout(context);
        setClipChildren(false);
    }

    FrameLayout center() { return center; }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        float density = getResources().getDisplayMetrics().density;
        int width = MeasureSpec.getSize(widthSpec);
        int widthDp = Math.round(width / density);
        int seatWidth = Math.round(TableSizing.seatWidthDp(widthDp, viewport.isCompact()) * density);
        int seatHeight = 0;
        for (int index = 0; index < players; index++) {
            var seat = getChildAt(index);
            seat.measure(exact(seatWidth), 0);
            seatHeight = Math.max(seatHeight, seat.getMeasuredHeight());
        }
        for (int index = 0; index < players; index++) getChildAt(index).measure(exact(seatWidth), exact(seatHeight));

        int gap = Math.round(viewport.regionGapDp() * density);
        int reference = RentzCardSpace.referenceHeightPx(players, footerDp, density, trick);
        int readable = Math.round((RentzCardSpace.MIN_READABLE_HEIGHT_DP + footerDp) * density);
        int minimum = TableGeometry.minimumHeight(players, seatHeight, Math.max(1, Math.round((4 + footerDp) * density)), gap);
        int offered = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? TableGeometry.minimumHeight(players, seatHeight, reference, gap) : MeasureSpec.getSize(heightSpec);
        int height = Math.max(offered, minimum);
        int referenceWidth = Math.min(width, Math.round(RentzCardSpace.REFERENCE_WIDTH_DP * density));
        plan = RentzCardSpace.plan(width, height, players, seatWidth, seatHeight, gap, referenceWidth, reference, readable);
        int budget = (int) Math.floor(plan.band().height() / density);
        if (widthDp != previousWidth || budget != previousBudget) {
            center.removeAllViews();
            View content = render.apply(widthDp, budget);
            if (content.getLayoutParams() == null) {
                content.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            center.addView(content);
            previousWidth = widthDp;
            previousBudget = budget;
        }
        center.measure(exact(width), 0);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int index = 0; index < players; index++) {
            var seat = plan.seats().get(index);
            getChildAt(index).layout(seat.left(), seat.top(), seat.right(), seat.bottom());
        }
        int centerHeight = center.getMeasuredHeight();
        int centerTop = plan.band().centeredTop(centerHeight);
        center.layout(0, centerTop, right - left, centerTop + centerHeight);
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    private static int exact(int value) { return MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY); }
}
