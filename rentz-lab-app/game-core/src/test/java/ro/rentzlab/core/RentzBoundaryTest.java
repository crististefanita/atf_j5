package ro.rentzlab.core;

import ro.rentzlab.ai.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RentzBoundaryTest {
    @ParameterizedTest @ValueSource(ints={3,4,5,6})
    void fixedDistributionRequiresExactlyTheActiveDeck(int players) {
        var hands=partition(players);
        var opening=new OpeningDeal(hands);
        assertEquals(players,opening.players());
        var duplicated=partition(players);duplicated.get(0).set(0,duplicated.get(1).get(0));
        assertThrows(IllegalArgumentException.class,()->new OpeningDeal(duplicated));
        var missing=partition(players);missing.get(0).remove(0);
        assertThrows(IllegalArgumentException.class,()->new OpeningDeal(missing));
        var offDeck=partition(players);offDeck.get(0).set(0,new Card(Card.Suit.CLUBS,2));
        assertThrows(IllegalArgumentException.class,()->new OpeningDeal(offDeck));
        var nullCard=partition(players);nullCard.get(0).set(0,null);
        assertThrows(IllegalArgumentException.class,()->new OpeningDeal(nullCard));
        var nullHand=partition(players);nullHand.set(0,null);
        assertThrows(IllegalArgumentException.class,()->new OpeningDeal(nullHand));
    }

    @Test void distributionOwnsItsCopiesAndTheActorDoesNotReceiveTheFixture() {
        var input=partition(4);Card expected=input.get(0).get(0);
        var opening=new OpeningDeal(input);input.get(0).clear();input.clear();
        var game=RentzMatch.fromOpeningDeal(opening,72,ScoreProfile.laboratory(),0);
        assertTrue(game.observe(0).hand().isEmpty(),"Fixed distribution does not bypass a blind choice");
        game.reveal(0);assertEquals(expected,game.observe(0).hand().get(0));
        assertThrows(UnsupportedOperationException.class,()->opening.hands().clear());
        assertThrows(UnsupportedOperationException.class,()->opening.hands().get(0).clear());
        assertThrows(UnsupportedOperationException.class,()->game.observe(0).hand().clear());
        assertThrows(IllegalArgumentException.class,()->new OpeningDeal(List.of()));
    }

    @Test void aceCanKeepTheTurnWithoutCreatingAPlayableContinuation() {
        var game=RentzMatch.fromOpeningDeal(blockedAceDistribution(),11,ScoreProfile.laboratory(),0);
        game.choose(0,Contract.RENTZ,false);
        for(int rank=11;rank<=13;rank++)
            for(int seat=0;seat<4;seat++)game.play(seat,new Card(Card.Suit.values()[seat],rank));
        game.play(0,new Card(Card.Suit.CLUBS,14));
        var afterAce=game.observe(0);
        assertEquals(0,afterAce.turn());assertEquals(4,afterAce.hand().size());
        assertTrue(afterAce.legal().isEmpty(),"An extra turn does not waive sequence adjacency");
        assertEquals(13,afterAce.board().size());
        game.pass(0);var afterPass=game.observe(0);
        assertEquals(1,afterPass.turn());assertEquals(afterAce.board(),afterPass.board());
        assertEquals(afterAce.dealPoints(),afterPass.dealPoints());
        var event=afterPass.events().get(afterPass.events().size()-1);
        assertEquals(RentzMatch.Action.PASS,event.action());assertEquals(13,event.boardCardCount());
    }

    @Test void offSuitOwnedForeignAndOutOfDeckCardsAreRefusedBeforeMutation() {
        var game=RentzMatch.fromOpeningDeal(blockedAceDistribution(),11,ScoreProfile.laboratory(),0);
        game.choose(0,Contract.WHIST,false);game.play(0,new Card(Card.Suit.CLUBS,11));
        var before=views(game,4);var history=game.history();
        for(Card card:Arrays.asList(new Card(Card.Suit.DIAMONDS,14),new Card(Card.Suit.CLUBS,14),new Card(Card.Suit.CLUBS,2),null)) {
            assertThrows(IllegalStateException.class,()->game.play(1,card));
            assertEquals(before,views(game,4));assertEquals(history,game.history());
        }
        game.play(1,new Card(Card.Suit.CLUBS,8));assertEquals(2,game.observe(0).turn());
    }

    @ParameterizedTest @ValueSource(ints={3,4,5,6})
    void chooserLeadsEachBaseContractAtEveryTableSize(int players) {
        for(int chooser=0;chooser<players;chooser++)for(var contract:Contract.values()) {
            var game=RentzMatch.fromOpeningDeal(new OpeningDeal(partition(players)),1,ScoreProfile.laboratory(),chooser);
            game.choose(chooser,contract,true);
            assertEquals(chooser,game.observe(0).turn());assertEquals(contract,game.observe(0).contract());
        }
    }

    @Test void aConsumedChoiceIsNotRestoredWhenTheChooserReturns() {
        var game=new RentzMatch(4,19,ScoreProfile.laboratory());
        for(int deal=0;deal<4;deal++) {
            game.choose(deal,Contract.TRICKS,false);completeTrickDeal(game);game.nextHand();
        }
        assertEquals(0,game.observe(0).chooser());var before=views(game,4);
        assertThrows(IllegalStateException.class,()->game.choose(0,Contract.TRICKS,false));
        assertEquals(before,views(game,4));assertEquals(4,game.history().size());
        game.choose(0,Contract.WHIST,false);
    }

    @Test void continuationSeedDoesNotChangeTheInjectedFirstDeal() {
        var opening=blockedAceDistribution();
        var first=RentzMatch.fromOpeningDeal(opening,1,ScoreProfile.laboratory(),0);
        var second=RentzMatch.fromOpeningDeal(opening,2,ScoreProfile.laboratory(),0);
        for(var game:List.of(first,second)){game.choose(0,Contract.TRICKS,false);completeTrickDeal(game);}
        assertEquals(views(first,4),views(second,4));assertEquals(first.history(),second.history());
        first.nextHand();second.nextHand();
        for(int seat=0;seat<4;seat++){first.reveal(seat);second.reveal(seat);}
        assertNotEquals(views(first,4),views(second,4),"Continuation seed starts affecting only subsequent distributions");
    }

    @Test void zeroScoringDoesNotWaiveLegalPlayOrTerminateAnUnfinishedDeal() {
        var zero=new ScoreProfile(0,0,0,0,0,0,List.of(0,0,0,0,0,0));
        var game=RentzMatch.fromOpeningDeal(new OpeningDeal(partition(4)),4,zero,0);
        game.choose(0,Contract.TRICKS,true);
        for(int action=0;action<31;action++) {
            int actor=game.observe(0).turn();game.play(actor,LabPolicy.card(game.observe(actor)));
            assertEquals(RentzMatch.Phase.PLAY,game.observe(0).phase(),"Zero point contribution does not end an ordinary trick deal");
        }
        int actor=game.observe(0).turn();game.play(actor,LabPolicy.card(game.observe(actor)));
        assertEquals(RentzMatch.Phase.DEAL_END,game.observe(0).phase());
        assertEquals(List.of(0,0,0,0),game.history().get(0).points());
        assertEquals(32,game.observe(0).seen().size());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={3,4,5,6})
    void theLastRemainingContractCannotBeChosenBlindAndRefusalIsAtomic(int players) {
        var game=new RentzMatch(players,31,ScoreProfile.laboratory());int rejected=0,actions=0;
        while(game.observe(0).phase()!=RentzMatch.Phase.MATCH_END){
            int actor=game.observe(0).turn();var view=game.observe(actor);
            switch(view.phase()){
                case CHOOSE->{
                    var contract=view.available().iterator().next();
                    if(view.available().size()==1){
                        var before=views(game,players);var history=game.history();
                        assertFalse(view.revealed());assertThrows(IllegalStateException.class,()->game.choose(actor,contract,true));
                        assertEquals(before,views(game,players));assertEquals(history,game.history());rejected++;
                    }
                    game.choose(actor,contract,false);
                }
                case PLAY->{if(view.legal().isEmpty())game.pass(actor);else game.play(actor,LabPolicy.card(view));}
                case DEAL_END->game.nextHand();
                default->fail("Unexpected phase");
            }
            assertTrue(++actions<10000);
        }
        assertEquals(players,rejected);assertEquals(players*8,game.history().size());
    }

    private static List<List<Card>> partition(int players) {
        var deck=Card.deck(players);var result=new ArrayList<List<Card>>();
        for(int seat=0;seat<players;seat++)result.add(new ArrayList<>(deck.subList(seat*8,seat*8+8)));
        return result;
    }
    private static OpeningDeal blockedAceDistribution() {
        return new OpeningDeal(List.of(
                cards("CJ CQ CK CA D7 H7 S7 S8"),cards("DJ DQ DK DA D8 D9 D10 C8"),
                cards("HJ HQ HK HA H8 H9 H10 C9"),cards("SJ SQ SK SA S9 S10 C10 C7")));
    }
    private static List<Card> cards(String specification) {
        var result=new ArrayList<Card>();
        for(String token:specification.split(" ")) {
            var suit=switch(token.charAt(0)){case 'C'->Card.Suit.CLUBS;case 'D'->Card.Suit.DIAMONDS;case 'H'->Card.Suit.HEARTS;case 'S'->Card.Suit.SPADES;default->throw new AssertionError();};
            int rank=switch(token.substring(1)){case "J"->11;case "Q"->12;case "K"->13;case "A"->14;default->Integer.parseInt(token.substring(1));};
            result.add(new Card(suit,rank));
        }
        return result;
    }
    private static List<RentzMatch.Observation> views(RentzMatch game,int players) {
        var observations=new ArrayList<RentzMatch.Observation>();for(int seat=0;seat<players;seat++)observations.add(game.observe(seat));return observations;
    }
    private static void completeTrickDeal(RentzMatch game) {
        int actions=0;
        while(game.observe(0).phase()==RentzMatch.Phase.PLAY){int actor=game.observe(0).turn();game.play(actor,LabPolicy.card(game.observe(actor)));assertTrue(++actions<=48);}
    }
}
