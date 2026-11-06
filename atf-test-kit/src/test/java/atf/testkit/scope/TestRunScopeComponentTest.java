package atf.testkit.scope;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/** Runs real Jupiter lifecycle boundaries; no browser, reporting or consumer dependency. */
@AtfComponentTest
class TestRunScopeComponentTest {
    private static final ThreadLocal<Observations> OBSERVED = new ThreadLocal<>();

    @Test
    void classScopeSharesMethodsButSeparatesConcreteClasses() {
        var observed = execute("class", false, FirstFixture.class, SecondFixture.class);
        assertEquals(0, observed.failedTests);
        assertEquals(2, observed.created);
        assertEquals(3, observed.values.size());
        assertEquals(2, observed.values.stream().distinct().count());
        assertReleased(observed);
    }

    @Test
    void suiteScopeSharesClassesOnlyWithinTheCurrentExecution() {
        var first = execute("suite", false, FirstFixture.class, SecondFixture.class);
        var second = execute("suite", false, FirstFixture.class, SecondFixture.class);
        assertEquals(0, first.failedTests + second.failedTests);
        assertEquals(1, first.created);
        assertEquals(1, second.created);
        assertEquals(1, first.values.stream().distinct().count());
        assertEquals(3, first.values.size());
        assertNotSame(first.values.getFirst(), second.values.getFirst());
        assertEquals(first.rootIds.getFirst(), second.rootIds.getFirst(),
                "identical textual engine IDs must not share resources across launches");
        assertReleased(first);
        assertReleased(second);
    }

    @Test
    void repeatedClassLaunchesInTheSameJvmDoNotReuseValues() {
        var first = execute(null, false, FirstFixture.class);
        var second = execute(null, false, FirstFixture.class);
        assertEquals(1, first.created);
        assertEquals(1, second.created);
        assertEquals(0, first.failedTests + second.failedTests);
        assertNotSame(first.values.getFirst(), second.values.getFirst());
        assertReleased(first);
        assertReleased(second);
    }

    @Test
    void failedPreparationIsNotRetriedByTheFollowingMethod() {
        var observed = execute("class", true, FirstFixture.class);
        assertEquals(2, observed.failedTests);
        assertEquals(1, observed.created);
        assertEquals(2, observed.values.size());
        assertSame(observed.values.getFirst(), observed.values.getLast());
        assertReleased(observed);
    }

    @Test
    void invalidScopeFailsBeforeTheFactory() {
        var observed = execute("unknown", false, FirstFixture.class);
        assertEquals(2, observed.failedTests);
        assertEquals(0, observed.created);
        assertTrue(observed.values.isEmpty());
    }

    @Test
    void failedTestBodyStillReleasesItsClassScope() {
        var observed = execute("class", false, FailingBodyFixture.class);
        assertEquals(1, observed.failedTests);
        assertEquals(1, observed.created);
        assertReleased(observed);
    }

    @Test
    void namespaceNameTypeAndScopeAreIndependent() {
        var observed = execute("suite", false, NamespaceFixture.class);
        assertEquals(0, observed.failedTests);
        assertEquals(5, observed.values.stream().distinct().count());
        assertReleased(observed);
    }

    private static Observations execute(String scope, boolean failPreparation, Class<?>... fixtures) {
        var observed = new Observations(failPreparation);
        OBSERVED.set(observed);
        try {
            var request = LauncherDiscoveryRequestBuilder.request()
                    .configurationParameter("junit.jupiter.execution.parallel.enabled", "false");
            if (scope != null) request.configurationParameter(TestRunScope.CONFIGURATION_PARAMETER, scope);
            for (Class<?> fixture : fixtures) request.selectors(selectClass(fixture));
            var summary = new SummaryGeneratingListener();
            LauncherFactory.create().execute(request.build(), summary);
            assertEquals(0, summary.getSummary().getTestsSkippedCount());
            assertTrue(summary.getSummary().getTestsFoundCount() > 0);
            observed.failedTests = summary.getSummary().getTestsFailedCount();
            return observed;
        } finally {
            OBSERVED.remove();
        }
    }

    private static void assertReleased(Observations observed) {
        assertTrue(observed.values.stream().allMatch(ScopedTestValue::isClosed));
        assertTrue(observed.values.stream().allMatch(value -> value.current().isEmpty()));
    }

    private static final class Observations {
        private final boolean failPreparation;
        private final List<ScopedTestValue<?>> values = new ArrayList<>();
        private final List<String> rootIds = new ArrayList<>();
        private int created;
        private long failedTests;
        private Observations(boolean failPreparation) { this.failPreparation = failPreparation; }
    }

    public static final class ScopeProbe implements BeforeEachCallback {
        @Override public void beforeEach(ExtensionContext context) {
            var observed = OBSERVED.get();
            observed.rootIds.add(context.getRoot().getUniqueId());
            var value = TestRunScope.selected(context).value(
                    context, ScopeProbe.class, "fixture", Object.class);
            observed.values.add(value);
            value.getOrCreate(() -> {
                observed.created++;
                if (observed.failPreparation) throw new IllegalStateException("uncertain preparation");
                return new Object();
            });
        }
    }

    @ExtendWith(ScopeProbe.class)
    static class FirstFixture {
        @Test void first() { assertFalse(OBSERVED.get().values.isEmpty()); }
        @Test void second() { assertFalse(OBSERVED.get().values.isEmpty()); }
    }

    @ExtendWith(ScopeProbe.class)
    static class SecondFixture {
        @Test void first() { assertFalse(OBSERVED.get().values.isEmpty()); }
    }

    @ExtendWith(ScopeProbe.class)
    static class FailingBodyFixture {
        @Test void expectedFailure() { fail("intentional fixture body failure"); }
    }

    public static final class NamespaceProbe implements BeforeEachCallback {
        @Override public void beforeEach(ExtensionContext context) {
            var scope = TestRunScope.selected(context);
            var first = scope.value(context, ScopeProbe.class, "one", String.class);
            assertSame(first, scope.value(context, ScopeProbe.class, "one", String.class));
            OBSERVED.get().values.addAll(List.of(first,
                    scope.value(context, ScopeProbe.class, "two", String.class),
                    scope.value(context, NamespaceProbe.class, "one", String.class),
                    scope.value(context, ScopeProbe.class, "one", Integer.class),
                    TestRunScope.CLASS.value(context, ScopeProbe.class, "one", String.class)));
        }
    }

    @ExtendWith(NamespaceProbe.class)
    static class NamespaceFixture {
        @Test void distinctKeys() { assertEquals(5, OBSERVED.get().values.size()); }
    }
}
