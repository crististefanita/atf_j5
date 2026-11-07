package ro.rentzlab.app.ui.screens;

import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;
import ro.rentzlab.core.Contract;

@AndroidComponentLayer
public final class ContractAdviceTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void suggestionUsesOnlyTheRevealedHumanHandAndLeavesSelectionToThePlayer() {
        var session = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
        var hidden = session.view();
        audit.truth("Hint is unavailable before reveal", !ContractAdvice.available(hidden, false));
        audit.rejects("A hidden hand is never evaluated", IllegalArgumentException.class,
                () -> ContractAdvice.suggest(hidden, session.difficulty()));
        session.apply(new GameSession.Command(GameSession.Action.REVEAL, 0, null, null, false));
        var journal = session.save();
        var revealed = session.view();
        audit.truth("There is a choice to advise", ContractAdvice.available(revealed, false));
        audit.truth("Blind picker does not offer AI advice", !ContractAdvice.available(revealed, true));
        var suggestion = ContractAdvice.suggest(revealed, session.difficulty());
        audit.truth("Suggestion belongs to the allowed choices", revealed.available().contains(suggestion));
        audit.equal("Hint leaves the command journal intact", journal, session.save());
        audit.equal("Hint leaves the actor observation intact", revealed, session.view());
        session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.TRICKS, false));
        audit.truth("Advice expires when the player chooses", !ContractAdvice.available(session.view(), false));
    }
}
