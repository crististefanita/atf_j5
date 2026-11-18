package ro.rentzlab.app.ui.screens;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Objects;
import java.util.function.Consumer;
import ro.cardtable.ui.screens.GameTitleBar;
import ro.cardtable.ui.screens.ScreenViews;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.Contract;

/** Rentz content adapter for the shared, measured native table header. */
public final class RentzGameHeader {
    public record Content(String progress, String contract, boolean contractVisible,
            boolean autoPlayVisible, boolean autoPlayChecked, boolean autoPlayEnabled) {
        public Content {
            Objects.requireNonNull(progress);
            Objects.requireNonNull(contract);
            if (!autoPlayVisible && (autoPlayChecked || autoPlayEnabled)) {
                throw new IllegalArgumentException("An absent AutoPlay control cannot carry active state");
            }
        }
    }

    private RentzGameHeader() { }

    public static Content content(RentzUiText copy, int hand, int totalHands, Contract contract,
            boolean blind, boolean autoPlay, boolean playable, boolean enabled) {
        Objects.requireNonNull(copy);
        boolean visible = playable;
        return new Content(copy.handProgress(hand, totalHands), copy.playedContract(contract, blind),
                contract != null, visible, visible && autoPlay, visible && (autoPlay || enabled));
    }

    public static GameTitleBar build(ScreenViews views, RentzUiText copy, Content content,
            View leading, Consumer<Boolean> autoPlayChanged) {
        Objects.requireNonNull(views);
        Objects.requireNonNull(copy);
        Objects.requireNonNull(content);
        Objects.requireNonNull(leading);
        Objects.requireNonNull(autoPlayChanged);

        var details = new LinearLayout(views.activity());
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        details.setMinimumHeight(views.dp(GameTitleBar.TOUCH_TARGET_DP));

        TextView progress = views.text(content.progress(), 14, Color.WHITE, true);
        progress.setSingleLine(true);
        progress.setIncludeFontPadding(false);
        details.addView(progress, views.matchWrap());

        TextView contract = views.text(content.contract(), 12, Color.WHITE, false);
        contract.setSingleLine(true);
        contract.setIncludeFontPadding(false);
        contract.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        contract.setEllipsize(TextUtils.TruncateAt.END);
        if (!content.contractVisible()) contract.setVisibility(View.INVISIBLE);
        details.addView(contract, views.matchWrap());

        GameTitleBar.AutoPlay control = content.autoPlayVisible()
                ? new GameTitleBar.AutoPlay(copy.autoPlay(), copy.startAutoPlayHand(), copy.stopAutoPlayHand(),
                        content.autoPlayChecked(), content.autoPlayEnabled(), Color.WHITE, autoPlayChanged)
                : null;
        return new GameTitleBar(views.activity(), leading, details, control);
    }
}
