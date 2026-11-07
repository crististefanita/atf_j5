package ro.rentzlab.app.ui.score;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.HandScoring;
import ro.rentzlab.core.ScoreProfile;
import ro.rentzlab.app.ui.copy.RentzUiText;
import java.util.List;
import java.util.Optional;

/** Exact confirmed deltas and totals; seat order is never sorted by score or edited names. */
public final class RentzScoreContent {
    public static final String HAND_POINTS_MARK = "Δ";
    public static final String TOTAL_MARK = "Σ";
    public record Value(int delta, int total) { }
    public record Row(int hand, int chooser, String label, List<Value> values, HandScoring scoring) {
        public Row { values = List.copyOf(values); }
    }
    public record Detail(int seat, int place, Value score, List<HandScoring.Contribution> contributions) { }
    private RentzScoreContent() { }

    public static List<List<Integer>> finishingAwards(ScoreProfile profile) {
        return java.util.stream.IntStream.rangeClosed(3, 6)
                .mapToObj(players -> List.copyOf(profile.finishRewards().subList(0, players)))
                .collect(immutable());
    }
    public static List<Row> rows(List<RentzMatch.HandResult> history, RentzUiText copy) {
        return history.stream().map(hand -> row(hand, copy)).collect(immutable());
    }
    public static Optional<Row> currentHand(RentzMatch.Observation view, List<RentzMatch.HandResult> history, RentzUiText copy) {
        if (view.phase() != RentzMatch.Phase.DEAL_END && view.phase() != RentzMatch.Phase.MATCH_END || history.isEmpty())
            return Optional.empty();
        var last = history.get(history.size() - 1);
        return last.number() == view.handNumber() ? Optional.of(row(last, copy)) : Optional.empty();
    }
    private static Row row(RentzMatch.HandResult hand, RentzUiText copy) {
        return new Row(hand.number(), hand.chooser(), copy.contract(hand.contract()) + (hand.blind() ? " ×2" : ""),
                java.util.stream.IntStream.range(0, hand.points().size()).mapToObj(seat ->
                        new Value(hand.points().get(seat), hand.totals().get(seat))).collect(immutable()), hand.scoring());
    }
    public static List<Detail> details(Row row) {
        if (row.scoring().bySeat().isEmpty()) return java.util.Collections.emptyList();
        var order = row.scoring().finishOrder();
        return java.util.stream.IntStream.range(0, row.values().size()).mapToObj(index -> {
            int seat = order.isEmpty() ? index : order.get(index);
            return new Detail(seat, order.isEmpty() ? 0 : index + 1, row.values().get(seat), row.scoring().bySeat().get(seat));
        }).collect(immutable());
    }
}
