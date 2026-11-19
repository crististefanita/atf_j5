package ro.rentzlab.app.ui.chrome;

import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;
import ro.rentzlab.app.ui.copy.RentzUiText;

@AndroidComponentLayer
public final class RentzGameMenuComponentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void browserAvailabilityUsesTheNativeConfirmedHandGate() {
        for (var language : RentzUiText.Language.values()) {
            var copy = new RentzUiText(language);
            audit.equal("A live hand has no confirmed score route " + language, List.of("score-sheet"), RentzGameMenu.unavailable(copy, false));
            audit.equal("A settled hand keeps all native routes " + language, List.of(), RentzGameMenu.unavailable(copy, true));
            audit.rejects("Projected availability is immutable", UnsupportedOperationException.class,
                    () -> RentzGameMenu.unavailable(copy, false).clear());
        }
    }
    @Test public void historyIsDirectAndStopRemainsConditionalInBothLanguages() {
        for (var language : RentzUiText.Language.values()) for (boolean active : new boolean[]{false, true}) {
            var copy = new RentzUiText(language);
            var calls = new ArrayList<String>();
            var menu = RentzGameMenu.items(copy, false, active, () -> calls.add("history"),
                    () -> calls.add("score"), () -> calls.add("stop"), () -> calls.add("home"));
            audit.equal("Common history identity " + language, "score-history", menu.get(0).id());
            audit.equal("Localized history label " + language, copy.history(), menu.get(0).label());
            audit.equal("Constructing a popup emits no command", List.of(), calls);
            menu.get(0).action().run();
            audit.equal("History opens without visiting the score sheet", List.of("history"), calls);
            audit.equal("Stop appears only for active AutoPlay", active ? 1L : 0L,
                    menu.stream().filter(item -> item.id().equals("stop-auto")).count());
        }
    }
}
