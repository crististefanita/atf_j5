package ro.rentzlab.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record Card(Suit suit, int rank) {
    public enum Suit { CLUBS, DIAMONDS, HEARTS, SPADES }
    private static final java.util.Map<Integer,List<Card>> DECKS=createDecks();
    public Card {
        Objects.requireNonNull(suit);
        if (rank < 2 || rank > 14) throw new IllegalArgumentException("Card rank");
    }
    public static List<Card> deck(int players) {
        if (players < 3 || players > 6) throw new IllegalArgumentException("Players must be 3..6");
        return DECKS.get(players);
    }
    private static java.util.Map<Integer,List<Card>> createDecks(){
        var decks=new java.util.HashMap<Integer,List<Card>>();
        for(int players=3;players<=6;players++){var cards=new ArrayList<Card>();for(Suit suit:Suit.values())for(int rank=15-2*players;rank<=14;rank++)cards.add(new Card(suit,rank));decks.put(players,immutable(cards));}
        return java.util.Collections.unmodifiableMap(decks);
    }
    public static <T> List<T> immutable(java.util.Collection<T> values) {
        return java.util.Collections.unmodifiableList(new ArrayList<>(values));
    }
    public String label() {
        String rankText = switch (rank) { case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; case 14 -> "A"; default -> String.valueOf(rank); };
        return rankText + switch(suit) { case CLUBS -> "♣"; case DIAMONDS -> "♦"; case HEARTS -> "♥"; case SPADES -> "♠"; };
    }
}
