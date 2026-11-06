package atf.testkit.lifecycle;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves cleanup is armed before a potentially partial create and cannot replace its primary failure. */
@AtfComponentTest
class ArmedCleanupTest {
    @Test
    void cleanupRunsAfterCreateFailureAndItsFailureIsSuppressed() {
        AtomicInteger cleanupCalls = new AtomicInteger();
        var primaryFailure = new IllegalStateException("mutation response was malformed");
        var cleanupFailure = new AssertionError("compensation failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            try (var cleanup = new ArmedCleanup(() -> {
                cleanupCalls.incrementAndGet();
                throw cleanupFailure;
            })) {
                cleanup.arm();
                throw primaryFailure;
            }
        });

        assertEquals(1, cleanupCalls.get());
        assertSame(primaryFailure, thrown, "the create failure remains the primary failure");
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(cleanupFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void cleanupDoesNotRunBeforeACreateAttemptIsArmed() {
        AtomicInteger cleanupCalls = new AtomicInteger();

        try (var cleanup = new ArmedCleanup(cleanupCalls::incrementAndGet)) {
            // No mutation was attempted, so no compensation is required.
        }

        assertEquals(0, cleanupCalls.get());
    }
    @Test
    void successfulCompensationDisarmsButFailedCompensationCanBeRetriedExplicitly() {
        AtomicInteger calls = new AtomicInteger();
        var cleanup = new ArmedCleanup(() -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("transient cleanup failure");
        });
        cleanup.arm();
        assertThrows(IllegalStateException.class, cleanup::close);
        cleanup.close();
        cleanup.close();
        assertEquals(2, calls.get());
    }

    @Test
    void completedCleanupCanBeArmedForANewMutation() {
        AtomicInteger calls = new AtomicInteger();
        var cleanup = new ArmedCleanup(calls::incrementAndGet);
        cleanup.arm();
        cleanup.close();
        cleanup.arm();
        cleanup.close();
        assertEquals(2, calls.get());
    }
}
