package atf.android.e2e;

import atf.reporting.contract.AtfAssertion;
import atf.testkit.AtfComponentTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class OriginalRomanianAuditTest {
    @Test void independentRomanianScoreAndOriginalFiveHandSeriesAreAccepted(){
        var start=deal();var audit=new OriginalRomanianAudit(start,4,3,new int[4],new int[]{4,4,-4,-4},true,9);
        assertPass(audit.start(start));var end=finished();end.scores[5]=new int[]{12,10,5,-6};assertPass(audit.verify(end));
        assertArrayEquals(new int[]{12,10,5,-6},audit.totals());assertArrayEquals(new int[]{0,0,1,0},audit.streaks());
        assertEquals(8,audit.publicPlays().size());assertTrue(audit.currentTrick().isEmpty());
    }
    @Test void completedOriginalScoreUsesNextHandIndexAndStillAuditsTheJustFinishedHand(){
        var audit=new OriginalRomanianAudit(deal(),4,3,new int[4],new int[4],false,9);
        var end=finished();assertPass(audit.verify(end));
        end.handIndex=4;assertReject(audit.verify(end),"scheduled hand index");
    }
    @Test void wrongScoreWinnerAndChangedHistoryAreRejected(){
        var audit=new OriginalRomanianAudit(deal(),new int[4],new int[4],false,9);var score=finished();
        assertPass(audit.verify(score));score.scores[5][0]=12;assertReject(audit.verify(score),"cumulative score");
        score=finished();score.won=new int[]{1,1,0,0};assertReject(audit.verify(score),"counted won");
        score=finished();score.history=new ArrayList<>(score.history);score.history.set(0,c(1,7));assertReject(audit.verify(score),"immutable public play");
    }
    @Test void aRecordedBidCannotChangeOrDisappear(){
        var audit=new OriginalRomanianAudit(deal(),new int[4],new int[4],false,null);var playing=deal();playing.bidding=false;playing.bids=new int[]{2,0,0,1};assertPass(audit.verify(playing));
        playing.bids[0]=1;assertReject(audit.verify(playing),"immutable observed bid");playing.bids[0]=-1;assertReject(audit.verify(playing),"cannot disappear");
    }
    @Test void mandatoryTrumpRejectsAnOtherwiseHeldDiscard(){
        var start=deal();start.cards=new int[][]{{c(1,14),c(1,7)},{c(0,7),c(2,7)},{c(1,13),c(1,8)},{c(1,12),c(1,9)}};start.trumpCard=c(0,8);
        var audit=new OriginalRomanianAudit(start,new int[4],new int[4],false,9);
        var after=deal();after.cards=new int[][]{{c(1,7),0},{c(0,7),0},{c(1,13),c(1,8)},{c(1,12),c(1,9)}};after.remaining=new int[]{1,1,2,2};
        after.bidding=false;after.bids=new int[]{1,0,0,0};after.trumpCard=c(0,8);after.turn=2;after.history=List.of(c(1,14),c(2,7));
        assertReject(audit.verify(after),"mandatory trump");
    }
    @Test void publicPrefixScheduleDeckAndModeAreIndependentlyChecked(){
        assertEquals(List.of(1,1,1,1,2,3,4,5,6,7,8,8,8,8,7,6,5,4,3,2,1,1,1,1),OriginalRomanianAudit.schedule());
        assertEquals(1,OriginalRomanianAudit.dealerAfter(3,2));
        var start=deal();var audit=new OriginalRomanianAudit(start,4,2,new int[4],new int[4],false,8);assertReject(audit.verify(start),"selected Romanian mode");assertReject(audit.verify(start),"rotating dealer");
        start=deal();start.cards[1][0]=start.cards[0][0];assertReject(new OriginalRomanianAudit(start,new int[4],new int[4],false,null).verify(start),"distinct reduced");
        start=deal();start.bids=new int[]{-1,0,-1,-1};assertReject(new OriginalRomanianAudit(start,new int[4],new int[4],false,null).verify(start),"public bid prefix");
    }
    @Test void oneCardDoesNotAdvanceButOppositeResultBreaksAnExistingSeries(){
        assertEquals(4,OriginalRomanianAudit.nextStreak(1,true,4,true));assertEquals(0,OriginalRomanianAudit.nextStreak(1,false,4,true));
        assertEquals(-4,OriginalRomanianAudit.nextStreak(1,false,-4,true));assertEquals(0,OriginalRomanianAudit.nextStreak(1,true,-4,true));
        assertEquals(6,OriginalRomanianAudit.scoreDelta(1,1,1,4,true));assertEquals(-1,OriginalRomanianAudit.scoreDelta(1,0,1,-4,true));
    }
    @Test void originalBiddingWonSentinelMeansZeroOnlyBeforeTheFirstPlay(){
        // Sentinel provenance: full-match-investigation/probe-fast/probe-first-state.txt.
        // That original-app observation is Oh Hell; only its persisted bidding sentinel is reproduced here.
        var start=deal();start.won=new int[]{-1,-1,-1,-1};
        var audit=new OriginalRomanianAudit(start,new int[4],new int[4],false,9);assertPass(audit.verify(start));
        var after=deal();after.bidding=false;after.bids=new int[]{2,0,0,1};after.history=List.of(c(1,14));
        after.remaining=new int[]{1,2,2,2};after.cards[0][0]=c(1,13);after.turn=1;after.won=new int[]{-1,0,0,0};
        assertReject(audit.verify(after),"independently counted won seat0");
        assertReject(audit.verify(after),"conserved completed tricks");
    }
    private static int c(int suit,int rank){return suit*13+rank-2;}
    private static OriginalWhistState deal(){
        var s=new OriginalWhistState();s.players=4;s.mode=9;s.dealer=3;s.handIndex=4;s.handSize=2;s.serializedHandSize=2;s.trump=0;s.trumpCard=c(0,7);s.turn=0;s.firstTurn=0;s.bidding=true;
        s.remaining=new int[]{2,2,2,2};s.won=new int[4];s.bids=new int[]{-1,-1,-1,-1};s.cards=new int[4][2];
        for(int p=0;p<4;p++){s.cards[p][0]=c(1,14-2*p);s.cards[p][1]=c(1,13-2*p);}s.history=List.of();s.scores=new int[5][4];return s;
    }
    private static OriginalWhistState finished(){var s=deal();s.bidding=false;s.handOver=true;s.handIndex=5;s.scores=Arrays.copyOf(s.scores,6);s.remaining=new int[4];s.bids=new int[]{2,0,0,1};s.won=new int[]{2,0,0,0};s.turn=0;
        s.history=List.of(c(1,14),c(1,12),c(1,10),c(1,8),c(1,13),c(1,11),c(1,9),c(1,7));s.scores[5]=new int[]{7,5,5,-1};return s;}
    private static void assertPass(List<AtfAssertion> a){assertTrue(a.stream().allMatch(AtfAssertion::passed),()->a.stream().filter(x->!x.passed()).toList().toString());}
    private static void assertReject(List<AtfAssertion> a,String text){assertTrue(a.stream().anyMatch(x->!x.passed()&&x.name().contains(text)),()->a.toString());}
}
