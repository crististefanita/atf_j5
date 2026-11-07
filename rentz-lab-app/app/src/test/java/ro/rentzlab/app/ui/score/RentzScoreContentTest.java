package ro.rentzlab.app.ui.score;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;
import java.util.List;

@AndroidComponentLayer
public final class RentzScoreContentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void negativeAndPositiveRentzDeltasAndTotalsSurviveRestoreWithoutWhistColumns() {
        for (Contract contract : List.of(Contract.TRICKS, Contract.WHIST)) {
            var session = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
            session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, contract, true)); int moves = 0;
            while (session.view().phase() == RentzMatch.Phase.PLAY) {
                session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false)); session.advanceBots();
                if (++moves > 20) throw new AssertionError("No completed trick hand");
            }
            var copy = new RentzUiText(RentzUiText.Language.EN); var rows = RentzScoreContent.rows(session.history(), copy);
            audit.equal("One confirmed history row " + contract, 1, rows.size());
            audit.equal("Eight tricks, blind doubling " + contract, contract == Contract.TRICKS ? -160 : 160, rows.get(0).values().stream().mapToInt(value -> value.delta()).sum());
            audit.equal("Restored exact history " + contract, rows, RentzScoreContent.rows(GameSession.restore(session.save()).history(), copy));
            audit.equal("Totals keep engine seat order " + contract, session.view().totals(), rows.get(0).values().stream().map(value -> value.total()).toList());
            audit.equal("Chooser retained " + contract, 0, rows.get(0).chooser());
        }
    }
}
