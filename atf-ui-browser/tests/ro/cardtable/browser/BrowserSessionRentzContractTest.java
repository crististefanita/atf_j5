package ro.cardtable.browser;

import java.util.LinkedHashMap;
import java.util.Map;
import ro.rentzlab.browser.RentzBrowser;

/** Exercises the exact shared J4 session validator with the J5 Rentz setup form projection. */
public final class BrowserSessionRentzContractTest {
    private BrowserSessionRentzContractTest() { }

    public static int run() {
        var game = new RentzBrowser(() -> 20261116L);
        var values = new LinkedHashMap<String, String>();
        for (var field : game.fields("ro")) values.put(field.key(), field.initial());

        // The shared setup presents only +5/+10, although the engine field includes 0=off.
        var series = game.fields("ro").stream().filter(field -> field.key().equals("series")).findFirst().orElseThrow();
        var visibleSeries = series.options().stream().filter(option -> !option.value().equals("0")).toList();
        if (visibleSeries.stream().noneMatch(option -> option.value().equals(values.get("series")))) {
            values.put("series", "");
        }
        int checks = 0;
        for (int players = 3; players <= 6; players++) {
            for (String difficulty : java.util.List.of("NORMAL", "HARD")) {
                var configuration = new LinkedHashMap<>(values);
                configuration.put("players", Integer.toString(players));
                configuration.put("difficulty", difficulty);
                var session = new BrowserSession(game.openProfile("component-test-" + players + "-" + difficulty));
                var state = session.start(game, 0, Map.copyOf(configuration));
                if (state.view() == null || state.view().seats().size() != players) {
                    throw new AssertionError("Shared session must start " + players + "-player Rentz in " + difficulty);
                }
                checks++;
            }
        }
        return checks;
    }
}
