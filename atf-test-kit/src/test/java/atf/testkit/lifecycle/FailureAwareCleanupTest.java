package atf.testkit.lifecycle;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@AtfUnitTest
class FailureAwareCleanupTest {
    @Test
    void laterStagesRunAndFirstFailureRemainsPrimary() {
        var events = new ArrayList<String>();
        var inspectorFailure = new IllegalStateException("network inspector failed");
        var phaseFailure = new IllegalArgumentException("phase completion failed");

        Throwable failure = FailureAwareCleanup.runAll(null,
                () -> { events.add("network inspector"); throw inspectorFailure; },
                () -> events.add("browser close"),
                () -> { events.add("phase completion"); throw phaseFailure; },
                () -> events.add("scenario completion"));

        assertEquals(List.of("network inspector", "browser close", "phase completion", "scenario completion"),
                events);
        assertSame(inspectorFailure, failure);
        assertEquals(List.of(phaseFailure), List.of(failure.getSuppressed()));
    }

    @Test
    void cleanupStagesAllRunAndKeepTheFirstFailurePrimary() {
        var primary = new AssertionError("test failed");
        var firstCleanupFailure = new IllegalStateException("inspector failed");
        var laterCleanupFailure = new IllegalArgumentException("browser close failed");
        var calls = new ArrayList<String>();

        Throwable result = FailureAwareCleanup.runAll(primary,
                () -> { calls.add("inspector"); throw firstCleanupFailure; },
                () -> calls.add("report phase"),
                () -> { calls.add("browser"); throw laterCleanupFailure; });

        assertSame(primary, result);
        assertEquals(List.of("inspector", "report phase", "browser"), calls);
        assertEquals(List.of(firstCleanupFailure, laterCleanupFailure),
                List.of(result.getSuppressed()));
    }

    @Test
    void partiallyAcquiredResourcesCloseInReverseAndKeepAcquisitionFailurePrimary() {
        var primary = new IllegalStateException("acquisition failed");
        var closeFailure = new IllegalArgumentException("browser close failed");
        var calls = new ArrayList<String>();

        Throwable result = FailureAwareCleanup.closeAfterFailure(primary,
                () -> calls.add("playwright"),
                () -> { calls.add("browser"); throw closeFailure; },
                () -> calls.add("context"));

        assertSame(primary, result);
        assertEquals(List.of("context", "browser", "playwright"), calls);
        assertEquals(List.of(closeFailure), List.of(result.getSuppressed()));
    }

    @Test
    void partialAcquisitionSkipsMissingHandlesAndKeepsAllCloseFailuresInReverseOrder() {
        var primary = new IllegalStateException("acquisition failed");
        var contextFailure = new AssertionError("context close failed");
        var browserFailure = new IllegalArgumentException("browser close failed");
        var calls = new ArrayList<String>();

        Throwable result = FailureAwareCleanup.closeAfterFailure(primary,
                () -> calls.add("runtime"), null,
                () -> { calls.add("browser"); throw browserFailure; },
                () -> { calls.add("context"); throw contextFailure; });

        assertSame(primary, result);
        assertEquals(List.of("context", "browser", "runtime"), calls);
        assertEquals(List.of(contextFailure, browserFailure), List.of(result.getSuppressed()));
    }

    @Test
    void cleanupRethrowingThePrimaryDoesNotSelfSuppressOrSkipRemainingResources() {
        var primary = new AssertionError("original failure");
        var calls = new ArrayList<String>();

        Throwable result = FailureAwareCleanup.closeAfterFailure(primary,
                () -> calls.add("remaining resource"),
                () -> { calls.add("rethrows primary"); throw primary; });

        assertSame(primary, result);
        assertEquals(List.of("rethrows primary", "remaining resource"), calls);
        assertEquals(List.of(), List.of(result.getSuppressed()));
    }

    @Test
    void cleanupFailureBecomesPrimaryWhenThereWasNoEarlierFailure() {
        var cleanupFailure = new IllegalStateException("close failed");

        assertSame(cleanupFailure, FailureAwareCleanup.combine(null, cleanupFailure));
    }

    @Test
    void capturesTestFailureBeforeCleanupAndDiagnosticsCanNavigateOrCloseThePage() {
        var primary = new AssertionError("Playwright action failed");
        var calls = new ArrayList<String>();

        Throwable result = FailureEvidenceCleanup.finish(primary,
                () -> calls.add("account cleanup navigated away"),
                () -> calls.add("network diagnostics"),
                observed -> { assertSame(primary, observed); calls.add("failure screenshot and evidence"); },
                () -> calls.add("browser close"));

        assertSame(primary, result);
        assertEquals(List.of("failure screenshot and evidence", "account cleanup navigated away",
                "network diagnostics", "browser close"), calls);
    }

    @Test
    void capturesFirstCleanupFailureAfterCleanupAndDiagnosticsButBeforeResourceClose() {
        var cleanupFailure = new IllegalStateException("cleanup failed");
        var calls = new ArrayList<String>();

        Throwable result = FailureEvidenceCleanup.finish(null,
                () -> { calls.add("cleanup"); throw cleanupFailure; },
                () -> calls.add("diagnostics"),
                observed -> { assertSame(cleanupFailure, observed); calls.add("failure evidence"); },
                () -> calls.add("resource close"));

        assertSame(cleanupFailure, result);
        assertEquals(List.of("cleanup", "diagnostics", "failure evidence", "resource close"), calls);
    }

    @Test
    void evidencePublicationFailureDoesNotPreventCleanupDiagnosticsOrResourceClose() {
        var primary = new AssertionError("test failed");
        var evidenceFailure = new IllegalStateException("screenshot publication failed");
        var calls = new ArrayList<String>();

        Throwable result = FailureEvidenceCleanup.finish(primary,
                () -> calls.add("cleanup"),
                () -> calls.add("diagnostics"),
                ignored -> { calls.add("failure evidence"); throw evidenceFailure; },
                () -> calls.add("resource close"));

        assertSame(primary, result);
        assertEquals(List.of("failure evidence", "cleanup", "diagnostics", "resource close"), calls);
        assertEquals(List.of(evidenceFailure), List.of(result.getSuppressed()));
    }

    @Test
    void successfulCleanupDoesNotPublishFailureEvidence() {
        var calls = new ArrayList<String>();

        Throwable result = FailureEvidenceCleanup.finish(null,
                () -> calls.add("cleanup"),
                () -> calls.add("diagnostics"),
                ignored -> calls.add("failure evidence"),
                () -> calls.add("resource close"));

        assertEquals(null, result);
        assertEquals(List.of("cleanup", "diagnostics", "resource close"), calls);
    }
}
