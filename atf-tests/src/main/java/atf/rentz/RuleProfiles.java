package atf.rentz;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import ro.rentzlab.core.ScoreProfile;

/** Typed scoring input. The separate traceability manifest is not executed as strategy. */
public final class RuleProfiles {
    private RuleProfiles() { }
    public static ScoreProfile read(Path file) throws IOException {
        JsonNode root=YamlDocuments.read(file);
        fields(root,Set.of("schemaVersion","profile","status","referencePresetParity","penaltyMagnitudes","rewards"));
        if(integer(root,"schemaVersion")!=1||!root.path("profile").asText().equals("laboratory")
                ||!root.path("status").asText().equals("LABORATORY_ONLY")
                ||!root.path("referencePresetParity").asText().equals("UNCONFIRMED"))throw new IllegalArgumentException("Unqualified score-profile identity");
        var penalties=root.required("penaltyMagnitudes");var rewards=root.required("rewards");
        fields(penalties, Set.of("trick", "queen", "diamond", "kingHearts"));
        fields(rewards, rewards.has("finishingPlacesByPlayers")
                ? Set.of("trick", "tenClubs", "finishingPlaces", "finishingPlacesByPlayers")
                : Set.of("trick", "tenClubs", "finishingPlaces"));
        var byPlayers = new HashMap<Integer, List<Integer>>();
        if (rewards.has("finishingPlacesByPlayers")) {
            var rows = rewards.required("finishingPlacesByPlayers");
            fields(rows, Set.of("3", "4", "5", "6"));
            for (int players = 3; players <= 6; players++) {
                byPlayers.put(players, awards(rows.required(Integer.toString(players)), players));
            }
        }
        return new ScoreProfile(integer(penalties, "trick"), integer(penalties, "queen"), integer(penalties, "diamond"),
                integer(penalties, "kingHearts"), integer(rewards, "trick"), integer(rewards, "tenClubs"),
                awards(rewards.required("finishingPlaces"), 6), byPlayers);
    }
    private static List<Integer> awards(JsonNode row, int count) {
        if (!row.isArray() || row.size() != count) throw new IllegalArgumentException("Expected " + count + " finishing awards");
        var result = new ArrayList<Integer>();
        for (var value : row) {
            if (!value.isIntegralNumber() || !value.canConvertToInt())
                throw new IllegalArgumentException("Integer finishing reward required");
            result.add(value.intValue());
        }
        return result;
    }
    private static int integer(JsonNode node,String field){var value=node.required(field);if(!value.isIntegralNumber()||!value.canConvertToInt())throw new IllegalArgumentException("Integer required: "+field);return value.intValue();}
    private static void fields(JsonNode node,Set<String> expected){if(node==null||!node.isObject())throw new IllegalArgumentException("Object required");var actual=new HashSet<String>();node.fieldNames().forEachRemaining(actual::add);if(!actual.equals(expected))throw new IllegalArgumentException("Unexpected or missing profile fields: "+actual);}
}
