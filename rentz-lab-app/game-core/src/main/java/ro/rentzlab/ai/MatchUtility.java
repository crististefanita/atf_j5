package ro.rentzlab.ai;

import java.util.List;

/** Separate score objectives; win share is conditional on simulated outcomes, not calibrated probability. */
public final class MatchUtility {
    private MatchUtility(){}
    public static double value(List<Integer> scores,int seat,PolicySettings.Objective objective){
        if(scores==null||scores.size()<3||scores.size()>6||seat<0||seat>=scores.size()||objective==null)throw new IllegalArgumentException("Score objective");
        if(scores.stream().anyMatch(java.util.Objects::isNull))throw new IllegalArgumentException("Null score");
        if(objective==PolicySettings.Objective.MATCH_POINTS)return scores.get(seat);
        if (objective == PolicySettings.Objective.MATCH_WIN_SHARE || objective == PolicySettings.Objective.MATCH_WIN_LAST_HAND
                || objective == PolicySettings.Objective.MATCH_WIN_PROVEN) {
            int max = java.util.Collections.max(scores);
            return scores.get(seat) == max ? 1.0 / scores.stream().filter(score -> score == max).count() : 0;
        }
        double other=0;for(int s=0;s<scores.size();s++)if(s!=seat)other+=scores.get(s);
        return scores.get(seat)-other/(scores.size()-1);
    }
    public static double leaderGap(List<Integer> totals,int seat){return totals.get(seat)-(double)java.util.stream.IntStream.range(0,totals.size()).filter(s->s!=seat).map(totals::get).max().orElseThrow();}
}
