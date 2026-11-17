package ro.rentzlab.core.score;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** One immutable competition-rank calculation shared by Android and browser score screens. */
public final class ScoreStandings {
    public record Place(int seat, int rank, int total) { }

    private ScoreStandings() { }

    /** Highest total wins; ties keep seat order and use competition ranks (1, 1, 3). */
    public static List<Place> rank(List<Integer> totals) {
        Objects.requireNonNull(totals, "totals");
        if (totals.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Every seat needs a total");
        var order = IntStream.range(0, totals.size()).boxed()
                .sorted(Comparator.<Integer>comparingInt(totals::get).reversed().thenComparingInt(Integer::intValue))
                .collect(Collectors.toList());
        var result = new ArrayList<Place>(order.size());
        int rank = 0;
        for (int index = 0; index < order.size(); index++) {
            int seat = order.get(index);
            int total = totals.get(seat);
            if (index == 0 || total != result.get(index - 1).total()) rank = index + 1;
            result.add(new Place(seat, rank, total));
        }
        return Collections.unmodifiableList(result);
    }

    /** Product-neutral text shared by the Android and browser final-score adapters. */
    public static String label(Place place, String playerName) {
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(playerName, "playerName");
        if (playerName.isBlank()) throw new IllegalArgumentException("A standing needs a player name");
        return place.rank() + ". " + playerName + " · Σ " + place.total();
    }
}
