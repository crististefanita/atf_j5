package ro.rentzlab.ai.tactics;

import java.util.Comparator;
import java.util.function.ToIntFunction;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

/** Keeps losing exits and weighs shortening a suit using only the actor's public observation. */
public final class VoidTactics {
    private static final int ACE = 14;
    private static final Comparator<Card> STRENGTH = Comparator.comparingInt(Card::rank)
            .thenComparing(Card::suit);

    private VoidTactics() { }

    public static boolean avoidance(Contract contract) {
        return switch (contract) {
            case TRICKS, QUEENS, DIAMONDS, KING_HEARTS, TOTALS -> true;
            default -> false;
        };
    }

    public static Card losingLead(RentzMatch.Observation view) {
        var remaining = new Remaining(view);
        return view.legal().stream().filter(card -> remaining.higher(card) > 0 && remaining.lower(card) == 0)
                .max(STRENGTH).orElse(null);
    }

    public static Card highestForcedLoser(RentzMatch.Observation view) {
        if (view.trick().isEmpty()) return null;
        var led = view.trick().get(0).card().suit();
        var strongest = view.legal().stream().max(STRENGTH).orElseThrow();
        if (strongest.suit() != led) return null;
        int winningRank = view.trick().stream().map(play -> play.card())
                .filter(card -> card.suit() == led).mapToInt(Card::rank).max().orElseThrow();
        return strongest.rank() < winningRank ? strongest : null;
    }

    public static Card discard(RentzMatch.Observation view, ToIntFunction<Card> penalty) {
        var remaining = new Remaining(view);
        return view.legal().stream().max(Comparator.comparingInt(penalty)
                .thenComparingInt(card -> -remaining.guard(card))
                .thenComparingDouble(remaining::discardValue)
                .thenComparing(STRENGTH)).orElseThrow();
    }

    private static final class Remaining {
        private final int bottom;
        private final int opponents;
        private final boolean[][] accounted = new boolean[Card.Suit.values().length][ACE + 1];
        private final int[] owned = new int[accounted.length];
        private final int[] minimum = new int[accounted.length];

        Remaining(RentzMatch.Observation view) {
            bottom = 15 - 2 * view.players();
            opponents = view.players() - 1;
            java.util.Arrays.fill(minimum, ACE + 1);
            for (var card : view.seen()) mark(card);
            for (var card : view.hand()) {
                int suit = card.suit().ordinal();
                mark(card);
                owned[suit]++;
                minimum[suit] = Math.min(minimum[suit], card.rank());
            }
        }

        private void mark(Card card) {
            accounted[card.suit().ordinal()][card.rank()] = true;
        }

        int higher(Card card) { return outside(card, card.rank() + 1, ACE); }
        int lower(Card card) { return outside(card, bottom, card.rank() - 1); }

        private int outside(Card card, int from, int to) {
            var ranks = accounted[card.suit().ordinal()];
            int count = 0;
            for (int rank = from; rank <= to; rank++) if (!ranks[rank]) count++;
            return count;
        }

        int guard(Card card) {
            int suit = card.suit().ordinal();
            if (card.rank() != minimum[suit]) return 0;
            if (higher(card) > 0 && lower(card) == 0) return 2;
            return owned[suit] > 1 ? 1 : 0;
        }

        double discardValue(Card card) {
            int lower = lower(card);
            int outside = lower + higher(card);
            double captureRisk = (lower + 1.0) / (outside + 1.0);
            double shortening = Math.min(1.0, (double) outside / opponents) / owned[card.suit().ordinal()];
            return captureRisk + shortening;
        }
    }
}
