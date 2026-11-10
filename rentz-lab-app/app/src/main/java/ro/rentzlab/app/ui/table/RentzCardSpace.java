package ro.rentzlab.app.ui.table;

import ro.cardtable.ui.cards.CardFaceMetrics;
import ro.cardtable.ui.table.PlayedCardMetrics;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableSeatLayout;

import java.util.List;

/** Uses the shared play geometry and fits the public Rentz sequence into its free band. */
final class RentzCardSpace {
    record Sequence(int rowHeight, int faceHeight, int faceWidth, int advance, int start,
                    int indexWidth, int indexPadding, float labelSizePx, boolean stackedIndex) {
        int left(int column) { return start + column * advance; }
        boolean overlaps() { return advance < faceWidth; }
    }
    record Band(int top, int bottom) {
        int height() { return bottom - top; }
        int centeredTop(int contentHeight) { return top + (height() - contentHeight) / 2; }
    }
    record Plan(List<TableGeometry.Rect> seats, Band band) { }

    private RentzCardSpace() { }

    static int trickHeightPx(float density, PlayedCardMetrics.Layout trick) {
        return Math.max(Math.round(trick.centerHeightDp() * density),
                Math.round((trick.rows() - 1) * trick.rowStrideDp() * density)
                        + Math.round(trick.cellHeightDp() * density));
    }

    static PlayedCardMetrics.Layout fitTrick(PlayedCardMetrics.Layout trick, int budgetDp) {
        if (trick.centerHeightDp() <= budgetDp) return trick;
        int stride = Math.max(1, Math.min(trick.rowStrideDp(),
                (budgetDp - trick.cellHeightDp()) / (trick.rows() - 1)));
        int cell = Math.max(1, Math.min(trick.cellHeightDp(), budgetDp - (trick.rows() - 1) * stride));
        int label = Math.min(trick.playerLabelHeightDp(), Math.max(0, cell - 1));
        int padding = Math.min(trick.itemVerticalPaddingDp(), Math.max(0, cell - label - 1));
        return new PlayedCardMetrics.Layout(trick.columns(), trick.rows(), trick.cardWidthDp(), cell - label - padding,
                label, padding, trick.centerWidthDp(), trick.safeCenterWidthDp(), cell, stride,
                cell + (trick.rows() - 1) * stride, trick.slots());
    }

    static Plan plan(int width, int height, int players, int seatWidth, int seatHeight, int gap, int trickHeight) {
        int maximumCenter = height - TableGeometry.minimumHeight(players, seatHeight, 0, gap);
        var seats = TableGeometry.arrangeDisplay(width, height, players, seatWidth, seatHeight,
                width, Math.min(trickHeight, maximumCenter), gap, true, TableGeometry.CenterAlignment.BALANCED).seats();
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

    static Sequence sequence(int players, int width, int height, float density, float fontScale) {
        int columns = 2 * players;
        int row = Math.min(Math.round(62 * density), height / 4);
        boolean shallow = row < Math.round(40 * density);
        int faceHeight = row - Math.min(Math.round((shallow ? 2 : 4) * density), row / 6);
        boolean stacked = faceHeight >= Math.round(24 * density);
        int minimumIndex = Math.min(width / columns, Math.round(20 * density));
        int portraitWidth = Math.round(CardFaceMetrics.atHeight(Math.max(1, Math.round(faceHeight / density)))
                .widthDp() * density);
        int readableWidth = shallow ? Math.min(width / columns + Math.round(6 * density),
                Math.round(faceHeight * (stacked ? 1.25f : 1.9f))) : 0;
        int faceWidth = Math.min(Math.max(portraitWidth, readableWidth), width - (columns - 1) * minimumIndex);
        int advance = Math.min(faceWidth + Math.round(3 * density), (width - faceWidth) / (columns - 1));
        int start = (width - faceWidth - (columns - 1) * advance) / 2;
        int indexWidth = Math.min(faceWidth, advance);
        int padding = Math.min(Math.round(2 * density), Math.min(indexWidth / 8, faceHeight / 6));
        float label = Math.min(15 * density * fontScale,
                Math.min((indexWidth - 2 * padding) / (stacked ? 1.5f : 2.2f),
                        (faceHeight - 2 * padding) / (stacked ? 2.6f : 1.4f)));
        return new Sequence(row, faceHeight, faceWidth, advance, start, indexWidth, padding, label, stacked);
    }
}
