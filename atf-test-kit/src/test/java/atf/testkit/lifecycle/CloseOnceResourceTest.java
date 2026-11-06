package atf.testkit.lifecycle;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class CloseOnceResourceTest {
    @Test
    void closesTheAcquiredResourceOnlyOnce() throws Exception {
        var calls = new AtomicInteger();
        AutoCloseable delegate = calls::incrementAndGet;
        var resource = new CloseOnceResource<>(delegate);
        assertSame(delegate, resource.value());
        resource.close();
        resource.close();
        assertEquals(1, calls.get());
    }

    @Test
    void failedCloseIsNotRepeated() {
        var calls = new AtomicInteger();
        var resource = new CloseOnceResource<>(() -> {
            calls.incrementAndGet();
            throw new IOException("close failed");
        });
        assertThrows(IOException.class, resource::close);
        assertDoesNotThrow(resource::close);
        assertEquals(1, calls.get());
    }

    @Test
    void preservesThePrimaryFailureAndSuppressesTeardown() {
        var teardown = new IOException("teardown failed");
        var primary = new AssertionError("observed test failure");
        var resource = new CloseOnceResource<>(() -> { throw teardown; });
        assertDoesNotThrow(() -> resource.closeAfter(primary));
        assertArrayEquals(new Throwable[]{teardown}, primary.getSuppressed());
    }

    @Test
    void propagatesTeardownWhenTheTestPassed() {
        var teardown = new IOException("teardown failed");
        var resource = new CloseOnceResource<>(() -> { throw teardown; });
        assertSame(teardown, assertThrows(IOException.class, () -> resource.closeAfter(null)));
    }

    @Test
    void avoidsSelfSuppressionAndPreservesErrors() {
        var primary = new AssertionError("shared failure");
        var resource = new CloseOnceResource<>(() -> { throw primary; });
        assertDoesNotThrow(() -> resource.closeAfter(primary));
        assertEquals(0, primary.getSuppressed().length);
        var failedClose = new CloseOnceResource<>(() -> { throw primary; });
        assertSame(primary, assertThrows(AssertionError.class, () -> failedClose.closeAfter(null)));
    }

    @Test
    void rejectsAMissingResource() {
        assertThrows(NullPointerException.class, () -> new CloseOnceResource<>(null));
    }
}
