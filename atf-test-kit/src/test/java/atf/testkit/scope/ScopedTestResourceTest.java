package atf.testkit.scope;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class ScopedTestResourceTest {
    @Test void ownsDisposalButDoesNotReplayAFailingClose() throws Exception {
        var closes = new AtomicInteger();
        var failure = new IllegalStateException("controlled close failure");
        var scope = new ScopedTestResource<AutoCloseable>();
        scope.getOrCreate(() -> () -> { closes.incrementAndGet(); throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, scope::close));
        scope.close();
        assertEquals(1, closes.get());
        assertThrows(IllegalStateException.class, () -> scope.getOrCreate(() -> () -> { }));
    }

    @Test void failedAcquisitionAndAnUnusedOwnerAreSafeToClose() throws Exception {
        var calls = new AtomicInteger();
        var scope = new ScopedTestResource<AutoCloseable>();
        assertThrows(IllegalStateException.class, () -> scope.getOrCreate(() -> {
            calls.incrementAndGet(); throw new IllegalStateException("factory failed");
        }));
        assertThrows(IllegalStateException.class, () -> scope.getOrCreate(() -> () -> { }));
        scope.close();
        new ScopedTestResource<AutoCloseable>().close();
        assertEquals(1, calls.get());
    }

    @Test void concurrentCallersAcquireOneOwnedResource() throws Exception {
        var calls = new AtomicInteger();
        var closes = new AtomicInteger();
        var scope = new ScopedTestResource<AutoCloseable>();
        try (var workers = Executors.newFixedThreadPool(4)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<AutoCloseable>>();
            for (int index = 0; index < 12; index++) jobs.add(workers.submit(() -> scope.getOrCreate(() -> {
                calls.incrementAndGet(); return (AutoCloseable) closes::incrementAndGet;
            }).value()));
            var resource = jobs.getFirst().get();
            for (var job : jobs) assertSame(resource, job.get());
        }
        scope.close();
        assertEquals(1, calls.get());
        assertEquals(1, closes.get());
    }
}
