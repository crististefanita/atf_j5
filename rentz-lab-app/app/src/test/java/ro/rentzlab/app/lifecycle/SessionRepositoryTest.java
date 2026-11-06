package ro.rentzlab.app.lifecycle;

import org.junit.Test;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.ai.RentzPlayer;
import static org.junit.Assert.*;

public class SessionRepositoryTest {
    @Test public void claimingNewOwnershipRevokesEveryOldWriteAndCallback() throws Exception {
        var storage=new LifecycleFixture.Memory();var repository=new SessionRepository(storage);
        var old=repository.claim();var current=repository.claim();var game=new GameSession(4,99,RentzPlayer.Difficulty.NORMAL);
        current.commit(game);assertFalse(old.isCurrent());assertTrue(current.isCurrent());String before=storage.saved;
        try{old.commit(new GameSession(4,31,RentzPlayer.Difficulty.NORMAL));fail("Stale write accepted");}catch(IllegalStateException expected){}
        try{old.publish(game,"GAME",false);fail("Stale projection accepted");}catch(IllegalStateException expected){}
        assertFalse(old.runIfCurrent(()->fail("Stale callback")));assertEquals(before,storage.saved);assertNull(storage.actor);
    }
    @Test public void closingAnOldHandleCannotRevokeANewerOwner() throws Exception {
        var repository=new SessionRepository(new LifecycleFixture.Memory());var old=repository.claim();var current=repository.claim();
        old.close();old.close();assertFalse(old.isCurrent());assertTrue(current.isCurrent());current.close();assertFalse(current.isCurrent());
    }
    @Test public void validSessionAndPublicProjectionUseDifferentDocuments() throws Exception {
        var storage=new LifecycleFixture.Memory();var owner=new SessionRepository(storage).claim();var game=new GameSession(4,31,RentzPlayer.Difficulty.NORMAL);
        owner.commit(game);owner.publish(game,"MENU",false);assertEquals(game.view(),owner.load().view());
        assertTrue(new org.json.JSONObject(storage.saved).has("seed"));assertFalse(new org.json.JSONObject(storage.actor).has("seed"));
    }
    @Test public void corruptReadIsPreservedWithoutResetOrWrite() throws Exception {
        var storage=new LifecycleFixture.Memory();storage.saved="{";var owner=new SessionRepository(storage).claim();
        try{owner.load();fail("Invalid checkpoint accepted");}catch(org.json.JSONException expected){}
        assertEquals("{",storage.saved);assertEquals(0,storage.writes);
    }
}
