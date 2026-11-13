package ro.rentzlab.app;

import ro.rentzlab.app.testing.AndroidComponentLayer;

import ro.rentzlab.ai.*;
import org.junit.Test;
import ro.rentzlab.core.*;
import ro.rentzlab.core.sequence.SequenceOpening;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
@AndroidComponentLayer
public class GameSessionTest {
    @Test public void savedRentzScoringKeepsTheVersionUsedAtSettlement() throws Exception {
        var legacy = RentzMatch.DealEnding.SCORING_CARDS;
        var current = RentzMatch.DealEnding.UNIQUE_LAST;
        for (var rules : List.of(legacy, current)) {
            var session = GameSession.restore(new GameSession.Saved(1, 6, 31,
                    RentzPlayer.Difficulty.NORMAL, List.of(), List.of(), rules));
            session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null,
                    Contract.RENTZ, true));
            for (int turn = 0; session.view().phase() == RentzMatch.Phase.PLAY; turn++) {
                assertTrue(turn < 200);
                int actor = session.view().turn();
                if (actor != 0) session.advanceBotTurn();
                else if (session.view().legal().isEmpty()) {
                    session.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false));
                } else session.advanceHumanTurn();
            }
            var history = session.history();
            assertEquals(rules == legacy ? 3L : 1L,
                    history.get(0).points().stream().filter(points -> points == 0).count());
            var journal = SessionJson.saved(session);
            assertEquals(rules.laboratoryProfileId(), session.scoreProfileId());
            assertEquals(session.scoreProfileId(), journal.getString("profile"));
            assertEquals(rules.journalVersion(), journal.getString("rules"));
            assertEquals(history, SessionJson.restore(journal.toString()).history());
        }
    }
    @Test public void incompatibleSavedIdentitiesAreRejected()throws Exception{
        var session=new GameSession(4,31,RentzPlayer.Difficulty.NORMAL);
        for(String key:new String[]{"schema","profile","policy","rules","sequenceOpening"}){
            var json=SessionJson.saved(session);json.put(key,key.equals("schema")?2:"UNKNOWN");
            try{SessionJson.restore(json.toString());fail("Accepted incompatible "+key);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void legacyRentzJournalWithoutOpeningPolicyKeepsItsOldTableMiddle()throws Exception {
        for (long seed = 0; seed < 96; seed++) {
            var saved = new GameSession.Saved(1, 3, seed, RentzPlayer.Difficulty.NORMAL, List.of(), List.of(),
                    RentzMatch.DealEnding.UNIQUE_LAST, SequenceOpening.ACTIVE_MIDDLE);
            var session = GameSession.restore(saved);
            session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, false));
            var opening = session.view().legal().stream().filter(card -> card.rank() == 12).findFirst();
            if (opening.isEmpty()) continue;
            session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, opening.get(), null, false));
            var before = session.view();
            var journal = SessionJson.saved(session);
            assertEquals("ACTIVE_MIDDLE", journal.getString("sequenceOpening"));
            journal.remove("sequenceOpening");
            var restored = SessionJson.restore(journal.toString());
            assertEquals(SequenceOpening.ACTIVE_MIDDLE, restored.view().sequenceOpening());
            assertEquals(before, restored.view());
            assertEquals(session.save().commands(), restored.save().commands());
            return;
        }
        fail("A deterministic fixture must expose the legacy Q opener");
    }
    @Test public void explicitJackJournalRetainsItsPlayedCardAtFiveSeats() throws Exception {
        for (long seed = 0; seed < 32; seed++) {
            var saved = new GameSession.Saved(1, 5, seed, RentzPlayer.Difficulty.NORMAL, List.of(), List.of(),
                    RentzMatch.DealEnding.UNIQUE_LAST, SequenceOpening.JACK);
            var session = GameSession.restore(saved);
            session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, false));
            var legal = session.view().legal();
            if (legal.isEmpty()) continue;
            assertTrue(legal.stream().allMatch(card -> card.rank() == 11));
            session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, legal.get(0), null, false));
            var journal = SessionJson.saved(session);
            assertEquals("JACK", journal.getString("sequenceOpening"));
            assertEquals(session.view(), SessionJson.restore(journal.toString()).view());
            return;
        }
        fail("A deterministic fixture must expose a Jack at five seats");
    }

    @Test public void blindEligibilityDisappearsBeforeTheLastChoice()throws Exception{
        var session=new GameSession(3,31,RentzPlayer.Difficulty.NORMAL);assertTrue(session.blindChoiceAvailable());
        int actions=0;
        while(true){var view=session.view();
            if(view.phase()==RentzMatch.Phase.CHOOSE&&view.turn()==0&&view.available().size()==1){
                assertFalse(view.revealed());assertFalse(session.blindChoiceAvailable());
                var restored=SessionJson.restore(SessionJson.saved(session).toString());assertFalse(restored.blindChoiceAvailable());
                assertEquals(view,restored.view());return;
            }
            if(view.phase()==RentzMatch.Phase.CHOOSE){session.apply(new GameSession.Command(GameSession.Action.CHOOSE,0,null,view.available().iterator().next(),false));}
            else if(view.phase()==RentzMatch.Phase.DEAL_END)session.apply(new GameSession.Command(GameSession.Action.NEXT_HAND,0,null,null,false));
            else if(view.phase()==RentzMatch.Phase.PLAY){session.apply(new GameSession.Command(view.legal().isEmpty()?GameSession.Action.PASS:GameSession.Action.PLAY,0,view.legal().isEmpty()?null:view.legal().get(0),null,false));}
            else fail("Missing last choice");
            session.advanceBots();assertTrue(++actions<2000);
        }
    }
    @Test public void oldJournalsKeepTheirTrailingTricksAndNewJournalsEndAtTheObjective()throws Exception {
        var score=GameSession.scoringProfile();
        var difficulty=RentzPlayer.Difficulty.NORMAL;
        for(var contract:List.of(Contract.QUEENS,Contract.DIAMONDS)) {
            boolean exercised=false;
            for(long seed=0;seed<32&&!exercised;seed++) {
                var oldGame=new RentzMatch(4,seed,score,0,RentzMatch.DealEnding.LEGACY,
                        SequenceOpening.ACTIVE_MIDDLE);
                oldGame.choose(0,contract,false);
                var commands=new ArrayList<GameSession.Command>();
                commands.add(new GameSession.Command(GameSession.Action.CHOOSE,0,null,contract,false));
                int frontier=0;
                while(oldGame.observe(0).phase()==RentzMatch.Phase.PLAY) {
                    int actor=oldGame.observe(0).turn();
                    var legal=oldGame.observe(actor).legal();
                    var card=legal.stream().filter(c->target(contract,c)).findFirst().orElse(legal.get(0));
                    oldGame.play(actor,card);
                    commands.add(new GameSession.Command(GameSession.Action.PLAY,actor,card,null,false));
                    var view=oldGame.observe(0);
                    if(frontier==0 && view.trick().isEmpty() && view.seen().stream().filter(c->target(contract,c)).count()==(contract==Contract.QUEENS?4:8))
                        frontier=commands.size();
                }
                if(frontier==0||frontier==commands.size())continue;
                var legacy=GameSession.restore(new GameSession.Saved(1,4,seed,difficulty,commands,List.of(),
                        RentzMatch.DealEnding.LEGACY,SequenceOpening.ACTIVE_MIDDLE));
                var saved=SessionJson.saved(legacy);
                assertEquals("RENTZ_RULES_V2",saved.getString("rules"));
                assertEquals(oldGame.observe(0),SessionJson.restore(saved.toString()).view());
                saved.remove("rules");
                assertEquals(oldGame.history(),SessionJson.restore(saved.toString()).history());

                var current=GameSession.restore(new GameSession.Saved(1,4,seed,difficulty,
                        commands.subList(0,frontier),List.of(),RentzMatch.DealEnding.UNIQUE_LAST,
                        SequenceOpening.ACTIVE_MIDDLE));
                var newJournal=SessionJson.saved(current);
                assertEquals("RENTZ_RULES_V4",newJournal.getString("rules"));
                assertEquals(RentzMatch.Phase.DEAL_END,current.view().phase());
                assertEquals(legacy.history(),current.history());
                assertEquals(current.view(),SessionJson.restore(newJournal.toString()).view());
                exercised=true;
            }
            assertTrue("Expected an early frontier for "+contract,exercised);
        }
    }

    private static boolean target(Contract contract,Card card) {
        return contract==Contract.QUEENS?card.rank()==12:card.suit()==Card.Suit.DIAMONDS;
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
