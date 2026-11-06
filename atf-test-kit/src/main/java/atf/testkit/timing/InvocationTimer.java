package atf.testkit.timing;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Monotonic, owner-local callback timing; no wall clock, runner or reporting dependency. */
public final class InvocationTimer {
    public record Snapshot(Duration elapsed, int intervals) {
        public Snapshot {
            Objects.requireNonNull(elapsed, "elapsed");
            if (elapsed.isNegative() || intervals <= 0) throw new IllegalArgumentException("Invalid invocation timing");
        }
    }
    private final LongSupplier ticks;
    private Duration elapsed = Duration.ZERO;
    private int intervals;
    private boolean running;

    /** The supplier returns monotonic nanoseconds, including the wrap semantics of System.nanoTime(). */
    public InvocationTimer(LongSupplier ticks) { this.ticks = Objects.requireNonNull(ticks, "ticks"); }

    public Interval start() {
        if (running) throw new IllegalStateException("Invocation intervals must not overlap on one timer");
        long started = ticks.getAsLong();
        running = true;
        return new Interval(started);
    }

    public void include(Snapshot value) {
        if (running) throw new IllegalStateException("Cannot import timing during an invocation");
        Duration total = elapsed.plus(value.elapsed());
        int count = Math.addExact(intervals, value.intervals());
        elapsed = total;
        intervals = count;
    }

    public Optional<Snapshot> snapshot() {
        if (running) throw new IllegalStateException("Cannot snapshot an unfinished invocation");
        return intervals == 0 ? Optional.empty() : Optional.of(new Snapshot(elapsed, intervals));
    }

    public final class Interval implements AutoCloseable {
        private final long started;
        private boolean closed;
        private Interval(long started) { this.started = started; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            running = false;
            long nanos = ticks.getAsLong() - started;
            if (nanos < 0) throw new IllegalStateException("Monotonic clock moved backwards");
            include(new Snapshot(Duration.ofNanos(nanos), 1));
        }
    }
}
