package ro.rentzlab.core.score;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.HandScoring;

/** Platform-neutral, seat-safe projection of one confirmed hand for Android and browser score views. */
public final class ScoreSettlement {
    public record PlayerLine(int seat, int finishPlace, int delta, int total,
            List<HandScoring.Contribution> contributions) {
        public PlayerLine { contributions = List.copyOf(contributions); }
    }

    private ScoreSettlement() { }

    /** Rentz follows its verified finish order; other contracts retain seat order. */
    public static List<PlayerLine> project(Contract contract, List<Integer> deltas, List<Integer> totals,
            HandScoring scoring) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(deltas, "deltas");
        Objects.requireNonNull(totals, "totals");
        Objects.requireNonNull(scoring, "scoring");
        if (deltas.size() != totals.size()) throw new IllegalArgumentException("Each seat needs a delta and total");
        if (deltas.stream().anyMatch(Objects::isNull) || totals.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Every seat needs a confirmed delta and total");
        }

        int seats = deltas.size();
        var finishOrder = scoring.finishOrder();
        boolean rentzOrder = contract == Contract.RENTZ && isCompleteSeatOrder(finishOrder, seats);
        var result = new ArrayList<PlayerLine>(seats);
        for (int row = 0; row < seats; row++) {
            int seat = rentzOrder ? finishOrder.get(row) : row;
            int finishPlace = rentzOrder ? row + 1 : 0;
            List<HandScoring.Contribution> contributions = scoring.bySeat().size() == seats
                    ? scoring.bySeat().get(seat) : List.of();
            result.add(new PlayerLine(seat, finishPlace, deltas.get(seat), totals.get(seat), contributions));
        }
        return List.copyOf(result);
    }

    private static boolean isCompleteSeatOrder(List<Integer> order, int seats) {
        if (order.size() != seats) return false;
        boolean[] seen = new boolean[seats];
        for (int seat : order) {
            if (seat < 0 || seat >= seats || seen[seat]) return false;
            seen[seat] = true;
        }
        return true;
    }
}
