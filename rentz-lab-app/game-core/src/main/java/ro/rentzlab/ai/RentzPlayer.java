package ro.rentzlab.ai;

import ro.rentzlab.core.*;

import java.util.List;
import java.util.Objects;

/** Stable product port: player decisions accept only actor observations. */
public final class RentzPlayer {
    public enum Difficulty { NORMAL, HARD }
    private final Difficulty difficulty;
    private final ScoreProfile scoring;
    private final PolicySettings settings;
    private final SearchPolicy search;
    public RentzPlayer(Difficulty difficulty,ScoreProfile scoring,PolicySettings settings){this.difficulty=Objects.requireNonNull(difficulty);this.scoring=Objects.requireNonNull(scoring);this.settings=Objects.requireNonNull(settings);search=new SearchPolicy(scoring,settings);}
    public SearchPolicy.Decision<Contract> choose(RentzMatch.Observation observation){
        if(difficulty==Difficulty.HARD)return search.choose(observation);
        return new SearchPolicy.Decision<>(TacticalPolicy.choose(observation),0,"NORMAL_BASELINE_CHOICE",java.util.Collections.emptyList());
    }
    public SearchPolicy.Decision<Card> card(RentzMatch.Observation observation){
        if(difficulty==Difficulty.HARD)return search.card(observation);
        return new SearchPolicy.Decision<>(TacticalPolicy.card(observation,scoring,settings),0,"NORMAL_PUBLIC_TACTICAL",java.util.Collections.emptyList());
    }
}
