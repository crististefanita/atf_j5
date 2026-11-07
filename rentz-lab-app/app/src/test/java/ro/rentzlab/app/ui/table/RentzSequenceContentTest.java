package ro.rentzlab.app.ui.table;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;
import java.util.List;

@AndroidComponentLayer
public final class RentzSequenceContentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void everyTableSizeKeepsItsActualOpeningRankAndStableSlotsWithoutInventingPlayedCards() {
        for (int count = 3; count <= 6; count++) {
            var session = new GameSession(count, 31, RentzPlayer.Difficulty.NORMAL);
            session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, true));
            var rows = RentzSequenceContent.rows(session.view());
            audit.equal("Four suits " + count, 4, rows.size());
            audit.equal("Active ranks " + count, count * 2, rows.get(0).size());
            audit.equal("Actual opener " + count, List.of(15 - count), rows.get(0).stream().filter(cell -> cell.opening()).map(cell -> cell.card().rank()).toList());
            audit.truth("No fictional opening cards " + count, rows.stream().flatMap(List::stream).noneMatch(cell -> cell.played()));
            var view = session.view();
            session.apply(new GameSession.Command(view.legal().isEmpty() ? GameSession.Action.PASS : GameSession.Action.PLAY, 0, view.legal().isEmpty() ? null : view.legal().get(0), null, false));
            session.advanceBots();
            var after = session.view();
            audit.equal("Exact public board " + count, java.util.Set.copyOf(after.board()),
                    java.util.Set.copyOf(RentzSequenceContent.rows(after).stream().flatMap(List::stream).filter(cell -> cell.played()).map(cell -> cell.card()).toList()));
        }
    }
}
