package ro.rentzlab.app.ui.chrome;

import java.util.ArrayList;
import java.util.List;
import ro.cardtable.ui.menu.TableMenuContent;
import ro.rentzlab.app.ui.copy.RentzText;

/** Rentz routes projected into the common table menu; the activity owns lifecycle. */
public final class RentzGameMenu {
    public static List<TableMenuContent.Item> items(RentzText copy, boolean settled, boolean autoPlay,
            Runnable history, Runnable score, Runnable stop, Runnable home) {
        var others = new ArrayList<TableMenuContent.Item>();
        if (settled) others.add(new TableMenuContent.Item("score-sheet",
                copy.text("Hand score", "Punctajul mâinii"), score));
        if (autoPlay) others.add(new TableMenuContent.Item("stop-auto",
                copy.stopAutoPlayHand(), stop));
        others.add(new TableMenuContent.Item("home", copy.mainMenu(), home));
        return TableMenuContent.historyFirst(copy.history(), history, others);
    }
    private RentzGameMenu() { }

    /** Exports native route availability; browser-owned settings and ranking routes stay intact. */
    public static List<String> unavailable(RentzText copy, boolean settled) {
        Runnable unused = () -> { };
        var available = items(copy, settled, true, unused, unused, unused, unused).stream()
                .map(TableMenuContent.Item::id).collect(java.util.stream.Collectors.toSet());
        return items(copy, true, true, unused, unused, unused, unused).stream()
                .map(TableMenuContent.Item::id).filter(id -> !available.contains(id))
                .collect(ro.rentzlab.app.ui.collection.UiLists.immutable());
    }
}
