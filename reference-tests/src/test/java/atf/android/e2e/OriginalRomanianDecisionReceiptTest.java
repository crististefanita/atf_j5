package atf.android.e2e;

import atf.reporting.contract.AtfAssertion;
import atf.testkit.AtfComponentTest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class OriginalRomanianDecisionReceiptTest {
    @Test void selectedBidMustEqualTheActuallyAcceptedPublicBid(){
        assertPass(OriginalRomanianDecisionReceipt.bid(2,2));
        assertReject(OriginalRomanianDecisionReceipt.bid(2,1),"actually accepted");
        assertReject(OriginalRomanianDecisionReceipt.bid(0,-1),"actually accepted");
    }
    @Test void chosenCardIsMatchedAmidSeveralNewOpponentActions(){
        var before=List.of(play(2,18),play(3,19));
        var after=List.of(play(2,18),play(3,19),play(0,12),play(1,20),play(0,25));
        // Two own actions would not be one accepted input, regardless of the intervening bot play.
        assertReject(OriginalRomanianDecisionReceipt.card(12,2,before,after,1),"exactly one new own");
        after=List.of(play(2,18),play(3,19),play(0,12),play(1,20),play(2,21),play(3,22));
        assertPass(OriginalRomanianDecisionReceipt.card(12,2,before,after,1));
    }
    @Test void aCounterDropCannotHideWrongMissingOrRewrittenCardEvidence(){
        var before=List.of(play(1,5));
        assertReject(OriginalRomanianDecisionReceipt.card(12,2,before,List.of(play(1,5),play(0,11)),1),"equals the actual");
        assertReject(OriginalRomanianDecisionReceipt.card(12,2,before,before,1),"exactly one new own");
        assertReject(OriginalRomanianDecisionReceipt.card(12,2,before,List.of(play(1,6),play(0,12)),1),"prior public prefix");
        assertReject(OriginalRomanianDecisionReceipt.card(12,2,before,List.of(play(1,5),play(0,12)),2),"consumes exactly one");
    }
    @Test void serialSelectionMatchesAdbPropertyEnvironmentAndDefaultPrecedence(){
        assertEquals("property-device",OriginalRomanianDecisionReceipt.selectedAdbSerial(" property-device ","env-device"));
        assertEquals("env-device",OriginalRomanianDecisionReceipt.selectedAdbSerial(" "," env-device "));
        assertEquals("emulator-5554",OriginalRomanianDecisionReceipt.selectedAdbSerial(null," "));
        assertEquals("emulator-5554",OriginalRomanianDecisionReceipt.selectedAdbSerial(null,null));
    }
    @Test void aFull32CardPublicTraceHasBoundedReceiptsAndStillDetectsEveryPriorRewrite(){
        var after=java.util.stream.IntStream.range(0,32).mapToObj(i->play(i%4,(i/8)*13+5+i%8)).toList();
        var before=after.subList(0,28);int selected=after.get(28).card();
        var checks=OriginalRomanianDecisionReceipt.card(selected,1,before,after,0);assertPass(checks);
        var receipt=checks.getFirst();assertTrue(receipt.expected().contains("prefix length=28; SHA-256="));assertTrue(receipt.actual().contains("total observed=32"));
        for(var check:checks){assertTrue(check.expected().length()<=240,check::name);assertTrue(check.actual().length()<=240,check::name);}
        for(int index=0;index<before.size();index++){
            var changed=new ArrayList<>(after);var old=changed.get(index);changed.set(index,play(old.seat(),(old.card()+1)%52));
            assertReject(OriginalRomanianDecisionReceipt.card(selected,1,before,changed,0),"prior public prefix");
        }
    }
    private static OriginalRomanianAudit.PublicPlay play(int seat,int card){return new OriginalRomanianAudit.PublicPlay(seat,card);}
    private static void assertPass(List<AtfAssertion> checks){assertTrue(checks.stream().allMatch(AtfAssertion::passed),()->checks.toString());}
    private static void assertReject(List<AtfAssertion> checks,String text){assertTrue(checks.stream().anyMatch(c->!c.passed()&&c.name().contains(text)),()->checks.toString());}
}
