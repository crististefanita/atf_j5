package ro.rentzlab.core.progress;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.OpeningDeal;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.RentzMatch.HandResult;
import ro.rentzlab.core.ScoreProfile;
import ro.rentzlab.testing.RentzComponentLayer;
import static org.junit.jupiter.api.Assertions.*;

@RentzComponentLayer
class PublicProgressTest {
    @Test void lateContinuationPreservesAnAlreadyEmptyOpposingPortfolio() {
        var history = new ArrayList<HandResult>();
        int[] funds = {-80,-100,-160,-100,-440,80,100};
        var cumulative = new ArrayList<>(List.of(0, 0, 0, 0));
        for (int hand = 0; hand < 29; hand++) {
            int round = hand / 4;
            var points = round == 7 ? List.of(300, 200, 100, 0) : List.of(funds[round], 0, 0, 0);
            for (int seat = 0; seat < 4; seat++) cumulative.set(seat, cumulative.get(seat) + points.get(seat));
            history.add(new HandResult(hand+1,hand%4,Contract.values()[round],false,
                    points, List.copyOf(cumulative)));
        }
        var hands = new ArrayList<List<Card>>();
        var deck = Card.deck(4);
        for (int seat = 0; seat < 4; seat++) hands.add(deck.subList(seat*8,seat*8+8));
        var game = RentzMatch.fromPublicProgress(new OpeningDeal(hands),31,ScoreProfile.laboratory(),1,history);
        assertEquals(29,game.history().size());
        assertTrue(game.observe(0).available().isEmpty());
        assertEquals(java.util.Set.of(Contract.RENTZ),game.observe(1).available());
        assertEquals(30,game.observe(1).handNumber());
    }
    @Test void ledgerRestoresRotationTotalsAndPortfolioWithoutAliasingInput() {
        var completed = new ArrayList<HandResult>();
        completed.add(new HandResult(1, 2, Contract.TRICKS, true, List.of(-160,0,0,0), List.of(-160,0,0,0)));
        var restored = PublicProgress.restore(4, ScoreProfile.laboratory(), 3, completed);
        completed.clear();
        assertEquals(2, restored.firstChooser());
        assertEquals(List.of(-160,0,0,0), restored.totals());
        assertEquals(1, restored.completed().size());
        assertFalse(restored.remaining().get(2).contains(Contract.TRICKS));
        assertTrue(restored.remaining().get(1).contains(Contract.TRICKS));
        assertThrows(UnsupportedOperationException.class, () -> restored.remaining().get(1).clear());
        assertThrows(UnsupportedOperationException.class, () -> restored.totals().set(0,0));
    }
    @Test void rangeFundCumulativeAndRotationDefectsAreIndependent() {
        var profile = ScoreProfile.laboratory();
        var valid = new HandResult(1,0,Contract.WHIST,false,List.of(80,0,0,0),List.of(80,0,0,0));
        assertDoesNotThrow(() -> PublicProgress.restore(4,profile,1,List.of(valid)));
        for (var invalid : List.of(
                new HandResult(1,0,Contract.WHIST,false,List.of(81,-1,0,0),List.of(81,-1,0,0)),
                new HandResult(1,0,Contract.WHIST,false,List.of(79,0,0,0),List.of(79,0,0,0)),
                new HandResult(1,0,Contract.WHIST,false,List.of(80,0,0,0),List.of(79,0,0,0)),
                new HandResult(2,0,Contract.WHIST,false,List.of(80,0,0,0),List.of(80,0,0,0)))) {
            assertThrows(IllegalArgumentException.class, () -> PublicProgress.restore(4,profile,1,List.of(invalid)));
        }
        assertThrows(IllegalArgumentException.class, () -> PublicProgress.restore(4,profile,2,List.of(valid)));
        assertEquals(List.of(80,0,0,0), valid.totals());
    }

    private static Stream<Arguments> allocations() {
        var lab = ScoreProfile.laboratory();
        var distinct = new ScoreProfile(1, 10, 100, 10_000, 7, 31, List.of(17, 17, 0, 5, 19, 2));
        return Stream.of(
                Arguments.of("trick units", lab, Contract.TRICKS, false, List.of(-10,-70,0,0), List.of(-1,-79,0,0)),
                Arguments.of("blind queen units", lab, Contract.QUEENS, true, List.of(-50,-150,0,0), List.of(-25,-175,0,0)),
                Arguments.of("diamond units", lab, Contract.DIAMONDS, false, List.of(-20,-140,0,0), List.of(-10,-150,0,0)),
                Arguments.of("custom blind Whist units", distinct, Contract.WHIST, true, List.of(14,98,0,0), List.of(7,105,0,0)),
                Arguments.of("one heart king", lab, Contract.KING_HEARTS, false, List.of(0,0,-100,0), List.of(-25,-75,0,0)),
                Arguments.of("one blind club ten", distinct, Contract.TEN_CLUBS, true, List.of(0,62,0,0), List.of(31,31,0,0)),
                Arguments.of("Rentz finishing places", lab, Contract.RENTZ, false, List.of(0,100,300,200), List.of(150,150,150,150)),
                Arguments.of("blind duplicate and zero places", distinct, Contract.RENTZ, true, List.of(34,0,10,34), List.of(34,2,8,34)),
                Arguments.of("Totals amounts beyond divisibility", lab, Contract.TOTALS, false, List.of(-10,-430,0,0), List.of(-5,-435,0,0)),
                Arguments.of("Totals diamond queen stays together", distinct, Contract.TOTALS, false, List.of(-704,-10144,0,0), List.of(-804,-10044,0,0)),
                Arguments.of("blind Totals capture capacity", distinct, Contract.TOTALS, true, List.of(-802,-20894,0,0), List.of(-1002,-20694,0,0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allocations")
    void refutesImpossibleAllocationsWithTheSameFund(String name, ScoreProfile profile, Contract contract,
                                                     boolean blind, List<Integer> legal, List<Integer> impossible) {
        assertEquals(legal.stream().mapToInt(Integer::intValue).sum(), impossible.stream().mapToInt(Integer::intValue).sum());
        var valid = new HandResult(1, 0, contract, blind, legal, legal);
        var invalid = new HandResult(1, 0, contract, blind, impossible, impossible);
        assertEquals(legal, PublicProgress.restore(4, profile, 1, List.of(valid)).totals(), name);
        assertThrows(IllegalArgumentException.class, () -> PublicProgress.restore(4, profile, 1, List.of(invalid)), name);
    }

    @Test void matchContinuationRejectsImpossiblePastBeforeAcceptingTheOpening() {
        var deck = Card.deck(3);
        var opening = new OpeningDeal(List.of(deck.subList(0,8), deck.subList(8,16), deck.subList(16,24)));
        var invalid = new HandResult(1, 0, Contract.RENTZ, false, List.of(200,200,200), List.of(200,200,200));
        assertThrows(IllegalArgumentException.class, () -> RentzMatch.fromPublicProgress(
                opening, 41, ScoreProfile.laboratory(), 1, List.of(invalid)));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6})
    void acceptsEngineSettlementsAtEveryTableSize(int players) {
        var zero = new ScoreProfile(0,0,0,0,0,0,List.of(0,0,0,0,0,0));
        var mixed = new ScoreProfile(0,7,0,11,0,5,List.of(0,0,9,0,5,9,11));
        for (var profile : List.of(ScoreProfile.laboratory(), zero, mixed)) {
            for (var contract : Contract.values()) {
                for (boolean blind : List.of(false, true)) {
                    var game = new RentzMatch(players, 93 + contract.ordinal(), profile);
                    int chooser = game.observe(0).chooser();
                    game.choose(chooser, contract, blind);
                    while (game.observe(0).phase() == RentzMatch.Phase.PLAY) {
                        int seat = game.observe(0).turn();
                        var legal = game.observe(seat).legal();
                        if (legal.isEmpty()) game.pass(seat);
                        else game.play(seat, legal.get(0));
                    }
                    var restored = PublicProgress.restore(players, profile, (chooser + 1) % players, game.history());
                    assertEquals(game.observe(0).totals(), restored.totals(), contract + " blind=" + blind);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6})
    void totalsMatchesAnIndependentOracleWhenScoresEncodeEachCount(int players) {
        var profile = new ScoreProfile(1, 10, 100, 10_000, 0, 0, List.of(0,0,0,0,0,0));
        var random = new Random(19);
        int feasible = 0;
        for (int trial = 0; trial < 100; trial++) {
            int[] tricks = distribute(random, players, 8);
            int[] queens = distribute(random, players, 4);
            int[] diamonds = distribute(random, players, 2 * players);
            int king = random.nextInt(players);
            boolean blind = trial % 2 == 0;
            var points = new ArrayList<Integer>();
            for (int seat = 0; seat < players; seat++) {
                int encoded = tricks[seat] + 10 * queens[seat] + 100 * diamonds[seat] + (seat == king ? 10_000 : 0);
                points.add(-encoded * (blind ? 2 : 1));
            }
            boolean fits = false;
            // The digits determine all counts; only the owner of the overlapping card is unknown.
            for (int overlap = 0; overlap < players && !fits; overlap++) {
                if (queens[overlap] == 0 || diamonds[overlap] == 0) continue;
                fits = true;
                for (int seat = 0; seat < players; seat++) {
                    int distinct = queens[seat] + diamonds[seat] + (seat == king ? 1 : 0) - (seat == overlap ? 1 : 0);
                    fits &= distinct <= tricks[seat] * players;
                }
            }
            var row = new HandResult(1, 0, Contract.TOTALS, blind, points, points);
            if (fits) {
                feasible++;
                assertEquals(points, PublicProgress.restore(players, profile, 1, List.of(row)).totals());
            } else {
                assertThrows(IllegalArgumentException.class, () -> PublicProgress.restore(players, profile, 1, List.of(row)),
                        points.toString());
            }
        }
        assertTrue(feasible > 0 && feasible < 100, "The fixed samples must exercise both verdicts");
    }

    private static int[] distribute(Random random, int players, int count) {
        var result = new int[players];
        for (int i = 0; i < count; i++) result[random.nextInt(players)]++;
        return result;
    }
}
