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

/** Uses Whist's full-width play positions and measures Rentz content in the free center band. */
final class RentzCardBoard extends ViewGroup {
    private final int players;
    private final int footerPx;
    private final TableViewportPolicy viewport;
    private final PlayedCardMetrics.Layout trick;
    private final BiFunction<Integer, Integer, View> render;
    private final FrameLayout center;
    private RentzCardSpace.Plan plan;
    private int previousWidth = -1;
    private int previousBudget = -1;

    RentzCardBoard(Context context, int players, int footerPx, TableViewportPolicy viewport, PlayedCardMetrics.Layout trick,
                   BiFunction<Integer, Integer, View> render) {
        super(context);
        this.players = players;
        this.footerPx = footerPx;
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
        int reference = RentzCardSpace.trickHeightPx(density, trick);
        int minimumCenter = Math.max(4, RentzCardSpace.minimumTrickHeightPx(density, trick.rows()));
        int minimum = TableGeometry.minimumHeight(players, seatHeight, minimumCenter + footerPx, gap);
        int offered = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? Math.max(Math.round(viewport.minimumBoardHeightDp() * density),
                        TableGeometry.minimumHeight(players, seatHeight, reference, gap)) : MeasureSpec.getSize(heightSpec);
        int height = Math.max(offered, minimum);
        plan = RentzCardSpace.plan(width, height, players, seatWidth, seatHeight, gap, reference);
        int budget = plan.band().height();
        if (width != previousWidth || budget != previousBudget) {
            center.removeAllViews();
            View content = render.apply(width, budget);
            if (content.getLayoutParams() == null) {
                content.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            center.addView(content);
            previousWidth = width;
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
