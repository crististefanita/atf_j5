package ro.cardtable.browser;

import org.json.JSONObject;
import org.teavm.jso.JSExport;
import ro.cardtable.ui.animation.TrickCollectionMotion;
import ro.rentzlab.browser.RentzBrowser;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Static Pages entry point. Only localStorage and this worker connect the shared browser shell to Rentz. */
public final class RentzPage {
    private static final Value name = new Value();
    private static final Value progress = new Value();
    private static final Value rankings = new Value();
    private static String checkpoint;
    private static RentzBrowser game;
    private static BrowserSession session;

    private RentzPage() { }

    @JSExport
    public static void initialize(int highSeed, int lowSeed) {
        long seed = ((long) highSeed << 32) | Integer.toUnsignedLong(lowSeed);
        var random = new Random(seed);
        game = new RentzBrowser(random::nextLong);
        session = new BrowserSession(RentzBrowser.localProfile(name, progress, rankings));
        checkpoint = null;
    }

    @JSExport
    public static String commandLocal(String path, String body, String savedName, String savedProgress,
            String savedCheckpoint, String savedRankings) {
        return execute(path, body, savedName, savedProgress, savedCheckpoint, savedRankings);
    }

    private static String execute(String path, String body, String savedName, String savedProgress,
            String savedCheckpoint, String savedRankings) {
        name.value = savedName;
        progress.value = savedProgress;
        rankings.value = savedRankings;
        boolean gameCommand = !path.equals("/api/setup") && !path.equals("/api/layout");
        boolean rejected = gameCommand && synchronize(savedCheckpoint);
        try {
            var input = new JSONObject(body);
            long revision = input.optLong("revision", -1);
            Object result = switch (path) {
                case "/api/setup" -> game.setup(TrickCollectionMotion.keyframes());
                case "/api/state" -> session.state();
                case "/api/rankings" -> session.rankings();
                case "/api/layout" -> BrowserGeometry.measure(game, parameters(input));
                case "/api/start" -> session.start(game, revision, parameters(input));
                case "/api/action" -> session.act(revision, input.optString("action"));
                case "/api/hand-feedback" -> session.handFeedback(revision, parameters(input));
                case "/api/profile" -> session.rename(revision, input.optString("name"));
                case "/api/language" -> session.language(revision, input.optString("language"));
                case "/api/notice" -> session.dismissNotice(revision);
                default -> throw new IllegalArgumentException("Unsupported operation");
            };
            if (path.equals("/api/start") || path.equals("/api/action") || path.equals("/api/profile")) {
                var match = session.match();
                if (match != null) checkpoint = game.checkpoint(match);
            }
            return PageJson.encode(PageJson.fields("result", result, "name", name.value,
                    "progress", progress.value, "rankings", rankings.value,
                    "checkpoint", gameCommand ? checkpoint : savedCheckpoint,
                    "rejectedCheckpoint", rejected));
        } catch (BrowserSession.StaleRevision stale) {
            return PageJson.encode(PageJson.fields("status", 409, "error", "Masa a avansat. Am reîncărcat starea."));
        } catch (IllegalArgumentException invalid) {
            return PageJson.encode(PageJson.fields("status", 400, "error", invalid.getMessage()));
        } catch (RuntimeException failure) {
            if (gameCommand) session.restore(null);
            return PageJson.encode(PageJson.fields("status", 500, "error", "Motorul Rentz nu a putut procesa comanda."));
        }
    }

    private static boolean synchronize(String saved) {
        if (Objects.equals(checkpoint, saved)) return false;
        if (saved == null || saved.isBlank()) {
            session.restore(null);
            checkpoint = null;
            return false;
        }
        try {
            session.restore(game.restore(saved, name.value == null ? "Tu" : name.value));
            checkpoint = saved;
            return false;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            session.restore(null);
            checkpoint = null;
            return true;
        }
    }

    static java.util.List<Map<String, Object>> sequence() {
        return session == null ? java.util.List.of() : RentzBrowser.sequence(session.match());
    }

    private static HashMap<String, String> parameters(JSONObject input) {
        var values = new HashMap<String, String>();
        for (String key : input.keySet()) values.put(key, input.getString(key));
        return values;
    }

    private static final class Value implements ro.rentzlab.browser.RentzBrowserProfile.Store {
        private String value;
        @Override public String read() { return value; }
        @Override public void write(String next) { value = next; }
    }
}
