package ro.cardtable.browser;

import java.util.Map;
import org.json.JSONObject;

/** JVM-only adapter/DTO checks for the exact local page entry point; no DOM or emulator. */
public final class RentzPageComponentTest {
    private static int checks;

    private RentzPageComponentTest() { }

    public static void main(String[] args) {
        pageCommandStartsTheRealGameSession();
        rentzArrivalIsAOneShotPublicProjectionBeforeTheScore();
        otherContractsDoNotEmitARentzArrival();
        System.out.println("Rentz Pages component checks passed: " + checks);
    }

    private static void pageCommandStartsTheRealGameSession() {
        RentzPage.initialize(0, 20261116);
        var setupReply = new JSONObject(RentzPage.commandLocal("/api/setup", "{}", null, null, null, null));
        check("Page setup returns a successful renderer contract", !setupReply.has("status"));

        var values = Map.of("name", "Ana", "players", "3", "mode", "RENTZ", "difficulty", "HARD",
                "series", "5", "streaks", "no", "zero", "no");
        var directGame = new ro.rentzlab.browser.RentzBrowser(() -> 20261116L);
        var directSession = new BrowserSession(directGame.openProfile("page-component"));
        var directState = directSession.start(directGame, 0, values);
        var directProjection = new JSONObject(PageJson.encode(directState));
        check("Page DTO serializes the common-session state", directProjection.has("view"));
        check("Serialized public view keeps three seats", directProjection.getJSONObject("view").getJSONArray("seats").length() == 3);

        var configValues = new java.util.LinkedHashMap<String, Object>(values);
        configValues.put("revision", "0");
        var config = new JSONObject(configValues);
        var startReply = new JSONObject(RentzPage.commandLocal("/api/start", config.toString(), null, null, null, null));
        if (startReply.has("status")) throw new AssertionError("Page start error response: " + startReply);
        check("Page start does not return a command error", true);
        var state = startReply.getJSONObject("result");
        var view = state.getJSONObject("view");
        check("Page starts three seats through the native session", view.getJSONArray("seats").length() == 3);
        check("Page keeps the initial human hand hidden", view.getJSONArray("hand").isEmpty());
        check("Page uses the legal-command version from the common session", state.getLong("revision") == 1L);
        check("Page checkpoint exists for browser resume", !startReply.optString("checkpoint").isBlank());
        String publicView = view.toString();
        check("Public view does not expose opponent hand collections", !publicView.contains("opponentHands") && !publicView.contains("hands"));
        check("Public view does not expose the private command journal", !publicView.contains("commands"));
    }

    private static void rentzArrivalIsAOneShotPublicProjectionBeforeTheScore() {
        for (int players : new int[]{3, 4, 5, 6}) {
            var checkpoint = startPage(20261117 + players, Integer.toString(players), "HARD");
            var reply = new JSONObject();
            for (int step = 0; step < 96; step++) {
                var current = pageState(checkpoint);
                var view = current.getJSONObject("view");
                if (view.optJSONObject("score") != null) break;
                String action = nextAction(view, "RENTZ");
                reply = pageAction(checkpoint, current.getLong("revision"), action);
                checkpoint = reply.optString("checkpoint", checkpoint);
                if (reply.has("rentzArrival")
                        || reply.getJSONObject("result").getJSONObject("view").optJSONObject("score") != null) break;
            }

            check("A completed Rentz hand emits an arrival projection at " + players + " seats", reply.has("rentzArrival"));
            var arrival = reply.getJSONObject("rentzArrival");
            check("Arrival identifies the first finisher at " + players + " seats", !arrival.optString("winner").isBlank());
            check("Arrival reports public unplayed cards at " + players + " seats", arrival.optInt("unplayedCards", -1) > 0);
            check("Arrival keeps the native 1.8 s duration at " + players + " seats", arrival.optLong("durationMillis") == 1800L);
            check("Arrival carries prior public totals for every seat at " + players + " seats",
                    arrival.getJSONArray("previousTotals").length() == players);
            check("The underlying response keeps its calculated score at " + players + " seats",
                    reply.getJSONObject("result").getJSONObject("view").optJSONObject("score") != null);

            var settled = pageState(checkpoint);
            check("A fresh state response has no transient arrival at " + players + " seats", !settled.has("rentzArrival"));
            check("A fresh state response exposes the settled score at " + players + " seats",
                    settled.getJSONObject("view").optJSONObject("score") != null);
        }
    }

    private static void otherContractsDoNotEmitARentzArrival() {
        var checkpoint = startPage(20261118, "3", "NORMAL");
        var reply = new JSONObject();
        for (int step = 0; step < 128; step++) {
            var current = pageState(checkpoint);
            var view = current.getJSONObject("view");
            String action = nextAction(view, "TRICKS");
            reply = pageAction(checkpoint, current.getLong("revision"), action);
            checkpoint = reply.optString("checkpoint", checkpoint);
            if (reply.getJSONObject("result").getJSONObject("view").optJSONObject("score") != null) break;
        }
        check("Non-Rentz scoring never emits the Rentz arrival adapter", !reply.has("rentzArrival"));
        check("The non-Rentz comparison reaches its score boundary", reply.getJSONObject("result")
                .getJSONObject("view").optJSONObject("score") != null);
    }

    private static String startPage(int seed, String players, String difficulty) {
        RentzPage.initialize(0, seed);
        var values = new JSONObject(Map.of("name", "Test", "players", players, "mode", "RENTZ",
                "difficulty", difficulty, "series", "0", "streaks", "no", "zero", "no", "revision", "0"));
        var started = new JSONObject(RentzPage.commandLocal("/api/start", values.toString(), "Test", null, null, null));
        if (started.has("status")) throw new AssertionError("Page start error response: " + started);
        return started.optString("checkpoint");
    }

    private static JSONObject pageState(String checkpoint) {
        return new JSONObject(RentzPage.commandLocal("/api/state", "{}", "Test", null, checkpoint, null))
                .getJSONObject("result");
    }

    private static JSONObject pageAction(String checkpoint, long revision, String action) {
        var values = new JSONObject(Map.of("revision", Long.toString(revision), "action", action));
        var reply = new JSONObject(RentzPage.commandLocal("/api/action", values.toString(), "Test", null, checkpoint, null));
        if (reply.has("status")) throw new AssertionError("Page action error response: " + reply);
        return reply;
    }

    private static String nextAction(JSONObject view, String contract) {
        var actions = view.getJSONArray("actions");
        for (int index = 0; index < actions.length(); index++) {
            String key = actions.getJSONObject(index).getString("key");
            if (key.equals("reveal")) return key;
            if (key.equals("choose:" + contract) || key.equals("blind:" + contract)) return key;
        }
        var hand = view.getJSONArray("hand");
        for (int index = 0; index < hand.length(); index++) {
            String action = hand.getJSONObject(index).optString("action");
            if (!action.isBlank()) return action;
        }
        var sequence = view.optJSONArray("rentzSequence");
        if (sequence != null) for (int row = 0; row < sequence.length(); row++) {
            var cells = sequence.getJSONObject(row).getJSONArray("cells");
            for (int cell = 0; cell < cells.length(); cell++) {
                String action = cells.getJSONObject(cell).optString("action");
                if (!action.isBlank()) return action;
            }
        }
        for (int index = 0; index < actions.length(); index++) {
            String key = actions.getJSONObject(index).getString("key");
            if (key.equals("auto-step") || key.equals("next")) return key;
        }
        throw new AssertionError("No legal page action in view: " + view);
    }

    private static void check(String description, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
}
