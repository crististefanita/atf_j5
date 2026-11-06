package atf.android.e2e;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import ro.whistlab.core.Card;
import ro.whistlab.core.GameMode;
import ro.whistlab.core.GameRules;
import ro.whistlab.core.MonteCarloAi;
import ro.whistlab.core.PlayedCard;
import ro.whistlab.core.Suit;
import ro.whistlab.core.Trump;

/** Seat-zero boundary: the original backup can contain private hands, but this adapter never reads them. */
final class OriginalRomanianPublicDecision {
    enum ExpectedProfile { ROMANIAN_FOUR_PLAYERS }
    record Observation(MonteCarloAi.View view,int nextActor,int submittedBids,boolean bidding) { }
    record Decision(String kind,int value,MonteCarloAi.View view,String json) { }
    private static final ObjectMapper JSON=new ObjectMapper();
    private OriginalRomanianPublicDecision() { }

    static MonteCarloAi.View publicView(OriginalWhistState state,int[] streaks,ExpectedProfile expected) {
        return observe(state,streaks,expected).view();
    }

    static Observation observe(OriginalWhistState s,int[] streaks,ExpectedProfile expected) {
        require(expected==ExpectedProfile.ROMANIAN_FOUR_PLAYERS,"Explicit confirmation of the Romanian four-player profile is required");
        require(s.players==4&&s.handSize>=1&&s.handSize<=8,"Expected a four-player Romanian hand of1..8 cards");
        require(s.dealer>=0&&s.dealer<4,"Dealer seat");
        require(streaks!=null&&streaks.length==4,"Four independently audited streak counters are required");
        require(s.remaining!=null&&s.remaining.length==4&&s.bids!=null&&s.bids.length==4&&s.won!=null&&s.won.length==4,"Public seat counters");
        require(s.scores!=null&&s.handIndex>=0&&s.handIndex<s.scores.length&&s.scores[s.handIndex].length==4,"Current public cumulative scores");
        require(s.cards!=null&&s.cards.length>0&&s.cards[0]!=null&&s.remaining[0]>=0&&s.remaining[0]<=s.cards[0].length,"Observer hand slots");
        for(int streak:streaks)require(streak>=-4&&streak<=4,"Audited series counters must be between-4 and4");

        Trump trump=s.handSize==8?Trump.NO_TRUMP:trump(s.trump);
        Card turned=s.handSize==8?null:card(s.trumpCard);
        require(turned==null||turned.suit().ordinal()==trump.ordinal(),"Turned card must identify the current trump suit");
        List<Card> own=s.hand(0).stream().map(OriginalRomanianPublicDecision::card).toList();
        require(new HashSet<>(own).size()==own.size(),"Distinct observer cards");
        require(turned==null||!own.contains(turned),"Turned card cannot be held by the observer");

        // The eight-card hand has no turned card. A stale serialized trump word must not strip its first real play.
        List<Integer> chronological=s.handSize==8?List.copyOf(s.history):List.copyOf(s.plays());
        require(new HashSet<>(chronological).size()==chronological.size(),"Distinct chronological public plays");
        var history=new ArrayList<PlayedCard>();var trick=new ArrayList<PlayedCard>();
        int next=(s.dealer+1)%4;int[] consumed=new int[4],won=new int[4];
        for(int id:chronological) {
            Card played=card(id);require(!own.contains(played),"A played card cannot remain in the observer hand");
            require(turned==null||!turned.equals(played),"Turned card cannot be played");
            var play=new PlayedCard(next,played);history.add(play);trick.add(play);consumed[next]++;
            if(trick.size()==4) {
                next=independentWinner(trick,trump);won[next]++;trick.clear();
            } else next=(next+1)%4;
        }

        int submitted=0,prior=0;boolean missing=false;
        for(int i=0;i<4;i++) {
            int seat=(s.dealer+1+i)%4,bid=s.bids[seat];
            if(bid<0){require(bid==-1,"Unknown bids use-1");missing=true;continue;}
            require(!missing&&bid<=s.handSize,"Public bids must form a legal chronological prefix");
            require(i!=3||prior+bid!=s.handSize,"Last bid cannot make the bid total equal the hand size");
            prior+=bid;submitted++;
        }
        require(!s.bidding||chronological.isEmpty(),"Bidding cannot contain future card plays");
        require(s.bidding||submitted==4,"All bids must be public before play");
        var seats=new ArrayList<MonteCarloAi.Seat>();
        for(int seat=0;seat<4;seat++) {
            require(s.remaining[seat]>=0&&s.remaining[seat]==s.handSize-consumed[seat],"Public remaining count must match chronological plays");
            require(s.won[seat]==won[seat]||s.bidding&&s.won[seat]==-1,"Public trick count must match independently reconstructed winners");
            seats.add(new MonteCarloAi.Seat(s.remaining[seat],s.bids[seat]<0?null:s.bids[seat],won[seat],s.scores[s.handIndex][seat],streaks[seat]));
        }
        // The original profile uses a+5 reward after five consecutive eligible exact contracts.
        var view=new MonteCarloAi.View(GameMode.ROMANIAN_WHIST,trump,0,s.dealer,s.handSize,own,seats,trick,history,turned,true,5);
        return new Observation(view,s.bidding?(s.dealer+1+submitted)%4:next,submitted,s.bidding);
    }

    static Decision chooseBid(OriginalWhistState s,int[] streaks,ExpectedProfile expected) {
        var observation=observe(s,streaks,expected);
        require(s.bidding&&!s.handOver&&observation.nextActor()==0&&s.bids[0]<0,"Observer must be the current unsubmitted bidder");
        var view=observation.view();int bid=MonteCarloAi.chooseBid(view);
        int prior=view.seats().stream().filter(p->p.bid()!=null).mapToInt(p->p.bid()).sum();
        require(GameRules.allowedBids(view.handSize(),prior,observation.submittedBids()==3).contains(bid),"Accepted Hard returned an illegal bid");
        return decision(s,observation,"bid",bid,"Accepted corrected Hard; legal public/own-hand observation only");
    }

    static Decision chooseCard(OriginalWhistState s,int[] streaks,ExpectedProfile expected) {
        var observation=observe(s,streaks,expected);
        require(!s.bidding&&!s.handOver&&observation.nextActor()==0&&s.turn==0,"Observer must be the independently reconstructed current card actor");
        var view=observation.view();var choice=MonteCarloAi.chooseCard(view);
        Suit led=view.trick().isEmpty()?null:view.trick().get(0).card().suit();
        require(GameRules.legalCards(view.hand(),led,view.trump(),view.mode()).contains(choice.card()),"Accepted Hard returned an illegal card");
        return decision(s,observation,"card",id(choice.card()),choice.method());
    }

    private static Decision decision(OriginalWhistState state,Observation observation,String kind,int value,String method) {
        var v=observation.view();var record=new LinkedHashMap<String,Object>();
        record.put("schemaVersion",1);record.put("sourcePackage","com.qsoft.android.whist");record.put("expectedProfile",ExpectedProfile.ROMANIAN_FOUR_PLAYERS.name());
        record.put("observedNumericMode",state.mode);record.put("numericModeMapping","No numeric mode mapping is inferred by this adapter; the caller explicitly confirms the observed profile.");
        record.put("kind",kind);record.put("observerSeat",0);record.put("handIndex",state.handIndex);record.put("handSize",v.handSize());record.put("dealer",v.dealer());record.put("nextActor",observation.nextActor());
        record.put("trump",v.trump().name());record.put("turnedCardId",v.turnedCard()==null?null:id(v.turnedCard()));
        record.put("observerOwnHand",v.hand().stream().map(OriginalRomanianPublicDecision::id).toList());
        record.put("players",java.util.stream.IntStream.range(0,4).mapToObj(seat->{var p=v.seats().get(seat);var row=new LinkedHashMap<String,Object>();row.put("seat",seat);row.put("remaining",p.remaining());row.put("bid",p.bid());row.put("won",p.won());row.put("remainingNeed",p.bid()==null?null:p.bid()-p.won());row.put("total",p.total());row.put("streak",p.streak());return row;}).toList());
        record.put("history",v.history().stream().map(OriginalRomanianPublicDecision::playRecord).toList());record.put("currentTrick",v.trick().stream().map(OriginalRomanianPublicDecision::playRecord).toList());record.put("historyIncludesCurrentTrick",true);
        record.put("seriesEnabled",true);record.put("seriesBonus",5);record.put("selectedValue",value);record.put("method",method);
        record.put("visibility","Observer's current cards and public facts only; no actual opponent holdings or future actions are read or serialized.");
        try{return new Decision(kind,value,v,JSON.writeValueAsString(record));}
        catch(JsonProcessingException error){throw new IllegalStateException("Cannot serialize the redacted public decision record",error);}
    }

    private static Map<String,Integer> playRecord(PlayedCard p){return Map.of("seat",p.seat(),"cardId",id(p.card()));}
    private static Card card(int id){require(id>=0&&id<52,"Standard original card ID0..51");return new Card(Suit.values()[id/13],id%13+2);}
    private static int id(Card card){return card.suit().ordinal()*13+card.rank()-2;}
    private static Trump trump(int suit){require(suit>=0&&suit<4,"Romanian hands below eight cards require an observed turned trump");return Trump.values()[suit];}
    private static int independentWinner(List<PlayedCard> plays,Trump trump){
        Suit led=plays.getFirst().card().suit();PlayedCard best=plays.getFirst();
        for(var play:plays)if(power(play.card(),led,trump)>power(best.card(),led,trump))best=play;
        return best.seat();
    }
    private static int power(Card c,Suit led,Trump trump){return(c.suit().ordinal()==trump.ordinal()?200:c.suit()==led?100:0)+c.rank();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
