package ro.rentzlab.app.ui.table;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
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
    public record Actions(Runnable reveal, Runnable choose, Runnable blind, Runnable pass, Runnable next, Consumer<String> card) { }
    private final Context context;
    private final RentzUiText copy;
    private final boolean paused;
    public RentzTablePanel(Context context, RentzMatch.Observation view, RentzUiText copy, String name,
            boolean paused, boolean holding, List<TrickRules.Play> visible, Actions actions) {
        super(context); this.context = context; this.copy = copy; this.paused = paused;
        var viewport = new TableViewportPolicy(WindowContent.heightDp(context), getResources().getConfiguration().fontScale);
        setOrientation(VERTICAL); setGravity(Gravity.BOTTOM); setPadding(dp(8), 0, dp(8), 0); setClipChildren(false);
        int width = Math.min(760, Math.max(240, WindowContent.widthDp(context) - 40));
        boolean cards = view.phase() == RentzMatch.Phase.PLAY && view.contract() != Contract.RENTZ || holding;
        var layout = PlayedCardMetrics.forTable(view.players(), width, getResources().getConfiguration().fontScale, viewport);
        var board = new BoardLayout(context, view.players(), cards ? width : Math.min(width, 220), viewport, cards, TableGeometry.CenterAlignment.BALANCED);
        board.setTag("game-board"); board.setContentDescription(copy.text("Rentz table", "Masa Rentz"));
        for (var seat : RentzTableContent.seats(view, copy, name, holding)) {
            var frame = new PlayerSeatFrame(context, viewport.isCompact()).build(seat, viewport.seatVerticalPaddingDp());
            board.addView(new PlayerSeatView(context, frame, copy.text("TO PLAY", "LA RÂND"), seat.active(), seat.seat(), viewport.isCompact()));
        }
        View center = cards ? new PlayedCardsView(context, viewport.isCompact()).build(RentzTableContent.plays(visible, copy, name), layout,
                holding ? copy.text("Completed trick", "Levată încheiată") : copy.text("Current trick", "Levată curentă"))
                : center(view, actions);
        board.addView(center);
        var boardParams = new LayoutParams(dp(width), 0, 1f); boardParams.gravity = Gravity.CENTER_HORIZONTAL;
        addView(board, boardParams);
        if (view.phase() == RentzMatch.Phase.PLAY && view.contract() == Contract.RENTZ && view.legal().isEmpty() && view.turn() == view.seat()) {
            var pass = button(copy.text("Pass", "Pas"), actions.pass());
            var params = new LayoutParams(dp(132), dp(48)); params.gravity = Gravity.CENTER_HORIZONTAL; addView(pass, params);
        }
        var hand = new CenteredHandView(context, dp(HandLayoutMetrics.maxViewportWidthDp()),
                new HandFan(context, RentzTableContent.hand(view, copy, paused), viewport.handClearanceDp(false), viewport.isCompact(), actions.card())::build);
        hand.setTag("player-hand"); hand.setContentDescription(copy.text("Your hand", "Mâna ta") + ": " + RentzTableContent.hand(view, copy, paused).size());
        addView(hand, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(HandLayoutMetrics.reservedHandHeightDp())));
    }
    private View center(RentzMatch.Observation view, Actions actions) {
        var box = new LinearLayout(context); box.setOrientation(VERTICAL); box.setGravity(Gravity.CENTER);
        if (view.phase() == RentzMatch.Phase.CHOOSE) {
            if (!view.revealed()) {
                box.addView(button(copy.text("Show cards", "Vezi cărțile"), actions.reveal()), choiceSize());
                if (view.available().size() > 1) box.addView(button(copy.text("Choose blind ×2", "Alege pe nevăzute ×2"), actions.blind()), choiceSize());
            } else box.addView(button(copy.text("Choose game", "Alege sub-jocul"), actions.choose()), choiceSize());
        } else if (view.contract() == Contract.RENTZ) {
            for (var row : RentzSequenceContent.rows(view)) {
                var line = new LinearLayout(context); line.setGravity(Gravity.CENTER);
                for (var cell : row) {
                    var tile = new TextView(context); tile.setGravity(Gravity.CENTER); tile.setTextSize(11);
                    tile.setText(cell.played() ? cell.card().label() : cell.opening() ? RentzTableContent.face(cell.card()).rankLabel() : "·");
                    tile.setTextColor(cell.played() ? TablePalette.ACTIVE_SEAT_TEXT : Color.rgb(194, 211, 197));
                    if (cell.played()) tile.setBackground(TablePalette.seatFrame(context, true));
                    tile.setContentDescription(cell.played() ? cell.card().label() : copy.text("Empty slot ", "Loc liber ") + cell.card().label());
                    tile.setTag("sequence:" + RentzTableContent.id(cell.card()));
                    line.addView(tile, new LayoutParams(0, dp(32), 1f));
                }
                box.addView(line, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
            }
            if (view.phase() == RentzMatch.Phase.DEAL_END) box.addView(button(copy.text("Next hand", "Mâna următoare"), actions.next()), choiceSize());
        } else {
            var end = new TextView(context); end.setText(copy.text("Hand completed", "Mână încheiată"));
            end.setTextColor(Color.WHITE); end.setTextSize(16); end.setGravity(Gravity.CENTER); box.addView(end);
            if (view.phase() == RentzMatch.Phase.DEAL_END) box.addView(button(copy.text("Next hand", "Mâna următoare"), actions.next()), choiceSize());
        }
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
