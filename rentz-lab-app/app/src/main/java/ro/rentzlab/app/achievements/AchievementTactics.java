package ro.rentzlab.app.achievements;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.TrickRules;
import ro.rentzlab.core.TrickRules.Play;

/** Public facts reconstructed from accepted commands at hand settlement. */
public record AchievementTactics(Set<Fact> facts) {
    public enum Fact {
        LOSE_FIRST, LOSE_LAST, AT_MOST_TWO, NO_RUN_OF_TWO, NO_TRICKS,
        QUEEN_PASSED, QUEEN_DISCARDED, NO_EARLY_QUEENS, AT_MOST_ONE_QUEEN, NO_QUEENS,
        FIRST_DIAMOND_PASSED, DIAMOND_DISCARDED, NO_EARLY_DIAMONDS, AT_MOST_TWO_DIAMONDS, NO_DIAMONDS,
        KING_PASSED, KING_FOLLOWED, KING_DISCARDED, KING_LEAD_PASSED, KING_PASSED_AFTER_WIN,
        NO_KING, WIN_FIRST, WIN_LAST, WIN_ANY, RUN_OF_TWO, AT_LEAST_THREE, AT_LEAST_FOUR,
        TEN_CAPTURED, OPPONENT_TEN, OWN_TEN, TEN_ON_LEAD, TEN_AFTER_LOSS,
        OPEN, EXTEND_LOW, EXTEND_HIGH, ACE_RETAINS_TURN, TOP_TWO, FOLLOWED_SUIT, SAFE_DISCARD
    }

    public AchievementTactics { facts = Set.copyOf(facts); }
    public boolean has(Fact fact) { return facts.contains(fact); }

    static AchievementTactics from(RentzMatch.HandResult result, List<GameSession.Command> moves, int openingRank) {
        var found = EnumSet.noneOf(Fact.class);
        if (result.contract() == Contract.RENTZ) sequence(result, moves, openingRank, found);
        else tricks(moves, result.points().size(), found);
        return new AchievementTactics(found);
    }

    private static void sequence(RentzMatch.HandResult result, List<GameSession.Command> moves,
            int openingRank, EnumSet<Fact> found) {
        for (int i = 0; i < moves.size(); i++) {
            var move = moves.get(i);
            if (move.seat() != 0 || move.action() != GameSession.Action.PLAY) continue;
            int rank = move.card().rank();
            found.add(rank == openingRank ? Fact.OPEN : rank < openingRank ? Fact.EXTEND_LOW : Fact.EXTEND_HIGH);
            if (rank == 14 && i + 1 < moves.size() && moves.get(i + 1).seat() == 0)
                found.add(Fact.ACE_RETAINS_TURN);
        }
        int place = result.scoring().finishOrder().indexOf(0);
        if (place >= 0 && place < 2) found.add(Fact.TOP_TWO);
    }

    private record Trick(List<Play> plays, int winner) {
        Play human() { return plays.stream().filter(p -> p.seat() == 0).findFirst().orElseThrow(); }
        Card.Suit led() { return plays.get(0).card().suit(); }
        boolean humanLead() { return plays.get(0).seat() == 0; }
        long count(Predicate<Card> card) { return plays.stream().map(Play::card).filter(card).count(); }
        boolean won() { return winner == 0; }
    }

    private static void tricks(List<GameSession.Command> moves, int players, EnumSet<Fact> found) {
        var tricks = new ArrayList<Trick>();
        var current = new ArrayList<Play>(players);
        for (var move : moves) {
            if (move.action() != GameSession.Action.PLAY) continue;
            current.add(new Play(move.seat(), move.card()));
            if (current.size() == players) {
                tricks.add(new Trick(List.copyOf(current), TrickRules.winner(current)));
                current.clear();
            }
        }
        if (tricks.isEmpty()) return;
        collectTrickCounts(tricks, found);
        collectCardFacts(tricks, found);
    }

    private static void collectTrickCounts(List<Trick> tricks, EnumSet<Fact> found) {
        found.add(tricks.get(0).won() ? Fact.WIN_FIRST : Fact.LOSE_FIRST);
        found.add(tricks.get(tricks.size() - 1).won() ? Fact.WIN_LAST : Fact.LOSE_LAST);
        int wins = 0;
        boolean previousWon = false;
        for (var trick : tricks) {
            if (trick.won()) {
                wins++;
                if (previousWon) found.add(Fact.RUN_OF_TWO);
            }
            previousWon = trick.won();
        }
        if (wins <= 2) found.add(Fact.AT_MOST_TWO);
        if (!found.contains(Fact.RUN_OF_TWO)) found.add(Fact.NO_RUN_OF_TWO);
        if (wins == 0) found.add(Fact.NO_TRICKS);
        else found.add(Fact.WIN_ANY);
        if (wins >= 3) found.add(Fact.AT_LEAST_THREE);
        if (wins >= 4) found.add(Fact.AT_LEAST_FOUR);
    }

    private static void collectCardFacts(List<Trick> tricks, EnumSet<Fact> found) {
        int queens = 0, diamonds = 0;
        boolean sawDiamond = false, earlyQueen = false, earlyDiamond = false;
        boolean wonEarlier = false, lostEarlier = false, capturedKing = false;
        for (int i = 0; i < tricks.size(); i++) {
            var trick = tricks.get(i);
            var card = trick.human().card();
            boolean offSuit = card.suit() != trick.led();
            if (!trick.humanLead() && !offSuit) found.add(Fact.FOLLOWED_SUIT);
            if (offSuit && !trick.won()) found.add(Fact.SAFE_DISCARD);
            long queenCount = trick.count(c -> c.rank() == 12);
            long diamondCount = trick.count(c -> c.suit() == Card.Suit.DIAMONDS);
            if (trick.won()) {
                queens += (int) queenCount;
                diamonds += (int) diamondCount;
                earlyQueen |= i < 4 && queenCount > 0;
                earlyDiamond |= i < 4 && diamondCount > 0;
            } else {
                if (queenCount > 0) found.add(Fact.QUEEN_PASSED);
                if (card.rank() == 12 && offSuit) found.add(Fact.QUEEN_DISCARDED);
                if (diamondCount > 0 && !sawDiamond) found.add(Fact.FIRST_DIAMOND_PASSED);
                if (card.suit() == Card.Suit.DIAMONDS && offSuit) found.add(Fact.DIAMOND_DISCARDED);
            }
            sawDiamond |= diamondCount > 0;
            if (trick.count(AchievementTactics::king) > 0) {
                capturedKing = trick.won();
                if (!capturedKing) {
                    found.add(Fact.KING_PASSED);
                    if (!trick.humanLead() && !offSuit) found.add(Fact.KING_FOLLOWED);
                    if (king(card) && offSuit) found.add(Fact.KING_DISCARDED);
                    if (trick.humanLead()) found.add(Fact.KING_LEAD_PASSED);
                    if (wonEarlier) found.add(Fact.KING_PASSED_AFTER_WIN);
                }
            }
            if (trick.won() && trick.count(AchievementTactics::ten) > 0) {
                found.add(Fact.TEN_CAPTURED);
                found.add(ten(card) ? Fact.OWN_TEN : Fact.OPPONENT_TEN);
                if (trick.humanLead()) found.add(Fact.TEN_ON_LEAD);
                if (lostEarlier) found.add(Fact.TEN_AFTER_LOSS);
            }
            wonEarlier |= trick.won();
            lostEarlier |= !trick.won();
        }
        if (!earlyQueen) found.add(Fact.NO_EARLY_QUEENS);
        if (queens <= 1) found.add(Fact.AT_MOST_ONE_QUEEN);
        if (queens == 0) found.add(Fact.NO_QUEENS);
        if (!earlyDiamond) found.add(Fact.NO_EARLY_DIAMONDS);
        if (diamonds <= 2) found.add(Fact.AT_MOST_TWO_DIAMONDS);
        if (diamonds == 0) found.add(Fact.NO_DIAMONDS);
        if (!capturedKing) found.add(Fact.NO_KING);
    }

    private static boolean king(Card card) { return card.suit() == Card.Suit.HEARTS && card.rank() == 13; }
    private static boolean ten(Card card) { return card.suit() == Card.Suit.CLUBS && card.rank() == 10; }
}
