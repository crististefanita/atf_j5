package ro.rentzlab.app.ui.table;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import ro.cardtable.ui.animation.CollectionTarget;
import ro.cardtable.ui.animation.TrickCollectionAnimator;
import ro.cardtable.ui.cards.CardFaceDrawable;
import ro.cardtable.ui.hand.*;
import ro.cardtable.ui.seat.*;
import ro.cardtable.ui.table.*;
import ro.cardtable.ui.theme.TablePalette;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.app.ui.window.WindowContent;
import ro.rentzlab.core.*;
import java.util.List;
import java.util.function.Consumer;

/** Rentz central modes compose neutral seats, public cards and a single shared hand renderer. */
public final class RentzTablePanel extends LinearLayout {
    public record Actions(Runnable reveal, Runnable choose, Runnable blind, Runnable next, Consumer<String> card) { }
    private static final int NEXT_HAND_FOOTER_DP = 51;
    private final Context context;
    private final RentzUiText copy;
    private final boolean paused;
    private final ViewGroup board;
    private final View center;
    private final boolean defaultCollectionScale;
    private ViewTreeObserver.OnPreDrawListener collectionExpiry;
    private RentzCollectionAnimation nativeCollection;
    public RentzTablePanel(Context context, RentzMatch.Observation view, RentzUiText copy, List<String> names,
            boolean paused, boolean holding, boolean forcedPass, List<TrickRules.Play> visible, Actions actions) {
        super(context); this.context = context; this.copy = copy; this.paused = paused;
        defaultCollectionScale = holding
                && Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 1f;
        float fontScale = getResources().getConfiguration().fontScale;
        var viewport = new TableViewportPolicy(WindowContent.heightDp(context), fontScale);
        setOrientation(VERTICAL); setGravity(Gravity.BOTTOM); setPadding(dp(8), 0, dp(8), 0); setClipChildren(false);
        int width = Math.min(760, Math.max(240, WindowContent.widthDp(context) - 40));
        boolean cards = view.phase() == RentzMatch.Phase.PLAY && view.contract() != Contract.RENTZ || holding;
        boolean sequence = view.contract() == Contract.RENTZ && view.phase() != RentzMatch.Phase.CHOOSE;
        if (sequence) {
            int footer = view.phase() == RentzMatch.Phase.DEAL_END ? NEXT_HAND_FOOTER_DP : 0;
            var cardBoard = new RentzCardBoard(context, view.players(), footer, viewport,
                    (measuredWidth, budget) -> sequence(view, actions, measuredWidth, budget, fontScale));
            board = cardBoard;
            center = cardBoard.center();
        } else {
            board = new BoardLayout(context, view.players(), cards ? width : Math.min(width, 220),
                    viewport, cards, TableGeometry.CenterAlignment.BALANCED);
            center = cards ? new PlayedCardsView(context, viewport.isCompact()).build(
                    RentzTableContent.plays(visible, names), PlayedCardMetrics.forTable(view.players(), width, fontScale, viewport),
                    holding ? copy.text("Completed trick", "Levată încheiată") : copy.text("Current trick", "Levată curentă"))
                    : center(view, actions);
        }
        board.setTag("game-board"); board.setContentDescription(copy.text("Rentz table", "Masa Rentz"));
        String turnBadge = RentzTableContent.turnLabel(view, copy, forcedPass).toUpperCase(java.util.Locale.ROOT);
        for (var seat : RentzTableContent.seats(view, copy, names, holding, forcedPass)) {
            var frame = new PlayerSeatFrame(context, viewport.isCompact()).buildInlineFacts(seat, viewport.seatVerticalPaddingDp());
            board.addView(new PlayerSeatView(context, frame, turnBadge, seat.active(), seat.seat(), viewport.isCompact()));
        }
        board.addView(center);
        var boardParams = new LayoutParams(dp(width), 0, 1f); boardParams.gravity = Gravity.CENTER_HORIZONTAL;
        addView(board, boardParams);
        var handCards = RentzTableContent.hand(view, copy, paused);
        var fan = new HandFan(context, handCards, viewport.handClearanceDp(false), viewport.isCompact(), actions.card());
        var hand = new CenteredHandView(context, dp(HandLayoutMetrics.maxViewportWidthDp()), widthPx -> {
            View rendered = fan.build(widthPx);
            for (var card : handCards) {
                RentzTableContent.handTint(view.contract(), card).ifPresent(tint -> {
                    var slot = rendered.<ViewGroup>findViewWithTag("hand-card:" + card.id());
                    if (slot != null && slot.getChildAt(0).getBackground() instanceof CardFaceDrawable face) {
                        face.setColor(tint);
                    }
                });
            }
            return rendered;
        });
        hand.setTag("player-hand"); hand.setContentDescription(copy.text("Your hand", "Mâna ta") + ": " + handCards.size());
        addView(hand, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(HandLayoutMetrics.reservedHandHeightDp())));
    }
    public void collect(CollectionTarget target, long deadline, Runnable resized) {
        if (defaultCollectionScale && center.isHardwareAccelerated() && ValueAnimator.areAnimatorsEnabled()) {
            nativeCollection = RentzCollectionAnimation.start(board, center, target, deadline, resized);
        }
        if (nativeCollection == null) {
            TrickCollectionAnimator.attach(board, target, deadline - SystemClock.uptimeMillis(), resized);
        }
    }
    public void clearCollectionBeforeDraw(long deadline, Runnable advance) {
        if (nativeCollection == null && defaultCollectionScale && center.isHardwareAccelerated()
                && ValueAnimator.areAnimatorsEnabled()) {
            // Let Android's render thread expire the whole trick even if the UI thread misses a frame.
            center.animate().alpha(0f).setDuration(Math.max(0, deadline - SystemClock.uptimeMillis()))
                    .setInterpolator(progress -> progress < 1f ? 0f : 1f).start();
        }
        collectionExpiry = () -> {
            if (SystemClock.uptimeMillis() >= deadline) finishCollection(advance);
            return true;
        };
        getViewTreeObserver().addOnPreDrawListener(collectionExpiry);
    }
    public void finishCollection(Runnable advance) {
        removeCollectionExpiry();
        if (center.getVisibility() != VISIBLE) return;
        center.setVisibility(INVISIBLE);
        center.animate().cancel();
        cancelNativeCollection();
        // Let this traversal clear the cards before rebuilding the screen.
        postOnAnimation(() -> post(advance));
    }
    private void removeCollectionExpiry() {
        if (collectionExpiry == null) return;
        var observer = getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(collectionExpiry);
        collectionExpiry = null;
    }
    @Override protected void onDetachedFromWindow() {
        if (collectionExpiry != null) center.animate().cancel();
        cancelNativeCollection();
        removeCollectionExpiry();
        super.onDetachedFromWindow();
    }
    private void cancelNativeCollection() {
        if (nativeCollection == null) return;
        nativeCollection.cancel();
        nativeCollection = null;
    }
    private View center(RentzMatch.Observation view, Actions actions) {
        var box = new LinearLayout(context); box.setOrientation(VERTICAL); box.setGravity(Gravity.CENTER);
        if (view.phase() == RentzMatch.Phase.CHOOSE) {
            if (view.turn() != view.seat()) return box;
            if (!view.revealed()) {
                box.addView(button(copy.text("Show cards", "Vezi cărțile"), actions.reveal()), choiceSize());
                if (view.available().size() > 1) box.addView(button(copy.text("Choose blind ×2", "Alege pe nevăzute ×2"), actions.blind()), choiceSize());
            } else box.addView(button(copy.text("Choose game", "Alege sub-jocul"), actions.choose()), choiceSize());
        } else {
            var end = new TextView(context); end.setText(copy.text("Hand completed", "Mână încheiată"));
            end.setTextColor(Color.WHITE); end.setTextSize(16); end.setGravity(Gravity.CENTER); box.addView(end);
            if (view.phase() == RentzMatch.Phase.DEAL_END) box.addView(button(copy.text("Next hand", "Mâna următoare"), actions.next()), choiceSize());
        }
        return box;
    }
    private View sequence(RentzMatch.Observation view, Actions actions, int width, int budget, float fontScale) {
        var box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER);
        boolean nextHand = view.phase() == RentzMatch.Phase.DEAL_END;
        var metrics = RentzCardSpace.sequence(view.players(), width, budget - (nextHand ? NEXT_HAND_FOOTER_DP : 0), fontScale);
        for (var row : RentzSequenceContent.rows(view)) {
            var line = new LinearLayout(context);
            line.setGravity(Gravity.CENTER);
            for (var cell : row) {
                var tile = new TextView(context);
                tile.setGravity(Gravity.CENTER);
                tile.setSingleLine(true);
                tile.setIncludeFontPadding(false);
                tile.setTextSize(metrics.labelSp());
                tile.setText(cell.played() ? cell.card().label() : cell.opening() ? RentzTableContent.face(cell.card()).rankLabel() : "·");
                tile.setTextColor(cell.played() ? TablePalette.ACTIVE_SEAT_TEXT : Color.rgb(194, 211, 197));
                if (cell.played()) tile.setBackground(TablePalette.seatFrame(context, true));
                tile.setContentDescription(cell.played() ? cell.card().label() : copy.text("Empty slot ", "Loc liber ") + cell.card().label());
                tile.setTag("sequence:" + RentzTableContent.id(cell.card()));
                int side = dp(metrics.sideInsetDp()), vertical = dp(metrics.verticalInsetDp());
                var slot = new LayoutParams(0, dp(metrics.rowHeightDp()) - 2 * vertical, 1f);
                slot.setMargins(side, vertical, side, vertical);
                line.addView(tile, slot);
            }
            box.addView(line, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(metrics.rowHeightDp())));
        }
        if (nextHand) box.addView(button(copy.text("Next hand", "Mâna următoare"), actions.next()), choiceSize());
        return box;
    }
    private Button button(String label, Runnable action) {
        var button = new Button(context); button.setAllCaps(false); button.setText(label); button.setTextSize(13);
        button.setContentDescription(label); button.setTextColor(TablePalette.ACTIVE_SEAT_TEXT);
        button.setBackground(TablePalette.seatFrame(context, true)); button.setEnabled(!paused);
        button.setOnClickListener(ignored -> action.run()); return button;
    }
    private LayoutParams choiceSize() { var params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)); params.topMargin = dp(3); return params; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
