package atf.testkit.lifecycle;

import java.util.Objects;

/** Orders failure evidence, consumer cleanup, diagnostics and resource closure without losing the primary cause. */
public final class FailureEvidenceCleanup {
    private FailureEvidenceCleanup() { }

    /**
     * Captures an existing test failure before cleanup can change the observed state. If cleanup or diagnostics
     * create the first failure, captures that failure before closing the browser or other evidence resource.
     */
    public static Throwable finish(Throwable primary,
                                   FailureAwareCleanup.Stage cleanup,
                                   FailureAwareCleanup.Stage diagnostics,
                                   FailureEvidence evidence,
                                   FailureAwareCleanup.Stage closeResource) {
        Objects.requireNonNull(cleanup, "cleanup");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(closeResource, "closeResource");

        Throwable failure = primary;
        boolean captured = failure != null;
        if (captured) failure = capture(failure, evidence);
        failure = FailureAwareCleanup.runAll(failure, cleanup);
        failure = FailureAwareCleanup.runAll(failure, diagnostics);
        if (failure != null && !captured) failure = capture(failure, evidence);
        return FailureAwareCleanup.runAll(failure, closeResource);
    }

    private static Throwable capture(Throwable failure, FailureEvidence evidence) {
        try {
            evidence.publish(failure);
        } catch (Exception | Error publicationFailure) {
            failure = FailureAwareCleanup.combine(failure, publicationFailure);
        }
        return failure;
    }

    @FunctionalInterface
    public interface FailureEvidence {
        void publish(Throwable failure) throws Exception;
    }
}
