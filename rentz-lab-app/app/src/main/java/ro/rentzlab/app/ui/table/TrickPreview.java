package ro.rentzlab.app.ui.table;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import ro.rentzlab.core.*;
import java.util.List;

/** Observes new public completions for 1.5 seconds; never applies or replays a game command. */
public final class TrickPreview {
    public static final long HOLD_MILLIS = 1500;
    private int hand = -1, seenSequence;
    private long until;
    private List<TrickRules.Play> held = List.of();
    public void reset(RentzMatch.Observation view) {
        hand = view.handNumber(); seenSequence = latestCompleted(view); until = 0; held = List.of();
    }
    public List<TrickRules.Play> display(RentzMatch.Observation view, long now) {
        if (view.contract() == Contract.RENTZ || view.contract() == null) { reset(view); return view.trick(); }
        if (hand != view.handNumber()) reset(view);
        var plays = view.events().stream().filter(event -> event.action() == RentzMatch.Action.PLAY).collect(immutable());
        int complete = plays.size() / view.players() * view.players();
        int sequence = complete == 0 ? 0 : plays.get(complete - 1).sequence();
        if (sequence > seenSequence) {
            seenSequence = sequence;
            held = plays.subList(complete - view.players(), complete).stream().map(event -> new TrickRules.Play(event.seat(), event.card())).collect(immutable());
            until = now + HOLD_MILLIS;
        }
        return now < until ? held : view.trick();
    }
    public long remaining(long now) { return Math.max(0, until - now); }
    private static int latestCompleted(RentzMatch.Observation view) {
        var plays = view.events().stream().filter(event -> event.action() == RentzMatch.Action.PLAY).collect(immutable());
        int complete = plays.size() / view.players() * view.players();
        return complete == 0 ? 0 : plays.get(complete - 1).sequence();
    }
}
