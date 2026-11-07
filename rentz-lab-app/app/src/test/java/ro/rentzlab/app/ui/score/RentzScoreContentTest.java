package ro.rentzlab.app.ui.score;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;
import java.util.List;

@AndroidComponentLayer
public final class RentzScoreContentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void helpShowsOnlyThePlacesAvailableAtEachTable() {
        var rows = RentzScoreContent.finishingAwards(GameSession.scoringProfile());
        audit.equal("Three through six seats with zero awards at actual places", List.of(
                List.of(300, 200, 100),
                List.of(300, 200, 100, 0),
                List.of(300, 200, 100, 0, 0),
                List.of(300, 200, 100, 0, 0, 0)), rows);
        audit.rejects("Award matrix cannot gain an unsupported table", UnsupportedOperationException.class, rows::clear);
        audit.rejects("An actual place cannot be removed", UnsupportedOperationException.class, rows.get(3)::clear);
    }

    @Test public void helpUsesTheEffectiveProfileWithoutSortingOrInventingPlaces() {
        var alternate = new ScoreProfile(0, 4, 8, 16, 2, 32, List.of(25, 900, 25, 7, 0, 12, 999));
        audit.equal("Non-default awards keep place order, ties and zero; seventh place stays outside the grid", List.of(
                List.of(25, 900, 25),
                List.of(25, 900, 25, 7),
                List.of(25, 900, 25, 7, 0),
                List.of(25, 900, 25, 7, 0, 12)), RentzScoreContent.finishingAwards(alternate));
        audit.equal("English trick tariffs come from the same profile", "Trick 0 · queen −4 · diamond −8 · K♥ −16. Whist +2 · 10♣ +32.",
                new RentzUiText(RentzUiText.Language.EN).trickScoring(alternate));
        audit.equal("Romanian trick tariffs come from the same profile", "Levată 0 · damă −4 · caro −8 · K♥ −16. Whist +2 · 10♣ +32.",
                new RentzUiText(RentzUiText.Language.RO).trickScoring(alternate));
    }

    @Test public void negativeAndPositiveRentzDeltasAndTotalsSurviveRestoreWithoutWhistColumns() {
        for (Contract contract : List.of(Contract.TRICKS, Contract.WHIST)) {
            var session = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
            var copy = new RentzUiText(RentzUiText.Language.EN);
            session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, contract, true)); int moves = 0;
            audit.truth("Current hand score stays hidden during play " + contract,
                    RentzScoreContent.currentHand(session.view(), session.history(), copy).isEmpty());
            while (session.view().phase() == RentzMatch.Phase.PLAY) {
                session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false)); session.advanceBots();
                if (++moves > 20) throw new AssertionError("No completed trick hand");
            }
            var rows = RentzScoreContent.rows(session.history(), copy);
            audit.equal("One confirmed history row " + contract, 1, rows.size());
            audit.equal("Eight tricks, blind doubling " + contract, contract == Contract.TRICKS ? -160 : 160, rows.get(0).values().stream().mapToInt(value -> value.delta()).sum());
            audit.equal("Restored exact history " + contract, rows, RentzScoreContent.rows(GameSession.restore(session.save()).history(), copy));
            audit.equal("Totals keep engine seat order " + contract, session.view().totals(), rows.get(0).values().stream().map(value -> value.total()).toList());
            audit.equal("Chooser retained " + contract, 0, rows.get(0).chooser());
            var restored = GameSession.restore(session.save());
            var score = RentzScoreContent.currentHand(restored.view(), restored.history(), copy).orElseThrow();
            audit.equal("Current score matches its history row after restart " + contract, rows.get(0), score);
            audit.equal("Current score has a contribution group for every seat " + contract, 4, RentzScoreContent.details(score).size());
            restored.apply(new GameSession.Command(GameSession.Action.NEXT_HAND, 0, null, null, false));
            audit.truth("The next hand cannot reopen the previous result " + contract,
                    RentzScoreContent.currentHand(restored.view(), restored.history(), copy).isEmpty());
        }
    }
}
