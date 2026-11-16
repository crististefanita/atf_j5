package ro.cardtable.browser;

import java.util.Map;
import org.json.JSONObject;

/** JVM-only adapter/DTO checks for the exact local page entry point; no DOM or emulator. */
public final class RentzPageComponentTest {
    private static int checks;

    private RentzPageComponentTest() { }

    public static void main(String[] args) {
        pageCommandStartsTheRealGameSession();
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

    private static void check(String description, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
}
