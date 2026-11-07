package ro.rentzlab.app.ui.table;

import org.junit.Test;
import ro.rentzlab.app.testing.AndroidComponentLayer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@AndroidComponentLayer
public final class TurnPacingTest {
    @Test public void botMovesAfterTheVisibleSeatHasHadItsOwnDelay() {
        var timer = new ManualTimer();
        var moves = new AtomicInteger();
        TurnPacing.afterVisibleFrame(true, false, () -> true, timer::schedule, moves::incrementAndGet);
        timer.advanceTo(549);
        assertEquals(0, moves.get());
        timer.advanceTo(550);
        assertEquals(1, moves.get());

        TurnPacing.afterVisibleFrame(true, false, () -> true, timer::schedule, moves::incrementAndGet);
        timer.advanceTo(1099);
        assertEquals(1, moves.get());
        timer.advanceTo(1100);
        assertEquals(2, moves.get());
    }

    @Test public void aHumanTurnCollectionOrInvalidatedFrameDoesNotPlay() {
        var timer = new ManualTimer();
        var current = new AtomicBoolean(true);
        Runnable invalidMove = () -> fail("An ineligible frame must not play");
        TurnPacing.afterVisibleFrame(false, false, current::get, timer::schedule, invalidMove);
        TurnPacing.afterVisibleFrame(true, true, current::get, timer::schedule, invalidMove);
        assertNull(timer.pending);

        TurnPacing.afterVisibleFrame(true, false, current::get, timer::schedule, invalidMove);
        assertNotNull(timer.pending);
        current.set(false);
        timer.advanceTo(550);
        TurnPacing.afterVisibleFrame(true, false, current::get, timer::schedule, invalidMove);
        assertNull(timer.pending);
    }

    private static final class ManualTimer {
        private long now, due;
        private Runnable pending;

        private void schedule(Runnable move, long delay) {
            assertNull(pending);
            pending = move;
            due = now + delay;
        }

        private void advanceTo(long time) {
            now = time;
            if (pending != null && now >= due) {
                Runnable move = pending;
                pending = null;
                move.run();
            }
        }
    }
}
