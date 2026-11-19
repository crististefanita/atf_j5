package ro.rentzlab.app.ui.score;

import android.content.Context;
import java.util.List;
import ro.cardtable.ui.score.ScoreTableView;
import ro.rentzlab.app.ui.copy.RentzUiText;

/** Rentz confirmed scores and column roles over the common Android widget. */
public final class RentzSettlementTable extends ScoreTableView {
    public RentzSettlementTable(Context context, RentzScoreContent.Row hand, List<String> names, RentzUiText copy) {
        super(context, RentzScoreContent.settlementContent(hand, names, copy));
        setTag("hand-score-table");
    }
}
