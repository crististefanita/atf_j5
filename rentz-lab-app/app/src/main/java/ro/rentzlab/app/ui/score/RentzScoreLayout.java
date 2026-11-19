package ro.rentzlab.app.ui.score;

import java.util.List;
import ro.cardtable.ui.table.ScoreGridStyle;

/** Rentz column roles over the neutral library style; no platform measurement or scoring. */
public final class RentzScoreLayout {
    public static final int HISTORY_SEPARATOR_DP = 1;
    public static final List<Float> SETTLEMENT_WEIGHTS = List.of(ScoreGridStyle.COLUMN_WEIGHTS.get(0),
            ScoreGridStyle.COLUMN_WEIGHTS.get(3), ScoreGridStyle.COLUMN_WEIGHTS.get(4));
    private RentzScoreLayout() { }
}
