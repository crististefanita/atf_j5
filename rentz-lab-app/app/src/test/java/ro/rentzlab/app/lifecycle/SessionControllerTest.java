package ro.rentzlab.app.lifecycle;

import ro.rentzlab.app.testing.AndroidComponentLayer;

import ro.rentzlab.ai.*;

import org.junit.Test;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.app.ui.table.RentzTableContent;
import ro.rentzlab.app.ui.table.TrickPreview;
import ro.rentzlab.core.*;
import java.io.IOException;
import static org.junit.Assert.*;

@AndroidComponentLayer
public class SessionControllerTest {
    @Test public void acceptedHumanCommandIsDurableBeforeBotWorkStarts() throws Exception {
        try(var f=new LifecycleFixture()){
            assertTrue(f.controller.apply(LifecycleFixture.REVEAL));
            assertTrue(f.storage.restored().view().revealed());
            assertTrue(f.controller.session().view().revealed());
            assertEquals(1,f.storage.restored().save().commands().size());
            assertEquals(0,f.events.ready);
            assertTrue(f.worker.tasks.isEmpty());
        }
    }
    @Test public void callbackQueuedBeforeCloseCannotReachTheDestroyedOwner() throws Exception {
        try(var f=new LifecycleFixture()){
            f.controller.resume();f.worker.next();assertFalse(f.delivery.tasks.isEmpty());
            f.controller.close();f.delivery.all();assertEquals(0,f.events.ready);assertEquals(0,f.events.failed);
        }
    }
    @Test public void delayedFailureIsDiscardedAfterPause() throws Exception {
        try(var f=new LifecycleFixture(s->{throw new IllegalStateException("calculation failed");})){
            f.controller.resume();f.worker.next();f.controller.deactivate();f.delivery.all();
            assertEquals(0,f.events.failed);assertFalse(f.controller.isBusy());
        }
    }
    @Test public void calculationIgnoringCancellationCannotMutateOrSaveTheLiveSession() throws Exception {
        final SessionController[] controller=new SessionController[1];
        try(var f=new LifecycleFixture(s->{controller[0].deactivate();Thread.interrupted();s.apply(LifecycleFixture.REVEAL);})){
            controller[0]=f.controller;String before=f.storage.saved;var original=f.controller.session().save();
            f.controller.resume();f.worker.next();f.delivery.all();
            assertEquals(original,f.controller.session().save());assertEquals(before,f.storage.saved);
            assertEquals(0,f.events.ready);assertEquals(0,f.events.failed);
        }
    }
    @Test public void anOlderActivityCannotOverwriteANewOwnersGame() throws Exception {
        try(var f=new LifecycleFixture(s->s.apply(LifecycleFixture.REVEAL))){
            f.controller.resume();var worker=new LifecycleFixture.Worker();var queue=new LifecycleFixture.Queue();var events=new LifecycleFixture.Events();
            try(var replacement=new SessionController(f.repository,worker,queue,events,s->{})){
                replacement.activate();assertTrue(replacement.newGame(4,99,RentzPlayer.Difficulty.NORMAL));
                assertTrue(worker.tasks.isEmpty());
                f.worker.next();f.delivery.all();assertEquals(99,f.storage.restored().save().seed());assertEquals(0,f.events.ready);
                f.controller.publish("GAME");assertNull(f.storage.actor);
            }
        }
    }
    @Test public void activationRetriesTheSavedGameAfterATransientReadFailure() throws Exception {
        try (var f = new LifecycleFixture()) {
            assertTrue(f.controller.apply(LifecycleFixture.REVEAL));
            var checkpoint = f.controller.session().save();
            String saved = f.storage.saved;
            int writes = f.storage.writes;
            f.controller.deactivate();
            f.storage.failRead = true;

            assertThrows(IOException.class, f.controller::activate);
            assertNull(f.controller.session());
            assertFalse(f.controller.isBusy());
            assertThrows(IllegalStateException.class,
                    () -> f.controller.newGame(4, 99, RentzPlayer.Difficulty.NORMAL));
            f.storage.failRead = false;

            assertTrue(f.controller.activate());
            assertEquals(checkpoint, f.controller.session().save());
            assertFalse(f.controller.activate());
            assertEquals(saved, f.storage.saved);
            assertEquals(writes, f.storage.writes);
        }
    }

    @Test public void pausedControllerReloadsTheLatestCheckpointAndAcceptsNewWork() throws Exception {
        try(var f=new LifecycleFixture()){
            f.controller.resume();f.worker.next();f.controller.deactivate();
            f.storage.saved=SessionJson.saved(new GameSession(4,99,RentzPlayer.Difficulty.NORMAL)).toString();
            f.controller.activate();assertTrue(f.controller.apply(LifecycleFixture.REVEAL));
            f.delivery.all();assertEquals(0,f.events.ready);assertEquals(99,f.controller.session().save().seed());
            assertTrue(f.worker.tasks.isEmpty());
            f.controller.resume();f.worker.next();f.delivery.all();
            assertEquals(1,f.events.ready);assertTrue(f.storage.restored().view().revealed());
        }
    }
    @Test public void changedRevisionInvalidatesAnAlreadyCalculatedResult() throws Exception {
        try(var f=new LifecycleFixture()){
            f.controller.resume();f.worker.next();String persisted=f.storage.saved;
            f.controller.session().apply(LifecycleFixture.REVEAL);f.delivery.all();
            assertEquals(persisted,f.storage.saved);assertEquals(0,f.events.ready);assertFalse(f.controller.isBusy());
        }
    }
    @Test public void failedHumanCheckpointKeepsThePreviousStateAndDoesNotScheduleBots() throws Exception {
        try(var f=new LifecycleFixture()){
            String before=f.storage.saved;f.storage.fail=true;
            assertFalse(f.controller.apply(LifecycleFixture.REVEAL));
            assertEquals(before,f.storage.saved);assertFalse(f.controller.session().view().revealed());
            assertTrue(f.worker.tasks.isEmpty());assertEquals(1,f.events.failed);assertFalse(f.controller.isBusy());
        }
    }
    @Test public void failedBotCheckpointCannotAdoptTheUnpersistedCandidate() throws Exception {
        try(var f=new LifecycleFixture(s->s.apply(LifecycleFixture.REVEAL))){
            String before=f.storage.saved;f.controller.resume();f.worker.next();f.storage.fail=true;f.delivery.all();
            assertEquals(before,f.storage.saved);assertFalse(f.controller.session().view().revealed());
            assertEquals(1,f.events.failed);assertEquals(0,f.events.ready);assertFalse(f.controller.isBusy());
        }
    }
    @Test public void rejectedWorkerDoesNotLeaveTheControllerBusy() throws Exception {
        try(var f=new LifecycleFixture()){
            f.worker.shutdown();try{f.controller.resume();}catch(java.util.concurrent.RejectedExecutionException ignored){}
            assertFalse(f.controller.isBusy());assertEquals(1,f.events.failed);
        }
    }
    @Test public void rejectedDeliveryKeepsTheCheckpointAndReleasesBusyState()throws Exception{
        try(var f=new LifecycleFixture(s->s.apply(LifecycleFixture.REVEAL))){
            String before=f.storage.saved;f.delivery.reject=true;f.controller.resume();f.worker.next();
            assertEquals(before,f.storage.saved);assertFalse(f.controller.isBusy());assertEquals(0,f.events.ready);assertEquals(0,f.events.failed);
        }
    }
    @Test public void closeIsIdempotentAndCannotBeReactivated()throws Exception{
        try(var f=new LifecycleFixture()){
            f.controller.close();f.controller.close();assertTrue(f.worker.isShutdown());
            try{f.controller.activate();fail("A closed controller was reactivated");}catch(IllegalStateException expected){}
        }
    }
    @Test public void realBotsCommitOneCardPerTurnAndExposeEachSeatBeforeCollection() throws Exception {
        try(var f=new LifecycleFixture(null)){
            assertTrue(f.controller.apply(LifecycleFixture.REVEAL));
            assertTrue(f.controller.apply(new GameSession.Command(GameSession.Action.CHOOSE,0,null,Contract.WHIST,false)));
            var preview=new TrickPreview();
            var initial=f.controller.session().view();
            preview.reset(initial);
            assertTrue(f.controller.apply(new GameSession.Command(GameSession.Action.PLAY,0,initial.legal().get(0),null,false)));
            var copy=new RentzUiText(RentzUiText.Language.RO);
            var names=f.controller.session().displayNames("Tu");
            for(int actor=1;actor<=3;actor++){
                var view=f.controller.session().view();
                assertEquals(actor,view.turn());
                assertEquals(actor,preview.display(view,1000).size());
                assertEquals(0L,preview.remaining(1000));
                var active=RentzTableContent.seats(view,copy,names,false).stream().filter(seat->seat.active()).toList();
                assertEquals(1,active.size());
                assertEquals(actor,active.get(0).seat());
                assertTrue(active.get(0).description().endsWith("Joacă"));
                var before=f.controller.session().save();
                assertTrue(f.worker.tasks.isEmpty());
                assertTrue(f.controller.resume());
                assertFalse(f.controller.resume());
                f.worker.next();
                assertEquals(before,f.controller.session().save());
                f.delivery.all();
                var after=f.storage.restored().save();
                assertEquals(before.commands().size()+1,after.commands().size());
                var move=after.commands().get(before.commands().size());
                assertEquals(GameSession.Action.PLAY,move.action());
                assertEquals(actor,move.seat());
                assertEquals(f.controller.session().view(),f.storage.restored().view());
            }
            var completed=f.controller.session().view();
            assertEquals(4,preview.display(completed,1000).size());
            assertTrue(RentzTableContent.seats(completed,copy,names,true).stream().noneMatch(seat->seat.active()));
            assertEquals(3,f.events.ready);
        }
    }

    @Test public void sequenceBotPassAndPlayAreSeparatePublicTurns() {
        var game=new GameSession(4,31,RentzPlayer.Difficulty.NORMAL);
        game.apply(new GameSession.Command(GameSession.Action.CHOOSE,0,null,Contract.RENTZ,true));
        var preview=new TrickPreview();
        preview.reset(game.view());
        boolean sawPlay=false, sawPass=false;
        for(int turn=0;turn<48&&game.view().phase()==RentzMatch.Phase.PLAY;turn++){
            var before=game.view();
            if(!game.hasPendingBotTurn()){
                var card=before.legal().isEmpty()?null:before.legal().get(0);
                game.apply(new GameSession.Command(card==null?GameSession.Action.PASS:GameSession.Action.PLAY,0,card,null,false));
                continue;
            }
            int revision=game.save().commands().size();
            game.advanceBotTurn();
            var after=game.view();
            var commands=game.save().commands();
            assertEquals(revision+1,commands.size());
            var move=commands.get(revision);
            assertEquals(before.turn(),move.seat());
            boolean played=move.action()==GameSession.Action.PLAY;
            assertTrue(played||move.action()==GameSession.Action.PASS);
            assertEquals(before.board().size()+(played?1:0),after.board().size());
            assertTrue(preview.display(after,1000).isEmpty());
            assertEquals(0L,preview.remaining(1000));
            sawPlay|=played;
            sawPass|=!played;
            if(sawPlay&&sawPass)break;
        }
        assertTrue("Fixture must exercise both a play and a pass",sawPlay&&sawPass);
    }

    @Test public void aSavedHandEmitsOneNoticeAndFailedOrRestoredSettlementsEmitNone() throws Exception {
        var storage = new LifecycleFixture.Memory();
        var profile = new ro.rentzlab.app.profile.LocalProfile(new java.util.UUID(0, 7), java.time.ZoneId.of("UTC"));
        var repository = new SessionRepository(storage, profile, java.time.Clock.systemUTC());
        var worker = new LifecycleFixture.Worker();
        var delivery = new LifecycleFixture.Queue();
        var events = new LifecycleFixture.Events();
        try (var controller = new SessionController(repository, worker, delivery, events)) {
            controller.activate();
            assertTrue(controller.newGame(4, 31, ro.rentzlab.ai.RentzPlayer.Difficulty.NORMAL));
            assertTrue(controller.apply(LifecycleFixture.REVEAL));
            assertTrue(controller.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null,
                    Contract.WHIST, false, GameSession.Origin.MANUAL)));
            for (int card = 0; card < 31; card++) playWhistTurn(controller, worker, delivery);
            assertNull(controller.takeAchievementNotice(1));
            storage.fail = true;
            playWhistTurn(controller, worker, delivery);
            assertEquals(1, events.failed);
            assertEquals(RentzMatch.Phase.PLAY, controller.session().view().phase());
            assertNull(controller.takeAchievementNotice(1));

            storage.fail = false;
            playWhistTurn(controller, worker, delivery);
            var notice = controller.takeAchievementNotice(1);
            assertNotNull(notice);
            assertFalse(notice.matchEnd());
            assertTrue(notice.changes().stream().anyMatch(change -> change.badge().id().equals("play-first-hand")));
            assertNull(controller.takeAchievementNotice(1));

            controller.deactivate();
            controller.activate();
            assertEquals(RentzMatch.Phase.DEAL_END, controller.session().view().phase());
            var progress = ro.rentzlab.app.SessionJson.restoreCheckpoint(storage.read()).progress();
            assertTrue("A settled manual hand persists its gameplay credit", progress.marks().containsKey("play-first-hand"));
            assertFalse("The restored incomplete match cannot persist a victory mark",
                    progress.marks().containsKey("play-first-win"));
            assertNull(controller.takeAchievementNotice(1));
        }
    }

    private static void playWhistTurn(SessionController controller, LifecycleFixture.Worker worker,
            LifecycleFixture.Queue delivery) {
        var view = controller.session().view();
        if (view.turn() == 0) {
            controller.apply(new GameSession.Command(GameSession.Action.PLAY, 0, view.legal().get(0), null,
                    false, GameSession.Origin.MANUAL));
        } else {
            assertTrue(controller.resume());
            worker.next();
            delivery.all();
        }
    }
}
