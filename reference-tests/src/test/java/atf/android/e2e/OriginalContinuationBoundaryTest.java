package atf.android.e2e;

import static org.junit.jupiter.api.Assertions.*;
import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;

@AtfComponentTest
class OriginalContinuationBoundaryTest {
    @Test void doesNotAdvanceTheScoreOfAnUnrecordedNextHandLastCard() {
        var live=new OriginalWhistState();live.handOver=true;live.handIndex=17;
        assertFalse(CompleteOriginalMatchE2eTest.advanceAfterHand(true,15,16,live));
    }
    @Test void advancesOnlyTheLastRetainedBoundaryWhenTheLiveScoreIsThatSameHand() {
        var live=new OriginalWhistState();live.handOver=true;live.handIndex=16;
        assertTrue(CompleteOriginalMatchE2eTest.advanceAfterHand(true,15,16,live));
        assertFalse(CompleteOriginalMatchE2eTest.advanceAfterHand(true,14,16,live));
        live.handOver=false;
        assertFalse(CompleteOriginalMatchE2eTest.advanceAfterHand(true,15,16,live));
    }
    @Test void newlyCompletedHandsAdvanceWithoutAContinuationObservation() {
        assertTrue(CompleteOriginalMatchE2eTest.advanceAfterHand(false,16,16,null));
        assertFalse(CompleteOriginalMatchE2eTest.advanceAfterHand(true,15,16,null));
    }
}
