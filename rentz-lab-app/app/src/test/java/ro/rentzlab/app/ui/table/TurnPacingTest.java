package ro.rentzlab.app.ui.table;

import org.junit.Test;
import ro.rentzlab.app.testing.AndroidComponentLayer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@AndroidComponentLayer
public final class TurnPacingTest {
    @Test public void botMovesAfterTheVisibleSeatHasHadItsOwnDelay() {
        var frame = new ManualFrame();
        var timer = new ManualTimer();
        var moves = new AtomicInteger();
        TurnPacing.afterVisibleFrame(true, false, frame::await, () -> true, timer::schedule, moves::incrementAndGet);
        assertNotNull(frame.pending);
        assertNull(timer.pending);
        timer.advanceTo(1000);
        frame.draw();
        assertNotNull(timer.pending);
        assertEquals(1550, timer.due);
        timer.advanceTo(1549);
        assertEquals(0, moves.get());
        timer.advanceTo(1550);
        assertEquals(1, moves.get());

        TurnPacing.afterVisibleFrame(true, false, frame::await, () -> true, timer::schedule, moves::incrementAndGet);
        frame.draw();
        timer.advanceTo(2099);
        assertEquals(1, moves.get());
        timer.advanceTo(2100);
        assertEquals(2, moves.get());
    }

    @Test public void humanTurnAndCollectionDoNotWaitForAFrame() {
        var timer = new ManualTimer();
        var current = new AtomicBoolean(true);
        var frame = new ManualFrame();
        Runnable invalidMove = () -> fail("An ineligible frame must not play");
        TurnPacing.afterVisibleFrame(false, false, frame::await, current::get, timer::schedule, invalidMove);
        TurnPacing.afterVisibleFrame(true, true, frame::await, current::get, timer::schedule, invalidMove);
        assertNull(frame.pending);
        assertNull(timer.pending);

        current.set(false);
        TurnPacing.afterVisibleFrame(true, false, frame::await, current::get, timer::schedule, invalidMove);
        assertNull(frame.pending);
        current.set(true);
        TurnPacing.afterVisibleFrame(true, false, frame::await, current::get, timer::schedule, invalidMove);
        assertNotNull(frame.pending);
        current.set(false);
        frame.draw();
        assertNull(timer.pending);

        current.set(true);
        TurnPacing.afterVisibleFrame(true, false, frame::await, current::get, timer::schedule, invalidMove);
        frame.draw();
        assertNotNull(timer.pending);
        current.set(false);
        timer.advanceTo(550);
        assertNull(timer.pending);
    }

    private static final class ManualFrame {
        private Runnable pending;

        private void await(Runnable action) {
            assertNull(pending);
            pending = action;
        }

        private void draw() {
            assertNotNull(pending);
            Runnable action = pending;
            pending = null;
            action.run();
        }
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
