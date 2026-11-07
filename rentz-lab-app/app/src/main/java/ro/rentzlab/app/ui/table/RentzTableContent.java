package ro.rentzlab.app.ui.table;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import ro.cardtable.ui.cards.CardFace;
import ro.cardtable.ui.hand.HandCard;
import ro.cardtable.ui.seat.SeatContent;
import ro.cardtable.ui.table.PlayedCardContent;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.*;
import java.util.List;
import java.util.stream.IntStream;

/** Public Rentz observation to neutral visual content; no Whist bids, trump or scoring. */
public final class RentzTableContent {
    private RentzTableContent() { }
    public static String id(Card card) { return card.suit().name() + ":" + card.rank(); }
    public static String symbol(Card.Suit suit) {
        return switch (suit) { case CLUBS -> "♣"; case DIAMONDS -> "♦"; case HEARTS -> "♥"; case SPADES -> "♠"; };
    }
    public static CardFace face(Card card) {
        String label = card.label();
        return new CardFace(label.substring(0, label.length() - 1), symbol(card.suit()),
                card.suit() == Card.Suit.HEARTS || card.suit() == Card.Suit.DIAMONDS);
    }
    public static List<SeatContent> seats(RentzMatch.Observation view, RentzUiText copy, String name, boolean paused) {
        return IntStream.range(0, view.players()).mapToObj(seat -> {
            boolean active = !paused && (view.phase() == RentzMatch.Phase.PLAY || view.phase() == RentzMatch.Phase.CHOOSE) && view.turn() == seat;
            var facts = List.of(new SeatContent.Fact(copy.points(), view.dealPoints().get(seat).toString()),
                    new SeatContent.Fact(copy.total(), view.totals().get(seat).toString()));
            String who = copy.name(seat, name);
            return new SeatContent(seat, who, facts, active, who + ". " + copy.points() + " " + facts.get(0).value()
                    + ". " + copy.total() + " " + facts.get(1).value() + (active ? copy.text(". To play", ". La rând") : ""));
        }).collect(immutable());
    }
    public static List<HandCard> hand(RentzMatch.Observation view, RentzUiText copy, boolean paused) {
        if (!view.revealed()) return List.of();
        return view.hand().stream().map(card -> {
            boolean legal = !paused && view.phase() == RentzMatch.Phase.PLAY && view.turn() == view.seat() && view.legal().contains(card);
            return new HandCard(id(card), face(card), legal ? copy.play(card.label()) : card.label() + copy.text(". Unavailable", ". Indisponibilă"), legal);
        }).collect(immutable());
    }
    public static Card selected(RentzMatch.Observation view, String id) {
        if (view.phase() != RentzMatch.Phase.PLAY || view.turn() != view.seat()) throw new IllegalStateException("Not the actor's play phase");
        return view.legal().stream().filter(card -> id(card).equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Card is not currently legal"));
    }
    public static List<PlayedCardContent> plays(List<TrickRules.Play> plays, RentzUiText copy, String name) {
        return plays.stream().map(play -> new PlayedCardContent(play.seat(), play.card().label(), face(play.card()).red(),
                copy.name(play.seat(), name), copy.name(play.seat(), name) + ": " + play.card().label())).collect(immutable());
    }
}
