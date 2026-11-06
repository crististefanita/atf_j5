package ro.rentzlab.core;

import ro.rentzlab.ai.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RentzSequenceTest {
    @ParameterizedTest @ValueSource(ints={3,4,5,6}) void acesRepeatAndLowestCardsSkipExactlyOneEligiblePlayer(int players){
        boolean aceSeen=false,skipSeen=false,passSeen=false;
        for(int seed=0;seed<16;seed++){
            var match=new RentzMatch(players,seed,ScoreProfile.laboratory());match.choose(0,Contract.RENTZ,false);
            int actions=0;
            while(match.observe(0).phase()==RentzMatch.Phase.PLAY){
                int actor=match.observe(0).turn();var before=match.observe(actor);
                if(before.legal().isEmpty()){match.pass(actor);passSeen=true;}
                else{
                    Card card=LabPolicy.card(before);match.play(actor,card);var after=match.observe(actor);
                    if(after.phase()==RentzMatch.Phase.PLAY){
                        if(card.rank()==14&&after.handSizes().get(actor)>0){assertEquals(actor,after.turn());aceSeen=true;}
                        if(card.rank()==15-2*players){
                            var eligible=new ArrayList<Integer>();
                            for(int offset=1;offset<=players;offset++){int seat=(actor+offset)%players;if(after.handSizes().get(seat)>0)eligible.add(seat);}
                            assertEquals(eligible.get(1%eligible.size()),after.turn());skipSeen=true;
                        }
                    }
                }
                assertTrue(++actions<1000);
            }
            assertEquals(RentzMatch.Phase.DEAL_END,match.observe(0).phase());
            assertEquals(600,match.history().get(0).points().stream().mapToInt(Integer::intValue).sum());
        }
        assertTrue(aceSeen);assertTrue(skipSeen);assertTrue(passSeen);
    }
    @Test void mandatoryPlayRefusesPassAndPreservesTheBoard(){
        for(int seed=0;seed<20;seed++){
            var match=new RentzMatch(4,seed,ScoreProfile.laboratory());match.choose(0,Contract.RENTZ,false);
            if(!match.observe(0).legal().isEmpty()){
                var before=match.observe(0);assertThrows(IllegalStateException.class,()->match.pass(0));assertEquals(before,match.observe(0));return;
            }
        }
        fail("Constructed sample set did not contain an opening card");
    }
    @Test void kingOfHeartsSettlesOnlyAfterTheWholeTrick(){
        boolean observed=false;
        for(int seed=0;seed<48&&!observed;seed++){
            var match=new RentzMatch(4,seed,ScoreProfile.laboratory());match.choose(0,Contract.KING_HEARTS,false);
            while(match.observe(0).phase()==RentzMatch.Phase.PLAY){
                int actor=match.observe(0).turn();var before=match.observe(actor);
                Card king=new Card(Card.Suit.HEARTS,13);
                Card card=before.legal().contains(king)?king:LabPolicy.card(before);
                boolean kingInTrick=before.trick().stream().anyMatch(p->p.card().equals(king))||card.equals(king);
                match.play(actor,card);var after=match.observe(actor);
                if(kingInTrick&&before.trick().size()<3)assertEquals(RentzMatch.Phase.PLAY,after.phase());
                if(kingInTrick&&before.trick().size()==3){
                    assertEquals(RentzMatch.Phase.DEAL_END,after.phase());
                    assertEquals(-100,match.history().get(0).points().stream().mapToInt(Integer::intValue).sum());observed=true;break;
                }
            }
        }
        assertTrue(observed);
    }
    @Test void finishingAHandDoesNotAllowDuplicatedSettlementOrAnEarlyNextHand(){
        var match=new RentzMatch(4,30,ScoreProfile.laboratory());
        assertThrows(IllegalStateException.class,match::nextHand);match.choose(0,Contract.TEN_CLUBS,true);
        while(match.observe(0).phase()==RentzMatch.Phase.PLAY){int actor=match.observe(0).turn();match.play(actor,LabPolicy.card(match.observe(actor)));}
        assertEquals(200,match.history().get(0).points().stream().mapToInt(Integer::intValue).sum());
        var before=match.history();assertThrows(IllegalStateException.class,()->match.play(0,new Card(Card.Suit.CLUBS,10)));assertEquals(before,match.history());
        match.nextHand();assertEquals(1,match.observe(0).chooser());assertEquals(2,match.observe(0).handNumber());
    }
    @Test void numericOverflowCannotSilentlyChangeTheScore(){
        var profile=new ScoreProfile(1,Integer.MAX_VALUE,1,1,1,1,List.of(1,0,0,0,0,0));
        var cards=List.of(new Card(Card.Suit.CLUBS,12),new Card(Card.Suit.HEARTS,12),new Card(Card.Suit.DIAMONDS,7));
        assertThrows(ArithmeticException.class,()->profile.trickPoints(Contract.QUEENS,cards,false));
        assertThrows(IllegalArgumentException.class,()->new RentzMatch(4,1,profile));
        var mutable=new ArrayList<>(List.of(300,200,100,0,0,0));var copy=new ScoreProfile(10,25,20,100,10,100,mutable);
        mutable.set(0,10000);assertEquals(300,copy.finishRewards().get(0));
    }
    @Test void publicEventsKeepActorAndBoardContextAfterACompletedTrick(){
        var match=new RentzMatch(4,6,ScoreProfile.laboratory());match.choose(0,Contract.WHIST,false);
        for(int action=0;action<4;action++){int actor=match.observe(0).turn();match.play(actor,LabPolicy.card(match.observe(actor)));}
        var view=match.observe(0);assertEquals(5,view.events().size());assertTrue(view.trick().isEmpty());
        assertEquals(Set.of(0,1,2,3),new HashSet<>(view.events().subList(1,5).stream().map(RentzMatch.TurnEvent::seat).toList()));
        assertEquals(view.seen(),view.events().stream().filter(e->e.action()==RentzMatch.Action.PLAY).map(RentzMatch.TurnEvent::card).toList());
        assertThrows(UnsupportedOperationException.class,()->view.events().clear());
    }
}
