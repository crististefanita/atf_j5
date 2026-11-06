package ro.rentzlab.app;

import org.json.*;
import ro.rentzlab.core.*;
import java.util.*;

/** Explicit Android/JVM JSON contract; public actor view and private replay data are different objects. */
public final class SessionJson {
    private SessionJson() { }
    private static Object card(Card card)throws JSONException{return card==null?JSONObject.NULL:new JSONObject().put("suit",card.suit().name()).put("rank",card.rank());}
    private static Card card(JSONObject card)throws JSONException{return new Card(Card.Suit.valueOf(card.getString("suit")),card.getInt("rank"));}
    private static JSONArray cards(List<Card> cards)throws JSONException{var array=new JSONArray();for(var card:cards)array.put(card(card));return array;}
    public static JSONObject actor(GameSession session,String screen,boolean busy)throws JSONException{
        var v=session.view();var trick=new JSONArray();for(var play:v.trick())trick.put(new JSONObject().put("seat",play.seat()).put("card",card(play.card())));
        var events=new JSONArray();for(var event:v.events())events.put(new JSONObject().put("sequence",event.sequence()).put("seat",event.seat()).put("action",event.action().name()).put("card",card(event.card())).put("boardCardCount",event.boardCardCount()));
        var available=new JSONArray();for(var contract:Contract.values())if(v.available().contains(contract))available.put(contract.name());
        var view=new JSONObject().put("seat",v.seat()).put("players",v.players()).put("turn",v.turn()).put("chooser",v.chooser()).put("handNumber",v.handNumber()).put("phase",v.phase().name()).put("contract",v.contract()==null?JSONObject.NULL:v.contract().name()).put("blind",v.blind()).put("revealed",v.revealed())
                .put("hand",cards(v.hand())).put("legal",cards(v.legal())).put("handSizes",new JSONArray(v.handSizes())).put("dealPoints",new JSONArray(v.dealPoints())).put("totals",new JSONArray(v.totals())).put("finishOrder",new JSONArray(v.finishOrder())).put("trick",trick).put("seen",cards(v.seen())).put("events",events).put("board",cards(v.board())).put("available",available);
        return new JSONObject().put("schema",1).put("screen",screen).put("busy",busy).put("revision",session.save().commands().size()).put("difficulty",session.difficulty().name()).put("view",view);
    }
    public static JSONObject saved(GameSession session)throws JSONException{
        var saved=session.save();var commands=new JSONArray();for(var command:saved.commands())commands.put(new JSONObject().put("action",command.action().name()).put("seat",command.seat()).put("card",card(command.card())).put("contract",command.contract()==null?JSONObject.NULL:command.contract().name()).put("blind",command.blind()));
        return new JSONObject().put("schema",saved.schema()).put("players",saved.players()).put("seed",saved.seed()).put("difficulty",saved.difficulty().name()).put("profile","LABORATORY_V1").put("policy","RENTZ_PUBLIC_LOOKAHEAD_V1").put("rules","RENTZ_RULES_V2").put("commands",commands);
    }
    public static GameSession restore(String text)throws JSONException{
        var json=new JSONObject(text);if(!json.getString("profile").equals("LABORATORY_V1")||!json.getString("policy").equals("RENTZ_PUBLIC_LOOKAHEAD_V1"))throw new IllegalArgumentException("Saved profile identity");
        String rules=json.optString("rules","RENTZ_RULES_V1");if(!rules.equals("RENTZ_RULES_V1")&&!rules.equals("RENTZ_RULES_V2"))throw new IllegalArgumentException("Saved rules identity");
        var commands=new ArrayList<GameSession.Command>();var rows=json.getJSONArray("commands");for(int n=0;n<rows.length();n++){var c=rows.getJSONObject(n);commands.add(new GameSession.Command(GameSession.Action.valueOf(c.getString("action")),c.getInt("seat"),c.isNull("card")?null:card(c.getJSONObject("card")),c.isNull("contract")?null:Contract.valueOf(c.getString("contract")),c.getBoolean("blind")));}
        return GameSession.restore(new GameSession.Saved(json.getInt("schema"),json.getInt("players"),json.getLong("seed"),RentzPlayer.Difficulty.valueOf(json.getString("difficulty")),commands));
    }
}
