package ro.rentzlab.app.ui.score;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.HandScoring;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.score.ScoreSettlement;
import ro.rentzlab.core.ScoreProfile;
import ro.rentzlab.core.score.ScoreStandings;
import ro.rentzlab.app.ui.copy.RentzUiText;
import java.util.List;
import java.util.Optional;

/** Exact confirmed deltas and totals; seat order is never sorted by score or edited names. */
public final class RentzScoreContent {
    public static final String HAND_POINTS_MARK = "P";
    public static final String TOTAL_MARK = "T";
    public record Value(int delta, int total) { }
    public record Row(int hand, int chooser, Contract contract, String label, List<Value> values, HandScoring scoring) {
        public Row { values = List.copyOf(values); }
    }
    public record Detail(int seat, int place, Value score, List<HandScoring.Contribution> contributions) { }
    public record Standing(int seat, int place, int total) { }
    private RentzScoreContent() { }

    public static List<List<Integer>> finishingAwards(ScoreProfile profile) {
        return java.util.stream.IntStream.rangeClosed(3, 6)
                .mapToObj(profile::finishRewardsFor)
                .collect(immutable());
    }
    public static List<Row> rows(List<RentzMatch.HandResult> history, RentzUiText copy) {
        return history.stream().map(hand -> row(hand, copy)).collect(immutable());
    }
    public static String legend(RentzUiText copy) {
        return copy.text("P: hand points · T: running total", "P: punctele mâinii · T: total după mână");
    }
    public static Optional<Row> currentHand(RentzMatch.Observation view, List<RentzMatch.HandResult> history, RentzUiText copy) {
        if (view.phase() != RentzMatch.Phase.DEAL_END && view.phase() != RentzMatch.Phase.MATCH_END || history.isEmpty())
            return Optional.empty();
        var last = history.get(history.size() - 1);
        return last.number() == view.handNumber() ? Optional.of(row(last, copy)) : Optional.empty();
    }
    public static List<Standing> finalStandings(RentzMatch.Observation view) {
        if (view.phase() != RentzMatch.Phase.MATCH_END) return List.of();
        return ScoreStandings.rank(view.totals()).stream()
                .map(place -> new Standing(place.seat(), place.rank(), place.total())).collect(immutable());
    }
    public static String standingLabel(Standing standing, String playerName) {
        return ScoreStandings.label(new ScoreStandings.Place(standing.seat(), standing.place(), standing.total()), playerName)
                .replace(" · Σ ", " · T ");
    }
    private static Row row(RentzMatch.HandResult hand, RentzUiText copy) {
        return new Row(hand.number(), hand.chooser(), hand.contract(), copy.playedContract(hand.contract(), hand.blind()),
                java.util.stream.IntStream.range(0, hand.points().size()).mapToObj(seat ->
                        new Value(hand.points().get(seat), hand.totals().get(seat))).collect(immutable()), hand.scoring());
    }
    public static List<Detail> details(Row row) {
        if (row.scoring().bySeat().isEmpty()) return java.util.Collections.emptyList();
        return ScoreSettlement.project(row.contract(), row.values().stream().map(Value::delta).toList(),
                        row.values().stream().map(Value::total).toList(), row.scoring()).stream()
                .map(line -> new Detail(line.seat(), line.finishPlace(),
                        new Value(line.delta(), line.total()), line.contributions()))
                .collect(immutable());
    }
}
