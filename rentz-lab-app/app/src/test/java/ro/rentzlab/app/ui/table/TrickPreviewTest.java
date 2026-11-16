package ro.rentzlab.app.ui.table;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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
    @Test public void rendererStartsAtTheExistingMotionBoundaryAndFadesOnlyAtTheEnd() {
        var timing = new RentzCollectionAnimation.Timeline(1500);
        audit.equal("Cards remain stationary at the end of the hold", 0f, timing.distance(1000f / 1500f));
        float firstMovement = timing.distance(1100f / 1500f);
        audit.truth("The first movement keeps neighboring faces distinguishable", firstMovement > .035f && firstMovement < .2f);
        audit.equal("Cards reach the seat before fading", 1f, timing.distance(1380f / 1500f));
        audit.equal("Cards are opaque through the first 400 ms of motion", 0f, timing.fade(1400f / 1500f));
        audit.equal("Cards reach the winning seat by the deadline", 1f, timing.distance(1f));
        audit.equal("The group disappears at the deadline", 1f, timing.fade(1f));
    }
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
        audit.truth("A settled checkpoint needs no bot turn on cold resume", !restored.hasPendingBotTurn());
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
    @Test public void rentzArrivalWaitsForOneVisibleFrameBeforeTheScore() {
        for (int players : new int[]{3, 5, 6}) {
            var play = rentzBoundary(players, RentzMatch.Phase.PLAY);
            var finish = rentzBoundary(players, RentzMatch.Phase.DEAL_END);
            var preview = new TrickPreview();
            preview.reset(play);
            preview.display(play, 100);
            audit.equal("No presentation pending during play at " + players, 0L, preview.remaining(100));
            preview.display(finish, 200);
            audit.truth("Rentz must present its arrival before the score at " + players, preview.awaitingFirstFrame());
            audit.truth("The pending frame is a Rentz arrival at " + players, preview.arrival(200));
            audit.equal("Queueing does not spend the arrival window at " + players, 1800L, preview.remaining(4000));
            audit.truth("No collection motion is created for Rentz at " + players, preview.collection(4000).isEmpty());
            preview.markVisible(4000);
            preview.display(finish, 5000);
            preview.markVisible(5000);
            audit.equal("One clock controls the deadline at " + players, 1L, preview.remaining(5799));
            audit.equal("Release the score at the deadline at " + players, 0L, preview.remaining(5800));
            audit.truth("The arrival is no longer eligible at " + players, !preview.arrival(5800));
            preview.display(finish, 6000);
            audit.equal("Redrawing the settled board never starts it again at " + players, 0L, preview.remaining(6000));
            preview.restoreLast(finish);
            preview.display(finish, 6200);
            audit.equal("Resuming the saved score does not replay Rentz arrival at " + players, 0L, preview.remaining(6200));
        }
    }

    @Test public void arrivalExposesTheWinnerAndRemainingCardsWithoutPresentingPendingPoints() {
        var english = new ro.rentzlab.app.ui.copy.RentzUiText(ro.rentzlab.app.ui.copy.RentzUiText.Language.EN);
        for (int players : new int[]{3, 5, 6}) {
            var match = new RentzMatch(players, 31, ScoreProfile.laboratory());
            match.choose(0, Contract.RENTZ, false);
            for (int action = 0; action < 128 && match.observe(0).phase() == RentzMatch.Phase.PLAY; action++) {
                var actor = match.observe(match.observe(0).turn());
                if (actor.legal().isEmpty()) match.pass(actor.seat());
                else match.play(actor.seat(), actor.legal().get(0));
            }
            var finish = match.observe(0);
            audit.equal("The public sequence settled at " + players, RentzMatch.Phase.DEAL_END, finish.phase());
            var names = java.util.stream.IntStream.range(0, players).mapToObj(seat -> "Player " + seat).toList();
            int winner = finish.finishOrder().get(0);
            int left = players * 8 - finish.board().size();
            audit.truth("The last player still holds cards at " + players, left > 0);
            audit.equal("Arrival announces public facts at " + players,
                    "Rentz finish · " + names.get(winner) + "\nUnplayed cards · " + left,
                    RentzTableContent.arrival(finish, english, names));
            var waiting = RentzTableContent.seats(finish, english, names, true, false, true);
            audit.equal("Arrival withholds the winning award at " + players, "…", waiting.get(winner).facts().get(0).value());
            audit.equal("Arrival shows only the previous total at " + players, "0", waiting.get(winner).facts().get(1).value());
            var confirmed = RentzTableContent.seats(finish, english, names, false);
            audit.equal("Confirmed board exposes the same winning seat at " + players, "300", confirmed.get(winner).facts().get(0).value());
        }
    }

    @Test public void trickCollectionKeepsOneCardPerSeatAndItsDeadlineAtEveryTargetSize() {
        for (int players : new int[]{3, 5, 6}) {
            var match = new RentzMatch(players, 31, ScoreProfile.laboratory());
            var preview = new TrickPreview();
            match.choose(0, Contract.TRICKS, false);
            preview.reset(match.observe(0));
            var played = new java.util.ArrayList<TrickRules.Play>();
            for (int index = 0; index < players; index++) {
                var actor = match.observe(match.observe(0).turn());
                var card = actor.legal().get(0);
                played.add(new TrickRules.Play(actor.seat(), card));
                match.play(actor.seat(), card);
            }
            var led = played.get(0).card().suit();
            int expectedWinner = played.stream().filter(play -> play.card().suit() == led)
                    .max(java.util.Comparator.comparingInt(play -> play.card().rank())).orElseThrow().seat();
            var after = match.observe(0);
            audit.equal("All played faces remain on the board at " + players, played, preview.display(after, 100));
            audit.equal("The independent suit/rank winner gets the collection at " + players,
                    expectedWinner, preview.collection(100).orElseThrow().winner());
            preview.markVisible(200);
            audit.equal("Faces hold until the last millisecond at " + players, played, preview.display(after, 1699));
            audit.equal("The collection clears before the next trick appears at " + players, after.trick(), preview.display(after, 1700));
            audit.truth("Collection does not replay on another redraw at " + players, preview.collection(1701).isEmpty());
        }
    }

    private static RentzMatch.Observation rentzBoundary(int players, RentzMatch.Phase phase) {
        var scores=java.util.Collections.nCopies(players, 0);
        return new RentzMatch.Observation(0, players, 0, 0, 1, phase, Contract.RENTZ, false, true,
                List.of(), List.of(), List.of(), scores, scores,
                List.of(), List.of(), List.of(), List.of(), List.of(), Set.of());
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
