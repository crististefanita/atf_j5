package ro.rentzlab.app;

import ro.rentzlab.app.testing.AndroidComponentLayer;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.ai.RentzPlayer;

import java.util.List;

import static org.junit.Assert.*;

@AndroidComponentLayer
public class SessionJsonValidationTest {
    private static String saved(long seed) throws Exception {
        return SessionJson.saved(new GameSession(4, seed, RentzPlayer.Difficulty.NORMAL)).toString();
    }

    private static void refused(String input) throws Exception {
        try {
            SessionJson.restore(input);
            fail("Invalid saved input was accepted: " + input);
        } catch (JSONException | IllegalArgumentException expected) {
            // Only parse/domain refusals count; unrelated failures must escape the test.
        }
    }

    @Test public void integerFieldsRejectFractionsStringsBooleansNullAndOverflow() throws Exception {
        String original = saved(31);
        for (String field : List.of("schema", "players", "seed")) {
            for (Object invalid : List.of(1.5, "1", true, JSONObject.NULL)) {
                var json = new JSONObject(original).put(field, invalid);
                refused(json.toString());
            }
        }
        refused(original.replace("\"seed\":31", "\"seed\":9223372036854775808"));
        refused(original.replace("\"players\":4", "\"players\":4294967300"));
        refused(original.replace("\"seed\":31", "\"seed\":31.0"));
        refused(original.replace("\"seed\":31", "\"seed\":3.1e1"));
    }

    @Test public void oneStrictDocumentRejectsTrailingTextDuplicatesAndLenientSyntax() throws Exception {
        String original = saved(31);
        for (String suffix : List.of(" trailing", " {}", " null", " // comment")) refused(original + suffix);
        refused(original.replace("\"seed\":31", "\"seed\":31,\"seed\":31"));
        refused(original.replace("\"schema\":1", "\"schema\":1,\"sch\\u0065ma\":1"));
        refused(original.replace("\"seed\":31", "\"seed\":+31"));
        refused(original.replace("\"seed\":31", "\"seed\":031"));
        refused(original.replace('"', '\''));
        refused("/* comment */" + original);
        refused(original.substring(0, original.length() - 1) + ",}");
    }

    @Test public void shapeAndIdentityTypesAreExactButMissingLegacyRulesRemainSupported() throws Exception {
        String original = saved(31);
        for (String required : List.of("schema", "players", "seed", "difficulty", "profile", "policy", "commands")) {
            var json = new JSONObject(original); json.remove(required); refused(json.toString());
        }
        refused(new JSONObject(original).put("unknown", 1).toString());
        refused(new JSONObject(original).put("rules", JSONObject.NULL).toString());
        refused(new JSONObject(original).put("commands", new JSONObject()).toString());
        var withoutRules = new JSONObject(original);
        withoutRules.remove("rules");
        refused(withoutRules.toString());

        var historical = GameSession.restore(new GameSession.Saved(1, 4, 31, RentzPlayer.Difficulty.NORMAL,
                List.of(), List.of(), RentzMatch.DealEnding.LEGACY));
        var legacy = SessionJson.saved(historical);
        assertEquals("LABORATORY_V1", legacy.getString("profile"));
        legacy.remove("rules");
        assertEquals(historical.view(), SessionJson.restore(legacy.toString()).view());
    }

    @Test public void commandFieldsAreTypedAndIrrelevantPayloadIsRefused() throws Exception {
        var session = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
        session.apply(new GameSession.Command(GameSession.Action.REVEAL, 0, null, null, false));
        session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.WHIST, false));
        session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false));
        String original = SessionJson.saved(session).toString();
        for (Object invalid : List.of(0.5, "0", true, JSONObject.NULL, 4294967296L)) {
            var json = new JSONObject(original); json.getJSONArray("commands").getJSONObject(0).put("seat", invalid);
            refused(json.toString());
        }
        var booleanString = new JSONObject(original); booleanString.getJSONArray("commands").getJSONObject(0).put("blind", "false"); refused(booleanString.toString());
        var missing = new JSONObject(original); missing.getJSONArray("commands").getJSONObject(0).remove("card"); refused(missing.toString());
        var extra = new JSONObject(original); extra.getJSONArray("commands").getJSONObject(0).put("unknown", 1); refused(extra.toString());
        var irrelevant = new JSONObject(original); irrelevant.getJSONArray("commands").getJSONObject(0).put("contract", "WHIST"); refused(irrelevant.toString());
        var rank = new JSONObject(original); rank.getJSONArray("commands").getJSONObject(2).getJSONObject("card").put("rank", session.save().commands().get(2).card().rank() + 0.5); refused(rank.toString());
        var duplicate = original.replace("\"seat\":0", "\"seat\":0,\"seat\":0"); refused(duplicate);
        assertEquals(session.view(), SessionJson.restore(original).view());
    }

    @Test public void exactLongSeedsCanonicalCommandsAndWhitespaceRoundTrip() throws Exception {
        for (long seed : new long[]{Long.MIN_VALUE, -1, 0, Long.MAX_VALUE}) {
            String original = saved(seed);
            var restored = SessionJson.restore(" \n\t" + original + "\r\n");
            assertEquals(seed, restored.save().seed());
            assertEquals(original, SessionJson.saved(restored).toString());
        }
    }
}
