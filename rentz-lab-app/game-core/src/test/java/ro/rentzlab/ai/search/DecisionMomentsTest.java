package ro.rentzlab.ai.search;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.ai.SearchPolicy;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.OpeningDeal;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.ScoreProfile;
import ro.rentzlab.core.sequence.SequenceOpening;
import ro.rentzlab.testing.RentzComponentLayer;
import static org.junit.jupiter.api.Assertions.*;

@RentzComponentLayer
class DecisionMomentsTest {
    @Test void equalRiskScoresKeepStableOrderUnlessFullMatchLeaderGapBreaksTheTie() {
        var moments = new DecisionMoments<>(List.of("first","second"));
        moments.commit(new double[]{0,10},new double[]{0,1});
        moments.commit(new double[]{10,0},new double[]{0,1});
        var work = new SearchPolicy.Work(3,2,2,123,128,true);
        var hand = moments.decision(PolicySettings.Objective.HAND_MARGIN, .05, false, work);
        var full = moments.decision(PolicySettings.Objective.MATCH_POINTS, .05, true, work);
        assertEquals("first",hand.selected());
        assertEquals("second",full.selected());
        assertEquals(2,hand.worlds());
        assertEquals(work,hand.work());
        assertEquals(5,hand.estimates().get(0).meanUtility());
        assertEquals(5,hand.estimates().get(0).standardDeviation());
        assertEquals(4.75,hand.estimates().get(0).selectionScore());
    }

    @Test void sampledReplayUsesTheOpeningPolicyOfThePublicGame() {
        var deck = Card.deck(5);
        var deal = new OpeningDeal(java.util.stream.IntStream.range(0, 5)
                .mapToObj(seat -> deck.subList(seat * 8, (seat + 1) * 8)).toList());
        var scoring = ScoreProfile.laboratory();
        for (var policy : SequenceOpening.values()) {
            var original = RentzMatch.fromOpeningDeal(deal, 0, scoring, 0, policy);
            original.choose(0, Contract.RENTZ, false);
            var opener = original.observe(0).legal().get(0);
            original.play(0, opener);
            var view = original.observe(original.observe(0).turn());
            var replay = DecisionEvaluator.restore(view, deal, Contract.RENTZ, scoring, new RolloutBudget(16));
            assertEquals(policy, replay.observe(view.seat()).sequenceOpening());
            assertEquals(List.of(opener), replay.observe(view.seat()).board());
            assertEquals(view.legal(), replay.observe(view.seat()).legal());
        }
    }
}
