package ro.rentzlab.core;

import java.util.List;

/** Configurable laboratory profile; no claim that these defaults match the reference presets. */
public record ScoreProfile(int trickPenalty, int queenPenalty, int diamondPenalty, int kingPenalty,
                           int trickReward, int tenReward, List<Integer> finishRewards) {
    private static final List<Contract> TOTALS_CATEGORIES = Card.immutable(
            java.util.Arrays.asList(Contract.TRICKS, Contract.QUEENS, Contract.DIAMONDS, Contract.KING_HEARTS));
    public ScoreProfile {
        finishRewards = Card.immutable(finishRewards);
        if (trickPenalty < 0 || queenPenalty < 0 || diamondPenalty < 0 || kingPenalty < 0
                || trickReward < 0 || tenReward < 0 || finishRewards.size() < 6
                || finishRewards.stream().anyMatch(value -> value == null || value < 0))
            throw new IllegalArgumentException("Invalid scoring profile");
    }
    public static ScoreProfile laboratory() { return new ScoreProfile(10,25,20,100,10,100,java.util.Arrays.asList(300,200,100,0,0,0)); }
    public void assertMatchSafe(int players) {
        Card.deck(players);
        long perDeal=8L*trickPenalty+4L*queenPenalty+2L*players*diamondPenalty+kingPenalty
                +8L*trickReward+tenReward+finishRewards.stream().mapToInt(Integer::intValue).max().orElseThrow(java.util.NoSuchElementException::new);
        if(perDeal*2L*players*Contract.values().length>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Scoring profile can exceed the match integer range");
    }
    public int trickPoints(Contract contract, List<Card> captured, boolean blind) {
        return trickPoints(contract, captured, blind, null);
    }

    int trickPoints(Contract contract, List<Card> captured, boolean blind, int[] counts) {
        if (captured.size() < 3 || captured.size() > 6 || captured.stream().distinct().count() != captured.size())
            throw new IllegalArgumentException("A trick has one distinct card per player");
        int points = 0;
        for (var category : trickCategories(contract)) {
            int count = switch (category) {
                case TRICKS, WHIST -> 1;
                case QUEENS -> (int) captured.stream().filter(card -> card.rank() == 12).count();
                case DIAMONDS -> (int) captured.stream().filter(card -> card.suit() == Card.Suit.DIAMONDS).count();
                case KING_HEARTS -> captured.contains(new Card(Card.Suit.HEARTS, 13)) ? 1 : 0;
                case TEN_CLUBS -> captured.contains(new Card(Card.Suit.CLUBS, 10)) ? 1 : 0;
                default -> throw new IllegalArgumentException("Expected a trick category");
            };
            points = Math.addExact(points, contribution(category, count, blind));
            if (counts != null) counts[category.ordinal()] += count;
        }
        return points;
    }

    static List<Contract> trickCategories(Contract contract) {
        return contract == Contract.TOTALS ? TOTALS_CATEGORIES : java.util.Collections.singletonList(contract);
    }

    int contribution(Contract category, int count, boolean blind) {
        int unit = switch (category) {
            case TRICKS -> -trickPenalty;
            case QUEENS -> -queenPenalty;
            case DIAMONDS -> -diamondPenalty;
            case KING_HEARTS -> -kingPenalty;
            case WHIST -> trickReward;
            case TEN_CLUBS -> tenReward;
            default -> throw new IllegalArgumentException("Expected a trick category");
        };
        return Math.multiplyExact(Math.multiplyExact(unit, count), blind ? 2 : 1);
    }
}
