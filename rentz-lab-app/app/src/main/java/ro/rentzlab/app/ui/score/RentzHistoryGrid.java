package ro.rentzlab.app.ui.score;

import android.content.Context;
import java.util.List;
import java.util.function.Consumer;
import ro.cardtable.ui.score.ScoreHistoryView;
import ro.rentzlab.app.ui.copy.RentzUiText;

/** Compatibility IDs and domain callback; measurement and scrolling belong to the library. */
public final class RentzHistoryGrid extends ScoreHistoryView {
    public RentzHistoryGrid(Context context, List<RentzScoreContent.Row> history, List<String> players,
            RentzUiText copy, Consumer<RentzScoreContent.Row> openDetails) {
        super(context, RentzScoreContent.historyContent(history, players, copy), RentzScoreContent.detailAction(history, openDetails),
                ro.cardtable.ui.score.HistoryAppearance.REGULAR_GROUPS);
        setTag("score-history-table");
        setId(ro.rentzlab.app.R.id.history_content);
        headingsView().setId(ro.rentzlab.app.R.id.history_headings);
        bodyView().setId(ro.rentzlab.app.R.id.history_body);
        scoresView().setId(ro.rentzlab.app.R.id.history_scores);
    }
}
