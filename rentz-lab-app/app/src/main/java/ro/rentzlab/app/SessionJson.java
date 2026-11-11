package ro.rentzlab.app;

import ro.rentzlab.ai.*;
import ro.rentzlab.app.achievements.*;

import org.json.*;
import ro.rentzlab.app.profile.LocalProfile;
import ro.rentzlab.core.*;
import java.util.*;

/** Explicit Android/JVM JSON contract; public actor view and private replay data are different objects. */
public final class SessionJson {
    public record Checkpoint(GameSession session, AchievementProgress progress) { }
    private SessionJson() { }
    private static Object card(Card card)throws JSONException{return card==null?JSONObject.NULL:new JSONObject().put("suit",card.suit().name()).put("rank",card.rank());}
    private static Card card(JSONObject card)throws JSONException{
        SavedJson.fields(card,Set.of("suit","rank"),Set.of());
        return new Card(Card.Suit.valueOf(SavedJson.text(card,"suit")),SavedJson.intValue(card,"rank"));
    }
    private static JSONArray cards(List<Card> cards)throws JSONException{var array=new JSONArray();for(var card:cards)array.put(card(card));return array;}
    public static JSONObject actor(GameSession session,String screen,boolean busy)throws JSONException{
        var v=session.view();var trick=new JSONArray();for(var play:v.trick())trick.put(new JSONObject().put("seat",play.seat()).put("card",card(play.card())));
        var events=new JSONArray();for(var event:v.events())events.put(new JSONObject().put("sequence",event.sequence()).put("seat",event.seat()).put("action",event.action().name()).put("card",card(event.card())).put("boardCardCount",event.boardCardCount()));
        var available=new JSONArray();for(var contract:Contract.values())if(v.available().contains(contract))available.put(contract.name());
        var view=new JSONObject().put("seat",v.seat()).put("players",v.players()).put("turn",v.turn()).put("chooser",v.chooser()).put("handNumber",v.handNumber()).put("phase",v.phase().name()).put("contract",v.contract()==null?JSONObject.NULL:v.contract().name()).put("blind",v.blind()).put("revealed",v.revealed())
                .put("hand",cards(v.hand())).put("legal",cards(v.legal())).put("handSizes",new JSONArray(v.handSizes())).put("dealPoints",new JSONArray(v.dealPoints())).put("totals",new JSONArray(v.totals())).put("finishOrder",new JSONArray(v.finishOrder())).put("trick",trick).put("seen",cards(v.seen())).put("events",events).put("board",cards(v.board())).put("available",available);
        return new JSONObject().put("schema",1).put("screen",screen).put("busy",busy).put("revision",session.save().commands().size()).put("difficulty",session.difficulty().name())
                .put("opponents",new JSONArray(session.opponentNames())).put("view",view);
    }
    public static JSONObject saved(GameSession session)throws JSONException{
        var saved=session.save();var commands=new JSONArray();for(var command:saved.commands()){
            var row=new JSONObject().put("action",command.action().name()).put("seat",command.seat()).put("card",card(command.card())).put("contract",command.contract()==null?JSONObject.NULL:command.contract().name()).put("blind",command.blind());
            if(command.origin()!=GameSession.Origin.UNKNOWN)row.put("origin",command.origin().name());
            commands.put(row);
        }
        var json = new JSONObject().put("schema",saved.schema()).put("players",saved.players()).put("seed",saved.seed()).put("difficulty",saved.difficulty().name()).put("profile",saved.dealEnding().laboratoryProfileId()).put("policy","RENTZ_PUBLIC_LOOKAHEAD_V1").put("rules",saved.dealEnding().journalVersion()).put("commands",commands);
        if (!saved.opponents().equals(GameSession.defaultOpponents(saved.players(),saved.seed()))) json.put("opponents",new JSONArray(saved.opponents()));
        return json;
    }
    public static JSONObject saved(GameSession session, AchievementProgress progress) throws JSONException {
        var json = saved(session);
        if (progress == null) return json;
        var marks = new JSONObject();
        for (var entry : progress.marks().entrySet()) {
            var rows = new JSONArray();
            for (var mark : entry.getValue()) {
                rows.put(new JSONObject().put("key", mark.key()).put("at", mark.at().toString()));
            }
            marks.put(entry.getKey(), rows);
        }
        return json.put("achievements", new JSONObject().put("schema", 1)
                .put("profile", progress.profile().id().toString()).put("zone", progress.profile().zone().getId())
                .put("marks", marks));
    }
    public static GameSession restore(String text)throws JSONException{
        return restoreCheckpoint(text).session();
    }
    public static Checkpoint restoreCheckpoint(String text)throws JSONException{
        var json=SavedJson.read(text);
        SavedJson.fields(json,Set.of("schema","players","seed","difficulty","profile","policy","commands"),Set.of("rules","opponents","achievements"));
        var dealEnding=RentzMatch.DealEnding.fromJournal(json.has("rules")?SavedJson.text(json,"rules"):"RENTZ_RULES_V1");
        if(!SavedJson.text(json,"profile").equals(dealEnding.laboratoryProfileId())||!SavedJson.text(json,"policy").equals("RENTZ_PUBLIC_LOOKAHEAD_V1")){
            throw new IllegalArgumentException("Saved profile identity");
        }
        int players=SavedJson.intValue(json,"players");
        if(players<3||players>6)throw new IllegalArgumentException("Saved table size");
        var commands=new ArrayList<GameSession.Command>();
        var rows=json.getJSONArray("commands");
        for(int n=0;n<rows.length();n++)commands.add(command(rows.getJSONObject(n),players));
        var opponents=new ArrayList<String>();
        if(json.has("opponents")){
            var names=json.getJSONArray("opponents");
            if(names.length()!=players-1)throw new IllegalArgumentException("Saved opponents");
            for(int n=0;n<names.length();n++){
                if(!(names.get(n) instanceof String name))throw new IllegalArgumentException("Saved opponent name");
                opponents.add(name);
            }
        }
        var session = GameSession.restore(new GameSession.Saved(SavedJson.intValue(json,"schema"),players,SavedJson.integer(json,"seed"),
                RentzPlayer.Difficulty.valueOf(SavedJson.text(json,"difficulty")),commands,opponents,dealEnding));
        return new Checkpoint(session, json.has("achievements") ? progress(json.getJSONObject("achievements")) : null);
    }

    private static AchievementProgress progress(JSONObject json) throws JSONException {
        SavedJson.fields(json, Set.of("schema", "profile", "zone", "marks"), Set.of());
        if (SavedJson.intValue(json, "schema") != 1) throw new JSONException("Unknown achievements schema");
        var profile = new LocalProfile(UUID.fromString(SavedJson.text(json, "profile")),
                java.time.ZoneId.of(SavedJson.text(json, "zone")));
        var values = json.getJSONObject("marks");
        var marks = new LinkedHashMap<String, List<AchievementProgress.Mark>>();
        for (var badge : AchievementCatalog.all()) {
            if (!values.has(badge.id())) continue;
            var rows = values.getJSONArray(badge.id());
            if (rows.length() > badge.goal()) throw new JSONException("Achievement goal exceeded");
            var parsed = new ArrayList<AchievementProgress.Mark>();
            var keys = new HashSet<String>();
            java.time.LocalDate lastDay = null;
            for (int i = 0; i < rows.length(); i++) {
                var row = rows.getJSONObject(i);
                SavedJson.fields(row, Set.of("key", "at"), Set.of());
                String key = SavedJson.text(row, "key");
                var instant = java.time.Instant.parse(SavedJson.text(row, "at"));
                boolean validKey = switch (badge.unit()) {
                    case CONTRACT -> Arrays.stream(Contract.values()).anyMatch(c -> c.name().equals(key));
                    case TABLE -> key.matches("[3-6]");
                    case HAND -> key.matches("[3-6]:[0-9]{1,15}:[1-9][0-9]?");
                    case MATCH, DAY -> key.matches("[3-6]:[0-9]{1,15}");
                };
                if (!validKey || !keys.add(key)) throw new JSONException("Invalid or repeated achievement evidence");
                if (badge.unit() == AchievementCatalog.Unit.DAY) {
                    var day = instant.atZone(profile.zone()).toLocalDate();
                    if (lastDay != null && !day.isAfter(lastDay)) throw new JSONException("Repeated or reversed achievement day");
                    lastDay = day;
                }
                parsed.add(new AchievementProgress.Mark(key, instant));
            }
            marks.put(badge.id(), parsed);
        }
        if (marks.size() != values.length()) throw new JSONException("Unknown achievement identifier");
        return new AchievementProgress(profile, marks);
    }

    private static GameSession.Command command(JSONObject json,int players)throws JSONException{
        SavedJson.fields(json,Set.of("action","seat","card","contract","blind"),Set.of("origin"));
        var action=GameSession.Action.valueOf(SavedJson.text(json,"action"));
        int seat=SavedJson.intValue(json,"seat");
        if(seat<0||seat>=players)throw new IllegalArgumentException("Saved actor");
        var card=json.isNull("card")?null:card(json.getJSONObject("card"));
        var contract=json.isNull("contract")?null:Contract.valueOf(SavedJson.text(json,"contract"));
        boolean blind=SavedJson.bool(json,"blind");
        boolean valid=switch(action){
            case PLAY->card!=null&&contract==null&&!blind;
            case CHOOSE->card==null&&contract!=null;
            case REVEAL,PASS,NEXT_HAND->card==null&&contract==null&&!blind;
        };
        if(!valid)throw new IllegalArgumentException("Unexpected saved command payload");
        var origin = json.has("origin") ? GameSession.Origin.valueOf(SavedJson.text(json, "origin")) : GameSession.Origin.UNKNOWN;
        return new GameSession.Command(action,seat,card,contract,blind,origin);
    }
}
