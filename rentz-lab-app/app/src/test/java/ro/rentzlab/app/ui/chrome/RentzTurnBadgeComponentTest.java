package ro.rentzlab.app.ui.chrome;

import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;
import ro.rentzlab.app.ui.copy.RentzUiText;

@AndroidComponentLayer
public final class RentzTurnBadgeComponentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void narrowAndEnlargedLabelsKeepOneReservationAcrossChoosingPlayingAndPassing() {
        for (var language : RentzUiText.Language.values()) for (int line : new int[]{22, 34, 48}) {
            var copy = new RentzUiText(language);
            var measured = new ArrayList<String>();
            int height = RentzTurnBadge.reservedHeight(copy, label -> {
                measured.add(label);
                return label.equals(copy.seatTurn(true).toUpperCase(java.util.Locale.ROOT)) ? 2 * line : line;
            });
            audit.equal("Choosing can occupy two native lines " + language + " " + line, 2 * line, height);
            audit.equal("All phase labels are measured before layout", 3, measured.size());
            audit.equal("Choosing and playing share the same stable reservation", true,
                    measured.containsAll(List.of(copy.seatTurn(true).toUpperCase(java.util.Locale.ROOT),
                            copy.seatTurn(false).toUpperCase(java.util.Locale.ROOT))));
        }
    }
}
