package ro.rentzlab.app.lifecycle;

import ro.rentzlab.ai.*;

import org.junit.Test;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.core.*;
import static org.junit.Assert.*;

public class SessionControllerTest {
    @Test public void acceptedHumanCommandIsDurableBeforeBotWorkStarts() throws Exception {
        try(var f=new LifecycleFixture()){
            assertTrue(f.controller.apply(LifecycleFixture.REVEAL));
            assertTrue(f.storage.restored().view().revealed());
            assertTrue(f.controller.session().view().revealed());
            assertEquals(1,f.storage.restored().save().commands().size());
            assertEquals(0,f.events.ready);
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
                replacement.activate();replacement.newGame(4,99,RentzPlayer.Difficulty.NORMAL);worker.next();queue.all();
                f.worker.next();f.delivery.all();assertEquals(99,f.storage.restored().save().seed());assertEquals(0,f.events.ready);
                f.controller.publish("GAME");assertNull(f.storage.actor);
            }
        }
    }
    @Test public void pausedControllerReloadsTheLatestCheckpointAndAcceptsNewWork() throws Exception {
        try(var f=new LifecycleFixture()){
            f.controller.resume();f.worker.next();f.controller.deactivate();
            f.storage.saved=SessionJson.saved(new GameSession(4,99,RentzPlayer.Difficulty.NORMAL)).toString();
            f.controller.activate();assertTrue(f.controller.apply(LifecycleFixture.REVEAL));
            f.delivery.all();assertEquals(0,f.events.ready);assertEquals(99,f.controller.session().save().seed());
            f.worker.next();f.delivery.all();assertEquals(1,f.events.ready);assertTrue(f.storage.restored().view().revealed());
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
    @Test public void realBotProgressIsIsolatedUntilAuthorizedDeliveryAndSurvivesReload() throws Exception {
        try(var f=new LifecycleFixture(GameSession::advanceBots)){
            var game=new GameSession(4,31,RentzPlayer.Difficulty.NORMAL);
            game.apply(LifecycleFixture.REVEAL);game.apply(new GameSession.Command(GameSession.Action.CHOOSE,0,null,Contract.WHIST,false));
            game.apply(new GameSession.Command(GameSession.Action.PLAY,0,game.view().legal().get(0),null,false));
            f.controller.deactivate();f.storage.saved=SessionJson.saved(game).toString();f.controller.activate();var before=f.controller.session().save();
            f.controller.resume();f.worker.next();assertEquals(before,f.controller.session().save());
            f.delivery.all();assertTrue(f.controller.session().save().commands().size()>before.commands().size());
            assertEquals(f.controller.session().view(),f.storage.restored().view());assertEquals(1,f.events.ready);
        }
    }
}
