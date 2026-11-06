package atf.testkit.lifecycle;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Acquires a resource and two dependent handles before transferring ownership to a run.
 * On failure, available handles are rolled back in reverse acquisition order.
 *
 * <p>Factories own partial acquisition before returning a handle. Callbacks supply
 * consumer-specific failure publication and close semantics; this class does not
 * know browsers, report nodes or verdicts. Successful runs own their normal cleanup.</p>
 */
public final class TestRunInitialization {
    private TestRunInitialization() { }

    public static <R, S, P, T> T create(Supplier<R> resourceFactory,
                                       Function<R, S> scenarioFactory,
                                       Function<S, P> phaseFactory,
                                       RunFactory<R, S, P, T> runFactory,
                                       Consumer<P> failPhase,
                                       Consumer<S> failScenario,
                                       Consumer<R> closeResource) {
        R resource = null;
        S scenario = null;
        P phase = null;
        try {
            resource = resourceFactory.get();
            scenario = scenarioFactory.apply(resource);
            phase = phaseFactory.apply(scenario);
            return runFactory.create(resource, scenario, phase);
        } catch (RuntimeException | Error setupFailure) {
            P acquiredPhase = phase;
            S acquiredScenario = scenario;
            R acquiredResource = resource;
            FailureAwareCleanup.runAll(setupFailure,
                    () -> { if (acquiredPhase != null) failPhase.accept(acquiredPhase); },
                    () -> { if (acquiredScenario != null) failScenario.accept(acquiredScenario); },
                    () -> { if (acquiredResource != null) closeResource.accept(acquiredResource); });
            throw setupFailure;
        }
    }

    @FunctionalInterface
    public interface RunFactory<R, S, P, T> {
        T create(R resource, S scenario, P phase);
    }
}
