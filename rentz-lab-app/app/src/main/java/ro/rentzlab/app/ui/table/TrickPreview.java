package ro.rentzlab.app.ui.table;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import ro.cardtable.ui.animation.CollectionTarget;
import ro.cardtable.ui.animation.TrickCollectionMotion;
import ro.rentzlab.core.*;
import java.util.List;
import java.util.Optional;

/** Observes new public completions and hands their winning seat to the shared animation. */
public final class TrickPreview {
    private int hand = -1, seenSequence;
    private long until;
    private boolean awaitingFirstFrame;
    private List<TrickRules.Play> held = List.of();
    private CollectionTarget target;
    public void reset(RentzMatch.Observation view) {
        hand = view.handNumber(); seenSequence = latestCompleted(view); until = 0; awaitingFirstFrame = false;
        held = List.of(); target = null;
    }
    /** One cold presentation of the confirmed boundary, without applying a game command. */
    public void restoreLast(RentzMatch.Observation view) {
        reset(view);
        if (seenSequence == 0 || !view.trick().isEmpty()) return;
        var plays = playedTrickCards(view);
        hold(view, plays, plays.size());
    }
    public List<TrickRules.Play> display(RentzMatch.Observation view, long now) {
        if (view.contract() == Contract.RENTZ || view.contract() == null) { reset(view); return view.trick(); }
        if (hand != view.handNumber()) reset(view);
        var plays = playedTrickCards(view);
        int complete = plays.size() / view.players() * view.players();
        int sequence = complete == 0 ? 0 : plays.get(complete - 1).sequence();
        if (sequence > seenSequence) {
            seenSequence = sequence;
            hold(view, plays, complete);
        }
        return remaining(now) > 0 ? held : view.trick();
    }
    private void hold(RentzMatch.Observation view, List<RentzMatch.TurnEvent> plays, int end) {
        held = plays.subList(end - view.players(), end).stream()
                .map(event -> new TrickRules.Play(event.seat(), event.card())).collect(immutable());
        target = new CollectionTarget(TrickRules.winner(held), held.stream().map(TrickRules.Play::seat).collect(immutable()));
        awaitingFirstFrame = true;
    }
    public boolean awaitingFirstFrame() { return awaitingFirstFrame; }
    public void markVisible(long now) {
        if (!awaitingFirstFrame) return;
        awaitingFirstFrame = false;
        until = now + TrickCollectionMotion.DISPLAY_MILLIS;
    }
    public long remaining(long now) {
        return awaitingFirstFrame ? TrickCollectionMotion.DISPLAY_MILLIS : Math.max(0, until - now);
    }
    public Optional<CollectionTarget> collection(long now) {
        return remaining(now) > 0 ? Optional.of(target) : Optional.empty();
    }
    private static int latestCompleted(RentzMatch.Observation view) {
        var plays = playedTrickCards(view);
        int complete = plays.size() / view.players() * view.players();
        return complete == 0 ? 0 : plays.get(complete - 1).sequence();
    }
    private static List<RentzMatch.TurnEvent> playedTrickCards(RentzMatch.Observation view) {
        if (view.contract() == null || view.contract() == Contract.RENTZ) return List.of();
        return view.events().stream().filter(event -> event.action() == RentzMatch.Action.PLAY).collect(immutable());
    }
}
