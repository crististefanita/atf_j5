package ro.rentzlab.app.ui.table;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import ro.rentzlab.core.*;
import java.util.List;
import java.util.stream.IntStream;

/** Fixed public sequence slots; empty cells are never represented as played cards. */
public final class RentzSequenceContent {
    public record Cell(Card card, boolean played, boolean opening) { }
    private RentzSequenceContent() { }
    public static List<List<Cell>> rows(RentzMatch.Observation view) {
        if (view.contract() != Contract.RENTZ) throw new IllegalArgumentException("A sequence board needs the Rentz contract");
        var policy = new RentzBoard(view.players());
        return java.util.Arrays.stream(Card.Suit.values()).map(suit -> IntStream.rangeClosed(policy.lowestRank(), 14)
                .mapToObj(rank -> { var card = new Card(suit, rank); return new Cell(card, view.board().contains(card), rank == policy.startRank()); }).collect(immutable())).collect(immutable());
    }
}
