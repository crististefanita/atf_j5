package ro.rentzlab.app.ui.screens;

import java.util.ArrayList;
import java.util.List;
import ro.cardtable.ui.menu.TableMenuContent;
import ro.rentzlab.app.ui.copy.RentzUiText;

/** Rentz routes projected into the common table menu; the activity owns lifecycle. */
public final class RentzGameMenu {
    public static List<TableMenuContent.Item> items(RentzUiText copy, boolean settled, boolean autoPlay,
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
}
