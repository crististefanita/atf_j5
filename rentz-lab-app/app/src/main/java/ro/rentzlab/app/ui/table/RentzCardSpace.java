package ro.rentzlab.app.ui.table;

import ro.cardtable.ui.table.PlayedCardMetrics;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableSeatLayout;
import ro.rentzlab.core.RentzMatch;

import java.util.List;

/** Fits the Rentz sequence grid around the existing table positions. */
final class RentzCardSpace {
    static final int REFERENCE_WIDTH_DP = 220;
    static final int REFERENCE_HEIGHT_DP = 128;
    static final int MIN_READABLE_HEIGHT_DP = 72;

    record Sequence(int rowHeightDp, int sideInsetDp, int verticalInsetDp, float labelSp) { }
    record Band(int top, int bottom) {
        int height() { return bottom - top; }
        int centeredTop(int contentHeight) { return top + (height() - contentHeight) / 2; }
    }
    record Plan(List<TableGeometry.Rect> seats, Band band) { }

    private RentzCardSpace() { }

    static int referenceHeightPx(int players, int footerDp, float density, PlayedCardMetrics.Layout trick) {
        int sequenceHeight = Math.round((REFERENCE_HEIGHT_DP + footerDp) * density);
        if (players != 4) return sequenceHeight;
        return Math.max(sequenceHeight, trickHeightPx(density, trick));
    }

    static int minimumControlsHeightPx(int players, RentzMatch.Phase phase, float density, PlayedCardMetrics.Layout trick) {
        if (players == 4 && (phase == RentzMatch.Phase.DEAL_END || phase == RentzMatch.Phase.MATCH_END)) {
            return trickHeightPx(density, trick);
        }
        // Shared table geometry requires a nonempty center, including when it has no controls.
        return Math.round(density);
    }

    private static int trickHeightPx(float density, PlayedCardMetrics.Layout trick) {
        return Math.max(Math.round(trick.centerHeightDp() * density),
                Math.round((trick.rows() - 1) * trick.rowStrideDp() * density)
                        + Math.round(trick.cellHeightDp() * density));
    }

    static Plan plan(int width, int height, int players, int seatWidth, int seatHeight, int gap,
                     int referenceWidth, int referenceHeight, int minimumReadableHeight) {
        int maximumCenter = height - TableGeometry.minimumHeight(players, seatHeight, 0, gap);
        int footprint = Math.min(referenceHeight, maximumCenter);
        var seats = TableGeometry.arrangeDisplay(width, height, players, seatWidth, seatHeight,
                referenceWidth, footprint, gap, false, TableGeometry.CenterAlignment.BALANCED).seats();
        var band = band(seats, gap);
        if (band.height() >= minimumReadableHeight) return new Plan(seats, band);

        // Fall back to the shared full-width play anchors only when the original seats crowd the grid.
        seats = TableGeometry.arrangeDisplay(width, height, players, seatWidth, seatHeight,
                width, footprint, gap, true, TableGeometry.CenterAlignment.BALANCED).seats();
        return new Plan(seats, band(seats, gap));
    }

    private static Band band(List<TableGeometry.Rect> seats, int gap) {
        var anchors = TableSeatLayout.positions(seats.size());
        int upperBottom = 0;
        int lowerTop = seats.get(0).top();
        for (int index = 0; index < anchors.length; index++) {
            var seat = seats.get(index);
            switch (anchors[index]) {
                case NORTH, NORTH_WEST, NORTH_EAST -> upperBottom = Math.max(upperBottom, seat.bottom());
                case SOUTH, SOUTH_WEST, SOUTH_EAST -> lowerTop = Math.min(lowerTop, seat.top());
                default -> throw new IllegalArgumentException("Unexpected seat on the sequence board");
            }
        }
        return new Band(upperBottom + gap, lowerTop - gap);
    }

    static Sequence sequence(int players, int width, int height, float fontScale) {
        int row = Math.max(1, Math.min(40, height / 4));
        int column = width / (2 * players);
        int sideInset = column >= 24 ? 1 : 0;
        int verticalInset = row >= 24 ? 1 : 0;
        float faceWidth = column - 2 * sideInset;
        float faceHeight = row - 2 * verticalInset;
        float label = Math.max(1f, Math.min(15f, Math.min(faceWidth / 2f, faceHeight / 1.45f) / fontScale));
        return new Sequence(row, sideInset, verticalInset, label);
    }
}
