package ro.rentzlab.core.progress;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import ro.rentzlab.core.ScoreProfile;

/** Bounded allocation of scoring cards to won tricks, independent of hidden hands and play order. */
final class TotalsAllocation {
    private record Captures(int tricks, int diamondOnly, int queenOnly, int queenDiamond, int kingHearts) {
        int scoringCards() {
            return diamondOnly + queenOnly + queenDiamond + kingHearts;
        }

        int penalty(ScoreProfile profile) {
            return tricks * profile.trickPenalty() + (diamondOnly + queenDiamond) * profile.diamondPenalty()
                    + (queenOnly + queenDiamond) * profile.queenPenalty() + kingHearts * profile.kingPenalty();
        }

        int key(int players) {
            return (((tricks * (2 * players) + diamondOnly) * 4 + queenOnly) * 2 + queenDiamond) * 2 + kingHearts;
        }

        Captures minus(int won, int diamonds, int queens, int shared, int king) {
            return new Captures(tricks - won, diamondOnly - diamonds, queenOnly - queens,
                    queenDiamond - shared, kingHearts - king);
        }
    }

    private final ScoreProfile profile;
    private final int[] losses;
    private final int players;
    private final BitSet[] rejected;

    private TotalsAllocation(ScoreProfile profile, List<Integer> points, int multiplier) {
        this.profile = profile;
        losses = points.stream().mapToInt(point -> -(point / multiplier)).toArray();
        Arrays.sort(losses);
        players = losses.length;
        rejected = new BitSet[players];
    }

    static boolean matches(ScoreProfile profile, List<Integer> points, int multiplier) {
        var search = new TotalsAllocation(profile, points, multiplier);
        var deck = new Captures(8, 2 * search.players - 1, 3, 1, 1);
        return search.assign(search.players - 1, deck);
    }

    private boolean assign(int seat, Captures left) {
        if (seat == 0) {
            return left.scoringCards() <= left.tricks() * players && left.penalty(profile) == losses[0];
        }
        int key = left.key(players);
        if (rejected[seat] != null && rejected[seat].get(key)) return false;

        int diamondUnit = profile.diamondPenalty();
        for (int tricks = 0; tricks <= left.tricks(); tricks++) {
            // Leave room for the residual scoring cards as well as those assigned to this seat.
            int minCards = left.scoringCards() - (left.tricks() - tricks) * players;
            int maxCards = tricks * players;
            for (int shared = 0; shared <= left.queenDiamond(); shared++) {
                for (int king = 0; king <= left.kingHearts(); king++) {
                    for (int queens = 0; queens <= left.queenOnly(); queens++) {
                        int fixed = tricks * profile.trickPenalty() + shared * profile.diamondPenalty()
                                + (queens + shared) * profile.queenPenalty() + king * profile.kingPenalty();
                        int fixedCards = shared + king + queens;
                        int low = Math.max(0, minCards - fixedCards);
                        int high = Math.min(left.diamondOnly(), maxCards - fixedCards);
                        int rest = losses[seat] - fixed;
                        if (diamondUnit == 0) {
                            if (rest != 0) continue;
                        } else {
                            if (rest < 0 || rest % diamondUnit != 0) continue;
                            low = Math.max(low, rest / diamondUnit);
                            high = Math.min(high, rest / diamondUnit);
                        }
                        for (int diamonds = low; diamonds <= high; diamonds++) {
                            if (assign(seat - 1, left.minus(tricks, diamonds, queens, shared, king))) return true;
                        }
                    }
                }
            }
        }
        if (rejected[seat] == null) rejected[seat] = new BitSet();
        rejected[seat].set(key);
        return false;
    }

}
