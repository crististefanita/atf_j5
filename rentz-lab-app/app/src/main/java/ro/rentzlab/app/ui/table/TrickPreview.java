package ro.rentzlab.app.ui.table;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import ro.cardtable.ui.animation.CollectionTarget;
import ro.cardtable.ui.animation.TrickCollectionMotion;
import ro.rentzlab.core.*;
import java.util.List;
import java.util.Optional;

/** Holds newly completed public tricks and a Rentz arrival until their presentation finishes. */
public final class TrickPreview {
    private static final long RENTZ_ARRIVAL_MILLIS = 1800;
    private int hand = -1, seenSequence;
    private long until;
    private boolean awaitingFirstFrame;
    private boolean seenArrival;
    private List<TrickRules.Play> held = List.of();
    private CollectionTarget target;
    public void reset(RentzMatch.Observation view) {
        hand = view.handNumber(); seenSequence = latestCompleted(view); until = 0; awaitingFirstFrame = false;
        seenArrival = rentzSettled(view);
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
        if (view.contract() == null) { reset(view); return view.trick(); }
        if (hand != view.handNumber()) reset(view);
        if (view.contract() == Contract.RENTZ) {
            if (rentzSettled(view) && !seenArrival) {
                seenArrival = true;
                held = List.of();
                target = null;
                awaitingFirstFrame = true;
            }
            return view.trick();
        }
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
        until = now + windowMillis();
    }
    public long remaining(long now) {
        return awaitingFirstFrame ? windowMillis() : Math.max(0, until - now);
    }
    public Optional<CollectionTarget> collection(long now) {
        return remaining(now) > 0 ? Optional.ofNullable(target) : Optional.empty();
    }
    public boolean arrival(long now) {
        return seenArrival && target == null && remaining(now) > 0;
    }
    private long windowMillis() {
        return target == null ? RENTZ_ARRIVAL_MILLIS : TrickCollectionMotion.DISPLAY_MILLIS;
    }
    private static boolean rentzSettled(RentzMatch.Observation view) {
        return view.contract() == Contract.RENTZ
                && (view.phase() == RentzMatch.Phase.DEAL_END || view.phase() == RentzMatch.Phase.MATCH_END);
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
