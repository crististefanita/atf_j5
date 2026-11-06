package ro.rentzlab.core;

import java.util.*;

/** Common-world one-deal lookahead using only the supplied actor observation. */
public final class SearchPolicy {
    public record Estimate<T>(T option,double meanUtility,double standardDeviation,double selectionScore) { }
    public record Decision<T>(T selected,int worlds,String method,List<Estimate<T>> estimates) { }
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
        if(view.contract()==Contract.RENTZ) {
            var ace=options.stream().filter(c->c.rank()==14).findFirst();
            if(ace.isPresent())return new Decision<>(ace.get(),0,"PUBLIC_ACE_EXTRA_TURN",Collections.emptyList());
        }
        return evaluate(view,options,false);
    }
    private <T> Decision<T> evaluate(RentzMatch.Observation view,List<T> options,boolean choosing) {
        var sampler=new KnowledgeSampler(view);var random=new Random(KnowledgeSampler.fingerprint(view));
        var worlds=new ArrayList<OpeningDeal>();for(int n=0;n<settings.worlds();n++)worlds.add(sampler.sample(random.nextLong()));
        var estimates=new ArrayList<Estimate<T>>();T best=null;double bestScore=Double.NEGATIVE_INFINITY;
        for(T option:options) {
            double sum=0,squares=0;
            for(var world:worlds) {
                var simulation=restore(view,world,choosing?(Contract)option:view.contract());
                if(!choosing)simulation.play(view.seat(),(Card)option);
                int actions=0;
                var publicView=simulation.observe(0);
                while(publicView.phase()==RentzMatch.Phase.PLAY) {
                    int actor=publicView.turn();var observation=actor==0?publicView:simulation.observe(actor);
                    if(observation.legal().isEmpty())simulation.pass(actor);
                    else simulation.play(actor,TacticalPolicy.card(observation,scoring,settings));
                    if(++actions>512)throw new IllegalStateException("Rollout action bound exceeded");
                    publicView=simulation.observe(0);
                }
                var points=simulation.history().get(0).points();double other=0;
                for(int seat=0;seat<view.players();seat++)if(seat!=view.seat())other+=points.get(seat);
                double value=points.get(view.seat())-other/(view.players()-1);
                sum+=value;squares+=value*value;
            }
            double mean=sum/worlds.size(),spread=Math.sqrt(Math.max(0,squares/worlds.size()-mean*mean));
            double score=mean-settings.riskWeight()*spread;
            estimates.add(new Estimate<>(option,mean,spread,score));
            if(score>bestScore+1e-9){best=option;bestScore=score;}
        }
        return new Decision<>(Objects.requireNonNull(best),worlds.size(),"COMMON_FEASIBLE_WORLDS_ROLLOUT",Card.immutable(estimates));
    }
    private RentzMatch restore(RentzMatch.Observation view,OpeningDeal world,Contract contract) {
        var simulation=RentzMatch.fromOpeningDeal(world,0,scoring,view.chooser());
        simulation.choose(view.chooser(),contract,view.blind());
        if(view.phase()==RentzMatch.Phase.PLAY)for(var event:view.events()) {
            if(event.action()==RentzMatch.Action.PLAY)simulation.play(event.seat(),event.card());
            else if(event.action()==RentzMatch.Action.PASS)simulation.pass(event.seat());
        }
        if(view.phase()==RentzMatch.Phase.PLAY) {
            var replay=simulation.observe(view.seat());
            if(replay.turn()!=view.turn()||!new HashSet<>(replay.legal()).equals(new HashSet<>(view.legal()))||!replay.dealPoints().equals(view.dealPoints()))throw new IllegalArgumentException("Sampled replay differs from public state");
        }
        return simulation;
    }
}
