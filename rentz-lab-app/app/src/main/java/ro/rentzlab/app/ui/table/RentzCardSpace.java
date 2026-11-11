package ro.rentzlab.app.ui.table;

import ro.cardtable.ui.cards.CardFaceMetrics;
import ro.cardtable.ui.table.PlayedCardMetrics;
import ro.cardtable.ui.table.TableGeometry;

import java.util.List;

/** Uses the shared play geometry and fits the public Rentz sequence into its free band. */
final class RentzCardSpace {
    record Sequence(int rowHeight, int faceHeight, int faceWidth, int advance, int start,
                    int indexWidth, int indexPadding, float labelSizePx, boolean stackedIndex) {
        int left(int column) { return start + column * advance; }
        boolean overlaps() { return advance < faceWidth; }
    }
    record Plan(List<TableGeometry.Rect> seats, TableGeometry.VerticalBand band) { }

    private RentzCardSpace() { }

    static int trickHeightPx(float density, PlayedCardMetrics.Layout trick) {
        return Math.max(Math.round(trick.centerHeightDp() * density),
                Math.round((trick.rows() - 1) * trick.rowStrideDp() * density)
                        + Math.round(trick.cellHeightDp() * density));
    }

    static int minimumTrickHeightPx(float density, int rows) {
        return Math.max(Math.round(rows * density), Math.round((rows - 1) * density) + Math.round(density));
    }

    static PlayedCardMetrics.Layout fitTrick(PlayedCardMetrics.Layout trick, int budgetPx, float density) {
        if (trickHeightPx(density, trick) <= budgetPx) return trick;
        // The native renderer rounds the last row's offset and height separately.
        for (int budgetDp = Math.min(trick.centerHeightDp() - 1, (int) Math.ceil(budgetPx / density));
                budgetDp >= trick.rows(); budgetDp--) {
            var fitted = compressTrick(trick, budgetDp);
            if (trickHeightPx(density, fitted) <= budgetPx) return fitted;
        }
        throw new IllegalArgumentException("Card band cannot fit the trick rows");
    }

    private static PlayedCardMetrics.Layout compressTrick(PlayedCardMetrics.Layout trick, int budgetDp) {
        int stride = Math.max(1, Math.min(trick.rowStrideDp(),
                (budgetDp - trick.cellHeightDp()) / (trick.rows() - 1)));
        int cell = Math.max(1, Math.min(trick.cellHeightDp(), budgetDp - (trick.rows() - 1) * stride));
        int label = Math.min(trick.playerLabelHeightDp(), Math.max(0, cell - 1));
        int padding = Math.min(trick.itemVerticalPaddingDp(), Math.max(0, cell - label - 1));
        return new PlayedCardMetrics.Layout(trick.columns(), trick.rows(), trick.cardWidthDp(), cell - label - padding,
                label, padding, trick.centerWidthDp(), trick.safeCenterWidthDp(), cell, stride,
                cell + (trick.rows() - 1) * stride, trick.slots());
    }

    static int boardMinimumHeight(int players, int seatHeight, int gap,
            int chooserHeight, int minimumTrickHeight, int footerHeight) {
        int centerHeight = Math.max(chooserHeight, Math.max(4, minimumTrickHeight) + footerHeight);
        return TableGeometry.minimumHeight(players, seatHeight, centerHeight, gap);
    }

    static Plan plan(int width, int height, int players, int seatWidth, int seatHeight, int gap, int trickHeight) {
        int maximumCenter = height - TableGeometry.minimumHeight(players, seatHeight, 0, gap);
        var layout = TableGeometry.arrangeDisplay(width, height, players, seatWidth, seatHeight,
                width, Math.min(trickHeight, maximumCenter), gap, true, TableGeometry.CenterAlignment.BALANCED);
        return new Plan(layout.seats(), TableGeometry.fullWidthCardBand(layout, gap));
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
