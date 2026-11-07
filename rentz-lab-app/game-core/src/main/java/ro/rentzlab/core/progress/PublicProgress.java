package ro.rentzlab.core.progress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch.HandResult;
import ro.rentzlab.core.ScoreProfile;

/** Validates only the public ledger; no hidden hand, actual seed or AI policy is accepted. */
public record PublicProgress(int firstChooser, List<Integer> totals, List<Set<Contract>> remaining,
                             List<HandResult> completed) {
    public PublicProgress {
        totals = Card.immutable(totals);
        var schedules = new ArrayList<Set<Contract>>();
        for (var contracts : remaining) {
            var copy = EnumSet.noneOf(Contract.class);
            copy.addAll(contracts);
            schedules.add(Collections.unmodifiableSet(copy));
        }
        remaining = Card.immutable(schedules);
        completed = Card.immutable(completed);
    }

    public static PublicProgress restore(int players, ScoreProfile scoring, int currentChooser,
                                         List<HandResult> completed) {
        Objects.requireNonNull(completed);
        Card.deck(players);
        Objects.requireNonNull(scoring).assertMatchSafe(players);
        if (completed.size() >= players * 8) throw new IllegalArgumentException("No unplayed hand in public progress");
        int first = completed.isEmpty() ? currentChooser : completed.get(0).chooser();
        if (first < 0 || first >= players) throw new IllegalArgumentException("First chooser");
        var remaining = new ArrayList<Set<Contract>>();
        var totals = new ArrayList<Integer>();
        for (int seat = 0; seat < players; seat++) {
            remaining.add(EnumSet.allOf(Contract.class));
            totals.add(0);
        }
        for (int index = 0; index < completed.size(); index++) {
            var hand = completed.get(index);
            int number = index + 1;
            if (hand.number() != number || hand.chooser() != (first + number - 1) % players
                    || hand.contract() == null || hand.points().size() != players || hand.totals().size() != players
                    || !remaining.get(hand.chooser()).contains(hand.contract())
                    || hand.blind() && remaining.get(hand.chooser()).size() == 1) {
                throw new IllegalArgumentException("Public contract schedule");
            }
            int multiplier = hand.blind() ? 2 : 1;
            long fund = fund(scoring, hand.contract(), players) * multiplier;
            long sum = 0;
            for (int seat = 0; seat < players; seat++) {
                int point = hand.points().get(seat);
                if (point < Math.min(0, fund) || point > Math.max(0, fund)) {
                    throw new IllegalArgumentException("Public hand score range");
                }
                sum += point;
                int total = Math.addExact(totals.get(seat), point);
                if (total != hand.totals().get(seat)) throw new IllegalArgumentException("Public cumulative score");
                totals.set(seat, total);
            }
            if (sum != fund) throw new IllegalArgumentException("Public hand score fund");
            if (fund != 0 && !feasible(scoring, hand, multiplier, fund)) {
                throw new IllegalArgumentException("Public hand score allocation: " + hand.contract() + " at hand " + number);
            }
            remaining.get(hand.chooser()).remove(hand.contract());
        }
        if ((first + completed.size()) % players != currentChooser) throw new IllegalArgumentException("Public chooser rotation");
        return new PublicProgress(first, totals, remaining, completed);
    }

    private static boolean feasible(ScoreProfile profile, HandResult hand, int multiplier, long fund) {
        var points = hand.points();
        return switch (hand.contract()) {
            case TRICKS -> multiples(points, (long) profile.trickPenalty() * multiplier);
            case QUEENS -> multiples(points, (long) profile.queenPenalty() * multiplier);
            case DIAMONDS -> multiples(points, (long) profile.diamondPenalty() * multiplier);
            case WHIST -> multiples(points, (long) profile.trickReward() * multiplier);
            // The fund has already been checked; only one seat can hold the whole nonzero amount.
            case KING_HEARTS, TEN_CLUBS -> points.stream().allMatch(point -> point == 0 || point == fund);
            case RENTZ -> Arrays.equals(points.stream().mapToInt(Integer::intValue).sorted().toArray(),
                    profile.finishRewards().subList(0, points.size()).stream().mapToInt(reward -> reward * multiplier)
                            .sorted().toArray());
            case TOTALS -> multiples(points, multiplier) && TotalsAllocation.matches(profile, points, multiplier);
        };
    }

    private static boolean multiples(List<Integer> points, long unit) {
        return points.stream().allMatch(point -> point % unit == 0);
    }

    private static long fund(ScoreProfile profile, Contract contract, int players) {
        return switch (contract) {
            case TRICKS -> -8L * profile.trickPenalty();
            case QUEENS -> -4L * profile.queenPenalty();
            case DIAMONDS -> -2L * players * profile.diamondPenalty();
            case KING_HEARTS -> -profile.kingPenalty();
            case WHIST -> 8L * profile.trickReward();
            case TEN_CLUBS -> profile.tenReward();
            case TOTALS -> -8L * profile.trickPenalty() - 4L * profile.queenPenalty()
                    - 2L * players * profile.diamondPenalty() - profile.kingPenalty();
            case RENTZ -> profile.finishRewards().subList(0, players).stream().mapToLong(Integer::longValue).sum();
        };
    }
}
