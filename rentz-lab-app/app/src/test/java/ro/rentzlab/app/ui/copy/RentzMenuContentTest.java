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
            var controls = copy.home().controls("Ana", false, new HomeMenuContent.Actions(
                    () -> seen.add("profile"), () -> seen.add("setup"), () -> seen.add("resume"),
                    () -> seen.add("history"), () -> seen.add("achievements"), () -> seen.add("help")));
            audit.equal("Rentz title " + language, "Rentz", copy.home().title());
            audit.equal("Six visible menu routes " + language, 6, controls.size());
            audit.equal("Resume remains visible and disabled " + language, false, controls.get(2).enabled());
            audit.equal("Localized profile name " + language, (language == RentzUiText.Language.EN ? "Player" : "Jucător") + ": Ana", controls.get(0).label());
            controls.get(3).action().run(); audit.equal("Scores own route " + language, List.of("history"), seen);
            var achievementRoutes = controls.stream().filter(row -> row.tag().equals("btn_achievements")).toList();
            audit.equal("One achievement route " + language, 1, achievementRoutes.size());
            audit.equal("Localized achievement label " + language,
                    language == RentzUiText.Language.EN ? "Achievements" : "Realizări", achievementRoutes.get(0).label());
            achievementRoutes.get(0).action().run();
            audit.equal("Achievements own route " + language, List.of("history", "achievements"), seen);
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

    @Test public void handProgressAndContractHaveSeparateLocalizedLabels() {
        var ro = new RentzUiText(RentzUiText.Language.RO);
        var en = new RentzUiText(RentzUiText.Language.EN);
        audit.equal("Romanian progress", "Mâna 5/32", ro.handProgress(5, 32));
        audit.equal("English progress", "Hand 48/48", en.handProgress(48, 48));
        audit.equal("Game not selected", "", ro.playedContract(null, false));
        audit.equal("Selected game below progress", "Zece de treflă", ro.playedContract(Contract.TEN_CLUBS, false));
        audit.equal("Blind multiplier remains with the game", "King of hearts ×2", en.playedContract(Contract.KING_HEARTS, true));
    }
}
