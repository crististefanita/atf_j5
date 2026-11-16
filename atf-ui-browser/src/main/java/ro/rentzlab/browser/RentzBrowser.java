package ro.rentzlab.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import ro.cardtable.browser.BrowserGame;
import ro.cardtable.browser.BrowserServer;
import ro.cardtable.names.PlayerNames;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;

public final class RentzBrowser implements BrowserGame {
    private final LongSupplier seeds;
    public RentzBrowser(LongSupplier seeds) { this.seeds = seeds; }

    public static void main(String[] args) throws Exception {
        BrowserServer.launch(new RentzBrowser(RandomGenerator.getDefault()::nextLong), args);
    }

    @Override public String title() { return "Rentz Lab"; }

    @Override public List<Field> fields() {
        var seats = new ArrayList<Option>();
        for (int count = 3; count <= 6; count++) seats.add(new Option("" + count, "" + count));
        return List.of(new Field("name", "Numele tău", "Tu", List.of()),
                new Field("players", "Jucători", "4", seats),
                new Field("difficulty", "Nivelul adversarilor", "NORMAL",
                        List.of(new Option("NORMAL", "Normal"), new Option("HARD", "Greu"))));
    }

    @Override public Match start(Map<String, String> config) {
        String name = PlayerNames.normalize(config.get("name"));
        var session = new GameSession(Integer.parseInt(config.get("players")), seeds.getAsLong(),
                RentzPlayer.Difficulty.valueOf(config.get("difficulty")), name);
        return new RentzTable(session, name);
    }
}
