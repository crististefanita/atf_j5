package ro.rentzlab.app.ui.score;

import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;

@AndroidComponentLayer
public final class HistoryGridMetricsTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void measuredLargeTextKeepsEveryScoreAndTheLastHistoryRowReachable() {
        for (int players = 3; players <= 6; players++) for (int width : new int[]{280, 360, 414, 600}) {
            for (float font : new float[]{1f, 1.5f, 2f}) for (int height : new int[]{320, 568, 896}) {
                // Explicit measurement fixtures, not a substitute for Android font rasterization.
                int delta = (int) Math.ceil(52 * font), total = (int) Math.ceil(65 * font);
                var grid = HistoryGridMetrics.fit(width, (int) Math.ceil(180 * font),
                        (int) Math.ceil(100 * font), delta, total, RentzScoreLayout.HISTORY_SEPARATOR_DP, players);
                String context = players + "/" + width + "x" + height + "/font=" + font;
                audit.truth("Measured signed numbers remain complete " + context, grid.delta() >= delta && grid.total() >= total);
                audit.truth("Pinned metadata leaves a visible score pane " + context, grid.hand() <= width * .4);
                audit.equal("Every player's last column remains in the scroll extent " + context,
                        players * (grid.delta() + grid.total() + RentzScoreLayout.HISTORY_SEPARATOR_DP), grid.scoresWidth());
                int heading = (int) Math.ceil(70 * font), legend = (int) Math.ceil(32 * font);
                int first = (int) Math.ceil(58 * font), allRows = players * 8 * first;
                int body = HistoryGridMetrics.bodyHeight(height, heading, legend, allRows, first);
                if (height >= heading + legend + first) {
                    audit.truth("Fixed headings and legend leave a complete readable row " + context,
                            body >= first && body <= height - heading - legend);
                } else {
                    audit.equal("Short window delegates the last row to the outer scroller " + context, allRows, body);
                }
            }
        }
    }

    @Test public void phoneAndTabletReserveScoresWithoutShrinkingSignedNumbers() {
        for (int players = 3; players <= 6; players++) {
            for (int viewport : new int[]{320, 393, 600, 800}) {
                String profile = players + " seats / " + viewport + " px";
                var grid = HistoryGridMetrics.fit(viewport, 310, 430, 103, 126, 1, players);
                audit.truth("Pinned metadata leaves a score viewport " + profile, grid.hand() <= viewport * .4);
                audit.truth("Full signed delta width " + profile, grid.delta() >= 103);
                audit.truth("Full signed total width " + profile, grid.total() >= 126);
                audit.equal("Last player right edge " + profile, players * (grid.delta() + grid.total() + 1), grid.scoresWidth());
                audit.truth("Wide names can wrap within a score viewport " + profile, grid.playerWidth() <= viewport - grid.hand() || viewport == 320);
            }
        }
    }

    @Test public void wideViewportDistributesSpaceAndNarrowViewportRetainsEveryColumn() {
        var narrow = HistoryGridMetrics.fit(320, 130, 92, 34, 44, 1, 6);
        audit.equal("Width of pinned hand column", 128, narrow.hand());
        audit.equal("Measured six score groups", 558, narrow.scoresWidth());
        audit.equal("Remaining horizontal travel", 366, narrow.hand() + narrow.scoresWidth() - 320);
        var wide = HistoryGridMetrics.fit(800, 130, 92, 34, 44, 1, 6);
        audit.equal("Tablet leaves only the four indivisible pixels", 4, 800 - wide.hand() - wide.scoresWidth());
        audit.equal("Independent signed score widths retain the wider total", 10, wide.total() - wide.delta());
    }

    @Test public void shortHistoryWrapsAndTallHistoryKeepsHeaderAndLegendVisible() {
        audit.equal("One completed hand uses only its own height", 58, HistoryGridMetrics.bodyHeight(480, 70, 38, 58, 58));
        audit.equal("Bounded row pane below the fixed header", 372, HistoryGridMetrics.bodyHeight(480, 70, 38, 2700, 58));
        audit.equal("Exactly one readable row remains pinned", 58, HistoryGridMetrics.bodyHeight(166, 70, 38, 2700, 58));
        audit.equal("Compact height scrolls the entire content", 2700, HistoryGridMetrics.bodyHeight(165, 70, 38, 2700, 58));
        audit.equal("Wrapped large-text legend preserves access to the last row", 2700, HistoryGridMetrics.bodyHeight(190, 86, 64, 2700, 80));
    }
}
