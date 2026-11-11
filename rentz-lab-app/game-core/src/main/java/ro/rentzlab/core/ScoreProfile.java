package ro.rentzlab.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Configurable laboratory profile; no claim that these defaults match the reference presets. */
public record ScoreProfile(int trickPenalty, int queenPenalty, int diamondPenalty, int kingPenalty,
                           int trickReward, int tenReward, List<Integer> finishRewards, Map<Integer, List<Integer>> finishingPlacesByPlayers) {
    private static final List<Contract> TOTALS_CATEGORIES = Card.immutable(
            Arrays.asList(Contract.TRICKS, Contract.QUEENS, Contract.DIAMONDS, Contract.KING_HEARTS));
    private static final ScoreProfile LABORATORY = laboratoryProfile(laboratoryAwards());
    private static final ScoreProfile LEGACY_LABORATORY = laboratoryProfile(Collections.emptyMap());
    public ScoreProfile(int trickPenalty, int queenPenalty, int diamondPenalty, int kingPenalty,
                        int trickReward, int tenReward, List<Integer> finishRewards) {
        this(trickPenalty, queenPenalty, diamondPenalty, kingPenalty, trickReward, tenReward, finishRewards, Collections.emptyMap());
    }

    public ScoreProfile {
        finishRewards = Card.immutable(finishRewards);
        if (trickPenalty < 0 || queenPenalty < 0 || diamondPenalty < 0 || kingPenalty < 0
                || trickReward < 0 || tenReward < 0 || finishRewards.size() < 6
                || finishRewards.stream().anyMatch(value -> value == null || value < 0))
            throw new IllegalArgumentException("Invalid scoring profile");
        if (finishingPlacesByPlayers == null || finishingPlacesByPlayers.isEmpty()) {
            finishingPlacesByPlayers = Collections.emptyMap();
        } else {
            var awardsByPlayers = new HashMap<Integer, List<Integer>>();
            if (finishingPlacesByPlayers.size() != 4) throw new IllegalArgumentException("Expected award rows for 3 to 6 players");
            for (int players = 3; players <= 6; players++) {
                var awards = finishingPlacesByPlayers.get(players);
                if (awards == null || awards.size() != players || awards.stream().anyMatch(java.util.Objects::isNull)
                        || awards.get(players - 1) != 0)
                    throw new IllegalArgumentException("Expected one finishing award per player and zero for last place");
                for (int place = 1; place < players; place++) {
                    if (awards.get(place - 1) <= awards.get(place))
                        throw new IllegalArgumentException("Finishing awards must strictly decrease");
                }
                awardsByPlayers.put(players, Card.immutable(awards));
            }
            finishingPlacesByPlayers = Collections.unmodifiableMap(awardsByPlayers);
        }
    }
    public static ScoreProfile laboratory() { return LABORATORY; }
    public static ScoreProfile legacyLaboratory() { return LEGACY_LABORATORY; }

    private static ScoreProfile laboratoryProfile(Map<Integer, List<Integer>> awardsByPlayers) {
        return new ScoreProfile(10, 25, 20, 100, 10, 100, Arrays.asList(300, 200, 100, 0, 0, 0), awardsByPlayers);
    }

    private static Map<Integer, List<Integer>> laboratoryAwards() {
        var awards = new HashMap<Integer, List<Integer>>();
        awards.put(3, Arrays.asList(300, 200, 0));
        awards.put(4, Arrays.asList(300, 200, 100, 0));
        awards.put(5, Arrays.asList(300, 175, 100, 25, 0));
        awards.put(6, Arrays.asList(300, 150, 75, 50, 25, 0));
        return awards;
    }
    public List<Integer> finishRewardsFor(int players) {
        if (players < 3 || players > 6) throw new IllegalArgumentException("Rentz requires 3 to 6 players");
        return finishingPlacesByPlayers.isEmpty() ? finishRewards.subList(0, players) : finishingPlacesByPlayers.get(players);
    }
    public void assertMatchSafe(int players) {
        Card.deck(players);
        long perDeal=8L*trickPenalty+4L*queenPenalty+2L*players*diamondPenalty+kingPenalty
                +8L*trickReward+tenReward+finishRewardsFor(players).stream().mapToInt(Integer::intValue).max().orElseThrow(java.util.NoSuchElementException::new);
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
