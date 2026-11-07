package atf.rentz.evaluation;

import atf.testkit.AtfUnitTest;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class ClusteredEvaluationTest {
    static List<ClusteredEvaluation.Sample> samples(int seeds,double margin,double points,double wins){var result=new ArrayList<ClusteredEvaluation.Sample>();for(int s=0;s<seeds;s++)for(int seat=0;seat<4;seat++)result.add(new ClusteredEvaluation.Sample(s,seat,margin,points,wins));return result;}
    @Test void rotationsDoNotInflateTheNumberOfIndependentDistributions(){var r=ClusteredEvaluation.assess(samples(1,5,5,.5),4,ClusteredEvaluation.Metric.WIN_SHARE,2);assertEquals(1,r.seedClusters());assertFalse(r.sufficient());assertFalse(r.promotes());}
    @Test void positiveMarginCannotPromoteARegressingWinObjective(){var r=ClusteredEvaluation.assess(samples(8,100,100,-.25),4,ClusteredEvaluation.Metric.WIN_SHARE,2);assertEquals(-.25,r.mean());assertFalse(r.promotes());assertTrue(ClusteredEvaluation.assess(samples(8,100,100,-.25),4,ClusteredEvaluation.Metric.MARGIN,2).promotes());}
    @Test void completePositiveClustersPassAndZeroUncertainOrNegativeOnesFail(){assertTrue(ClusteredEvaluation.assess(samples(8,2,2,.25),4,ClusteredEvaluation.Metric.WIN_SHARE,2).promotes());assertFalse(ClusteredEvaluation.assess(samples(8,0,0,0),4,ClusteredEvaluation.Metric.WIN_SHARE,2).promotes());var mixed=samples(8,0,0,.25);mixed=new ArrayList<>(mixed);for(int n=0;n<16;n++)mixed.set(n,new ClusteredEvaluation.Sample(n/4,n%4,0,0,-.25));assertFalse(ClusteredEvaluation.assess(mixed,4,ClusteredEvaluation.Metric.WIN_SHARE,2).promotes());}
    @Test void duplicateOrMissingRotationsCannotBecomeIndependentEvidence(){var bad=new ArrayList<>(samples(8,2,2,.25));bad.remove(0);assertThrows(IllegalArgumentException.class,()->ClusteredEvaluation.assess(bad,4,ClusteredEvaluation.Metric.WIN_SHARE,2));var duplicate=new ArrayList<>(samples(8,2,2,.25));duplicate.set(0,duplicate.get(1));assertThrows(IllegalArgumentException.class,()->ClusteredEvaluation.assess(duplicate,4,ClusteredEvaluation.Metric.WIN_SHARE,2));}
    @Test void metricsAndFamilyConfidenceAreExplicitAndReproducible(){var rows=samples(8,3,7,.25);var a=ClusteredEvaluation.assess(rows,4,ClusteredEvaluation.Metric.POINTS,2);assertEquals(7,a.mean());assertEquals(.975,a.familyAdjustedConfidence());Collections.reverse(rows);assertEquals(a,ClusteredEvaluation.assess(rows,4,ClusteredEvaluation.Metric.POINTS,2));}

    @Test void eightAliasLabelsCannotSatisfyTheIndependentClusterThreshold() {
        var aliased = samples(8, 2, 2, .25).stream().map(sample ->
                new ClusteredEvaluation.Sample(123L + (sample.seed() << 48), sample.seat(),
                        sample.marginDelta(), sample.pointsDelta(), sample.winDelta())).toList();
        assertThrows(IllegalArgumentException.class, () ->
                ClusteredEvaluation.assess(aliased, 4, ClusteredEvaluation.Metric.WIN_SHARE, 2));

        var distinct = ClusteredEvaluation.assess(samples(8, 2, 2, .25), 4,
                ClusteredEvaluation.Metric.WIN_SHARE, 2);
        assertEquals(8, distinct.seedClusters());
        assertTrue(distinct.sufficient());
    }
}
