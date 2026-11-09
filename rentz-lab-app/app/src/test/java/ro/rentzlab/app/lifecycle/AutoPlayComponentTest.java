package ro.rentzlab.app.lifecycle;

import org.junit.Test;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

import static org.junit.Assert.*;

@AndroidComponentLayer
public class AutoPlayComponentTest {
    @Test public void optInMakesOneLegalChoiceAtTheSelectedDifficulty() throws Exception {
        for (var difficulty : RentzPlayer.Difficulty.values()) {
            try (var fixture = new LifecycleFixture(null)) {
                var controller = fixture.controller;
                assertTrue(controller.newGame(4, 31, difficulty));
                var before = controller.session().save();
                assertTrue(controller.resume());
                deliver(fixture);
                assertEquals(before, fixture.storage.restored().save());

                assertTrue(controller.resumeHumanTurn());
                assertFalse(controller.resumeHumanTurn());
                fixture.worker.next();
                assertEquals(before, fixture.storage.restored().save());
                fixture.delivery.all();

                var restored = fixture.storage.restored();
                var commands = restored.save().commands();
                assertEquals(2, commands.size());
                assertEquals(GameSession.Action.REVEAL, commands.get(0).action());
                assertEquals(GameSession.Action.CHOOSE, commands.get(1).action());
                assertEquals(0, commands.get(1).seat());
                assertFalse(commands.get(1).blind());
                assertEquals(difficulty, restored.difficulty());
                assertEquals(RentzMatch.Phase.PLAY, restored.view().phase());
            }
        }
    }

    @Test public void switchingOffRejectsAQueuedChoiceAndReturnsTheSameTurnToThePlayer() throws Exception {
        try (var fixture = new LifecycleFixture(null)) {
            var controller = fixture.controller;
            var before = controller.session().save();
            assertTrue(controller.resumeHumanTurn());
            fixture.worker.next();
            assertFalse(fixture.delivery.tasks.isEmpty());

            controller.cancelHumanAutomation();
            fixture.delivery.all();

            assertFalse(controller.isBusy());
            assertEquals(0, fixture.events.ready);
            assertEquals(before, fixture.storage.restored().save());
            assertTrue(controller.apply(LifecycleFixture.REVEAL));
            assertTrue(fixture.storage.restored().view().revealed());
        }
    }

    @Test public void switchingOffDoesNotCancelAnOpponentTurn() throws Exception {
        try (var fixture = new LifecycleFixture(null)) {
            var controller = fixture.controller;
            assertTrue(controller.apply(LifecycleFixture.REVEAL));
            assertTrue(controller.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.WHIST, false)));
            var card = controller.session().view().legal().get(0);
            assertTrue(controller.apply(new GameSession.Command(GameSession.Action.PLAY, 0, card, null, false)));
            var before = controller.session().save();

            assertFalse(controller.resumeHumanTurn());
            assertTrue(controller.resume());
            controller.cancelHumanAutomation();
            assertTrue(controller.isBusy());
            deliver(fixture);
            var commands = fixture.storage.restored().save().commands();
            assertEquals(before.commands().size() + 1, commands.size());
            assertEquals(1, commands.get(before.commands().size()).seat());
        }
    }

    @Test public void autoPlayCompletesRentzAndTricksThroughDurableTurnsAndStopsAtSettlement() throws Exception {
        for (var contract : new Contract[]{Contract.RENTZ, Contract.WHIST}) {
            try (var fixture = new LifecycleFixture(null)) {
                var controller = fixture.controller;
                assertTrue(controller.apply(LifecycleFixture.REVEAL));
                assertTrue(controller.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, contract, false)));
                int automatedMoves = 0;
                for (int turn = 0; turn < 128 && controller.session().view().phase() == RentzMatch.Phase.PLAY; turn++) {
                    var state = controller.session().view();
                    int revision = controller.session().save().commands().size();
                    if (controller.session().hasForcedHumanPass()) {
                        assertFalse(controller.resumeHumanTurn());
                        assertTrue(controller.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false)));
                    } else if (state.turn() == 0) {
                        assertTrue(controller.resumeHumanTurn());
                        deliver(fixture);
                        var move = fixture.storage.restored().save().commands().get(revision);
                        assertEquals(GameSession.Action.PLAY, move.action());
                        assertEquals(0, move.seat());
                        assertTrue(state.legal().contains(move.card()));
                        automatedMoves++;
                    } else {
                        assertTrue(controller.resume());
                        deliver(fixture);
                    }
                    assertEquals(revision + 1, fixture.storage.restored().save().commands().size());
                }

                var settled = fixture.storage.restored();
                assertTrue(automatedMoves > 0);
                assertEquals(RentzMatch.Phase.DEAL_END, settled.view().phase());
                assertEquals(1, settled.history().size());
                assertFalse(controller.resumeHumanTurn());
                controller.deactivate();
                assertTrue(controller.activate());
                assertEquals(settled.save(), controller.session().save());
                assertFalse(controller.resumeHumanTurn());
                assertTrue(fixture.worker.tasks.isEmpty());
            }
        }
    }

    @Test public void debugProjectionFlagsADelegatedDecisionOnlyOnTheTable() throws Exception {
        try (var fixture = new LifecycleFixture(null)) {
            var controller = fixture.controller;
            controller.publish("GAME");
            assertFalse(new org.json.JSONObject(fixture.storage.actor).getBoolean("busy"));
            controller.publish("GAME", true);
            assertTrue(new org.json.JSONObject(fixture.storage.actor).getBoolean("busy"));
            controller.publish("MENU", true);
            assertFalse(new org.json.JSONObject(fixture.storage.actor).getBoolean("busy"));
        }
    }

    @Test public void theCheckboxHasEnglishAndRomanianLabels() {
        assertEquals("Auto play", new RentzUiText(RentzUiText.Language.EN).autoPlay());
        assertEquals("Joc automat", new RentzUiText(RentzUiText.Language.RO).autoPlay());
    }

    private static void deliver(LifecycleFixture fixture) {
        fixture.worker.next();
        fixture.delivery.all();
        assertEquals(0, fixture.events.failed);
    }
}
