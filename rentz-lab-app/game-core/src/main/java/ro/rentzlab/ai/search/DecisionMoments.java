package ro.rentzlab.ai.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.ai.SearchPolicy;
import ro.rentzlab.core.Card;

/** Commits complete common-world batches and selects with the original stable option order. */
final class DecisionMoments<T> {
    private final List<T> options;
    private final double[] sums;
    private final double[] squares;
    private final double[] gaps;
    private int completed;

    DecisionMoments(List<T> options) {
        this.options = options;
        sums = new double[options.size()];
        squares = new double[options.size()];
        gaps = new double[options.size()];
    }

    void commit(double[] values, double[] worldGaps) {
        for (int option = 0; option < options.size(); option++) {
            sums[option] += values[option];
            squares[option] += values[option] * values[option];
            gaps[option] += worldGaps[option];
        }
        completed++;
    }

    int completed() { return completed; }

    SearchPolicy.Decision<T> decision(PolicySettings.Objective objective, double riskWeight, boolean full, SearchPolicy.Work work) {
        var estimates = new ArrayList<SearchPolicy.Estimate<T>>();
        T best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestGap = Double.NEGATIVE_INFINITY;
        for (int option = 0; option < options.size(); option++) {
            double mean = sums[option] / completed;
            double spread = Math.sqrt(Math.max(0, squares[option] / completed - mean * mean));
            double score = mean - riskWeight * spread;
            double gap = gaps[option] / completed;
            estimates.add(new SearchPolicy.Estimate<>(options.get(option), mean, spread, score));
            if (score > bestScore + 1e-9 || full && Math.abs(score - bestScore) <= 1e-9 && gap > bestGap + 1e-9) {
                best = options.get(option);
                bestScore = score;
                bestGap = gap;
            }
        }
        String method = full ? "COMMON_FEASIBLE_WORLDS_FULL_MATCH_" + objective : "COMMON_FEASIBLE_WORLDS_ROLLOUT";
        return new SearchPolicy.Decision<>(Objects.requireNonNull(best), completed, method, Card.immutable(estimates), work);
    }
}
