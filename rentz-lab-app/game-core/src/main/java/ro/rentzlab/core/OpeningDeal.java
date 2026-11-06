package ro.rentzlab.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Validated, immutable first distribution for offline scenarios, separate from actor observations. */
public record OpeningDeal(List<List<Card>> hands) {
    public OpeningDeal {
        if (hands == null) throw new IllegalArgumentException("Hands required");
        var deck = Card.deck(hands.size());
        var copy = new ArrayList<List<Card>>();
        var distinct = new HashSet<Card>();
        for (var hand : hands) {
            if (hand == null || hand.size() != 8) throw new IllegalArgumentException("Eight cards per seat required");
            for (var card : hand)
                if (card == null || !distinct.add(card)) throw new IllegalArgumentException("Distinct non-null cards required");
            copy.add(Card.immutable(hand));
        }
        if (!distinct.equals(new HashSet<>(deck))) throw new IllegalArgumentException("Distribution must partition the active deck");
        hands = Card.immutable(copy);
    }

    public int players() { return hands.size(); }
}
