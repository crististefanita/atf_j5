package ro.rentzlab.app.ui.table;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import ro.cardtable.ui.cards.CardFace;
import ro.cardtable.ui.hand.HandCard;
import ro.cardtable.ui.seat.SeatContent;
import ro.cardtable.ui.table.PlayedCardContent;
import ro.rentzlab.app.ui.copy.RentzText;
import ro.rentzlab.app.ui.score.RentzScoreContent;
import ro.rentzlab.core.*;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Public Rentz observation to neutral visual content; no Whist bids, trump or scoring. */
public final class RentzTableContent {
    private static final List<Card.Suit> SUIT_ORDER = List.of(Card.Suit.SPADES, Card.Suit.HEARTS, Card.Suit.CLUBS, Card.Suit.DIAMONDS);
    private static final Comparator<Card> HAND_ORDER = Comparator.comparingInt((Card card) -> SUIT_ORDER.indexOf(card.suit()))
            .thenComparingInt(Card::rank);
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
    public static List<SeatContent> seats(RentzMatch.Observation view, RentzText copy, List<String> names, boolean paused) {
        return seats(view, copy, names, paused, false);
    }
    static List<SeatContent> seats(RentzMatch.Observation view, RentzText copy, List<String> names, boolean paused, boolean passing) {
        return seats(view, copy, names, paused, passing, false);
    }
    static List<SeatContent> seats(RentzMatch.Observation view, RentzText copy, List<String> names,
            boolean paused, boolean passing, boolean arrival) {
        return IntStream.range(0, view.players()).mapToObj(seat -> {
            boolean active = !paused && (view.phase() == RentzMatch.Phase.PLAY || view.phase() == RentzMatch.Phase.CHOOSE) && view.turn() == seat;
            int delta = view.dealPoints().get(seat);
            String points = arrival ? "…" : Integer.toString(delta);
            String total = Integer.toString(view.totals().get(seat) - (arrival ? delta : 0));
            var facts = List.of(new SeatContent.Fact(RentzScoreContent.HAND_POINTS_MARK, points),
                    new SeatContent.Fact(RentzScoreContent.TOTAL_MARK, total));
            String who = names.get(seat);
            return new SeatContent(seat, who, facts, active, who + ". " + copy.points() + " " + points
                    + ". " + copy.total() + " " + total
                    + (active ? ". " + turnLabel(view, copy, passing) : ""));
        }).collect(immutable());
    }
    static String arrival(RentzMatch.Observation view, RentzText copy, List<String> names) {
        int unplayed = view.players() * 8 - view.board().size();
        return copy.text("Rentz finish", "Sosire Rentz") + " · " + names.get(view.finishOrder().get(0))
                + "\n" + copy.text("Unplayed cards", "Cărți neplasate") + " · " + unplayed;
    }
    public static List<HandCard> hand(RentzMatch.Observation view, RentzText copy, boolean paused) {
        if (!view.revealed()) return List.of();
        return view.hand().stream().sorted(HAND_ORDER).map(card -> {
            boolean legal = !paused && isPlayerTurn(view) && view.legal().contains(card);
            return new HandCard(id(card), face(card), legal ? copy.play(card.label()) : card.label() + copy.text(". Unavailable", ". Indisponibilă"), legal);
        }).collect(immutable());
    }
    public static Card selected(RentzMatch.Observation view, String id) {
        if (!isPlayerTurn(view)) throw new IllegalStateException("Not the actor's play phase");
        return view.legal().stream().filter(card -> id(card).equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Card is not currently legal"));
    }
    public static String turnLabel(RentzMatch.Observation view, RentzText copy, boolean passing) {
        return passing ? copy.text("Passing", "Pasează") : copy.seatTurn(view.phase() == RentzMatch.Phase.CHOOSE);
    }
    static Set<String> highlightedCards(RentzMatch.Observation view) {
        if (!view.revealed() || view.contract() != Contract.RENTZ || !isPlayerTurn(view)) return Set.of();
        return view.legal().stream().map(RentzTableContent::id).collect(Collectors.toSet());
    }
    private static boolean isPlayerTurn(RentzMatch.Observation view) {
        return view.phase() == RentzMatch.Phase.PLAY && view.turn() == view.seat();
    }
    public static List<PlayedCardContent> plays(List<TrickRules.Play> plays, List<String> names) {
        return plays.stream().map(play -> new PlayedCardContent(play.seat(), play.card().label(), face(play.card()).red(),
                names.get(play.seat()), names.get(play.seat()) + ": " + play.card().label())).collect(immutable());
    }
}
