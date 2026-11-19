package ro.rentzlab.app.ui.score;

import java.util.List;

/** Compatibility facade for retained Rentz measurement scenarios; no local layout algorithm. */
record HistoryGridMetrics(int hand, int delta, int total, int separator, int players) {
    static HistoryGridMetrics fit(int viewport, int hand, int name, int delta, int total, int separator, int players) {
        var grid = ro.cardtable.ui.score.HistoryGridMetrics.fit(viewport, hand, name, List.of(delta, total), separator, players);
        return new HistoryGridMetrics(grid.label(), grid.columns().get(0), grid.columns().get(1), separator, players);
    }
    int playerWidth() { return separator + delta + total; }
    int scoresWidth() { return players * playerWidth(); }
    static int bodyHeight(int available, int header, int legend, int rows, int firstRow) {
        return ro.cardtable.ui.score.HistoryGridMetrics.bodyHeight(available, header, legend, rows, firstRow);
    }
}
