package ro.rentzlab.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Independent lab model. Rule-profile parity with the reference requires explicit qualification. */
public final class RentzMatch {
    public enum Phase { CHOOSE, PLAY, DEAL_END, MATCH_END }
    public enum Action { CHOOSE, PLAY, PASS }
    public record TurnEvent(int sequence,int seat,Action action,Card card,int boardCardCount) { }
    public record HandResult(int number, int chooser, Contract contract, boolean blind, List<Integer> points, List<Integer> totals) { }
    public record Observation(int seat, int players, int turn, int chooser, int handNumber, Phase phase,
                              Contract contract, boolean blind, boolean revealed, List<Card> hand, List<Card> legal,
                              List<Integer> handSizes, List<Integer> dealPoints, List<Integer> totals, List<Integer> finishOrder, List<TrickRules.Play> trick,
                              List<Card> seen, List<TurnEvent> events, List<Card> board, Set<Contract> available) { }
    private final int players;
    private final int firstChooser;
    private final ScoreProfile scoring;
    private final Random shuffle;
    private final List<EnumSet<Contract>> remaining = new ArrayList<>();
    private final List<HandResult> history = new ArrayList<>();
    private final List<List<Card>> hands = new ArrayList<>();
    private final List<TrickRules.Play> trick = new ArrayList<>();
    private final List<Card> seen = new ArrayList<>();
    private final List<TurnEvent> events = new ArrayList<>();
    private final List<Integer> finishOrder = new ArrayList<>();
    private final int[] totals, points;
    private final boolean[] revealed;
    private RentzBoard board;
    private Phase phase;
    private Contract contract;
    private int turn, chooser, handNumber;
    private boolean blind;

    public RentzMatch(int players, long seed, ScoreProfile scoring) {
        this(players,seed,scoring,0);
    }
    public RentzMatch(int players, long seed, ScoreProfile scoring, int firstChooser) {
        this(players,seed,scoring,firstChooser,null);
    }
    public static RentzMatch fromOpeningDeal(OpeningDeal opening,long continuationSeed,ScoreProfile scoring,int firstChooser) {
        java.util.Objects.requireNonNull(opening);
        return new RentzMatch(opening.players(),continuationSeed,scoring,firstChooser,opening);
    }
    private RentzMatch(int players,long seed,ScoreProfile scoring,int firstChooser,OpeningDeal opening) {
        Card.deck(players);
        if(firstChooser<0||firstChooser>=players)throw new IllegalArgumentException("First chooser");
        this.firstChooser=firstChooser;
        this.players=players; this.scoring=java.util.Objects.requireNonNull(scoring);scoring.assertMatchSafe(players); shuffle=new Random(seed);
        totals=new int[players]; points=new int[players]; revealed=new boolean[players];
        for(int seat=0;seat<players;seat++) remaining.add(EnumSet.allOf(Contract.class));
        deal(opening);
    }
    private void deal() { deal(null); }
    private void deal(OpeningDeal opening) {
        chooser=(firstChooser+handNumber)%players; turn=chooser; phase=Phase.CHOOSE; contract=null; blind=false;
        hands.clear(); trick.clear(); seen.clear(); events.clear(); finishOrder.clear(); board=new RentzBoard(players);
        java.util.Arrays.fill(points,0); java.util.Arrays.fill(revealed,false);
        if(opening==null) {
            var deck=new ArrayList<>(Card.deck(players)); Collections.shuffle(deck,shuffle);
            for(int seat=0;seat<players;seat++) hands.add(new ArrayList<>(deck.subList(seat*8,seat*8+8)));
        } else for(var hand:opening.hands())hands.add(new ArrayList<>(hand));
    }
    private void seat(int seat) { if(seat<0||seat>=players) throw new IllegalArgumentException("Seat"); }
    public void reveal(int seat) { seat(seat); revealed[seat]=true; }
    public Observation observe(int seat) {
        seat(seat); var visible=phase!=Phase.CHOOSE||revealed[seat];
        var own=visible?Card.immutable(hands.get(seat)):Collections.<Card>emptyList();
        var legal=phase==Phase.PLAY&&turn==seat?legalCards(seat):Collections.<Card>emptyList();
        var sizes=new ArrayList<Integer>(); var scores=new ArrayList<Integer>(); var currentPoints=new ArrayList<Integer>();
        for(int s=0;s<players;s++){sizes.add(hands.get(s).size()); scores.add(totals[s]); currentPoints.add(points[s]);}
        int displayedHand=(phase==Phase.DEAL_END||phase==Phase.MATCH_END)?handNumber:handNumber+1;
        return new Observation(seat,players,turn,chooser,displayedHand,phase,contract,blind,visible,own,Card.immutable(legal),
                Card.immutable(sizes),Card.immutable(currentPoints),Card.immutable(scores),Card.immutable(finishOrder),Card.immutable(trick),Card.immutable(seen),Card.immutable(events),board.played(),
                Collections.unmodifiableSet(EnumSet.copyOf(remaining.get(seat))));
    }
    public List<HandResult> history() { return Card.immutable(history); }
    public void choose(int seat, Contract selected, boolean blind) {
        seat(seat);
        if(phase!=Phase.CHOOSE||seat!=chooser||!remaining.get(seat).contains(selected)||blind&&(revealed[seat]||remaining.get(seat).size()==1))
            throw new IllegalStateException("Contract choice is not legal");
        contract=selected; this.blind=blind; remaining.get(seat).remove(selected); phase=Phase.PLAY;
        events.add(new TurnEvent(events.size()+1,seat,Action.CHOOSE,null,0));
    }
    private List<Card> legalCards(int seat) {
        return contract==Contract.RENTZ?board.legal(hands.get(seat)):TrickRules.legal(hands.get(seat),trick);
    }
    public void play(int seat, Card card) {
        seat(seat);
        if(phase!=Phase.PLAY||seat!=turn||!legalCards(seat).contains(card)) throw new IllegalStateException("Illegal card");
        hands.get(seat).remove(card); seen.add(card);
        events.add(new TurnEvent(events.size()+1,seat,Action.PLAY,card,board.played().size()));
        if(contract==Contract.RENTZ){
            board.play(card);
            if(hands.get(seat).isEmpty())finishOrder.add(seat);
            if(finishOrder.size()==players-1){
                for(int s=0;s<players;s++)if(!finishOrder.contains(s))finishOrder.add(s);
                for(int place=0;place<players;place++) points[finishOrder.get(place)]=Math.multiplyExact(scoring.finishRewards().get(place),blind?2:1);
                finish(); return;
            }
            if(card.rank()==14&&!hands.get(seat).isEmpty())return;
            turn=nextActive(seat);
            if(card.rank()==board.lowestRank())turn=nextActive(turn);
            return;
        }
        trick.add(new TrickRules.Play(seat,card));
        if(trick.size()<players){turn=(turn+1)%players;return;}
        int winner=TrickRules.winner(trick);
        var captured=new ArrayList<Card>(); for(var play:trick)captured.add(play.card());
        points[winner]=Math.addExact(points[winner],scoring.trickPoints(contract,captured,blind));
        boolean terminal=hands.get(0).isEmpty()
                ||contract==Contract.KING_HEARTS&&captured.contains(new Card(Card.Suit.HEARTS,13))
                ||contract==Contract.TEN_CLUBS&&captured.contains(new Card(Card.Suit.CLUBS,10));
        trick.clear(); turn=winner; if(terminal)finish();
    }
    private int nextActive(int seat) {
        for(int n=1;n<=players;n++){int next=(seat+n)%players;if(!hands.get(next).isEmpty())return next;}
        throw new IllegalStateException("No active player");
    }
    public void pass(int seat) {
        seat(seat);
        if(phase!=Phase.PLAY||contract!=Contract.RENTZ||turn!=seat||!legalCards(seat).isEmpty())throw new IllegalStateException("Pass is not legal");
        events.add(new TurnEvent(events.size()+1,seat,Action.PASS,null,board.played().size()));
        turn=nextActive(seat);
    }
    private void finish() {
        var delta=new ArrayList<Integer>();var sum=new ArrayList<Integer>();
        for(int s=0;s<players;s++){totals[s]=Math.addExact(totals[s],points[s]);delta.add(points[s]);sum.add(totals[s]);}
        history.add(new HandResult(handNumber+1,chooser,contract,blind,Card.immutable(delta),Card.immutable(sum)));
        handNumber++; phase=handNumber==players*Contract.values().length?Phase.MATCH_END:Phase.DEAL_END;
    }
    public void nextHand() { if(phase!=Phase.DEAL_END)throw new IllegalStateException("Deal is not complete");deal(); }
}
