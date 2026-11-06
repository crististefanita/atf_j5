package ro.rentzlab.core;

import ro.rentzlab.ai.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RentzRulesTest {
    private Card c(Card.Suit suit,int rank){return new Card(suit,rank);}
    @ParameterizedTest @ValueSource(ints={3,4,5,6}) void deckAndOpeningRankFitTheTable(int players){
        var deck=Card.deck(players);assertEquals(players*8,deck.size());assertEquals(deck.size(),new HashSet<>(deck).size());
        assertEquals(Map.of(3,12,4,11,5,10,6,9).get(players),new RentzBoard(players).startRank());
        assertEquals(Map.of(3,9,4,7,5,5,6,3).get(players),deck.stream().mapToInt(Card::rank).min().orElseThrow());
        assertTrue(deck.contains(c(Card.Suit.HEARTS,13)));assertTrue(deck.contains(c(Card.Suit.CLUBS,10)));
    }
    @Test void followSuitIsMandatoryAndThereIsNoTrump(){
        var hand=List.of(c(Card.Suit.CLUBS,7),c(Card.Suit.HEARTS,14));
        var trick=List.of(new TrickRules.Play(2,c(Card.Suit.CLUBS,10)));
        assertEquals(List.of(hand.get(0)),TrickRules.legal(hand,trick));
        assertEquals(2,TrickRules.winner(List.of(trick.get(0),new TrickRules.Play(0,hand.get(0)),new TrickRules.Play(1,hand.get(1)))));
        assertEquals(hand,TrickRules.legal(hand,List.of(new TrickRules.Play(1,c(Card.Suit.DIAMONDS,9)))));
    }
    @Test void scoringAddsOverlappingPenaltiesAndBlindDoublesOnce(){
        var profile=ScoreProfile.laboratory();
        var cards=List.of(c(Card.Suit.DIAMONDS,12),c(Card.Suit.HEARTS,13),c(Card.Suit.CLUBS,7),c(Card.Suit.SPADES,8));
        assertEquals(-155,profile.trickPoints(Contract.TOTALS,cards,false));
        assertEquals(-310,profile.trickPoints(Contract.TOTALS,cards,true));
        assertEquals(-25,profile.trickPoints(Contract.QUEENS,cards,false));
        assertEquals(-20,profile.trickPoints(Contract.DIAMONDS,cards,false));
        assertEquals(-100,profile.trickPoints(Contract.KING_HEARTS,cards,false));
        assertEquals(10,profile.trickPoints(Contract.WHIST,cards,false));
        assertThrows(IllegalArgumentException.class,()->profile.trickPoints(Contract.RENTZ,cards,false));
    }
    @Test void sequenceOnlyAcceptsAnOpeningOrAnAdjacentCardOfThatSuit(){
        var board=new RentzBoard(4);var before=board.played();
        assertFalse(board.fits(c(Card.Suit.CLUBS,10)));
        assertThrows(IllegalArgumentException.class,()->board.play(c(Card.Suit.CLUBS,10)));assertEquals(before,board.played());
        board.play(c(Card.Suit.CLUBS,11)); assertTrue(board.fits(c(Card.Suit.CLUBS,10)));
        assertTrue(board.fits(c(Card.Suit.CLUBS,12))); assertFalse(board.fits(c(Card.Suit.CLUBS,14)));
        assertTrue(board.fits(c(Card.Suit.HEARTS,11)));assertFalse(board.fits(c(Card.Suit.HEARTS,10)));
        assertFalse(board.fits(c(Card.Suit.CLUBS,11)));
    }
    @Test void blindChoiceRejectsPreviouslyRevealedCardsWithoutChangingState(){
        var match=new RentzMatch(4,24,ScoreProfile.laboratory());
        assertTrue(match.observe(0).hand().isEmpty());match.reveal(0);var before=match.observe(0);
        assertThrows(IllegalStateException.class,()->match.choose(0,Contract.WHIST,true));assertEquals(before,match.observe(0));
        match.choose(0,Contract.WHIST,false);assertEquals(8,match.observe(1).hand().size());
        assertThrows(UnsupportedOperationException.class,()->match.observe(1).hand().clear());
        assertThrows(UnsupportedOperationException.class,()->match.observe(1).available().clear());
    }
    @ParameterizedTest @ValueSource(ints={3,4,5,6}) void completeOfflineMatchesConserveCardsAndChooseEveryGameOnce(int players){
        for(int seed=0;seed<24;seed++){
            var match=new RentzMatch(players,seed,ScoreProfile.laboratory());int actions=0;
            while(match.observe(0).phase()!=RentzMatch.Phase.MATCH_END){
                var publicView=match.observe(0);int seat=publicView.turn();
                switch(publicView.phase()){
                    case CHOOSE -> {match.reveal(seat);match.choose(seat,LabPolicy.choose(match.observe(seat)),false);}
                    case PLAY -> {
                        var own=match.observe(seat);
                        if(own.legal().isEmpty())match.pass(seat);else match.play(seat,LabPolicy.card(own));
                        var after=match.observe(seat);assertEquals(new HashSet<>(after.seen()).size(),after.seen().size());
                        assertEquals(players*8,after.handSizes().stream().mapToInt(Integer::intValue).sum()+after.seen().size());
                    }
                    case DEAL_END -> match.nextHand();
                    default -> fail("Unexpected phase");
                }
                assertTrue(++actions<20000,"A match must terminate");
            }
            assertEquals(players*8,match.history().size());
            assertEquals(players*8,match.observe(0).handNumber(),"The terminal observation cannot invent the next deal");
            for(int seat=0;seat<players;seat++){
                int actor=seat;var chosen=match.history().stream().filter(h->h.chooser()==actor).map(RentzMatch.HandResult::contract).toList();
                assertEquals(EnumSet.allOf(Contract.class),new HashSet<>(chosen));assertEquals(8,chosen.size());
                int sum=0;for(var hand:match.history()){sum+=hand.points().get(seat);assertEquals(sum,hand.totals().get(seat));}
                assertEquals(sum,match.observe(seat).totals().get(seat));
            }
        }
    }
    @Test void illegalTurnAndPassPreserveTheObservation(){
        var match=new RentzMatch(4,4,ScoreProfile.laboratory());match.choose(0,Contract.WHIST,false);var before=match.observe(0);
        assertThrows(IllegalStateException.class,()->match.play(1,match.observe(1).hand().get(0)));
        assertThrows(IllegalStateException.class,()->match.pass(0));assertEquals(before,match.observe(0));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3}) void chooserRotationHasAnExplicitStartingSeat(int first){
        var match=new RentzMatch(4,9,ScoreProfile.laboratory(),first);
        assertEquals(first,match.observe(0).chooser());match.choose(first,Contract.KING_HEARTS,false);
        while(match.observe(0).phase()==RentzMatch.Phase.PLAY){int actor=match.observe(0).turn();match.play(actor,LabPolicy.card(match.observe(actor)));}
        assertEquals(1,match.observe(0).handNumber());match.nextHand();
        assertEquals((first+1)%4,match.observe(0).chooser());assertEquals(2,match.observe(0).handNumber());
    }
    @Test void sequenceRankEffectsNeverLeakIntoTheTrickGames(){
        for(var contract:Contract.values())if(contract!=Contract.RENTZ){
            boolean ace=false,low=false;
            for(int seed=0;seed<64;seed++){
                var match=new RentzMatch(4,seed,ScoreProfile.laboratory());match.choose(0,contract,false);
                var view=match.observe(0);
                var selected=view.legal().stream().filter(c->c.rank()==14||c.rank()==7).findFirst();
                if(selected.isEmpty())continue;
                var card=selected.get();match.play(0,card);var after=match.observe(0);
                assertEquals(1,after.turn(),"First card in a trick always passes to the next seat");
                assertEquals(RentzMatch.Phase.PLAY,after.phase());assertEquals(1,after.trick().size());
                if(card.rank()==14)ace=true;else low=true;
            }
            assertTrue(ace);assertTrue(low);
        }
    }
}
