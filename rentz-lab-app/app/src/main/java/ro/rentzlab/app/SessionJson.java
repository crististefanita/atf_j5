package ro.rentzlab.app;

import ro.rentzlab.ai.*;

import org.json.*;
import ro.rentzlab.core.*;
import java.util.*;

/** Explicit Android/JVM JSON contract; public actor view and private replay data are different objects. */
public final class SessionJson {
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
        return new JSONObject().put("schema",1).put("screen",screen).put("busy",busy).put("revision",session.save().commands().size()).put("difficulty",session.difficulty().name()).put("view",view);
    }
    public static JSONObject saved(GameSession session)throws JSONException{
        var saved=session.save();var commands=new JSONArray();for(var command:saved.commands())commands.put(new JSONObject().put("action",command.action().name()).put("seat",command.seat()).put("card",card(command.card())).put("contract",command.contract()==null?JSONObject.NULL:command.contract().name()).put("blind",command.blind()));
        return new JSONObject().put("schema",saved.schema()).put("players",saved.players()).put("seed",saved.seed()).put("difficulty",saved.difficulty().name()).put("profile","LABORATORY_V1").put("policy","RENTZ_PUBLIC_LOOKAHEAD_V1").put("rules","RENTZ_RULES_V2").put("commands",commands);
    }
    public static GameSession restore(String text)throws JSONException{
        var json=SavedJson.read(text);
        SavedJson.fields(json,Set.of("schema","players","seed","difficulty","profile","policy","commands"),Set.of("rules"));
        if(!SavedJson.text(json,"profile").equals("LABORATORY_V1")||!SavedJson.text(json,"policy").equals("RENTZ_PUBLIC_LOOKAHEAD_V1")){
            throw new IllegalArgumentException("Saved profile identity");
        }
        String rules=json.has("rules")?SavedJson.text(json,"rules"):"RENTZ_RULES_V1";
        if(!rules.equals("RENTZ_RULES_V1")&&!rules.equals("RENTZ_RULES_V2"))throw new IllegalArgumentException("Saved rules identity");
        int players=SavedJson.intValue(json,"players");
        if(players<3||players>6)throw new IllegalArgumentException("Saved table size");
        var commands=new ArrayList<GameSession.Command>();
        var rows=json.getJSONArray("commands");
        for(int n=0;n<rows.length();n++)commands.add(command(rows.getJSONObject(n),players));
        return GameSession.restore(new GameSession.Saved(SavedJson.intValue(json,"schema"),players,SavedJson.integer(json,"seed"),
                RentzPlayer.Difficulty.valueOf(SavedJson.text(json,"difficulty")),commands));
    }

    private static GameSession.Command command(JSONObject json,int players)throws JSONException{
        SavedJson.fields(json,Set.of("action","seat","card","contract","blind"),Set.of());
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
        return new GameSession.Command(action,seat,card,contract,blind);
    }
}
