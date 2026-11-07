package ro.rentzlab.ai.search;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.ai.SearchPolicy;
import ro.rentzlab.testing.RentzComponentLayer;
import static org.junit.jupiter.api.Assertions.*;

@RentzComponentLayer
class DecisionMomentsTest {
    @Test void equalRiskScoresKeepStableOrderUnlessFullMatchLeaderGapBreaksTheTie() {
        var moments = new DecisionMoments<>(List.of("first","second"));
        moments.commit(new double[]{0,10},new double[]{0,1});
        moments.commit(new double[]{10,0},new double[]{0,1});
        var work = new SearchPolicy.Work(3,2,2,123,128,true);
        var settings = new PolicySettings(3,.05,3,.3,2);
        var hand = moments.decision(settings,false,work);
        var full = moments.decision(settings,true,work);
        assertEquals("first",hand.selected());
        assertEquals("second",full.selected());
        assertEquals(2,hand.worlds());
        assertEquals(work,hand.work());
        assertEquals(5,hand.estimates().get(0).meanUtility());
        assertEquals(5,hand.estimates().get(0).standardDeviation());
        assertEquals(4.75,hand.estimates().get(0).selectionScore());
    }
}
