package ro.rentzlab.app.ui.table;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;
import ro.rentzlab.core.sequence.SequenceOpening;
import java.util.List;

@AndroidComponentLayer
public final class RentzSequenceContentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void terminalProjectionKeepsOnlyCardsFromPublicPlayEvents() {
        for (int players = 3; players <= 6; players++) {
            var match = new RentzMatch(players, 31, ScoreProfile.laboratory());
            match.choose(0, Contract.RENTZ, false);
            int turns = 0;
            while (match.observe(0).phase() == RentzMatch.Phase.PLAY) {
                var actor = match.observe(match.observe(0).turn());
                if (actor.legal().isEmpty()) match.pass(actor.seat());
                else match.play(actor.seat(), actor.legal().get(0));
                if (++turns > 128) throw new AssertionError("Fixture did not complete within its action budget");
            }
            var terminal = match.observe(0);
            var played = terminal.events().stream().filter(event -> event.action() == RentzMatch.Action.PLAY)
                    .map(RentzMatch.TurnEvent::card).collect(java.util.stream.Collectors.toSet());
            var cells = RentzSequenceContent.rows(terminal).stream().flatMap(List::stream).toList();
            audit.equal("Terminal Rentz phase " + players, RentzMatch.Phase.DEAL_END, terminal.phase());
            audit.equal("Four fixed rows at settlement " + players, 8 * players, cells.size());
            audit.equal("Public play events are the only rendered cards " + players, played,
                    cells.stream().filter(RentzSequenceContent.Cell::played).map(RentzSequenceContent.Cell::card)
                            .collect(java.util.stream.Collectors.toSet()));
            audit.equal("Unused positions stay empty " + players, 8 * players - played.size(),
                    (int) cells.stream().filter(cell -> !cell.played()).count());
            audit.truth("Fixture preserves at least one unplayed card " + players, played.size() < 8 * players);
        }
    }
    @Test public void everyTableSizeKeepsItsActualOpeningRankAndStableSlotsWithoutInventingPlayedCards() {
        for (int count = 3; count <= 6; count++) {
            var session = new GameSession(count, 31, RentzPlayer.Difficulty.NORMAL);
            session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, true));
            var rows = RentzSequenceContent.rows(session.view());
            audit.equal("Four suits " + count, 4, rows.size());
            audit.equal("Active ranks " + count, count * 2, rows.get(0).size());
            audit.equal("New-game Jack opener " + count, List.of(11), rows.get(0).stream().filter(cell -> cell.opening()).map(cell -> cell.card().rank()).toList());
            audit.truth("No fictional opening cards " + count, rows.stream().flatMap(List::stream).noneMatch(cell -> cell.played()));
            var view = session.view();
            session.apply(new GameSession.Command(view.legal().isEmpty() ? GameSession.Action.PASS : GameSession.Action.PLAY, 0, view.legal().isEmpty() ? null : view.legal().get(0), null, false));
            session.advanceBots();
            var after = session.view();
            audit.equal("Exact public board " + count, java.util.Set.copyOf(after.board()),
                    java.util.Set.copyOf(RentzSequenceContent.rows(after).stream().flatMap(List::stream).filter(cell -> cell.played()).map(cell -> cell.card()).toList()));
        }
    }
    @Test public void compatibilityProjectionRetainsTheOpeningOfAnOlderSavedSession() {
        for (int count = 3; count <= 6; count++) {
            var saved = new GameSession.Saved(1, count, 31, RentzPlayer.Difficulty.NORMAL, List.of(), List.of(),
                    RentzMatch.DealEnding.UNIQUE_LAST, SequenceOpening.ACTIVE_MIDDLE);
            var session = GameSession.restore(saved);
            session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, false));
            var rows = RentzSequenceContent.rows(session.view());
            audit.equal("Stored opening policy " + count, SequenceOpening.ACTIVE_MIDDLE, session.view().sequenceOpening());
            audit.equal("Legacy opener remains explicit " + count, List.of(15 - count),
                    rows.get(0).stream().filter(RentzSequenceContent.Cell::opening)
                            .map(cell -> cell.card().rank()).toList());
        }
    }
}
