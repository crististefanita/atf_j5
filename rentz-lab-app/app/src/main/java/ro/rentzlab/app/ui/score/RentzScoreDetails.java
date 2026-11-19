package ro.rentzlab.app.ui.score;

import java.util.List;
import ro.rentzlab.app.ui.copy.RentzText;
import ro.rentzlab.core.Contract;

/** Product presenter. Android widgets and Browser DTOs adapt the same immutable detail lines. */
public final class RentzScoreDetails {
    public record Player(int seat, String heading, String points, List<String> contributions) {
        public Player { contributions = List.copyOf(contributions); }
    }
    private final RentzText copy;
    public RentzScoreDetails(RentzText copy) { this.copy = java.util.Objects.requireNonNull(copy); }
    public List<Player> present(RentzScoreContent.Row row, List<String> names) {
        if (row.values().size() != names.size()) throw new IllegalArgumentException("Every score requires one seat name");
        var labels = List.copyOf(names);
        return RentzScoreContent.details(row).stream().map(player -> new Player(player.seat(),
                (player.place() == 0 ? "" : player.place() + ". ") + labels.get(player.seat()),
                RentzScoreContent.HAND_POINTS_MARK + " " + player.score().delta() + " · "
                        + RentzScoreContent.TOTAL_MARK + " " + player.score().total(),
                player.contributions().stream().map(contribution -> {
                    String label = contribution.category() == Contract.RENTZ
                            ? copy.text("Finishing award", "Premiu de sosire")
                            : copy.contract(contribution.category()) + " × " + contribution.count();
                    return label + " · " + contribution.points();
                }).toList())).toList();
    }
}
