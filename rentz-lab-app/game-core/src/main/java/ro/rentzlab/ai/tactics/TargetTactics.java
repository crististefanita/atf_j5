package ro.rentzlab.ai.tactics;

import ro.rentzlab.core.*;
import java.util.*;

/** Narrow public proofs and local penalty ordering; no real opposing cards or dealing seed. */
public final class TargetTactics {
    private TargetTactics(){}
    private static final Comparator<Card> STRENGTH=Comparator.comparingInt(Card::rank).thenComparing(c->c.suit().ordinal());
    public static Card tenMaster(RentzMatch.Observation view){
        if(view.contract()!=Contract.TEN_CLUBS)return null;
        var ten=new Card(Card.Suit.CLUBS,10);if(!view.hand().contains(ten))return null;
        for(Card card:Card.deck(view.players()))if(card.suit()==Card.Suit.CLUBS&&card.rank()>10&&!view.hand().contains(card)&&!view.seen().contains(card))return null;
        if(view.trick().isEmpty())return view.legal().contains(ten)?ten:null;
        if(view.trick().get(0).card().suit()!=Card.Suit.CLUBS)return null;
        int top=view.trick().stream().map(TrickRules.Play::card).filter(c->c.suit()==Card.Suit.CLUBS).mapToInt(Card::rank).max().orElseThrow(java.util.NoSuchElementException::new);
        if(ten.rank()>top&&view.legal().contains(ten))return ten;
        return view.legal().stream().filter(c->c.rank()>top).min(STRENGTH).orElse(null);
    }
    public static Card negativeFollow(RentzMatch.Observation view,ScoreProfile scoring){
        if(view.trick().isEmpty()||view.contract()==Contract.WHIST||view.contract()==Contract.TEN_CLUBS||view.contract()==Contract.RENTZ)return null;
        Card.Suit led=view.trick().get(0).card().suit();if(view.legal().get(0).suit()!=led)return null;
        int highest=view.trick().stream().map(TrickRules.Play::card).filter(c->c.suit()==led).mapToInt(Card::rank).max().orElseThrow(java.util.NoSuchElementException::new);
        var loser=view.legal().stream().filter(c->c.rank()<highest).max(Comparator.<Card>comparingInt(c->penalty(c,view.contract(),scoring)).thenComparing(STRENGTH));
        if(loser.isPresent())return loser.get();
        if(view.trick().size()!=view.players()-1)return null;
        int cost=view.legal().stream().mapToInt(c->penalty(c,view.contract(),scoring)).min().orElseThrow(java.util.NoSuchElementException::new);
        return view.legal().stream().filter(c->penalty(c,view.contract(),scoring)==cost).max(STRENGTH).orElseThrow(java.util.NoSuchElementException::new);
    }
    private static int penalty(Card card,Contract contract,ScoreProfile scoring){
        int queen=card.rank()==12?scoring.queenPenalty():0;
        int diamond=card.suit()==Card.Suit.DIAMONDS?scoring.diamondPenalty():0;
        int king=card.suit()==Card.Suit.HEARTS&&card.rank()==13?scoring.kingPenalty():0;
        return switch(contract){case QUEENS->queen;case DIAMONDS->diamond;case KING_HEARTS->king;case TOTALS->queen+diamond+king;default->0;};
    }
}
