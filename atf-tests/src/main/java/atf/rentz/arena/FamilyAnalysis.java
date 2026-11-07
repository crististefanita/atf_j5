package atf.rentz.arena;

import atf.rentz.OfflineArena.CostSummary;
import atf.rentz.OfflineArena.FamilySummary;
import atf.rentz.OfflineArena.Kind;
import atf.rentz.OfflineArena.Paired;
import atf.rentz.evaluation.ClusteredEvaluation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure experiment aggregation; simulation, registry exposure and file retention stay outside. */
public final class FamilyAnalysis {
    private FamilyAnalysis() { }

    public static List<Paired> retained(List<Paired> pairs, int count) {
        var ranked = new ArrayList<>(pairs);
        ranked.sort(java.util.Comparator.comparingDouble(Paired::delta));
        var selected = new ArrayList<Paired>();
        for (int n = 0; n < Math.min(count, ranked.size()); n++) selected.add(ranked.get(n));
        if (count > 0 && !ranked.isEmpty() && !selected.contains(ranked.getLast())) selected.add(ranked.getLast());
        return List.copyOf(selected);
    }
    public static String fileName(Paired pair) {
        return "game-" + pair.family().name().toLowerCase(java.util.Locale.ROOT) + "-" + pair.seed() + "-seat" + pair.seat() + ".json";
    }

    public static FamilySummary summarize(Kind family, List<Paired> pairs, List<Long> seeds,
                                          int players, ClusteredEvaluation.Metric metric, int comparisons) {
        var seedMeans = new ArrayList<Double>();
        for (long seed : seeds) {
            seedMeans.add(pairs.stream().filter(pair -> pair.seed() == seed)
                    .mapToDouble(Paired::delta).average().orElseThrow());
        }
        var assessment = ClusteredEvaluation.assess(pairs.stream().map(pair ->
                new ClusteredEvaluation.Sample(pair.seed(), pair.seat(), pair.delta(),
                        pair.rootScore() - (double) pair.controlScore(),
                        pair.winCredit() - pair.controlWinCredit())).toList(), players, metric, comparisons);
        return new FamilySummary(family, pairs.size(), seeds.size(),
                pairs.stream().mapToDouble(Paired::delta).average().orElseThrow(),
                pairs.stream().mapToDouble(Paired::winCredit).average().orElseThrow(),
                Collections.min(seedMeans), Collections.max(seedMeans),
                pairs.stream().mapToDouble(Paired::controlWinCredit).average().orElseThrow(),
                pairs.stream().mapToDouble(pair -> pair.rootScore() - (double) pair.controlScore()).average().orElseThrow(),
                assessment, cost(pairs));
    }

    private static CostSummary cost(List<Paired> pairs) {
        long candidateTransitions = 0;
        long controlTransitions = 0;
        long candidateNanos = 0;
        long controlNanos = 0;
        int candidateFallbacks = 0;
        int controlFallbacks = 0;
        int candidateLimited = 0;
        int controlLimited = 0;
        for (var pair : pairs) {
            var candidate = pair.candidateWork();
            var control = pair.controlWork();
            candidateTransitions += candidate.transitions();
            controlTransitions += control.transitions();
            candidateNanos += candidate.decisionNanos();
            controlNanos += control.decisionNanos();
            candidateFallbacks += candidate.fallbacks();
            controlFallbacks += control.fallbacks();
            candidateLimited += candidate.limitedSearches();
            controlLimited += control.limitedSearches();
        }
        return new CostSummary(candidateTransitions, controlTransitions, candidateNanos, controlNanos,
                candidateFallbacks, controlFallbacks, candidateLimited, controlLimited);
    }
}
