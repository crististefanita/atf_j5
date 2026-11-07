package ro.rentzlab.app.ui.table;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;
import ro.cardtable.ui.animation.TrickCollectionMotion;

@AndroidComponentLayer
public final class TrickPreviewTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    @Test public void completedTrickRemainsVisibleForExactlyTheOriginalDeadlineWithoutReplayingCommands() {
        var session = game(); var preview = new TrickPreview(); preview.reset(session.view());
        session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false)); session.advanceBots();
        var state = session.view(); var journal = session.save();
        var shown = preview.display(state, 1000);
        preview.markVisible(1000);
        audit.equal("All four public cards", 4, shown.size());
        audit.equal("One card per actor", 4L, shown.stream().map(play -> play.seat()).distinct().count());
        audit.equal("Original hold deadline", 1500L, preview.remaining(1000));
        var collection = preview.collection(1000).orElseThrow();
        audit.equal("The next lead is the collection destination", state.trick().isEmpty() ? state.turn() : state.trick().get(0).seat(), collection.winner());
        audit.equal("Motion keeps physical seats in play order", shown.stream().map(TrickRules.Play::seat).toList(), collection.seats());
        audit.equal("Faces hold before the shared motion", new TrickCollectionMotion.Timing(1000, 500, 0),
                TrickCollectionMotion.timing(preview.remaining(1000), 0));
        audit.equal("Repeated observation keeps the same trick", shown, preview.display(state, 2000));
        audit.equal("No deadline extension", 500L, preview.remaining(2000));
        audit.equal("Current trick after deadline", state.trick(), preview.display(state, 2500));
        audit.truth("Expired collection cannot attach again", preview.collection(2500).isEmpty());
        audit.equal("Presentation never changes journal", journal, session.save());
    }
    @Test public void sameProcessResetDoesNotAnimateAnOldCompletionAgain() {
        var session = game(); session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false)); session.advanceBots();
        var restored = GameSession.restore(session.save()); var preview = new TrickPreview(); preview.reset(restored.view());
        audit.equal("Resume displays actual current trick", restored.view().trick(), preview.display(restored.view(), 1000));
        audit.equal("No old collection deadline", 0L, preview.remaining(1000));
        audit.truth("No old animation target after restore", preview.collection(1000).isEmpty());
    }
    @Test public void aColdRestorePresentsOnlyTheFinalConfirmedTrickAndDoesNotApplyItAgain() throws Exception {
        var session = game();
        GameSession.Saved beforeLastPlay = null;
        while (session.view().phase() == RentzMatch.Phase.PLAY) {
            var view = session.view();
            if (view.turn() == 0 && view.hand().size() == 1) beforeLastPlay = session.save();
            var actor = view.turn();
            if (actor == 0) session.apply(new GameSession.Command(GameSession.Action.PLAY, actor, view.legal().get(0), null, false));
            else session.advanceBots();
        }
        var journal = SessionJson.saved(session).toString();
        var restored = SessionJson.restore(journal);
        var view = restored.view();
        var preview = new TrickPreview();
        preview.restoreLast(view);
        var last = preview.display(view, 100);
        audit.equal("Four public faces on cold restore", 4, last.size());
        var events = view.events().stream().filter(event -> event.card() != null).toList();
        audit.equal("Only final played cards", events.subList(events.size() - 4, events.size()).stream().map(RentzMatch.TurnEvent::card).toList(),
                last.stream().map(TrickRules.Play::card).toList());
        audit.truth("Cold replay waits for the first Android frame", preview.awaitingFirstFrame());
        audit.equal("Layout work does not spend the display window", 1500L, preview.remaining(450));
        preview.markVisible(450);
        preview.markVisible(600);
        audit.equal("Restart opens a full display window", 1500L, preview.remaining(450));
        audit.equal("Persisted totals unchanged", session.history(), restored.history());
        audit.equal("No write or command in the presentation", journal, SessionJson.saved(restored).toString());
        audit.equal("Last frame before deadline", last, preview.display(view, 1949));
        audit.equal("Restored table after deadline", List.of(), preview.display(view, 1950));
        audit.truth("A redraw cannot reattach the expired collection", preview.collection(1951).isEmpty());
        audit.truth("The pre-final journal has an explicit human play", beforeLastPlay != null);
        writeNativeFixture(beforeLastPlay, journal, preview, restored);
    }
    @Test public void incompleteTricksAndRentzSequenceNeverStartCollection() {
        var session = game();
        var preview = new TrickPreview();
        preview.reset(session.view());
        session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false));
        preview.display(session.view(), 1000);
        audit.truth("One played card cannot start collection", preview.collection(1000).isEmpty());
        var sequence = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
        sequence.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, true));
        sequence.advanceBots();
        preview.display(sequence.view(), 1100);
        audit.truth("The Rentz sequence cannot start collection", preview.collection(1100).isEmpty());
        preview.restoreLast(sequence.view());
        audit.truth("Cold resume of a sequence has no collection", preview.collection(1100).isEmpty());
        preview.restoreLast(session.view());
        audit.truth("Cold resume preserves a partially played trick", preview.collection(1100).isEmpty());
    }
    private static void writeNativeFixture(GameSession.Saved before, String journal, TrickPreview preview, GameSession restored) throws Exception {
        String output = System.getProperty("rentz.uiEvidence");
        if (output == null || output.isBlank()) return;
        var directory = Files.createDirectories(Path.of(output, "collection-fixture"));
        Files.write(directory.resolve("pre-final.json"), SessionJson.saved(GameSession.restore(before)).toString().getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("final.json"), journal.getBytes(StandardCharsets.UTF_8));
        preview.restoreLast(restored.view());
        Files.write(directory.resolve("winner.txt"),
                Integer.toString(preview.collection(1700).orElseThrow().winner()).getBytes(StandardCharsets.UTF_8));
        var chooser = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
        Files.write(directory.resolve("chooser.json"), SessionJson.saved(chooser).toString().getBytes(StandardCharsets.UTF_8));
    }
    private GameSession game() {
        var session = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
        session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.WHIST, true)); return session;
    }
}
