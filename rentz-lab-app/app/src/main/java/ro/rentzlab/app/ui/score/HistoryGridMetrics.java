package ro.rentzlab.app.ui.score;

/** Pixel widths shared by the pinned heading, score headings and every confirmed row. */
record HistoryGridMetrics(int hand, int delta, int total, int separator, int players) {
    static HistoryGridMetrics fit(int viewport, int hand, int name, int delta, int total, int separator, int players) {
        // Keep space for scores even when the hand caption wraps at a large font scale.
        int pinned = Math.min(hand, Math.max(1, (int) (viewport * 0.4)));
        int availablePerPlayer = (viewport - pinned) / players - separator;
        int minimum = delta + total;
        int group = Math.max(minimum, Math.max(Math.min(name, viewport - pinned - separator), availablePerPlayer));
        int spare = group - minimum;
        return new HistoryGridMetrics(pinned, delta + spare / 2, total + spare - spare / 2, separator, players);
    }

    int playerWidth() { return separator + delta + total; }
    int scoresWidth() { return players * playerWidth(); }

    /** Use the outer scroller when the fixed content leaves less than one readable row. */
    static int bodyHeight(int available, int header, int legend, int rows, int firstRow) {
        int remainder = available - header - legend;
        return remainder < firstRow ? rows : Math.min(rows, remainder);
    }
}
