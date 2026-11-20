package ro.rentzlab.app.ui.chrome;

import java.util.List;
import java.util.Map;
import ro.cardtable.ui.menu.TableMenuContent;
import ro.rentzlab.app.ui.copy.RentzText;

/** One localized native plan; platform adapters provide their own navigation callbacks. */
public final class RentzGameMenu {
    public record Actions(Runnable history, Runnable score, Runnable home, Runnable stop, Runnable name) { }

    public static List<TableMenuContent.Entry> entries(RentzText copy, boolean settled) {
        var entries = new java.util.ArrayList<TableMenuContent.Entry>();
        entries.add(new TableMenuContent.Entry("score-history", copy.text("Game history", "Istoricul jocului")));
        if (settled) entries.add(new TableMenuContent.Entry("score-sheet", copy.text("Results", "Rezultate")));
        entries.add(new TableMenuContent.Entry("home", copy.mainMenu()));
        entries.add(new TableMenuContent.Entry("stop-auto", copy.stopAutoPlayHand()));
        entries.add(new TableMenuContent.Entry("settings", copy.text("Name", "Nume")));
        return List.copyOf(entries);
    }

    public static List<TableMenuContent.Item> items(RentzText copy, boolean settled, boolean autoPlay, Actions actions) {
        var visible = entries(copy, settled).stream().filter(entry -> autoPlay || !entry.id().equals("stop-auto"))
                .collect(java.util.stream.Collectors.toList());
        return TableMenuContent.bind(visible, Map.of("score-history", actions.history(), "score-sheet", actions.score(),
                "home", actions.home(), "stop-auto", actions.stop(), "settings", actions.name()));
    }

    public static List<String> unavailable(RentzText copy, boolean settled) {
        var available = entries(copy, settled).stream().map(TableMenuContent.Entry::id).collect(java.util.stream.Collectors.toSet());
        return entries(copy, true).stream().map(TableMenuContent.Entry::id).filter(id -> !available.contains(id))
                .collect(ro.rentzlab.app.ui.collection.UiLists.immutable());
    }
    private RentzGameMenu() { }
}
