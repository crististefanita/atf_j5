package ro.rentzlab.core;

import java.util.ArrayList;
import java.util.List;

/** Sequencing board. The chosen player starts; alternative starting-seat rules need their own profile. */
public final class RentzBoard {
    private final int startRank;
    private final int lowestRank;
    private final List<Card> played = new ArrayList<>();
    private final int[] low=new int[4],high=new int[4];
    private final boolean[][] present=new boolean[4][15];
    public RentzBoard(int players) {
        if(players<3||players>6)throw new IllegalArgumentException("Players must be 3..6");
        startRank = 15 - players;
        lowestRank = 15 - 2 * players;
    }
    public int startRank() { return startRank; }
    public int lowestRank() { return lowestRank; }
    public List<Card> played() { return Card.immutable(played); }
    public List<Card> legal(List<Card> hand) { return hand.stream().filter(this::fits).collect(java.util.stream.Collectors.toList()); }
    public boolean fits(Card card) {
        int suit=card.suit().ordinal();
        if (card.rank() < lowestRank || present[suit][card.rank()]) return false;
        if(low[suit]==0)return card.rank()==startRank;
        return card.rank()==low[suit]-1||card.rank()==high[suit]+1;
    }
    public void play(Card card) {
        if (!fits(card)) throw new IllegalArgumentException("Card does not fit the Rentz board");
        int suit=card.suit().ordinal();present[suit][card.rank()]=true;
        if(low[suit]==0){low[suit]=card.rank();high[suit]=card.rank();}
        else{low[suit]=Math.min(low[suit],card.rank());high[suit]=Math.max(high[suit],card.rank());}
        played.add(card);
    }
}
