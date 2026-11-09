package ro.rentzlab.ai;

import ro.rentzlab.core.*;
import ro.rentzlab.ai.tactics.VoidTactics;

import java.util.*;

/** Common-world bounded lookahead; the selected objective declares hand or full-match scope. */
public final class SearchPolicy {
    public record Estimate<T>(T option,double meanUtility,double standardDeviation,double selectionScore) { }
    public record Work(int requestedWorlds,int completedWorlds,int alternatives,int transitions,int transitionLimit,boolean exhausted) {
        public static Work none(){return new Work(0,0,0,0,0,false);}
    }
    public record Decision<T>(T selected,int worlds,String method,List<Estimate<T>> estimates,Work work) {
        public Decision(T selected,int worlds,String method,List<Estimate<T>> estimates){this(selected,worlds,method,estimates,Work.none());}
    }
    private final ScoreProfile scoring;
    private final PolicySettings settings;
    public SearchPolicy(ScoreProfile scoring,PolicySettings settings){this.scoring=Objects.requireNonNull(scoring);this.settings=Objects.requireNonNull(settings);}
    public Decision<Contract> choose(RentzMatch.Observation view) {
        if(view.phase()!=RentzMatch.Phase.CHOOSE||view.turn()!=view.seat()||!view.revealed()||view.available().isEmpty())throw new IllegalArgumentException("Choice observation");
        var options=new ArrayList<>(view.available());options.sort(Comparator.comparingInt(Contract::ordinal));
        return evaluate(view,options,true);
    }
    public Decision<Card> card(RentzMatch.Observation view) {
        if(view.phase()!=RentzMatch.Phase.PLAY||view.turn()!=view.seat()||view.legal().isEmpty())throw new IllegalArgumentException("Card observation");
        var options=new ArrayList<>(view.legal());options.sort(TacticalPolicy.order());
        if(options.size()==1)return new Decision<>(options.get(0),0,"FORCED_LEGAL_CARD",Collections.emptyList());
        if((settings.tactic()==PolicySettings.Tactic.PUBLIC_TARGET_AWARE||settings.tactic()==PolicySettings.Tactic.PUBLIC_TEN_PROOF_ONLY)&&settings.objective()==PolicySettings.Objective.HAND_MARGIN){
            Card proved=ro.rentzlab.ai.tactics.TargetTactics.tenMaster(view);
            if(proved!=null)return new Decision<>(proved,0,"PUBLIC_TEN_MASTER_CAPTURE",Collections.emptyList());
        }
        if(view.contract()==Contract.RENTZ) {
            var ace=options.stream().filter(c->c.rank()==14).findFirst();
            if(ace.isPresent())return new Decision<>(ace.get(),0,"PUBLIC_ACE_EXTRA_TURN",Collections.emptyList());
        }
        if (settings.tactic() == PolicySettings.Tactic.PUBLIC_VOID_AWARE && VoidTactics.avoidance(view.contract())) {
            Card loser = VoidTactics.highestForcedLoser(view);
            if (loser != null) return new Decision<>(loser, 0, "PUBLIC_HIGHEST_FORCED_LOSER", Collections.emptyList());
            Card preferred = TacticalPolicy.card(view, scoring, settings);
            options.remove(preferred);
            options.add(0, preferred);
        }
        return evaluate(view,options,false);
    }
    private <T> Decision<T> evaluate(RentzMatch.Observation view,List<T> options,boolean choosing) {
        return ro.rentzlab.ai.search.DecisionEvaluator.evaluate(view,options,choosing,scoring,settings);
    }
}
