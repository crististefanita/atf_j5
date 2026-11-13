package atf.rentz;

import java.util.*;
import ro.rentzlab.core.*;
import ro.rentzlab.core.sequence.SequenceOpening;

/** Independent public-transition checks. Does not call the engine's winner, scorer or board helpers. */
public final class OfflineGameAudit {
    private OfflineGameAudit() { }
    public static void card(RentzMatch.Observation before,Card card,RentzMatch.Observation after,ScoreProfile profile) {
        card(before,card,after,profile,RentzMatch.DealEnding.SCORING_CARDS);
    }
    public static void card(RentzMatch.Observation before,Card card,RentzMatch.Observation after,ScoreProfile profile,
                            RentzMatch.DealEnding dealEnding) {
        require(before.turn()==before.seat()&&before.hand().contains(card),"Actor ownership");
        var sizes=new ArrayList<>(before.handSizes());sizes.set(before.seat(),sizes.get(before.seat())-1);require(sizes.equals(after.handSizes()),"Only actor card count changes");
        var seen=new ArrayList<>(before.seen());require(!seen.contains(card),"Card played once");seen.add(card);require(seen.equals(after.seen()),"Public card memory");
        require(after.seen().size()+after.handSizes().stream().mapToInt(Integer::intValue).sum()==before.players()*8,"Card conservation");
        var expectedPoints=new ArrayList<>(before.dealPoints());boolean terminal=false;int next;
        if(before.contract()==Contract.RENTZ) {
            require(fits(before.board(),card,before.players(),openingRank(before.sequenceOpening(),before.players())),"Sequence adjacency");
            var board=new ArrayList<>(before.board());board.add(card);require(board.equals(after.board()),"Sequence board");
            var finished=new ArrayList<>(before.finishOrder());if(sizes.get(before.seat())==0)finished.add(before.seat());
            terminal=finished.size()==before.players()-1;
            if(terminal) {
                for(int seat=0;seat<before.players();seat++)if(!finished.contains(seat))finished.add(seat);
                var awards = profile.finishRewardsFor(before.players());
                for(int place=0;place<before.players();place++)expectedPoints.set(finished.get(place),awards.get(place)*(before.blind()?2:1));
                next=before.turn();
            } else {
                if(card.rank()==14&&sizes.get(before.seat())>0)next=before.seat();
                else{next=nextActive(before.seat(),sizes);if(card.rank()==15-2*before.players())next=nextActive(next,sizes);}
            }
            require(finished.equals(after.finishOrder()),"Finishing order");
        } else {
            var trick=new ArrayList<>(before.trick());
            if(!trick.isEmpty()) {Card.Suit led=trick.get(0).card().suit();if(before.hand().stream().anyMatch(c->c.suit()==led))require(card.suit()==led,"Follow suit");}
            trick.add(new TrickRules.Play(before.seat(),card));
            if(trick.size()<before.players()){next=(before.seat()+1)%before.players();require(trick.equals(after.trick()),"Incomplete trick");}
            else {
                var best=trick.get(0);for(var play:trick)if(play.card().suit()==trick.get(0).card().suit()&&play.card().rank()>best.card().rank())best=play;
                next=best.seat();expectedPoints.set(next,expectedPoints.get(next)+points(before.contract(),trick,profile)*(before.blind()?2:1));
                terminal=sizes.stream().allMatch(n->n==0)||objectiveCaptured(before,seen,dealEnding);
                require(after.trick().isEmpty(),"Completed trick cleared");
            }
        }
        require(after.turn()==next,"Next actor");require(expectedPoints.equals(after.dealPoints()),"Exact per-seat score delta");
        require(terminal?(after.phase()==RentzMatch.Phase.DEAL_END||after.phase()==RentzMatch.Phase.MATCH_END):after.phase()==RentzMatch.Phase.PLAY,"Termination frontier");
    }
    private static boolean objectiveCaptured(RentzMatch.Observation before,List<Card> seen,RentzMatch.DealEnding dealEnding) {
        boolean current=dealEnding.closesOnScoringCards();
        return switch(before.contract()) {
            case KING_HEARTS -> seen.contains(new Card(Card.Suit.HEARTS,13));
            case TEN_CLUBS -> seen.contains(new Card(Card.Suit.CLUBS,10));
            case QUEENS -> current && seen.stream().filter(card->card.rank()==12).count()==4;
            case DIAMONDS -> current && seen.stream().filter(card->card.suit()==Card.Suit.DIAMONDS).count()==2L*before.players();
            default -> false;
        };
    }
    public static void pass(RentzMatch.Observation before,RentzMatch.Observation after) {
        require(before.contract()==Contract.RENTZ&&before.hand().stream().noneMatch(
                c->fits(before.board(),c,before.players(),openingRank(before.sequenceOpening(),before.players()))),
                "Mandatory play before pass");
        require(before.handSizes().equals(after.handSizes())&&before.board().equals(after.board())&&before.seen().equals(after.seen())&&before.dealPoints().equals(after.dealPoints()),"Pass cannot change cards or score");
        require(after.turn()==nextActive(before.seat(),before.handSizes()),"Pass turn");
    }
    private static int points(Contract contract,List<TrickRules.Play> cards,ScoreProfile p) {
        int queens=0,diamonds=0;for(var card:cards){if(card.card().rank()==12)queens++;if(card.card().suit()==Card.Suit.DIAMONDS)diamonds++;}
        int king=has(cards,Card.Suit.HEARTS,13)?p.kingPenalty():0;
        return switch(contract){case TRICKS->-p.trickPenalty();case QUEENS->-queens*p.queenPenalty();case DIAMONDS->-diamonds*p.diamondPenalty();case KING_HEARTS->-king;case TOTALS->-p.trickPenalty()-queens*p.queenPenalty()-diamonds*p.diamondPenalty()-king;case WHIST->p.trickReward();case TEN_CLUBS->has(cards,Card.Suit.CLUBS,10)?p.tenReward():0;case RENTZ->throw new IllegalArgumentException("No trick in Rentz");};
    }
    private static boolean has(List<TrickRules.Play> cards,Card.Suit suit,int rank){return cards.stream().anyMatch(c->c.card().suit()==suit&&c.card().rank()==rank);}
    private static boolean fits(List<Card> board,Card card,int players,int openingRank){if(card.rank()<15-2*players||board.contains(card))return false;int low=15,high=0;for(Card placed:board)if(placed.suit()==card.suit()){low=Math.min(low,placed.rank());high=Math.max(high,placed.rank());}return high==0?card.rank()==openingRank:card.rank()==low-1||card.rank()==high+1;}
    private static int openingRank(SequenceOpening opening,int players){return switch(opening){case JACK->11;case ACTIVE_MIDDLE->15-players;};}
    private static int nextActive(int actor,List<Integer> sizes){for(int n=1;n<=sizes.size();n++){int next=(actor+n)%sizes.size();if(sizes.get(next)>0)return next;}throw new IllegalArgumentException("No active actor");}
    private static void require(boolean condition,String finding){if(!condition)throw new IllegalStateException("OFFLINE_ORACLE: "+finding);}
}
