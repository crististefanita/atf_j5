package ro.rentzlab.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Public settlement facts. Seat indices are stable even when multiple places pay the same award. */
public record HandScoring(List<List<Contribution>> bySeat, List<Integer> finishOrder) {
    public record Contribution(Contract category, int count, int points) {}

    public HandScoring {
        var seats = new ArrayList<List<Contribution>>();
        for (var contributions : bySeat) seats.add(Card.immutable(contributions));
        bySeat = Card.immutable(seats);
        finishOrder = Card.immutable(finishOrder);
    }

    static HandScoring unavailable() {
        return new HandScoring(Collections.emptyList(), Collections.emptyList());
    }

    static HandScoring settle(ScoreProfile profile, Contract contract, boolean blind,
            int[][] counts, int[] points, List<Integer> finishOrder) {
        var seats = new ArrayList<List<Contribution>>();
        for (int seat = 0; seat < points.length; seat++) {
            var contributions = new ArrayList<Contribution>();
            if (contract == Contract.RENTZ) {
                contributions.add(new Contribution(Contract.RENTZ, 1, points[seat]));
            } else {
                for (var category : ScoreProfile.trickCategories(contract)) {
                    int count = counts[seat][category.ordinal()];
                    contributions.add(new Contribution(category, count, profile.contribution(category, count, blind)));
                }
            }
            seats.add(contributions);
        }
        return new HandScoring(seats, finishOrder);
    }

    void verify(Contract contract, List<Integer> points) {
        if (bySeat.isEmpty() && finishOrder.isEmpty()) return;
        if (bySeat.size() != points.size()) throw new IllegalArgumentException("Missing seat breakdown");
        if (contract == Contract.RENTZ) {
            if (finishOrder.size() != points.size() || finishOrder.stream().distinct().count() != points.size()
                    || finishOrder.stream().anyMatch(seat -> seat < 0 || seat >= points.size()))
                throw new IllegalArgumentException("Invalid finishing order");
        } else if (!finishOrder.isEmpty()) throw new IllegalArgumentException("Unexpected finishing order");

        var categories = ScoreProfile.trickCategories(contract);
        for (int seat = 0; seat < points.size(); seat++) {
            var contributions = bySeat.get(seat);
            if (contributions.size() != categories.size()) throw new IllegalArgumentException("Missing score category");
            long sum = 0;
            for (int index = 0; index < categories.size(); index++) {
                var value = contributions.get(index);
                if (value.category() != categories.get(index) || value.count() < 0)
                    throw new IllegalArgumentException("Invalid score category");
                sum += value.points();
            }
            if (sum != points.get(seat)) throw new IllegalArgumentException("Breakdown differs from settlement");
        }
    }
}
