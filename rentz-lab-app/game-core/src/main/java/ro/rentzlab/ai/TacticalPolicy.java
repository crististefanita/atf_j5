package ro.rentzlab.ai;

import ro.rentzlab.core.*;
import ro.rentzlab.ai.tactics.VoidTactics;

import java.util.*;

/** Local, public-information continuation policy. No sampled or real opponent hand is accepted. */
public final class TacticalPolicy {
    private TacticalPolicy() { }
    public static Card continuation(RentzMatch.Observation view,int rootSeat,ScoreProfile scoring,PolicySettings settings){return select(view,scoring,settings,view.seat()==rootSeat);}
    public static Card card(RentzMatch.Observation view,ScoreProfile scoring,PolicySettings settings) {
        return select(view,scoring,settings,true);
    }
    private static Card select(RentzMatch.Observation view,ScoreProfile scoring,PolicySettings settings,boolean ownContinuation){
        if(view.phase()!=RentzMatch.Phase.PLAY||view.turn()!=view.seat()||view.legal().isEmpty())throw new IllegalArgumentException("Acting player with a legal card required");
        if(ownContinuation&&settings.tactic()==PolicySettings.Tactic.PUBLIC_TARGET_AWARE){
            Card proved=ro.rentzlab.ai.tactics.TargetTactics.tenMaster(view);if(proved!=null)return proved;
            Card local=ro.rentzlab.ai.tactics.TargetTactics.negativeFollow(view,scoring);if(local!=null)return local;
        }
        if(view.contract()==Contract.RENTZ)return sequence(view,settings);
        boolean voidAware = ownContinuation && settings.tactic().usesVoidTactics()
                && VoidTactics.avoidance(view.contract());
        if(view.trick().isEmpty()) {
            if (voidAware) {
                Card loser = VoidTactics.losingLead(view);
                if (loser != null) return loser;
            }
            if(view.contract()==Contract.WHIST)return highest(view.legal());
            if(view.contract()==Contract.TEN_CLUBS)return view.legal().stream().filter(c->!target(c,Card.Suit.CLUBS,10)).min(order()).orElse(view.legal().get(0));
            return lowest(view.legal());
        }
        Card.Suit suit=view.trick().get(0).card().suit();
        boolean follows=view.legal().get(0).suit()==suit;
        if(!follows) {
            if (voidAware) return VoidTactics.discard(view, card -> penalty(card, view.contract(), scoring));
            if(view.contract()==Contract.WHIST||view.contract()==Contract.TEN_CLUBS)
                return view.legal().stream().filter(c->view.contract()!=Contract.TEN_CLUBS||!target(c,Card.Suit.CLUBS,10)).min(order()).orElse(view.legal().get(0));
            return view.legal().stream().max(Comparator.<Card>comparingInt(c->penalty(c,view.contract(),scoring)).thenComparing(order())).orElseThrow(java.util.NoSuchElementException::new);
        }
        int highest=view.trick().stream().map(TrickRules.Play::card).filter(c->c.suit()==suit).mapToInt(Card::rank).max().orElseThrow(java.util.NoSuchElementException::new);
        var losing=view.legal().stream().filter(c->c.rank()<highest).collect(java.util.stream.Collectors.toList());
        var winning=view.legal().stream().filter(c->c.rank()>highest).collect(java.util.stream.Collectors.toList());
        if(view.contract()==Contract.WHIST) {
            if(winning.isEmpty())return lowest(view.legal());
            return view.trick().size()==view.players()-1?lowest(winning):highest(winning);
        }
        if(view.contract()==Contract.TEN_CLUBS) {
            boolean targetVisible=view.trick().stream().anyMatch(p->target(p.card(),Card.Suit.CLUBS,10));
            if(targetVisible&&!winning.isEmpty())return view.trick().size()==view.players()-1?lowest(winning):highest(winning);
            if(!losing.isEmpty())return losing.stream().filter(c->!target(c,Card.Suit.CLUBS,10)).min(order()).orElse(lowest(losing));
            return lowest(view.legal());
        }
        if(!losing.isEmpty())return highest(losing);
        // Last seat already knows the trick is unavoidable: expend strength while it is safe to do so.
        return view.trick().size()==view.players()-1?highest(view.legal()):lowest(view.legal());
    }
    public static Contract choose(RentzMatch.Observation view) {return LabPolicy.choose(view);}
    private static Card sequence(RentzMatch.Observation view,PolicySettings settings) {
        var ace=view.legal().stream().filter(c->c.rank()==14).min(order());if(ace.isPresent())return ace.get();
        Card best=null;double bestValue=Double.NEGATIVE_INFINITY;
        for(Card card:view.legal()) {
            var board=new RentzBoard(view.players());for(Card old:view.board())board.play(old);board.play(card);
            var remaining=new ArrayList<>(view.hand());remaining.remove(card);
            int chain=0;boolean changed;
            do {changed=false;for(Card own:new ArrayList<>(remaining))if(board.fits(own)){board.play(own);remaining.remove(own);chain++;changed=true;}}while(changed);
            double value=chain*settings.ownChainWeight();
            if(card.rank()==15-2*view.players()) {
                int next=nextActive(view.seat(),view.handSizes());
                value+=settings.skipThreatWeight()/(double)view.handSizes().get(next);
            }
            int exposed=0;var actual=new RentzBoard(view.players());for(Card old:view.board())actual.play(old);actual.play(card);
            for(Card unknown:Card.deck(view.players()))if(!view.hand().contains(unknown)&&!view.seen().contains(unknown)&&actual.fits(unknown))exposed++;
            value-=settings.releaseWeight()*exposed;
            if(value>bestValue+1e-9||Math.abs(value-bestValue)<=1e-9&&(best==null||order().compare(card,best)<0)){best=card;bestValue=value;}
        }
        return Objects.requireNonNull(best);
    }
    private static int nextActive(int seat,List<Integer> sizes){for(int step=1;step<=sizes.size();step++){int next=(seat+step)%sizes.size();if(sizes.get(next)>0)return next;}throw new IllegalArgumentException("No active seat");}
    private static int penalty(Card card,Contract contract,ScoreProfile scoring) {
        return switch(contract) {
            case QUEENS -> card.rank()==12?scoring.queenPenalty():0;
            case DIAMONDS -> card.suit()==Card.Suit.DIAMONDS?scoring.diamondPenalty():0;
            case KING_HEARTS -> target(card,Card.Suit.HEARTS,13)?scoring.kingPenalty():0;
            case TOTALS -> (card.rank()==12?scoring.queenPenalty():0)+(card.suit()==Card.Suit.DIAMONDS?scoring.diamondPenalty():0)+(target(card,Card.Suit.HEARTS,13)?scoring.kingPenalty():0);
            default -> 0;
        };
    }
    private static boolean target(Card card,Card.Suit suit,int rank){return card.suit()==suit&&card.rank()==rank;}
    static Comparator<Card> order(){return Comparator.comparingInt(Card::rank).thenComparing(c->c.suit().ordinal());}
    private static Card lowest(List<Card> cards){return cards.stream().min(order()).orElseThrow(java.util.NoSuchElementException::new);}
    private static Card highest(List<Card> cards){return cards.stream().max(order()).orElseThrow(java.util.NoSuchElementException::new);}
}
