package ro.rentzlab.core;

import ro.rentzlab.ai.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Oracle formulas deliberately do not call TrickRules or ScoreProfile settlement methods. */
class RentzSettlementTest {
    private static final ScoreProfile DISTINCT = new ScoreProfile(3, 7, 11, 29, 13, 31,
            List.of(101, 67, 43, 29, 17, 11));

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6})
    void everyTrickContractSettlesTheRightSeatAndContribution(int players) {
        for (Contract contract : Contract.values()) {
            if (contract == Contract.RENTZ) continue;
            for (boolean blind : List.of(false, true)) {
                for (int seed = 0; seed < 4; seed++) {
                    var game = new RentzMatch(players, seed, DISTINCT, seed % players);
                    game.choose(seed % players, contract, blind);
                    var expected = new int[players];
                    var pending = new ArrayList<TrickRules.Play>();
                    int tricks = 0, actions = 0;
                    while (game.observe(0).phase() == RentzMatch.Phase.PLAY) {
                        int actor = game.observe(0).turn();
                        var before = game.observe(actor);
                        var expectedLegal = new ArrayList<>(before.hand());
                        if (!pending.isEmpty()) {
                            Card.Suit led = pending.get(0).card().suit();
                            if (expectedLegal.stream().anyMatch(c -> c.suit() == led))
                                expectedLegal.removeIf(c -> c.suit() != led);
                        }
                        assertEquals(expectedLegal, before.legal(), "Legal cards are derived from the actor's hand and led suit");
                        // Baseline sees only its own observation; the oracle accumulates independently.
                        Card card = LabPolicy.card(before);
                        pending.add(new TrickRules.Play(actor, card));
                        game.play(actor, card);
                        var after = game.observe(actor);
                        assertEquals(blind, after.blind());
                        if (pending.size() < players) {
                            assertEquals((actor + 1) % players, after.turn());
                            assertEquals(RentzMatch.Phase.PLAY, after.phase());
                        } else {
                            int winner = independentWinner(pending);
                            expected[winner] += independentPoints(contract, pending) * (blind ? 2 : 1);
                            tricks++;
                            boolean terminal = tricks == 8
                                    || contract == Contract.KING_HEARTS && contains(pending, Card.Suit.HEARTS, 13)
                                    || contract == Contract.TEN_CLUBS && contains(pending, Card.Suit.CLUBS, 10);
                            assertEquals(terminal ? RentzMatch.Phase.DEAL_END : RentzMatch.Phase.PLAY, after.phase());
                            assertEquals(winner, after.turn(), "Capturer, not chooser or last actor, leads the next trick");
                            assertTrue(after.trick().isEmpty());
                            pending.clear();
                        }
                        assertEquals(integers(expected), after.dealPoints(), "Only the completed trick changes public points");
                        assertTrue(++actions <= players * 8, "A trick deal cannot play more than its deck");
                    }
                    assertEquals(integers(expected), game.history().get(0).points());
                    assertEquals(integers(expected), game.observe(0).totals());
                    assertEquals(independentPool(contract, players) * (blind ? 2 : 1), Arrays.stream(expected).sum());
                    if (contract != Contract.KING_HEARTS && contract != Contract.TEN_CLUBS) assertEquals(8, tricks);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6})
    void sequenceAwardsPlacesWithoutPretendingTheLastHandWasEmptied(int players) {
        for (boolean blind : List.of(false, true)) {
            for (int seed = 0; seed < 16; seed++) {
                var game = new RentzMatch(players, seed, DISTINCT);
                game.choose(0, Contract.RENTZ, blind);
                var finished = new ArrayList<Integer>();
                int actions = 0;
                while (game.observe(0).phase() == RentzMatch.Phase.PLAY) {
                    int actor = game.observe(0).turn();
                    var before = game.observe(actor);
                    assertFalse(finished.contains(actor), "A finished player cannot be asked to play or pass");
                    if (before.legal().isEmpty()) game.pass(actor);
                    else {
                        Card card = LabPolicy.card(before);
                        game.play(actor, card);
                        if (before.hand().size() == 1) finished.add(actor);
                    }
                    var after = game.observe(actor);
                    if (finished.size() == players - 1) {
                        int last = -1;
                        for (int seat = 0; seat < players; seat++) if (!finished.contains(seat)) last = seat;
                        assertTrue(after.handSizes().get(last) > 0, "Last place is determined, not injected as an empty hand");
                        finished.add(last);
                        var expected = new int[players];
                        for (int place = 0; place < players; place++)
                            expected[finished.get(place)] = List.of(101, 67, 43, 29, 17, 11).get(place) * (blind ? 2 : 1);
                        assertEquals(integers(expected), after.dealPoints());
                        assertEquals(integers(expected), game.history().get(0).points());
                        assertEquals(RentzMatch.Phase.DEAL_END, after.phase());
                    } else {
                        assertEquals(integers(new int[players]), after.dealPoints(), "This model publishes sequence awards at settlement");
                    }
                    assertEquals(finished, after.finishOrder());
                    assertEquals(players * 8, after.seen().size() + after.handSizes().stream().mapToInt(Integer::intValue).sum());
                    assertTrue(++actions < 1000, "Valid sequence play must make progress");
                }
            }
        }
    }

    @Test
    void nextDealResetsOnlyDealFactsAndDoesNotMutatePastSnapshots() {
        var game = new RentzMatch(4, 8, DISTINCT);
        game.choose(0, Contract.TEN_CLUBS, true);
        while (game.observe(0).phase() == RentzMatch.Phase.PLAY) {
            int actor = game.observe(0).turn();
            game.play(actor, LabPolicy.card(game.observe(actor)));
        }
        var completed = game.observe(0);
        assertTrue(completed.blind());
        assertEquals(62, completed.dealPoints().stream().mapToInt(Integer::intValue).sum());
        game.nextHand();
        var next = game.observe(0);
        assertFalse(next.blind());
        assertNull(next.contract());
        assertEquals(List.of(0, 0, 0, 0), next.dealPoints());
        assertEquals(completed.totals(), next.totals());
        assertTrue(next.events().isEmpty());
        assertTrue(next.finishOrder().isEmpty());
        assertTrue(completed.blind());
        assertEquals(62, completed.dealPoints().stream().mapToInt(Integer::intValue).sum());
        assertThrows(UnsupportedOperationException.class, () -> next.dealPoints().clear());
        assertThrows(UnsupportedOperationException.class, () -> next.finishOrder().clear());
    }

    private static List<Integer> integers(int[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    private static int independentWinner(List<TrickRules.Play> cards) {
        var led = cards.get(0);
        var best = led;
        for (var card : cards)
            if (card.card().suit() == led.card().suit() && card.card().rank() > best.card().rank()) best = card;
        return best.seat();
    }

    private static boolean contains(List<TrickRules.Play> cards, Card.Suit suit, int rank) {
        return cards.stream().anyMatch(p -> p.card().suit() == suit && p.card().rank() == rank);
    }

    private static int independentPoints(Contract contract, List<TrickRules.Play> cards) {
        int queens = 0, diamonds = 0;
        for (var play : cards) {
            if (play.card().rank() == 12) queens++;
            if (play.card().suit() == Card.Suit.DIAMONDS) diamonds++;
        }
        int king = contains(cards, Card.Suit.HEARTS, 13) ? 29 : 0;
        return switch (contract) {
            case TRICKS -> -3;
            case QUEENS -> -7 * queens;
            case DIAMONDS -> -11 * diamonds;
            case KING_HEARTS -> -king;
            case TOTALS -> -3 - 7 * queens - 11 * diamonds - king;
            case WHIST -> 13;
            case TEN_CLUBS -> contains(cards, Card.Suit.CLUBS, 10) ? 31 : 0;
            case RENTZ -> throw new AssertionError("Sequences do not award tricks");
        };
    }

    private static int independentPool(Contract contract, int players) {
        return switch (contract) {
            case TRICKS -> -24;
            case QUEENS -> -28;
            case DIAMONDS -> -22 * players;
            case KING_HEARTS -> -29;
            case TOTALS -> -24 - 28 - 22 * players - 29;
            case WHIST -> 104;
            case TEN_CLUBS -> 31;
            case RENTZ -> throw new AssertionError("Sequence pool depends on finishing places");
        };
    }
}
