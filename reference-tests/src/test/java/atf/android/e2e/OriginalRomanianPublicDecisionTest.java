package atf.android.e2e;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import atf.testkit.AtfComponentTest;
import ro.whistlab.core.Trump;

@AtfComponentTest
class OriginalRomanianPublicDecisionTest {
    private static final OriginalRomanianPublicDecision.ExpectedProfile PROFILE=OriginalRomanianPublicDecision.ExpectedProfile.ROMANIAN_FOUR_PLAYERS;
    private static final int[] STREAKS={0,0,0,0};
    private static int id(String code){int suit="SHCD".indexOf(code.charAt(code.length()-1));String rank=code.substring(0,code.length()-1);int value=switch(rank){case "A"->14;case "K"->13;case "Q"->12;case "J"->11;default->Integer.parseInt(rank);};return suit*13+value-2;}
    private static OriginalWhistState state(boolean bidding){
        var s=new OriginalWhistState();s.players=4;s.mode=9327;s.dealer=3;s.handSize=2;s.serializedHandSize=2;s.handIndex=0;s.trump=0;s.trumpCard=id("QS");s.bidding=bidding;s.turn=0;
        s.remaining=new int[]{2,2,2,2};s.bids=bidding?new int[]{-1,-1,-1,-1}:new int[]{1,0,0,0};s.won=new int[]{0,0,0,0};s.scores=new int[][]{{0,0,0,0}};
        s.cards=new int[][]{{id("AS"),id("7H")},{id("KS"),id("8H")},{id("JS"),id("9H")},{id("10S"),id("10H")}};s.history=List.of(s.trumpCard);return s;
    }
    private static void corruptOnlyUnreadOpponentSlots(OriginalWhistState s){s.cards[1]=null;s.cards[2]=new int[]{-999,999};s.cards[3]=new int[0];}

    @Test void opponentPrivateSlotsCannotChangeTheBidViewDecisionOrJson(){
        var s=state(true);var before=OriginalRomanianPublicDecision.chooseBid(s,STREAKS,PROFILE);corruptOnlyUnreadOpponentSlots(s);
        var after=OriginalRomanianPublicDecision.chooseBid(s,STREAKS,PROFILE);
        assertEquals(before.view(),after.view());assertEquals(before.value(),after.value());assertEquals(before.json(),after.json());
        assertTrue(after.json().contains("observerOwnHand"));assertFalse(after.json().contains("999"));
    }
    @Test void opponentPrivateSlotsCannotChangeTheCardViewDecisionOrJson(){
        var s=state(false);var before=OriginalRomanianPublicDecision.chooseCard(s,STREAKS,PROFILE);corruptOnlyUnreadOpponentSlots(s);
        var after=OriginalRomanianPublicDecision.chooseCard(s,STREAKS,PROFILE);
        assertEquals(before.view(),after.view());assertEquals(before.value(),after.value());assertEquals(before.json(),after.json());assertTrue(s.hand(0).contains(after.value()));
    }
    @Test void actorsFollowTheIndependentWinnerAndTheCurrentTrickIsOnlyThePartialSuffix(){
        var s=state(false);s.handSize=3;s.trump=1;s.trumpCard=id("7H");s.cards[0]=new int[]{id("KS"),id("QS"),0};
        s.history=List.of(s.trumpCard,id("7S"),id("8S"),id("AS"),id("9S"),id("KH"),id("AH"));s.remaining=new int[]{2,2,1,1};s.won=new int[]{0,0,1,0};s.bids=new int[]{1,0,2,1};
        var observed=OriginalRomanianPublicDecision.observe(s,STREAKS,PROFILE);var v=observed.view();
        assertEquals(0,observed.nextActor());assertEquals(List.of(0,1,2,3,2,3),v.history().stream().map(p->p.seat()).toList());
        assertEquals(List.of(2,3),v.trick().stream().map(p->p.seat()).toList());assertEquals(1,v.seats().get(2).won());assertEquals(6,v.history().size());
    }
    @Test void eightCardsIgnoreTheStaleTrumpWordWithoutRemovingTheFirstRealAcePlay(){
        var s=state(false);s.handSize=8;s.trump=0;s.trumpCard=id("AS");s.history=List.of(id("AS"));s.cards[0]=new int[]{id("KS"),id("QS"),id("JS"),id("10S"),id("9S"),id("8S"),id("7S")};s.remaining=new int[]{7,8,8,8};s.bids=new int[]{1,1,1,1};s.turn=1;
        var observed=OriginalRomanianPublicDecision.observe(s,STREAKS,PROFILE);
        assertEquals(Trump.NO_TRUMP,observed.view().trump());assertNull(observed.view().turnedCard());assertEquals(1,observed.view().history().size());assertEquals(0,observed.view().history().getFirst().seat());assertEquals(1,observed.nextActor());
        assertThrows(IllegalArgumentException.class,()->OriginalRomanianPublicDecision.chooseCard(s,STREAKS,PROFILE));
        s.trump=4;s.trumpCard=65535;
        assertTrue(assertDoesNotThrow(s::summary).contains("turned card=none"),"No-trump diagnostics must not decode an absent turned-card sentinel as a suit");
    }
    @Test void finalBidUsesOnlyTheActualPublicPrefixAndAvoidsTheForbiddenTotal(){
        var s=state(true);s.dealer=0;s.bids=new int[]{-1,0,0,1};
        var decision=OriginalRomanianPublicDecision.chooseBid(s,new int[]{4,0,-4,0},PROFILE);
        assertTrue(List.of(0,2).contains(decision.value()));assertNotEquals(1,decision.value());assertEquals(4,decision.view().seats().get(0).streak());assertEquals(5,decision.view().streakBonusPoints());
    }
    @Test void explicitProfileAndPublicCounterConsistencyAreRequired(){
        var s=state(false);assertThrows(IllegalArgumentException.class,()->OriginalRomanianPublicDecision.publicView(s,STREAKS,null));
        s.remaining[2]=1;assertThrows(IllegalArgumentException.class,()->OriginalRomanianPublicDecision.publicView(s,STREAKS,PROFILE));
    }
}
