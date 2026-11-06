package ro.rentzlab.ai.search;

import ro.rentzlab.ai.*;
import ro.rentzlab.core.*;
import java.util.*;

/** Whole common-world batches commit atomically; an exhausted prefix cannot favor early alternatives. */
public final class DecisionEvaluator {
    private DecisionEvaluator(){}
    public static <T> SearchPolicy.Decision<T> evaluate(RentzMatch.Observation view,List<T> options,boolean choosing,ScoreProfile scoring,PolicySettings settings){
        var sampler=new KnowledgeSampler(view);var random=new Random(KnowledgeSampler.fingerprint(view));
        var budget=new RolloutBudget(settings.maxTransitions());boolean full=settings.objective()!=PolicySettings.Objective.HAND_MARGIN;
        var sums=new double[options.size()];var squares=new double[options.size()];var gaps=new double[options.size()];
        int completed=0;boolean exhausted=false;
        for(int n=0;n<settings.worlds();n++){
            long seed=random.nextLong();var world=sampler.sample(seed);var values=new double[options.size()];var worldGaps=new double[options.size()];
            try{
                for(int option=0;option<options.size();option++){
                    Contract contract=choosing?(Contract)options.get(option):view.contract();
                    var simulation=full?FullMatchRollout.restore(view,world,seed^0x6a09e667f3bcc909L,scoring,contract,budget):restore(view,world,contract,scoring,budget);
                    if(!choosing){budget.spend();simulation.play(view.seat(),(Card)options.get(option));}
                    List<Integer> scores;
                    if(full)scores=FullMatchRollout.complete(simulation,scoring,settings,budget,view.seat());
                    else{finishHand(simulation,scoring,settings,budget,view.seat());scores=simulation.history().get(0).points();}
                    values[option]=MatchUtility.value(scores,view.seat(),settings.objective());
                    if(full)worldGaps[option]=MatchUtility.leaderGap(scores,view.seat());
                }
            }catch(RolloutBudget.Exhausted stop){exhausted=true;break;}
            for(int option=0;option<options.size();option++){sums[option]+=values[option];squares[option]+=values[option]*values[option];gaps[option]+=worldGaps[option];}
            completed++;
        }
        var work=new SearchPolicy.Work(settings.worlds(),completed,options.size(),budget.used(),budget.limit(),exhausted);
        if(completed==0){
            @SuppressWarnings("unchecked") T fallback=(T)(choosing?TacticalPolicy.choose(view):TacticalPolicy.card(view,scoring,settings));
            return new SearchPolicy.Decision<>(fallback,0,"PUBLIC_TACTICAL_BUDGET_FALLBACK",Collections.emptyList(),work);
        }
        var estimates=new ArrayList<SearchPolicy.Estimate<T>>();T best=null;double bestScore=Double.NEGATIVE_INFINITY,bestGap=Double.NEGATIVE_INFINITY;
        for(int option=0;option<options.size();option++){
            double mean=sums[option]/completed,spread=Math.sqrt(Math.max(0,squares[option]/completed-mean*mean)),score=mean-settings.riskWeight()*spread,gap=gaps[option]/completed;
            estimates.add(new SearchPolicy.Estimate<>(options.get(option),mean,spread,score));
            if(score>bestScore+1e-9||full&&Math.abs(score-bestScore)<=1e-9&&gap>bestGap+1e-9){best=options.get(option);bestScore=score;bestGap=gap;}
        }
        String method=full?"COMMON_FEASIBLE_WORLDS_FULL_MATCH_"+settings.objective():"COMMON_FEASIBLE_WORLDS_ROLLOUT";
        return new SearchPolicy.Decision<>(Objects.requireNonNull(best),completed,method,Card.immutable(estimates),work);
    }
    private static RentzMatch restore(RentzMatch.Observation view,OpeningDeal world,Contract contract,ScoreProfile scoring,RolloutBudget budget){
        var game=RentzMatch.fromOpeningDeal(world,0,scoring,view.chooser());budget.spend();game.choose(view.chooser(),contract,view.blind());
        if(view.phase()==RentzMatch.Phase.PLAY){
            for(var event:view.events())if(event.action()==RentzMatch.Action.PLAY){budget.spend();game.play(event.seat(),event.card());}else if(event.action()==RentzMatch.Action.PASS){budget.spend();game.pass(event.seat());}
            var replay=game.observe(view.seat());
            if(replay.turn()!=view.turn()||!new HashSet<>(replay.legal()).equals(new HashSet<>(view.legal()))||!replay.dealPoints().equals(view.dealPoints()))throw new IllegalArgumentException("Sampled replay differs from public state");
        }
        return game;
    }
    private static void finishHand(RentzMatch game,ScoreProfile scoring,PolicySettings settings,RolloutBudget budget,int rootSeat){
        int actions=0;var state=game.observe(0);
        while(state.phase()==RentzMatch.Phase.PLAY){
            int actor=state.turn();var own=actor==0?state:game.observe(actor);budget.spend();
            if(own.legal().isEmpty())game.pass(actor);else game.play(actor,TacticalPolicy.continuation(own,rootSeat,scoring,settings));
            if(++actions>512)throw new IllegalStateException("Rollout action bound exceeded");state=game.observe(0);
        }
    }
}
