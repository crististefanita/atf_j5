package ro.rentzlab.app.ui.table;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;

@AndroidComponentLayer
public final class TrickPreviewTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void completedTrickRemainsVisibleForExactlyTheOriginalDeadlineWithoutReplayingCommands() {
        var session = game(); var preview = new TrickPreview(); preview.reset(session.view());
        session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false)); session.advanceBots();
        var state = session.view(); var journal = session.save();
        var shown = preview.display(state, 1000);
        audit.equal("All four public cards", 4, shown.size());
        audit.equal("One card per actor", 4L, shown.stream().map(play -> play.seat()).distinct().count());
        audit.equal("Original hold deadline", 1500L, preview.remaining(1000));
        audit.equal("Repeated observation keeps the same trick", shown, preview.display(state, 2000));
        audit.equal("No deadline extension", 500L, preview.remaining(2000));
        audit.equal("Current trick after deadline", state.trick(), preview.display(state, 2500));
        audit.equal("Presentation never changes journal", journal, session.save());
    }
    @Test public void resumeAndNewPreviewDoNotAnimateAnOldCompletionAgain() {
        var session = game(); session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false)); session.advanceBots();
        var restored = GameSession.restore(session.save()); var preview = new TrickPreview(); preview.reset(restored.view());
        audit.equal("Resume displays actual current trick", restored.view().trick(), preview.display(restored.view(), 1000));
        audit.equal("No old collection deadline", 0L, preview.remaining(1000));
    }
    private GameSession game() {
        var session = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
        session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.WHIST, true)); return session;
    }
}
