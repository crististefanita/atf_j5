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
            var menu = RentzGameMenu.items(copy, false, active, new RentzGameMenu.Actions(() -> calls.add("history"),
                    () -> calls.add("score"), () -> calls.add("home"), () -> calls.add("stop"), () -> calls.add("name")));
            audit.equal("Common history identity " + language, "score-history", menu.get(0).id());
            audit.equal("Localized history label " + language, copy.text("Game history", "Istoricul jocului"), menu.get(0).label());
            audit.equal("Constructing a popup emits no command", List.of(), calls);
            menu.get(0).action().run();
            audit.equal("History opens without visiting the score sheet", List.of("history"), calls);
            audit.equal("Stop appears only for active AutoPlay", active ? 1L : 0L,
                    menu.stream().filter(item -> item.id().equals("stop-auto")).count());
        }
    }

    @Test public void bothAdaptersUseTheSameFiveCapabilitiesAndLocalizedOrder() {
        for (var language : RentzUiText.Language.values()) for (boolean settled : new boolean[]{false, true})
            for (boolean automatic : new boolean[]{false, true}) {
                var copy = new RentzUiText(language); var calls = new ArrayList<String>();
                var expected = new ArrayList<String>(); expected.add("score-history"); if (settled) expected.add("score-sheet");
                expected.add("home"); if (automatic) expected.add("stop-auto"); expected.add("settings");
                var actions = new RentzGameMenu.Actions(() -> calls.add("score-history"), () -> calls.add("score-sheet"),
                        () -> calls.add("home"), () -> calls.add("stop-auto"), () -> calls.add("settings"));
                var actual = RentzGameMenu.items(copy, settled, automatic, actions);
                audit.equal("Independent route order " + language + settled + automatic, expected,
                        actual.stream().map(item -> item.id()).collect(java.util.stream.Collectors.toList()));
                audit.equal("Menu creation performs no navigation", List.of(), calls);
                actual.forEach(item -> item.action().run()); audit.equal("Each route invokes its own platform callback", expected, calls);
                var projected = RentzGameMenu.entries(copy, settled).stream()
                        .filter(entry -> automatic || !entry.id().equals("stop-auto")).collect(java.util.stream.Collectors.toList());
                audit.equal("Native and browser identities agree", expected, projected.stream().map(entry -> entry.id()).collect(java.util.stream.Collectors.toList()));
                audit.equal("Native and browser labels agree", actual.stream().map(item -> item.label()).collect(java.util.stream.Collectors.toList()),
                        projected.stream().map(entry -> entry.label()).collect(java.util.stream.Collectors.toList()));
                audit.equal("Profile editing is reachable in game", copy.text("Name", "Nume"), actual.get(actual.size()-1).label());
            }
    }
}
