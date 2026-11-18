package ro.rentzlab.app.ui.table;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;
import java.util.ArrayList;
import java.util.HashSet;
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
            var expected = new ArrayList<String>();
            for (String suit : List.of("SPADES", "HEARTS", "CLUBS", "DIAMONDS")) {
                for (int rank = 2; rank <= 14; rank++) {
                    String id = suit + ":" + rank;
                    if (view.hand().stream().map(RentzTableContent::id).anyMatch(id::equals)) expected.add(id);
                }
            }
            audit.equal("Cards grouped ♠ ♥ ♣ ♦ then increasing rank " + count, expected, cards.stream().map(card -> card.id()).toList());
            audit.equal("Legal projection " + count, new HashSet<>(view.legal().stream().map(RentzTableContent::id).toList()),
                    new HashSet<>(cards.stream().filter(card -> card.playable()).map(card -> card.id()).toList()));
            var selected = view.legal().get(0);
            audit.truth("Original legal card identity " + count, selected == RentzTableContent.selected(view, RentzTableContent.id(selected)));
            audit.truth("Paused input " + count, RentzTableContent.hand(view, copy, true).stream().noneMatch(card -> card.playable()));
            audit.rejects("Unknown card " + count, IllegalArgumentException.class, () -> RentzTableContent.selected(view, "SPADES:99"));
        }
    }
    @Test public void actorAndFactsFollowRentzEventsAcrossEveryContractWithoutWhistFields() {
        for (Contract contract : Contract.values()) {
            var session = ready(4, contract); int actions = 0;
            boolean constrained = false;
            while (session.view().phase() == RentzMatch.Phase.PLAY) {
                var view = session.view(); var seats = RentzTableContent.seats(view, copy, session.displayNames("Ana"), false);
                audit.equal("Active actor " + contract + "/" + actions, List.of(view.turn()), seats.stream().filter(seat -> seat.active()).map(seat -> seat.seat()).toList());
                for (var seat : seats) {
                    audit.equal("Own stats " + contract + "/" + actions + "/" + seat.seat(), List.of("P", "T"), seat.facts().stream().map(f -> f.label()).toList());
                    audit.equal("Exact confirmed total " + contract + "/" + actions + "/" + seat.seat(), view.totals().get(seat.seat()).toString(), seat.facts().get(1).value());
                }
                var cards = RentzTableContent.hand(view, copy, false);
                audit.equal("Manual input matches the engine for " + contract + "/" + actions,
                        new HashSet<>(view.legal().stream().map(RentzTableContent::id).toList()),
                        new HashSet<>(cards.stream().filter(card -> card.playable()).map(card -> card.id()).toList()));
                if (!constrained && contract != Contract.RENTZ && !view.trick().isEmpty()) {
                    var lead = view.trick().get(0).card().suit();
                    var offSuit = view.hand().stream().filter(card -> card.suit() != lead).findFirst();
                    var following = view.hand().stream().filter(card -> card.suit() == lead).toList();
                    if (offSuit.isPresent() && !following.isEmpty()) {
                        var before = session.save().commands();
                        audit.rejects("Reject off-suit when the lead suit is present in " + contract,
                                IllegalArgumentException.class,
                                () -> RentzTableContent.selected(view, RentzTableContent.id(offSuit.get())));
                        audit.equal("Rejected attempt leaves the journal intact", before, session.save().commands());
                        constrained = true;
                    }
                }
                if (view.legal().isEmpty()) session.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false));
                else session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, RentzTableContent.selected(view, RentzTableContent.id(view.legal().get(0))), null, false));
                session.advanceBots();
                if (++actions > 80) throw new AssertionError("Rentz hand did not terminate");
            }
            if (contract == Contract.WHIST) audit.truth("The Whist hand exercised a follow-suit rejection", constrained);
            audit.equal("Single completed hand " + contract, 1, session.history().size());
            audit.truth("No active actor after completion " + contract,
                    RentzTableContent.seats(session.view(), copy, session.displayNames("Ana"), false).stream().noneMatch(seat -> seat.active()));
        }
    }
    @Test public void signedScoresKeepTheirSeatsAndLocalizeAfterRestore() throws Exception {
        for (int count : List.of(3, 4, 5, 6)) {
            for (Contract contract : List.of(Contract.TRICKS, Contract.WHIST)) {
                var session = ready(count, contract);
                for (int moves = 0; session.view().phase() == RentzMatch.Phase.PLAY; moves++) {
                    if (moves == 8) throw new AssertionError("Expected at most eight player moves");
                    session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false));
                    session.advanceBots();
                }
                var restored = GameSession.restore(session.save());
                var view = restored.view();
                var names = restored.displayNames("Ana");
                int expectedSign = contract == Contract.TRICKS ? -1 : 1;
                audit.truth("Fixture has a point value of the expected sign " + count + "/" + contract,
                        view.dealPoints().stream().anyMatch(points -> Integer.signum(points) == expectedSign));
                for (var language : RentzUiText.Language.values()) {
                    var seats = RentzTableContent.seats(view, new RentzUiText(language), names, false);
                    String label = count + "/" + contract + "/" + language;
                    audit.equal("Every seat retained " + label, count, seats.size());
                    for (int index = 0; index < count; index++) {
                        var seat = seats.get(index);
                        int points = view.dealPoints().get(index);
                        int total = view.totals().get(index);
                        String details = language == RentzUiText.Language.RO ? "Puncte: " : "Points: ";
                        audit.equal("Seat position " + label + "/" + index, index, seat.seat());
                        audit.equal("Score marker order " + label + "/" + index,
                                List.of("P " + points, "T " + total), seat.facts().stream().map(fact -> fact.inlineText()).toList());
                        audit.equal("Accessible description " + label + "/" + index,
                                names.get(index) + ". " + details + points + ". Total: " + total, seat.description());
                    }
                }
            }
        }
    }

    private GameSession ready(int count, Contract contract) {
        var session = new GameSession(count, 31, RentzPlayer.Difficulty.NORMAL);
        session.apply(new GameSession.Command(GameSession.Action.REVEAL, 0, null, null, false));
        session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, contract, false)); return session;
    }
}
