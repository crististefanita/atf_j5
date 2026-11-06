package ro.rentzlab.core;

import java.util.List;

/** Configurable laboratory profile; no claim that these defaults match the reference presets. */
public record ScoreProfile(int trickPenalty, int queenPenalty, int diamondPenalty, int kingPenalty,
                           int trickReward, int tenReward, List<Integer> finishRewards) {
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
        if (captured.size() < 3 || captured.size() > 6 || captured.stream().distinct().count() != captured.size())
            throw new IllegalArgumentException("A trick has one distinct card per player");
        int points = switch(contract) {
            case TRICKS -> -trickPenalty;
            case QUEENS -> -Math.multiplyExact(queenPenalty,(int)captured.stream().filter(c -> c.rank() == 12).count());
            case DIAMONDS -> -Math.multiplyExact(diamondPenalty,(int)captured.stream().filter(c -> c.suit() == Card.Suit.DIAMONDS).count());
            case KING_HEARTS -> captured.contains(new Card(Card.Suit.HEARTS,13)) ? -kingPenalty : 0;
            case WHIST -> trickReward;
            case TEN_CLUBS -> captured.contains(new Card(Card.Suit.CLUBS,10)) ? tenReward : 0;
            case TOTALS -> Math.addExact(Math.addExact(trickPoints(Contract.TRICKS,captured,false),trickPoints(Contract.QUEENS,captured,false)),
                    Math.addExact(trickPoints(Contract.DIAMONDS,captured,false),trickPoints(Contract.KING_HEARTS,captured,false)));
            case RENTZ -> throw new IllegalArgumentException("Rentz has finishing places, not tricks");
        };
        return Math.multiplyExact(points, blind ? 2 : 1);
    }
}
