package atf.testkit.assertions;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.opentest4j.TestAbortedException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/** Captured callback outcomes survive external JUnit exception handlers. */
@AtfComponentTest
class HandledComponentFailureComponentTest {
    private static final ThreadLocal<Probe> ACTIVE = new ThreadLocal<>();
    private static final class Probe {
        final RuntimeException failure = new IllegalStateException("captured runtime failure");
        final TestAbortedException abort = new TestAbortedException("captured abort");
        final AtomicLong nanos = new AtomicLong();
        final List<ComponentAssertionAudit.Result> results = new ArrayList<>();
        TestExecutionSummary summary;
        int closed;
        void advance(int millis) { nanos.addAndGet(Duration.ofMillis(millis).toNanos()); }
    }
    @Test void swallowingABodyErrorCannotMakeJunitPassWhileItsReportFails() {
        var probe = launch(FailureCase.class);
        assertEquals(1, probe.summary.getTestsFailedCount());
        assertSame(probe.failure, probe.summary.getFailures().getFirst().getException());
        assertEquals(ComponentAssertionAudit.Outcome.FAIL, probe.results.getFirst().outcome());
        assertEquals(Duration.ofMillis(20), probe.results.getFirst().phases().getFirst().timing().orElseThrow().elapsed());
        assertTrue(probe.results.getFirst().phases().getFirst().checks().getFirst().passed(), "a later runtime error must not fabricate a failed assertion");
        assertEquals(1, probe.closed);
    }
    @Test void swallowingAnAbortRetainsSkipAndItsMeasuredInterval() {
        var probe = launch(AbortCase.class);
        assertEquals(1, probe.summary.getTestsAbortedCount());
        assertEquals(0, probe.summary.getTestsFailedCount());
        assertEquals(ComponentAssertionAudit.Outcome.ABORTED, probe.results.getFirst().outcome());
        assertSame(probe.abort, probe.results.getFirst().failure());
        assertEquals(Duration.ofMillis(40), probe.results.getFirst().phases().getFirst().timing().orElseThrow().elapsed());
    }
    @Test void translatingAnErrorToAnAbortKeepsTheOriginalErrorPrimary() {
        var probe = launch(TranslatedCase.class);
        assertEquals(1, probe.summary.getTestsFailedCount());
        assertSame(probe.failure, probe.summary.getFailures().getFirst().getException());
        assertEquals(List.of(probe.abort), List.of(probe.failure.getSuppressed()));
    }
    @Test void handledLifecycleErrorsKeepTheirOwnPhaseAndDoNotStopLaterCallbacks() {
        var probe = launch(LifecycleCase.class);
        assertEquals(1, probe.summary.getTestsFailedCount());
        assertSame(probe.failure, probe.summary.getFailures().getFirst().getException());
        var result = probe.results.getFirst();
        assertEquals(List.of(9L, 20L, 11L), result.phases().stream().map(p -> p.timing().orElseThrow().elapsed().toMillis()).toList());
        assertEquals(ComponentAssertionAudit.Outcome.FAIL, result.phases().getFirst().outcome());
        assertEquals(ComponentAssertionAudit.Outcome.PASS, result.phases().get(1).outcome());
    }
    private static Probe launch(Class<?> fixture) {
        var probe = new Probe(); ACTIVE.set(probe);
        try {
            var summary = new SummaryGeneratingListener();
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture))
                    .configurationParameter("junit.jupiter.execution.parallel.enabled", "false").build(), summary);
            probe.summary = summary.getSummary(); return probe;
        } finally { ACTIVE.remove(); }
    }
    public static class Adapter implements ComponentAuditAdapter {
        private final ComponentAuditExtension audit = new ComponentAuditExtension(Adapter.class, context -> new ComponentAuditExtension.Publication() {
            public AssertionValueDisplay display() { return new AssertionValueDisplay(240, (name, value) -> value); }
            public void publish(ComponentAssertionAudit.Result result) { ACTIVE.get().results.add(result); }
            public void close() { ACTIVE.get().closed++; }
        }, () -> ACTIVE.get().nanos.get());
        public ComponentAuditExtension auditExtension() { return audit; }
    }
    public static class Swallow implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {
        public void handleTestExecutionException(ExtensionContext context, Throwable error) { }
        public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable error) { }
    }
    public static class Translate implements TestExecutionExceptionHandler {
        public void handleTestExecutionException(ExtensionContext context, Throwable error) { throw ACTIVE.get().abort; }
    }
    @ExtendWith({Adapter.class, Swallow.class}) public static class FailureCase {
        @Test void body() { ACTIVE.get().advance(20); AuditedAssertions.assertTrue(true); throw ACTIVE.get().failure; }
    }
    @ExtendWith({Adapter.class, Swallow.class}) public static class AbortCase {
        @Test void body() { ACTIVE.get().advance(40); AuditedAssertions.assertTrue(true); throw ACTIVE.get().abort; }
    }
    @ExtendWith({Adapter.class, Translate.class}) public static class TranslatedCase {
        @Test void body() { ACTIVE.get().advance(20); AuditedAssertions.assertTrue(true); throw ACTIVE.get().failure; }
    }
    @ExtendWith({Adapter.class, Swallow.class}) public static class LifecycleCase {
        @BeforeEach void setup() { ACTIVE.get().advance(9); throw ACTIVE.get().failure; }
        @Test void body() { ACTIVE.get().advance(20); AuditedAssertions.assertTrue(true); }
        @AfterEach void teardown() { ACTIVE.get().advance(11); }
    }
}
