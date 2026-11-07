package ro.rentzlab.app.ui.copy;

import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.ui.navigation.SetupSelection;
import ro.cardtable.ui.menu.HomeMenuContent;
import ro.rentzlab.core.Contract;
import ro.rentzlab.ai.RentzPlayer;
import java.util.*;
import org.junit.*;

@AndroidComponentLayer
public final class RentzMenuContentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void bothLanguagesUseTheCommonHomeWithRealRentzRoutes() {
        for (var language : RentzUiText.Language.values()) {
            var copy = new RentzUiText(language); var seen = new ArrayList<String>();
            var controls = copy.home().controls("Ana", false, new HomeMenuContent.Actions(() -> seen.add("profile"), () -> seen.add("setup"), () -> seen.add("resume"), () -> seen.add("history"), () -> seen.add("help")));
            audit.equal("Rentz title " + language, "Rentz", copy.home().title());
            audit.equal("Five visible menu routes " + language, 5, controls.size());
            audit.equal("Resume remains visible and disabled " + language, false, controls.get(2).enabled());
            audit.equal("Localized profile name " + language, (language == RentzUiText.Language.EN ? "Player" : "Jucător") + ": Ana", controls.get(0).label());
            controls.get(3).action().run(); audit.equal("Scores own route " + language, List.of("history"), seen);
            audit.equal("Eight distinct contract names " + language, 8L, Arrays.stream(Contract.values()).map(copy::contract).distinct().count());
        }
    }
    @Test public void setupKeepsRentzChoicesAcrossCopyChangesWithoutStartingAnotherGame() {
        var setup = new SetupSelection(); setup.players(6); setup.difficulty(RentzPlayer.Difficulty.NORMAL);
        for (var language : RentzUiText.Language.values()) {
            new RentzUiText(language).home();
            audit.equal("Retained table size " + language, 6, setup.players());
            audit.equal("Retained opponent level " + language, RentzPlayer.Difficulty.NORMAL, setup.difficulty());
        }
        audit.rejects("Unsupported Rentz table size", IllegalArgumentException.class, () -> setup.players(2));
    }
}
