package ro.rentzlab.app;

import ro.rentzlab.app.testing.AndroidComponentLayer;

import ro.rentzlab.ai.*;
import org.junit.Test;
import ro.rentzlab.core.*;
import static org.junit.Assert.*;
@AndroidComponentLayer
public class GameSessionTest {
    @Test public void incompatibleSavedIdentitiesAreRejected()throws Exception{
        var session=new GameSession(4,31,RentzPlayer.Difficulty.NORMAL);
        for(String key:new String[]{"schema","profile","policy","rules"}){
            var json=SessionJson.saved(session);json.put(key,key.equals("schema")?2:"UNKNOWN");
            try{SessionJson.restore(json.toString());fail("Accepted incompatible "+key);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void blindEligibilityDisappearsBeforeTheLastChoice()throws Exception{
        var session=new GameSession(3,31,RentzPlayer.Difficulty.NORMAL);assertTrue(session.blindChoiceAvailable());
        int actions=0;
        while(true){var view=session.view();
            if(view.phase()==RentzMatch.Phase.CHOOSE&&view.turn()==0&&view.available().size()==1){
                assertFalse(view.revealed());assertFalse(session.blindChoiceAvailable());
                var restored=SessionJson.restore(SessionJson.saved(session).toString());assertFalse(restored.blindChoiceAvailable());
                var legacy=SessionJson.saved(session);legacy.remove("rules");assertEquals(view,SessionJson.restore(legacy.toString()).view());return;
            }
            if(view.phase()==RentzMatch.Phase.CHOOSE){session.apply(new GameSession.Command(GameSession.Action.CHOOSE,0,null,view.available().iterator().next(),false));}
            else if(view.phase()==RentzMatch.Phase.DEAL_END)session.apply(new GameSession.Command(GameSession.Action.NEXT_HAND,0,null,null,false));
            else if(view.phase()==RentzMatch.Phase.PLAY){session.apply(new GameSession.Command(view.legal().isEmpty()?GameSession.Action.PASS:GameSession.Action.PLAY,0,view.legal().isEmpty()?null:view.legal().get(0),null,false));}
            else fail("Missing last choice");
            session.advanceBots();assertTrue(++actions<2000);
        }
    }
    @Test public void aSavedSnapshotCannotBeChangedByLaterCommands(){
        var session=new GameSession(4,3,RentzPlayer.Difficulty.NORMAL);var before=session.save();
        session.apply(new GameSession.Command(GameSession.Action.REVEAL,0,null,null,false));assertTrue(before.commands().isEmpty());
        try{session.save().commands().clear();fail("Mutable replay commands");}catch(UnsupportedOperationException expected){}
    }
    @Test public void interruptedBotProgressCanResumeFromTheSameCommands()throws Exception{
        var session=new GameSession(4,31,RentzPlayer.Difficulty.NORMAL);
        session.apply(new GameSession.Command(GameSession.Action.REVEAL,0,null,null,false));session.apply(new GameSession.Command(GameSession.Action.CHOOSE,0,null,Contract.WHIST,false));
        session.apply(new GameSession.Command(GameSession.Action.PLAY,0,session.view().legal().get(0),null,false));
        var restored=SessionJson.restore(SessionJson.saved(session).toString());session.advanceBots();restored.advanceBots();
        assertEquals(session.view(),restored.view());assertEquals(session.save().commands(),restored.save().commands());
    }
    @Test public void rejectedHumanCommandDoesNotEnterTheSavedJournal(){var session=new GameSession(4,3,RentzPlayer.Difficulty.NORMAL);int before=session.save().commands().size();try{session.apply(new GameSession.Command(GameSession.Action.PLAY,0,new Card(Card.Suit.CLUBS,7),null,false));fail();}catch(IllegalStateException expected){}assertEquals(before,session.save().commands().size());}
    @Test public void publicProjectionContainsNoSeedOrOpposingHandAndSaveRestoresExactly()throws Exception{var session=new GameSession(4,31,RentzPlayer.Difficulty.HARD);session.apply(new GameSession.Command(GameSession.Action.REVEAL,0,null,null,false));session.apply(new GameSession.Command(GameSession.Action.CHOOSE,0,null,Contract.WHIST,false));session.apply(new GameSession.Command(GameSession.Action.PLAY,0,session.view().legal().get(0),null,false));session.advanceBots();var actor=SessionJson.actor(session,"GAME",false);assertFalse(actor.has("seed"));assertFalse(actor.getJSONObject("view").has("opponentHands"));assertEquals(session.view(),SessionJson.restore(SessionJson.saved(session).toString()).view());}
    @Test public void aCompleteHandAndHistorySurviveRestoration()throws Exception{var session=new GameSession(3,17,RentzPlayer.Difficulty.NORMAL);session.apply(new GameSession.Command(GameSession.Action.REVEAL,0,null,null,false));session.apply(new GameSession.Command(GameSession.Action.CHOOSE,0,null,Contract.TRICKS,false));int actions=0;while(session.view().phase()==RentzMatch.Phase.PLAY){if(session.view().turn()==0)session.apply(new GameSession.Command(GameSession.Action.PLAY,0,session.view().legal().get(0),null,false));session.advanceBots();assertTrue(++actions<100);}var restored=SessionJson.restore(SessionJson.saved(session).toString());assertEquals(session.view(),restored.view());assertEquals(session.history(),restored.history());assertEquals(1,restored.history().size());}
}
