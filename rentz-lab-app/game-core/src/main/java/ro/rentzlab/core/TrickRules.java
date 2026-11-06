package ro.rentzlab.core;

import java.util.List;

public final class TrickRules {
    public record Play(int seat, Card card) { }
    private TrickRules() { }
    public static List<Card> legal(List<Card> hand, List<Play> trick) {
        if (trick.isEmpty()) return Card.immutable(hand);
        var matching = hand.stream().filter(c -> c.suit() == trick.get(0).card().suit()).collect(java.util.stream.Collectors.toList());
        return matching.isEmpty() ? Card.immutable(hand) : Card.immutable(matching);
    }
    public static int winner(List<Play> trick) {
        if (trick.isEmpty()) throw new IllegalArgumentException("Empty trick");
        var suit = trick.get(0).card().suit();
        return trick.stream().filter(p -> p.card().suit() == suit)
                .max(java.util.Comparator.comparingInt(p -> p.card().rank())).orElseThrow(java.util.NoSuchElementException::new).seat();
    }
}
