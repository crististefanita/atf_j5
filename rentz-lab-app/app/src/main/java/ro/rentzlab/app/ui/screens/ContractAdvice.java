package ro.rentzlab.app.ui.screens;

import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

/** Advice reads the revealed human observation and uses the current game's strategy and scoring. */
final class ContractAdvice {
    private ContractAdvice() { }

    static boolean available(RentzMatch.Observation view, boolean blind) {
        return !blind && view.phase() == RentzMatch.Phase.CHOOSE && view.seat() == 0 && view.turn() == 0
                && view.revealed() && view.available().size() > 1;
    }

    static Contract suggest(RentzMatch.Observation view, RentzPlayer.Difficulty difficulty) {
        if (!available(view, false)) throw new IllegalArgumentException("No revealed human choice to advise");
        var player = new RentzPlayer(difficulty, GameSession.scoringProfile(), PolicySettings.defaults());
        var suggestion = player.choose(view).selected();
        if (!view.available().contains(suggestion)) throw new IllegalStateException("Suggested contract is unavailable");
        return suggestion;
    }
}
