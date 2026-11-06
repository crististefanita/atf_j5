package atf.testkit.timing;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class InvocationTimerTest {
    @Test void accumulatesOnlyClosedIntervalsIncludingMeasuredZero() {
        var ticks = new AtomicLong();
        var timer = new InvocationTimer(ticks::get);
        assertTrue(timer.snapshot().isEmpty());
        try (var first = timer.start()) { ticks.addAndGet(12); }
        var snapshot = timer.snapshot().orElseThrow();
        ticks.addAndGet(9_000);
        try (var second = timer.start()) { ticks.addAndGet(34); }
        try (var zero = timer.start()) { }
        assertEquals(new InvocationTimer.Snapshot(Duration.ofNanos(46), 3), timer.snapshot().orElseThrow());
        assertEquals(Duration.ofNanos(12), snapshot.elapsed(), "earlier snapshots are immutable");
    }
    @Test void closureIsAttemptedOnceAndIndependentTimersHaveNoCommonBaseline() {
        var ticks = new AtomicLong();
        var first = new InvocationTimer(ticks::get);
        var second = new InvocationTimer(ticks::get);
        var interval = first.start(); ticks.addAndGet(7); interval.close(); interval.close();
        ticks.addAndGet(1_000);
        try (var next = second.start()) { ticks.addAndGet(11); }
        assertEquals(Duration.ofNanos(7), first.snapshot().orElseThrow().elapsed());
        assertEquals(Duration.ofNanos(11), second.snapshot().orElseThrow().elapsed());
    }
    @Test void handlesTheValidNanoTimeWrapAndRejectsBackwardsTime() {
        var ticks = new AtomicLong(Long.MAX_VALUE - 4);
        var timer = new InvocationTimer(ticks::get);
        try (var interval = timer.start()) { ticks.set(Long.MIN_VALUE + 5); }
        assertEquals(Duration.ofNanos(10), timer.snapshot().orElseThrow().elapsed());
        var bad = new InvocationTimer(ticks::get);
        var interval = bad.start(); ticks.decrementAndGet();
        assertThrows(IllegalStateException.class, interval::close);
        interval.close();
        assertTrue(bad.snapshot().isEmpty());
    }
    @Test void rejectsUnfinishedOrOverlappingIntervalsAndInvalidSnapshots() {
        var timer = new InvocationTimer(() -> 0L);
        try (var interval = timer.start()) {
            assertThrows(IllegalStateException.class, timer::start);
            assertThrows(IllegalStateException.class, timer::snapshot);
        }
        assertEquals(Duration.ZERO, timer.snapshot().orElseThrow().elapsed());
        assertThrows(IllegalArgumentException.class, () -> new InvocationTimer.Snapshot(Duration.ofNanos(-1), 1));
        assertThrows(IllegalArgumentException.class, () -> new InvocationTimer.Snapshot(Duration.ZERO, 0));
    }
}
