package ro.cardtable.browser;

import java.util.Map;
import org.json.JSONObject;

/** JVM-only adapter/DTO checks for the exact local page entry point; no DOM or emulator. */
public final class RentzPageComponentTest {
    private static int checks;

    private RentzPageComponentTest() { }

    public static void main(String[] args) {
        pageCommandStartsTheRealGameSession();
        rentzSequenceUsesTheSharedFreeBandWithoutMovingAnySeat();
        rentzCardPlaybackIsAnIncrementalPublicProjection();
        rentzLegalCardActionsRemainAvailableAfterAnAutomaticStep();
        rentzArrivalIsAOneShotPublicProjectionBeforeTheScore();
        historyKeepsWhistShortLabelsAndTheSettledScoreProjection();
        otherContractsDoNotEmitARentzArrival();
        System.out.println("Rentz Pages component checks passed: " + checks);
    }

    private static void rentzSequenceUsesTheSharedFreeBandWithoutMovingAnySeat() {
        for(int width:new int[]{280,320,374,414,600}) for(int height:new int[]{440,640,800}) {
            var input=Map.of("width",Integer.toString(width),"height",Integer.toString(height),
                "mode","RENTZ","seatHeight","66","bidOptions","0","players","4",
                "viewport","896","handWidth",Integer.toString(width),"cards","8");
            var common=BrowserGeometry.measure(new ro.rentzlab.browser.RentzBrowser(()->31L),input);
            var parameters=new java.util.HashMap<>(input);
            parameters.put("rentzSequence","true");
            var response=new JSONObject(RentzPage.commandLocal("/api/layout",new JSONObject(parameters).toString(),null,null,null,null));
            var actual=response.getJSONObject("result").getJSONObject("board");
            var viewport=new ro.cardtable.ui.table.TableViewportPolicy(896);
            var band=ro.cardtable.ui.table.TableGeometry.fullWidthCardBand(common.board(),viewport.regionGapDp());
            check("Rentz consumes the shared free band top without moving seats "+width+"/"+height,
                actual.getJSONObject("center").getInt("top")==band.top());
            check("Rentz consumes the shared free band bottom "+width+"/"+height,
                actual.getJSONObject("center").getInt("bottom")==band.bottom());
            for(int seat=0;seat<4;seat++) {
                var rectangle=actual.getJSONArray("seats").getJSONObject(seat);
                var expected=common.board().seats().get(seat);
                check("Whist seat anchors remain exact "+width+"/"+height+"/"+seat,
                    rectangle.getInt("left")==expected.left() && rectangle.getInt("top")==expected.top()
                    && rectangle.getInt("right")==expected.right() && rectangle.getInt("bottom")==expected.bottom());
            }
        }
    }

    private static void historyKeepsWhistShortLabelsAndTheSettledScoreProjection() {
        var checkpoint = startPage(20261118, "4", "HARD");
        JSONObject view = null;
        for (int step = 0; step < 96; step++) {
            var current = pageState(checkpoint);
            view = current.getJSONObject("view");
            if (view.optJSONObject("score") != null) break;
            String action = nextAction(view, "RENTZ");
            var reply = pageAction(checkpoint, current.getLong("revision"), action);
            checkpoint = reply.optString("checkpoint", checkpoint);
        }
        if (view == null || view.optJSONObject("score") == null) {
            throw new AssertionError("The four-seat Rentz fixture did not reach the settled score page.");
        }
        var grid = view.getJSONObject("history").getJSONObject("grid");
        check("Page DTO maps per-hand points to P", "P".equals(grid.optString("bidLabel")));
        check("Page DTO maps running totals to T", "T".equals(grid.optString("pointsLabel")));
        check("Page DTO carries the concise legend beneath the score table",
                "P: punctele mâinii · T: total după mână".equals(grid.optString("legend")));
        var settled = view.getJSONObject("scoreDetails").optString("awards");
        check("Score page uses the same P/T notation", settled.contains(" · P ") && settled.contains(" · T "));
        var row = grid.getJSONArray("rows").getJSONObject(0);
        var cells = row.getJSONArray("cells");
        var state = pageState(checkpoint).getJSONObject("view");
        var completed = state.getJSONObject("history").getJSONArray("rows").getJSONArray(0);
        var names = grid.getJSONArray("players");
        for (int seat = 0; seat < cells.length(); seat++) {
            var cell = cells.getJSONObject(seat);
            check("Page history keeps the confirmed running total in seat order " + seat,
                    cell.getString("total").equals(completed.getString(seat + 2)));
            String projection = names.getString(seat) + " · P " + cell.getString("bid") + " · T " + cell.getString("total");
            check("Score details preserve both confirmed values for seat " + seat, settled.contains(projection));
        }
    }

    private static void pageCommandStartsTheRealGameSession() {
        RentzPage.initialize(0, 20261116);
        var setupReply = new JSONObject(RentzPage.commandLocal("/api/setup", "{}", null, null, null, null));
        check("Page setup returns a successful renderer contract", !setupReply.has("status"));
        check("Page setup forwards the Android-aligned action cadence",
                setupReply.getJSONObject("result").optInt("autoDelayMillis") == 550);

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
            var terminalView = reply.getJSONObject("result").getJSONObject("view");
            check("The underlying response keeps its calculated score at " + players + " seats",
                    terminalView.optJSONObject("score") != null);
            var terminalPlayback = reply.optJSONArray("rentzPlayback");
            check("The completing action carries incremental card playback at " + players + " seats",
                    terminalPlayback != null && terminalPlayback.length() > 0);
            if (terminalPlayback != null && terminalPlayback.length() > 0) {
                var finalFrame = terminalPlayback.getJSONObject(terminalPlayback.length() - 1).getJSONObject("after");
                check("The last played card remains in the final playback snapshot at " + players + " seats",
                        playedInSequence(finalFrame.getJSONArray("rentzSequence"))
                                == playedInSequence(terminalView.getJSONArray("rentzSequence")));
            }

            var settled = pageState(checkpoint);
            check("A fresh state response has no transient arrival at " + players + " seats", !settled.has("rentzArrival"));
            check("A fresh state response exposes the settled score at " + players + " seats",
                    settled.getJSONObject("view").optJSONObject("score") != null);
        }
    }

    private static void rentzCardPlaybackIsAnIncrementalPublicProjection() {
        var checkpoint = startPage(20261123, "4", "HARD");
        var current = pageState(checkpoint);
        var reply = pageAction(checkpoint, current.getLong("revision"), "reveal");
        checkpoint = reply.optString("checkpoint", checkpoint);
        current = pageState(checkpoint);
        reply = pageAction(checkpoint, current.getLong("revision"), "choose:RENTZ");
        checkpoint = reply.optString("checkpoint", checkpoint);
        current = pageState(checkpoint);
        var hand = current.getJSONObject("view").getJSONArray("hand");
        var firstView = current.getJSONObject("view");
        var legalHandActions = legalHandActions(hand);
        var legalSequenceActions = legalSequenceActions(firstView.getJSONArray("rentzSequence"));
        check("Rentz marks every and only legal card as an actionable hand face",
                !legalHandActions.isEmpty() && legalHandActions.equals(legalSequenceActions));
        check("Rentz keeps the Joc automat action marker alongside the legal hand", hasAction(firstView, "auto-step"));
        String cardAction = "";
        String selectedLabel = "";
        for (int index = 0; index < hand.length(); index++) {
            var card = hand.getJSONObject(index);
            if (!card.optString("action").isBlank()) {
                cardAction = card.getString("action");
                selectedLabel = card.getString("label");
                break;
            }
        }
        check("A public legal card exists for the human Rentz turn", !cardAction.isBlank());

        reply = pageAction(checkpoint, current.getLong("revision"), cardAction);
        var frames = reply.optJSONArray("rentzPlayback");
        check("The action DTO carries the step-by-step Rentz playback", frames != null && frames.length() > 0);
        var first = frames.getJSONObject(0);
        check("The first playback event is the already-lifted human card",
                first.getInt("actorSeat") == 0 && first.getInt("delayMillis") == 0);
        var before = first.getJSONObject("before");
        var after = first.getJSONObject("after");
        check("The first Rentz card is added exactly once to the public sequence",
                playedInSequence(before.getJSONArray("rentzSequence")) == 0
                        && playedInSequence(after.getJSONArray("rentzSequence")) == 1);
        check("The same card is removed from the private hand in that rendered snapshot",
                handHasLabel(after.getJSONArray("hand"), selectedLabel) == false
                        && sequenceHasPlayedLabel(after.getJSONArray("rentzSequence"), selectedLabel));

        int expectedPlayed = 1;
        for (int index = 1; index < frames.length(); index++) {
            var frame = frames.getJSONObject(index);
            check("Automated card has the J4 first-frame delay", frame.getInt("delayMillis") == 550);
            var step = frame.getJSONObject("after");
            check("Every bot card advances one public sequence slot",
                    playedInSequence(step.getJSONArray("rentzSequence")) == ++expectedPlayed);
            String projection = frame.toString();
            check("Playback does not reveal opponent hands or private hand collections",
                    !projection.contains("opponentHands") && !projection.contains("\"hands\""));
        }
    }

    private static void rentzLegalCardActionsRemainAvailableAfterAnAutomaticStep() {
        var checkpoint = startPage(20261124, "4", "HARD");
        var current = pageState(checkpoint);
        var reply = pageAction(checkpoint, current.getLong("revision"), "reveal");
        checkpoint = reply.optString("checkpoint", checkpoint);
        current = pageState(checkpoint);
        reply = pageAction(checkpoint, current.getLong("revision"), "choose:RENTZ");
        checkpoint = reply.optString("checkpoint", checkpoint);
        current = pageState(checkpoint);
        var before = current.getJSONObject("view");
        check("Rentz exposes an automatic step before delegation", hasAction(before, "auto-step"));
        check("Rentz has a legal human card before autoplay", !legalHandActions(before.getJSONArray("hand")).isEmpty());

        reply = pageAction(checkpoint, current.getLong("revision"), "auto-step");
        checkpoint = reply.optString("checkpoint", checkpoint);
        var after = reply.getJSONObject("result").getJSONObject("view");
        check("Rentz returns to a playable human decision after autoplay advances the table",
                after.optJSONArray("rentzSequence") != null && !legalHandActions(after.getJSONArray("hand")).isEmpty());
        check("Rentz keeps the automatic action available at the next human decision", hasAction(after, "auto-step"));
        check("Rentz legal actions still match the sequence after autoplay advances the table",
                legalHandActions(after.getJSONArray("hand")).equals(legalSequenceActions(after.getJSONArray("rentzSequence"))));
        check("The automatic step preserves a resumable checkpoint", !checkpoint.isBlank());
    }

    private static java.util.List<String> legalHandActions(org.json.JSONArray hand) {
        var actions = new java.util.ArrayList<String>();
        for (int index = 0; index < hand.length(); index++) {
            String action = hand.getJSONObject(index).optString("action");
            if (!action.isBlank()) actions.add(action);
        }
        return actions.stream().sorted().toList();
    }

    private static java.util.List<String> legalSequenceActions(org.json.JSONArray sequence) {
        var actions = new java.util.ArrayList<String>();
        for (int row = 0; row < sequence.length(); row++) {
            var cells = sequence.getJSONObject(row).getJSONArray("cells");
            for (int cell = 0; cell < cells.length(); cell++) {
                String action = cells.getJSONObject(cell).optString("action");
                if (!action.isBlank()) actions.add(action);
            }
        }
        return actions.stream().sorted().toList();
    }

    private static boolean hasAction(JSONObject view, String key) {
        var actions = view.optJSONArray("actions");
        if (actions == null) return false;
        for (int index = 0; index < actions.length(); index++) {
            if (key.equals(actions.getJSONObject(index).optString("key"))) return true;
        }
        return false;
    }

    private static int playedInSequence(org.json.JSONArray sequence) {
        int count = 0;
        for (int row = 0; row < sequence.length(); row++) {
            var cells = sequence.getJSONObject(row).getJSONArray("cells");
            for (int cell = 0; cell < cells.length(); cell++) {
                if (cells.getJSONObject(cell).optBoolean("played")) count++;
            }
        }
        return count;
    }

    private static boolean sequenceHasPlayedLabel(org.json.JSONArray sequence, String label) {
        for (int row = 0; row < sequence.length(); row++) {
            var cells = sequence.getJSONObject(row).getJSONArray("cells");
            for (int cell = 0; cell < cells.length(); cell++) {
                var card = cells.getJSONObject(cell);
                if (card.optBoolean("played") && label.equals(card.optString("label"))) return true;
            }
        }
        return false;
    }

    private static boolean handHasLabel(org.json.JSONArray hand, String label) {
        for (int index = 0; index < hand.length(); index++) {
            if (label.equals(hand.getJSONObject(index).optString("label"))) return true;
        }
        return false;
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
