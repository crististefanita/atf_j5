package ro.rentzlab.app.ui.screens;

import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.cardtable.ui.screens.GameTitleBar;
import ro.rentzlab.core.Contract;

@AndroidComponentLayer
public final class RentzGameHeaderComponentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void sharedHeaderReceivesLocalizedRentzProgressAndContractWithoutChangingControlState() {
        for (var language : RentzUiText.Language.values()) {
            var copy = new RentzUiText(language);
            var content = RentzGameHeader.content(copy, 5, 32, Contract.QUEENS, true,
                    true, true, false);
            audit.equal("Localized hand progress " + language,
                    language == RentzUiText.Language.RO ? "Mâna 5/32" : "Hand 5/32", content.progress());
            audit.equal("Localized blind contract " + language,
                    copy.playedContract(Contract.QUEENS, true), content.contract());
            audit.truth("Contract row remains visible after selection " + language, content.contractVisible());
            audit.truth("AutoPlay is shown only on a playable hand " + language, content.autoPlayVisible());
            audit.truth("Header mirrors checked AutoPlay state " + language, content.autoPlayChecked());
            audit.equal("Active AutoPlay always keeps the stop control available " + language, true, content.autoPlayEnabled());
            audit.equal("Localized AutoPlay label " + language,
                    language == RentzUiText.Language.RO ? "Joc automat" : "Auto play", copy.autoPlay());
            audit.equal("Accessible start action " + language,
                    language == RentzUiText.Language.RO ? "Joacă automat mâna curentă" : "Auto-play this hand",
                    copy.startAutoPlayHand());
            audit.equal("Accessible stop action " + language,
                    language == RentzUiText.Language.RO ? "Oprește jocul automat" : "Stop auto-play",
                    copy.stopAutoPlayHand());
            audit.equal("Common touch target remains at least 48 dp " + language,
                    48, GameTitleBar.TOUCH_TARGET_DP);
        }
    }

    @Test public void inactiveAndUnselectedHandsDoNotRenderAStaleCheckedControl() {
        var copy = new RentzUiText(RentzUiText.Language.RO);
        var beforeContract = RentzGameHeader.content(copy, 1, 32, null, false,
                false, true, true);
        var settled = RentzGameHeader.content(copy, 8, 32, Contract.WHIST, false,
                true, false, false);
        audit.equal("An unselected contract keeps its row reserved", false, beforeContract.contractVisible());
        audit.equal("An active hand exposes the optional control", true, beforeContract.autoPlayVisible());
        audit.equal("A completed hand hides the control", false, settled.autoPlayVisible());
        audit.equal("A hidden control cannot retain a checked value", false, settled.autoPlayChecked());
        audit.equal("A hidden control cannot remain enabled", false, settled.autoPlayEnabled());
    }

    @Test public void contentRejectsCheckedOrEnabledStateForAnAbsentControl() {
        audit.rejects("A hidden AutoPlay cannot be checked", IllegalArgumentException.class,
                () -> new RentzGameHeader.Content("Hand 1/32", "", false, false, true, false));
        audit.rejects("A hidden AutoPlay cannot be enabled", IllegalArgumentException.class,
                () -> new RentzGameHeader.Content("Hand 1/32", "", false, false, false, true));
    }

    @Test public void queuedAutomationCanBeStoppedButCannotBeStartedWhileInputIsBlocked() {
        for (var language : RentzUiText.Language.values()) {
            var copy = new RentzUiText(language);
            for (var contract : new Contract[]{null, Contract.RENTZ, Contract.WHIST}) {
                var active = RentzGameHeader.content(copy, 2, 32, contract, false, true, true, false);
                var manual = RentzGameHeader.content(copy, 2, 32, contract, false, false, true, false);
                audit.equal("A queued AI decision keeps its stop action " + language + " " + contract,
                        true, active.autoPlayEnabled());
                audit.equal("Blocked input cannot start an automatic decision " + language + " " + contract,
                        false, manual.autoPlayEnabled());
                audit.equal("The control value changes only through its owner " + language + " " + contract,
                        false, manual.autoPlayChecked());
            }
        }
    }
}
