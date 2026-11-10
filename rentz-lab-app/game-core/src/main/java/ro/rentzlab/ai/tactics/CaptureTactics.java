package ro.rentzlab.ai.tactics;

import java.util.Comparator;
import ro.rentzlab.ai.KnowledgeSampler;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.TrickRules;

/** Conservative capture proof from the current trick and public constraints on the seats still to act. */
public final class CaptureTactics {
    private static final Card TARGET = new Card(Card.Suit.CLUBS, 10);

    private CaptureTactics() { }

    public static Card cheapestSafeCapture(RentzMatch.Observation view, KnowledgeSampler knowledge) {
        var trick = view.trick();
        boolean whist = view.contract() == Contract.WHIST;
        if (!whist && view.contract() != Contract.TEN_CLUBS) return null;
        boolean targetOnTable = trick.stream().anyMatch(play -> play.card().equals(TARGET));
        if (whist ? trick.isEmpty() : !targetOnTable && !view.legal().contains(TARGET)) return null;
        var led = trick.isEmpty() ? TARGET.suit() : trick.get(0).card().suit();
        int rankToBeat = trick.stream().map(TrickRules.Play::card)
                .filter(card -> card.suit() == led).mapToInt(Card::rank).max().orElse(0);
        int safeRank = Math.max(rankToBeat, highestThreat(view, knowledge, led));
        return view.legal().stream()
                .filter(card -> card.suit() == led && card.rank() > safeRank)
                .filter(card -> whist || targetOnTable || card.equals(TARGET))
                .min(Comparator.comparingInt(Card::rank)).orElse(null);
    }

    private static int highestThreat(RentzMatch.Observation view, KnowledgeSampler knowledge, Card.Suit led) {
        var unknown = knowledge.unknownCards();
        int seatsBehind = view.players() - view.trick().size() - 1;
        int highest = 0;
        for (int offset = 1; offset <= seatsBehind; offset++) {
            int seat = (view.seat() + offset) % view.players();
            for (var card : unknown) {
                if (card.suit() == led && knowledge.allows(seat, card)) highest = Math.max(highest, card.rank());
            }
        }
        return highest;
    }
}
