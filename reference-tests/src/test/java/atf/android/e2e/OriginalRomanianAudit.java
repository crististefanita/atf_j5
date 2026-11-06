package atf.android.e2e;

import atf.reporting.contract.AtfAssertion;
import java.util.*;

/** Independent original-app auditor. Private deal data stays here, never in its public-play projection. */
final class OriginalRomanianAudit {
    record PublicPlay(int seat,int card) { }
    private final int index,size,dealer,trump,turned,mode;
    private final Integer requiredMode;
    private final boolean series;
    private final int[] prior,priorStreak,bids={-1,-1,-1,-1};
    private final List<List<Integer>> initial;
    private List<PublicPlay> observed=List.of(),table=List.of();
    private int[] won=new int[4];
    private int next;
    private boolean complete;

    OriginalRomanianAudit(OriginalWhistState deal,int expectedHandIndex,int expectedDealer,
            int[] previousTotals,int[] previousStreaks,boolean seriesEnabled,Integer expectedMode) {
        if(previousTotals.length!=4||previousStreaks.length!=4)throw new IllegalArgumentException("Four score/streak counters required");
        if(deal.players!=4||deal.remaining==null||deal.cards==null||!deal.plays().isEmpty())
            throw new IllegalArgumentException("Audit requires a four-player initial backup before any card play");
        index=expectedHandIndex;size=schedule().get(index);dealer=expectedDealer;
        if(dealer<0||dealer>3)throw new IllegalArgumentException("Dealer seat0..3 required");
        trump=deal.trump;turned=deal.trumpCard;mode=deal.mode;requiredMode=expectedMode;
        series=seriesEnabled;prior=previousTotals.clone();priorStreak=previousStreaks.clone();
        var copy=new ArrayList<List<Integer>>();for(int s=0;s<4;s++)copy.add(List.copyOf(deal.hand(s)));
        initial=List.copyOf(copy);next=(dealer+1)%4;
    }
    OriginalRomanianAudit(OriginalWhistState deal,int[] previousTotals,int[] previousStreaks,
            boolean seriesEnabled,Integer expectedMode) {
        this(deal,deal.handIndex,deal.dealer,previousTotals,previousStreaks,seriesEnabled,expectedMode);
    }
    static List<Integer> schedule(){
        var r=new ArrayList<Integer>();for(int i=0;i<4;i++)r.add(1);for(int h=2;h<=7;h++)r.add(h);
        for(int i=0;i<4;i++)r.add(8);for(int h=7;h>=2;h--)r.add(h);for(int i=0;i<4;i++)r.add(1);
        return List.copyOf(r);
    }
    static int dealerAfter(int firstDealer,int handIndex){return Math.floorMod(firstDealer+handIndex,4);}
    List<AtfAssertion> start(OriginalWhistState state){return verify(state);}

    List<AtfAssertion> verify(OriginalWhistState s){
        var checks=new ArrayList<AtfAssertion>();
        equal(checks,"RW01 four players",4,s.players);equal(checks,"RW01 stable observed mode",mode,s.mode);
        if(requiredMode!=null)equal(checks,"RW01 selected Romanian mode",requiredMode,s.mode);
        int expectedObservedHandIndex=index+(s.handOver?1:0);
        equal(checks,"RW02 scheduled hand index",expectedObservedHandIndex,s.handIndex);equal(checks,"RW02 scheduled size",size,s.handSize);
        equal(checks,"RW02 rotating dealer",dealer,s.dealer);equal(checks,"RW02 fixed trump",trump,s.trump);
        if(trump<4)equal(checks,"RW02 fixed turned card",turned,s.trumpCard);
        boolean shape=s.players==4&&s.remaining!=null&&s.remaining.length==4&&s.bids!=null&&s.bids.length==4
                &&s.won!=null&&s.won.length==4&&s.cards!=null&&s.cards.length==4;
        truth(checks,"RW01 complete observation shape","four arrays of four seats",Boolean.toString(shape),shape);
        if(!shape)return checks;
        List<Integer> plays=s.plays();
        var distinct=new HashSet<Integer>();
        int[] observedWon=new int[4];
        boolean biddingBeforePlay=s.bidding&&plays.isEmpty();
        for(int seat=0;seat<4;seat++){
            equal(checks,"RW01 original hand size seat"+seat,size,initial.get(seat).size());
            for(int card:initial.get(seat))truth(checks,"RW01 distinct reduced card seat"+seat,
                    "unique standard rank7..A",Integer.toString(card),validCard(card)&&distinct.add(card));
        }
        if(size==8)equal(checks,"RW02 eight-card hand has no trump",4,trump);
        else truth(checks,"RW02 turned card is undealt and determines trump","unique reduced-deck card matching trump",
                Integer.toString(turned),trump>=0&&trump<4&&validCard(turned)&&turned/13==trump&&!distinct.contains(turned));

        int count=0,sum=0;boolean gap=false;
        for(int position=0;position<4;position++){
            int seat=(dealer+1+position)%4,bid=s.bids[seat];
            if(bid<0){gap=true;equal(checks,"RW03 unsubmitted bid sentinel seat"+seat,-1,bid);equal(checks,"RW03 observed bid cannot disappear seat"+seat,-1,bids[seat]);continue;}
            count++;sum+=bid;
            truth(checks,"RW03 clockwise public bid prefix seat"+seat,"no unsubmitted preceding bidder",Integer.toString(position),!gap);
            truth(checks,"RW03 bid range seat"+seat,"0.."+size,Integer.toString(bid),bid<=size);
            if(bids[seat]>=0)equal(checks,"RW03 immutable observed bid seat"+seat,bids[seat],bid);
            else bids[seat]=bid;
        }
        if(count==4)truth(checks,"RW03 final bid cannot equal available tricks","total != "+size,Integer.toString(sum),sum!=size);
        equal(checks,"RW03 bidding phase agrees with submitted prefix",count<4,s.bidding);
        if(count<4)equal(checks,"RW03 next bidder after public prefix",(dealer+1+count)%4,s.turn);

        truth(checks,"RW07 complete earlier history retained","append-only history",Integer.toString(plays.size()),plays.size()>=observed.size());
        for(int i=0;i<Math.min(plays.size(),observed.size());i++)equal(checks,"RW07 immutable public play"+i,observed.get(i).card(),plays.get(i));
        truth(checks,"RW07 play count bound","0.."+(4*size),Integer.toString(plays.size()),plays.size()<=4*size);
        var hands=new ArrayList<List<Integer>>();for(var hand:initial)hands.add(new ArrayList<>(hand));
        var trace=new ArrayList<PublicPlay>();var current=new ArrayList<PublicPlay>();
        int actor=(dealer+1)%4;int[] counted=new int[4];boolean valid=true;
        for(int i=0;i<plays.size();i++){
            int card=plays.get(i),led=current.isEmpty()?-1:current.getFirst().card()/13;
            List<Integer> choices=legal(hands.get(actor),led,trump);
            boolean held=hands.get(actor).contains(card),allowed=choices.contains(card);
            if(i>=observed.size()){
                truth(checks,"Move"+(i+1)+" seat"+actor+" card ownership",hands.get(actor).toString(),Integer.toString(card),held);
                truth(checks,"Move"+(i+1)+" follow suit or mandatory trump",choices.toString(),Integer.toString(card),allowed);
            }
            if(!held||!allowed){valid=false;break;}
            hands.get(actor).remove(Integer.valueOf(card));var play=new PublicPlay(actor,card);trace.add(play);current.add(play);
            if(current.size()==4){int winner=0;for(int j=1;j<4;j++)if(power(current.get(j).card(),led<0?current.getFirst().card()/13:led,trump)>
                        power(current.get(winner).card(),current.getFirst().card()/13,trump))winner=j;
                actor=current.get(winner).seat();counted[actor]++;
                current.clear();
            }else actor=(actor+1)%4;
        }
        for(int seat=0;seat<4;seat++){
            boolean capacity=s.remaining[seat]>=0&&s.remaining[seat]<=s.cards[seat].length;
            truth(checks,"RW07 remaining capacity seat"+seat,"0..serialized card capacity",Integer.toString(s.remaining[seat]),capacity);
            if(capacity){equal(checks,"RW07 remaining card count seat"+seat,hands.get(seat).size(),s.remaining[seat]);
                equal(checks,"RW07 remaining card identity seat"+seat,new TreeSet<>(hands.get(seat)),new TreeSet<>(s.hand(seat)));}
            // save2.dat uses -1 before the first play; it is not a negative completed-trick count.
            observedWon[seat]=biddingBeforePlay&&s.won[seat]==-1?0:s.won[seat];
            equal(checks,"RW05 independently counted won seat"+seat,counted[seat],observedWon[seat]);
        }
        boolean finished=valid&&trace.size()==size*4&&current.isEmpty();
        equal(checks,"RW07 completed hand flag",finished,s.handOver);
        if(!finished&&!s.bidding&&!s.trickOver)equal(checks,"RW05 next public actor",actor,s.turn);
        equal(checks,"RW07 conserved completed tricks",trace.size()/4,Arrays.stream(observedWon).sum());
        if(valid){observed=List.copyOf(trace);table=List.copyOf(current);won=counted;next=actor;complete=finished;}
        boolean scoreShape=s.scores!=null&&s.handIndex>=0&&s.handIndex<s.scores.length&&s.scores[s.handIndex].length==4;
        truth(checks,"RW06 score row exists","four cumulative scores at current index",Boolean.toString(scoreShape),scoreShape);
        if(scoreShape)for(int seat=0;seat<4;seat++){
            int expected=prior[seat]+(finished?scoreDelta(size,won[seat],bids[seat],priorStreak[seat],series):0);
            equal(checks,"RW06 independent Romanian cumulative score seat"+seat,expected,s.scores[s.handIndex][seat]);
        }
        return checks;
    }
    static List<Integer> legal(List<Integer> hand,int led,int trump){
        if(led<0)return List.copyOf(hand);
        var follow=hand.stream().filter(c->c/13==led).toList();if(!follow.isEmpty())return follow;
        var cuts=trump>=0&&trump<4?hand.stream().filter(c->c/13==trump).toList():List.<Integer>of();
        return cuts.isEmpty()?List.copyOf(hand):cuts;
    }
    static int scoreDelta(int size,int won,int bid,int priorStreak,boolean series){
        boolean exact=won==bid;int base=exact?5+won:-Math.abs(won-bid);
        return base+(!series||size==1?0:exact&&priorStreak==4?5:!exact&&priorStreak==-4?-5:0);
    }
    static int nextStreak(int size,boolean exact,int before,boolean series){
        if(!series)return 0;int run=exact?Math.max(before,0):Math.min(before,0);
        if(size>1)run+=exact?1:-1;return Math.abs(run)==5?0:run;
    }
    int[] totals(){if(!complete)throw new IllegalStateException("Hand is not complete");int[] result=prior.clone();for(int s=0;s<4;s++)result[s]+=scoreDelta(size,won[s],bids[s],priorStreak[s],series);return result;}
    int[] streaks(){if(!complete)throw new IllegalStateException("Hand is not complete");int[] result=new int[4];for(int s=0;s<4;s++)result[s]=nextStreak(size,won[s]==bids[s],priorStreak[s],series);return result;}
    List<PublicPlay> publicPlays(){return observed;}
    List<PublicPlay> currentTrick(){return table;}
    int nextSeat(){return next;}
    int moves(){return observed.size();}
    private static boolean validCard(int c){return c>=0&&c<52&&c%13>=5;}
    private static int power(int c,int led,int trump){return (trump>=0&&trump<4&&c/13==trump?200:c/13==led?100:0)+c%13;}
    private static void equal(List<AtfAssertion> a,String name,Object expected,Object actual){a.add(new AtfAssertion(name,String.valueOf(expected),String.valueOf(actual),Objects.equals(expected,actual)));}
    private static void truth(List<AtfAssertion> a,String name,String expected,String actual,boolean passed){a.add(new AtfAssertion(name,expected,actual,passed));}
}
