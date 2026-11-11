package ro.rentzlab.app.ui.table;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import ro.cardtable.ui.animation.CollectionTarget;
import ro.cardtable.ui.animation.TrickCollectionAnimator;
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
    private static final int CHOICE_HEIGHT_DP = 48;
    private static final int CHOICE_MARGIN_DP = 3;
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
        float density = getResources().getDisplayMetrics().density;
        var viewport = new TableViewportPolicy(WindowContent.heightDp(context), fontScale);
        setOrientation(VERTICAL); setGravity(Gravity.BOTTOM); setPadding(dp(8), 0, dp(8), 0); setClipChildren(false);
        int width = Math.min(760, Math.max(240, WindowContent.widthDp(context) - 40));
        boolean cards = view.phase() == RentzMatch.Phase.PLAY && view.contract() != Contract.RENTZ || holding;
        boolean sequence = view.contract() == Contract.RENTZ && view.phase() != RentzMatch.Phase.CHOOSE;
        var trick = PlayedCardMetrics.forTable(view.players(), width, fontScale, viewport);
        if (view.phase() != RentzMatch.Phase.CHOOSE) {
            int footer = sequence && view.phase() == RentzMatch.Phase.DEAL_END ? dp(CHOICE_HEIGHT_DP) + dp(CHOICE_MARGIN_DP) : 0;
            var cardBoard = new RentzCardBoard(context, view.players(), footer, viewport, trick,
                    (widthPx, budgetPx) -> {
                        if (sequence) return sequence(view, actions, RentzCardSpace.sequence(view.players(), widthPx,
                                budgetPx - footer, density, fontScale));
                        if (cards) return playedCards(visible, names, holding, viewport,
                                RentzCardSpace.fitTrick(trick, budgetPx, density));
                        View controls = center(view, actions, 0);
                        controls.setLayoutParams(new FrameLayout.LayoutParams(Math.min(dp(220), widthPx),
                                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
                        return controls;
                    });
            board = cardBoard;
            center = cardBoard.center();
        } else {
            board = new BoardLayout(context, view.players(), cards ? width : Math.min(width, 220),
                    viewport, cards, TableGeometry.CenterAlignment.BALANCED);
            center = cards ? playedCards(visible, names, holding, viewport, trick) : center(view, actions, dp(1));
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
        var fan = new HandFan(context, handCards, viewport.handClearanceDp(false), viewport.isCompact(),
                actions.card(), card -> RentzTableContent.highlightPlayable(view.contract(), card));
        var hand = new CenteredHandView(context, dp(HandLayoutMetrics.maxViewportWidthDp()), fan::build);
        hand.setTag("player-hand"); hand.setContentDescription(copy.text("Your hand", "Mâna ta") + ": " + handCards.size());
        addView(hand, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(HandLayoutMetrics.reservedHandHeightDp())));
    }
    private View playedCards(List<TrickRules.Play> visible, List<String> names, boolean holding,
            TableViewportPolicy viewport, PlayedCardMetrics.Layout trick) {
        return new PlayedCardsView(context, viewport.isCompact()).build(RentzTableContent.plays(visible, names), trick,
                holding ? copy.text("Completed trick", "Levată încheiată") : copy.text("Current trick", "Levată curentă"));
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
    private View center(RentzMatch.Observation view, Actions actions, int minimumHeightPx) {
        var box = new LinearLayout(context); box.setOrientation(VERTICAL); box.setGravity(Gravity.CENTER);
        box.setMinimumHeight(minimumHeightPx);
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
    private View sequence(RentzMatch.Observation view, Actions actions, RentzCardSpace.Sequence metrics) {
        var box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER);
        for (var row : RentzSequenceContent.rows(view)) {
            var line = new FrameLayout(context);
            for (int column = 0; column < row.size(); column++) {
                var cell = row.get(column);
                var face = RentzTableContent.face(cell.card());
                boolean covered = column > 0 && row.get(column - 1).played() && metrics.overlaps();
                var tile = new TextView(context);
                tile.setGravity(Gravity.LEFT | (metrics.stackedIndex() ? Gravity.TOP : Gravity.CENTER_VERTICAL));
                tile.setSingleLine(!cell.played() || !metrics.stackedIndex());
                tile.setIncludeFontPadding(false);
                tile.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                tile.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.labelSizePx());
                int width = cell.played() ? metrics.faceWidth() : metrics.indexWidth();
                int padding = metrics.indexPadding();
                if (cell.played()) {
                    tile.setBackground(sequenceCardFace());
                    tile.setTextColor(face.red() ? Color.rgb(173, 57, 58) : Color.rgb(42, 53, 47));
                    tile.setText(metrics.stackedIndex() ? face.rankLabel() + "\n" + face.suitSymbol() : cell.card().label());
                } else {
                    tile.setTextColor(Color.rgb(194, 211, 197));
                    tile.setText(cell.opening() ? face.rankLabel() : covered ? "" : "·");
                }
                tile.setPadding(padding, padding, width - metrics.indexWidth() + padding, padding);
                tile.setContentDescription(cell.played() ? cell.card().label() : copy.text("Empty slot ", "Loc liber ") + cell.card().label());
                tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                tile.setTag("sequence:" + RentzTableContent.id(cell.card()));
                var slot = new FrameLayout.LayoutParams(width, metrics.faceHeight(), Gravity.TOP | Gravity.LEFT);
                slot.leftMargin = metrics.left(column);
                slot.topMargin = (metrics.rowHeight() - metrics.faceHeight()) / 2;
                line.addView(tile, slot);
            }
            box.addView(line, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, metrics.rowHeight()));
        }
        if (view.phase() == RentzMatch.Phase.DEAL_END) box.addView(button(copy.text("Next hand", "Mâna următoare"), actions.next()), choiceSize());
        return box;
    }
    private GradientDrawable sequenceCardFace() {
        var face = new GradientDrawable();
        face.setColor(Color.rgb(255, 254, 248));
        face.setStroke(dp(1), Color.rgb(191, 201, 191));
        face.setCornerRadius(dp(5));
        return face;
    }
    private Button button(String label, Runnable action) {
        var button = new Button(context); button.setAllCaps(false); button.setText(label); button.setTextSize(13);
        button.setContentDescription(label); button.setTextColor(TablePalette.ACTIVE_SEAT_TEXT);
        button.setBackground(TablePalette.seatFrame(context, true)); button.setEnabled(!paused);
        button.setOnClickListener(ignored -> action.run()); return button;
    }
    private LayoutParams choiceSize() { var params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CHOICE_HEIGHT_DP)); params.topMargin = dp(CHOICE_MARGIN_DP); return params; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
