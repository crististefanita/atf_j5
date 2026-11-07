package ro.rentzlab.app.ui.score;

import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;

@AndroidComponentLayer
public final class HistoryGridMetricsTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

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
