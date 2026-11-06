package atf.testkit.scope;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class ScopedTestValueTest {
    @Test
    void acquisitionRunsOnceAndReportsCreationSeparatelyFromReuse() {
        var scope = new ScopedTestValue<Object>();
        var created = new Object();
        var calls = new AtomicInteger();
        assertTrue(scope.current().isEmpty());
        var first = scope.getOrCreate(() -> { calls.incrementAndGet(); return created; });
        var second = scope.getOrCreate(() -> fail("must not acquire twice"));
        assertSame(created, first.value());
        assertSame(created, second.value());
        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(1, calls.get());
    }

    @Test
    void acquisitionFailureIsPreservedAndNeverReplayed() {
        var scope = new ScopedTestValue<String>();
        var original = new IllegalStateException("uncertain external mutation");
        assertSame(original, assertThrows(IllegalStateException.class,
                () -> scope.getOrCreate(() -> { throw original; })));
        var rejected = assertThrows(IllegalStateException.class,
                () -> scope.getOrCreate(() -> fail("automatic retry is forbidden")));
        assertSame(original, rejected.getCause());
        assertSame(original, assertThrows(IllegalStateException.class, scope::current).getCause());
        assertTrue(scope.hasFailed());
    }

    @Test
    void nullResultAndErrorAlsoPreventImplicitReplay() {
        var missing = new ScopedTestValue<String>();
        assertThrows(NullPointerException.class, () -> missing.getOrCreate(() -> null));
        assertThrows(IllegalStateException.class,
                () -> missing.getOrCreate(() -> fail("null is not a retry permit")));
        var scope = new ScopedTestValue<String>();
        var original = new AssertionError("setup failed");
        assertSame(original, assertThrows(AssertionError.class,
                () -> scope.getOrCreate(() -> { throw original; })));
        assertSame(original, assertThrows(IllegalStateException.class, scope::current).getCause());
    }

    @Test
    void closeForgetsTheReferenceWithoutDisposingBorrowedValues() {
        var calls = new AtomicInteger();
        var scope = new ScopedTestValue<AutoCloseable>();
        scope.getOrCreate(() -> calls::incrementAndGet);
        scope.close();
        scope.close();
        assertEquals(0, calls.get());
        assertTrue(scope.isClosed());
        assertTrue(scope.current().isEmpty());
        assertThrows(IllegalStateException.class,
                () -> scope.getOrCreate(() -> fail("closed scope cannot be reused")));
    }

    @Test
    void closeAlsoForgetsTheAcquisitionFailure() {
        var scope = new ScopedTestValue<String>();
        assertThrows(IllegalArgumentException.class,
                () -> scope.getOrCreate(() -> { throw new IllegalArgumentException("failed"); }));
        scope.close();
        assertFalse(scope.hasFailed());
        assertTrue(scope.current().isEmpty());
        assertTrue(scope.isClosed());
    }

    @Test
    void concurrentCallersCannotExecuteTheFactoryTwice() throws Exception {
        var scope = new ScopedTestValue<Object>();
        var calls = new AtomicInteger();
        var expected = new Object();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<java.util.concurrent.Future<Object>>();
            for (int index = 0; index < 12; index++) {
                results.add(pool.submit(() -> scope.getOrCreate(() -> {
                    calls.incrementAndGet(); return expected;
                }).value()));
            }
            for (var result : results) assertSame(expected, result.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, calls.get());
    }

    @Test
    void reentrantAcquisitionIsRejectedRatherThanRunningAnotherFactory() {
        var scope = new ScopedTestValue<String>();
        assertThrows(IllegalStateException.class, () -> scope.getOrCreate(
                () -> scope.getOrCreate(() -> fail("recursive factory must not run")).value()));
        assertTrue(scope.hasFailed());
    }

    @Test
    void aFactoryCannotReopenAScopeItClosedDuringAcquisition() {
        var scope = new ScopedTestValue<String>();
        assertThrows(IllegalStateException.class,
                () -> scope.getOrCreate(() -> { scope.close(); return "discarded"; }));
        assertTrue(scope.isClosed());
        assertTrue(scope.current().isEmpty());
    }

    @Test
    void diagnosticsNeverRenderTheStoredValue() {
        var scope = new ScopedTestValue<String>();
        var acquisition = scope.getOrCreate(() -> "private-fixture-value");
        assertFalse(scope.toString().contains("private-fixture-value"));
        assertFalse(acquisition.toString().contains("private-fixture-value"));
    }
}
