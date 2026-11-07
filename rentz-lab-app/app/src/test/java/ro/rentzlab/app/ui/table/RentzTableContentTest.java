package ro.rentzlab.app.ui.table;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;
import java.util.List;

@AndroidComponentLayer
public final class RentzTableContentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    private final RentzUiText copy = new RentzUiText(RentzUiText.Language.EN);
    @Test public void hiddenChoiceNeverProjectsCardsBeforeReveal() {
        for (int count = 3; count <= 6; count++) {
            var session = new GameSession(count, 31, RentzPlayer.Difficulty.NORMAL); var view = session.view();
            audit.equal("Hidden hand " + count, List.of(), RentzTableContent.hand(view, copy, false));
            session.apply(new GameSession.Command(GameSession.Action.REVEAL, 0, null, null, false));
            audit.equal("Eight revealed cards " + count, 8, RentzTableContent.hand(session.view(), copy, false).size());
            audit.truth("Choice cards are not playable " + count, RentzTableContent.hand(session.view(), copy, false).stream().noneMatch(card -> card.playable()));
        }
    }
    @Test public void visibleHandUsesOnlyCurrentLegalIdentityAndPausedInputStaysDisabled() {
        for (int count = 3; count <= 6; count++) {
            var session = ready(count, Contract.WHIST); var view = session.view();
            var cards = RentzTableContent.hand(view, copy, false);
            audit.equal("Own hand order " + count, view.hand().stream().map(RentzTableContent::id).toList(), cards.stream().map(card -> card.id()).toList());
            audit.equal("Legal projection " + count, view.legal().stream().map(RentzTableContent::id).toList(), cards.stream().filter(card -> card.playable()).map(card -> card.id()).toList());
            var selected = view.legal().get(0);
            audit.truth("Original legal card identity " + count, selected == RentzTableContent.selected(view, RentzTableContent.id(selected)));
            audit.truth("Paused input " + count, RentzTableContent.hand(view, copy, true).stream().noneMatch(card -> card.playable()));
            audit.rejects("Unknown card " + count, IllegalArgumentException.class, () -> RentzTableContent.selected(view, "SPADES:99"));
        }
    }
    @Test public void actorAndFactsFollowRentzEventsAcrossEveryContractWithoutWhistFields() {
        for (Contract contract : Contract.values()) {
            var session = ready(4, contract); int actions = 0;
            while (session.view().phase() == RentzMatch.Phase.PLAY) {
                var view = session.view(); var seats = RentzTableContent.seats(view, copy, "Ana", false);
                audit.equal("Active actor " + contract + "/" + actions, List.of(view.turn()), seats.stream().filter(seat -> seat.active()).map(seat -> seat.seat()).toList());
                for (var seat : seats) {
                    audit.equal("Own stats " + contract + "/" + actions + "/" + seat.seat(), List.of("Points:", "Total:"), seat.facts().stream().map(f -> f.label()).toList());
                    audit.equal("Exact confirmed total " + contract + "/" + actions + "/" + seat.seat(), view.totals().get(seat.seat()).toString(), seat.facts().get(1).value());
                }
                if (view.legal().isEmpty()) session.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false));
                else session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, RentzTableContent.selected(view, RentzTableContent.id(view.legal().get(0))), null, false));
                session.advanceBots();
                if (++actions > 80) throw new AssertionError("Rentz hand did not terminate");
            }
            audit.equal("Single completed hand " + contract, 1, session.history().size());
            audit.truth("No active actor after completion " + contract, RentzTableContent.seats(session.view(), copy, "Ana", false).stream().noneMatch(seat -> seat.active()));
        }
    }
    private GameSession ready(int count, Contract contract) {
        var session = new GameSession(count, 31, RentzPlayer.Difficulty.NORMAL);
        session.apply(new GameSession.Command(GameSession.Action.REVEAL, 0, null, null, false));
        session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, contract, false)); return session;
    }
}
