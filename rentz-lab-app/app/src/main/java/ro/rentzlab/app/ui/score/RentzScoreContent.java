package ro.rentzlab.app.ui.score;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import ro.rentzlab.core.RentzMatch;
import ro.cardtable.ui.score.ScoreTableContent;
import ro.cardtable.ui.score.HistoryContent;
import ro.rentzlab.core.HandScoring;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.score.ScoreSettlement;
import ro.rentzlab.core.ScoreProfile;
import ro.rentzlab.core.score.ScoreStandings;
import ro.rentzlab.app.ui.copy.RentzText;
import java.util.List;
import java.util.Optional;

/** Exact confirmed deltas and totals; seat order is never sorted by score or edited names. */
public final class RentzScoreContent {
    public static final String HAND_POINTS_MARK = "Δ:";
    public static final String TOTAL_MARK = "Σ:";
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
    public static List<Row> rows(List<RentzMatch.HandResult> history, RentzText copy) {
        return history.stream().map(hand -> row(hand, copy)).collect(immutable());
    }
    public static String legend(RentzText copy) {
        return copy.text("Δ: hand points · Σ: running total", "Δ: punctele mâinii · Σ: total după mână");
    }
    /** Presentation only: the confirmed values stay in seat order on both platforms. */
    public static List<List<String>> settlement(Row row, List<String> names, RentzText copy) {
        var content = settlementContent(row, names, copy);
        var table = new java.util.ArrayList<List<String>>();
        table.add(content.columns().stream().map(ScoreTableContent.Column::label).toList());
        content.rows().forEach(value -> table.add(value.cells().stream().map(ScoreTableContent.Cell::text).toList()));
        return List.copyOf(table);
    }
    public static ScoreTableContent settlementContent(Row row, List<String> names, RentzText copy) {
        if (names.size() < 3 || names.size() > 6 || row.values().size() != names.size())
            throw new IllegalArgumentException("Settlement needs one confirmed score per seat");
        var columns = List.of(copy.text("Player", "Jucător"), HAND_POINTS_MARK, TOTAL_MARK);
        var specs = java.util.stream.IntStream.range(0, 3).mapToObj(index ->
                new ScoreTableContent.Column(columns.get(index), RentzScoreLayout.SETTLEMENT_WEIGHTS.get(index), index > 0)).toList();
        var rows = new java.util.ArrayList<ScoreTableContent.Row>();
        for (int seat = 0; seat < names.size(); seat++) {
            var value = row.values().get(seat);
            var cells = List.of(names.get(seat), Integer.toString(value.delta()), Integer.toString(value.total())).stream()
                    .map(ScoreTableContent.Cell::new).toList();
            rows.add(new ScoreTableContent.Row("seat:" + seat, cells, seat == 0));
        }
        return new ScoreTableContent(specs, rows);
    }
    public static HistoryContent historyContent(List<Row> history, List<String> names, RentzText copy) {
        var players = java.util.stream.IntStream.range(0, names.size()).mapToObj(seat ->
                new HistoryContent.Player("seat:" + seat, names.get(seat))).toList();
        var rows = history.stream().map(row -> {
            var values = java.util.stream.IntStream.range(0, row.values().size()).mapToObj(seat -> {
                var value = row.values().get(seat);
                String prefix = names.get(seat) + ". " + copy.text("Hand ", "Mâna ") + row.hand() + ". ";
                return List.of(new ScoreTableContent.Cell(Integer.toString(value.delta()),
                                prefix + copy.points() + " " + value.delta(), false, false),
                        new ScoreTableContent.Cell(Integer.toString(value.total()),
                                prefix + copy.total() + " " + value.total(), false, true));
            }).toList();
            String label = row.hand() + " · " + row.label() + "\n" + names.get(row.chooser());
            return new HistoryContent.Row("hand:" + row.hand(), label, label, values);
        }).toList();
        return new HistoryContent(copy.text("Hand · game", "Mână · joc"), legend(copy),
                copy.text("Hand scoring details", "Detaliul punctajului mâinii"), players, List.of(HAND_POINTS_MARK, TOTAL_MARK), rows);
    }
    public static java.util.function.Consumer<String> detailAction(List<Row> history, java.util.function.Consumer<Row> open) {
        var snapshot = history.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(row -> "hand:" + row.hand(), row -> row));
        java.util.Objects.requireNonNull(open);
        return id -> {
            var row = snapshot.get(id);
            if (row == null) throw new IllegalArgumentException("Unknown confirmed hand: " + id);
            open.accept(row);
        };
    }
    public static Optional<Row> currentHand(RentzMatch.Observation view, List<RentzMatch.HandResult> history, RentzText copy) {
        if (view.phase() != RentzMatch.Phase.DEAL_END && view.phase() != RentzMatch.Phase.MATCH_END || history.isEmpty())
            return Optional.empty();
        var last = history.get(history.size() - 1);
        return last.number() == view.handNumber() ? Optional.of(row(last, copy)) : Optional.empty();
    }
    public static List<Standing> finalStandings(RentzMatch.Observation view) {
        if (view.phase() != RentzMatch.Phase.MATCH_END) return List.of();
        return finalStandings(view.totals());
    }
    public static List<Standing> finalStandings(List<Integer> totals) {
        return ScoreStandings.rank(totals).stream()
                .map(place -> new Standing(place.seat(), place.rank(), place.total())).collect(immutable());
    }
    public static String standingLabel(Standing standing, String playerName) {
        return ScoreStandings.label(new ScoreStandings.Place(standing.seat(), standing.place(), standing.total()), playerName).replace(" · Σ ", " · Σ: ");
    }
    public static Row row(RentzMatch.HandResult hand, RentzText copy) {
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
