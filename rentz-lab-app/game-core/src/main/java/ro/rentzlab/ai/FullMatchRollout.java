package ro.rentzlab.ai;

import ro.rentzlab.core.*;
import ro.rentzlab.ai.search.RolloutBudget;
import java.util.*;

/** Hypothetical continuation: public ledger plus sampled cards, with shared future shuffles per alternative. */
public final class FullMatchRollout {
    private FullMatchRollout(){}
    public static RentzMatch restore(RentzMatch.Observation view,OpeningDeal world,long hypotheticalSeed,ScoreProfile scoring,Contract selected){
        return restore(view,world,hypotheticalSeed,scoring,selected,new RolloutBudget(Integer.MAX_VALUE));
    }
    public static RentzMatch restore(RentzMatch.Observation view,OpeningDeal world,long hypotheticalSeed,ScoreProfile scoring,Contract selected,RolloutBudget budget){
        if(view.completedHands().size()+1!=view.handNumber())throw new IllegalArgumentException("Complete public match progress required");
        var game=RentzMatch.fromPublicProgress(world,hypotheticalSeed,scoring,view.chooser(),view.completedHands());
        if(!game.observe(view.seat()).totals().equals(view.totals()))throw new IllegalArgumentException("Public score ledger differs from totals");
        budget.spend();game.choose(view.chooser(),selected,view.blind());
        if(view.phase()==RentzMatch.Phase.PLAY){
            for(var event:view.events())if(event.action()==RentzMatch.Action.PLAY){budget.spend();game.play(event.seat(),event.card());}else if(event.action()==RentzMatch.Action.PASS){budget.spend();game.pass(event.seat());}
            var replay=game.observe(view.seat());
            if(replay.turn()!=view.turn()||!new HashSet<>(replay.legal()).equals(new HashSet<>(view.legal()))||!replay.dealPoints().equals(view.dealPoints()))throw new IllegalArgumentException("Sampled replay differs from public state");
        }
        return game;
    }
    public static List<Integer> complete(RentzMatch game,ScoreProfile scoring,PolicySettings settings){
        return complete(game,scoring,settings,new RolloutBudget(Integer.MAX_VALUE));
    }
    public static List<Integer> complete(RentzMatch game,ScoreProfile scoring,PolicySettings settings,RolloutBudget budget){
        return complete(game,scoring,settings,budget,-1);
    }
    public static List<Integer> complete(RentzMatch game,ScoreProfile scoring,PolicySettings settings,RolloutBudget budget,int rootSeat){
        int actions=0;var state=game.observe(0);int bound=state.players()*8*512;
        while(state.phase()!=RentzMatch.Phase.MATCH_END){
            budget.spend();
            if(state.phase()==RentzMatch.Phase.DEAL_END)game.nextHand();
            else{int actor=state.turn();if(state.phase()==RentzMatch.Phase.CHOOSE){game.reveal(actor);game.choose(actor,TacticalPolicy.choose(game.observe(actor)),false);}
                else{var own=actor==0?state:game.observe(actor);if(own.legal().isEmpty())game.pass(actor);else game.play(actor,TacticalPolicy.continuation(own,rootSeat,scoring,settings));}}
            if(++actions>bound)throw new IllegalStateException("Full match rollout action bound exceeded");
            state=game.observe(0);
        }
        return state.totals();
    }
}
