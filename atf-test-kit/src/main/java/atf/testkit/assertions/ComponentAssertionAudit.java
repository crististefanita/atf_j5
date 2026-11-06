package atf.testkit.assertions;

import org.opentest4j.TestAbortedException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import atf.testkit.timing.InvocationTimer;

/** Renderer-independent result policy and evaluated checks for one JUnit invocation. */
public final class ComponentAssertionAudit {
    public enum Phase { SETUP, TEST_BODY, TEARDOWN }
    public enum Outcome { PASS, FAIL, ABORTED }
    public enum AssertionRequirement { ANY_PHASE, TEST_BODY }
    public record PhaseResult(Phase phase, List<RecordedAssertions.Check> checks,
                              Outcome outcome, String diagnostic, Optional<InvocationTimer.Snapshot> timing) {
        public PhaseResult(Phase phase, List<RecordedAssertions.Check> checks, Outcome outcome, String diagnostic) {
            this(phase, checks, outcome, diagnostic, Optional.empty());
        }
        public PhaseResult {
            checks = List.copyOf(checks);
            timing = Objects.requireNonNull(timing, "timing");
        }
    }
    public record Result(List<PhaseResult> phases, Outcome outcome, Throwable failure,
                         boolean propagate) { }

    private final AssertionValueDisplay display;
    private final LongSupplier ticks;
    private final AssertionRequirement requirement;
    private final EnumMap<Phase, InvocationTimer> timers = new EnumMap<>(Phase.class);
    private final EnumMap<Phase, RecordedAssertions> recorders = new EnumMap<>(Phase.class);
    private final EnumMap<Phase, Throwable> failures = new EnumMap<>(Phase.class);
    private final java.util.Set<Phase> started = java.util.EnumSet.noneOf(Phase.class);

    public ComponentAssertionAudit(AssertionValueDisplay display) {
        this(display, System::nanoTime);
    }

    public ComponentAssertionAudit(AssertionValueDisplay display, LongSupplier ticks) {
        this(display, ticks, AssertionRequirement.ANY_PHASE);
    }

    public ComponentAssertionAudit(AssertionValueDisplay display, LongSupplier ticks, AssertionRequirement requirement) {
        this.display = Objects.requireNonNull(display, "display");
        this.ticks = Objects.requireNonNull(ticks, "ticks");
        this.requirement = Objects.requireNonNull(requirement, "requirement");
        for (Phase phase : Phase.values()) recorders.put(phase, new RecordedAssertions(display));
    }

    public RecordedAssertions recorder(Phase phase) { return recorders.get(Objects.requireNonNull(phase)); }
    public void start(Phase phase) { started.add(Objects.requireNonNull(phase)); }
    public void failed(Phase phase, Throwable failure) { failures.putIfAbsent(phase, failure); }

    public InvocationTimer.Interval measure(Phase phase) {
        start(phase);
        return timer(phase).start();
    }
    public void timing(Phase phase, InvocationTimer.Snapshot timing) {
        start(phase);
        timer(phase).include(timing);
    }
    private InvocationTimer timer(Phase phase) { return timers.computeIfAbsent(phase, ignored -> new InvocationTimer(ticks)); }

    public Result finish(Throwable executionFailure) {
        Throwable unconsumed = unconsumedFailure();
        Throwable failure = executionFailure;
        boolean propagate = false;
        if (unconsumed != null && (failure == null || failure instanceof TestAbortedException
                && !(unconsumed instanceof TestAbortedException))) {
            // The runner merges its existing execution exception when this result is propagated.
            failure = unconsumed;
            propagate = true;
        }
        boolean empty = requirement == AssertionRequirement.TEST_BODY ? recorder(Phase.TEST_BODY).checks().isEmpty()
                : recorders.values().stream().allMatch(recorder -> recorder.checks().isEmpty());
        if (failure == null && empty) {
            failure = missingAssertion();
            if (requirement == AssertionRequirement.TEST_BODY) recorder(Phase.TEST_BODY).record(
                    "Audited assertion coverage / TEST BODY", "at least 1 evaluated assertion", 0, false, failure);
            failures.put(Phase.TEST_BODY, failure);
            started.add(Phase.TEST_BODY);
            propagate = true;
        }
        if (failure != null && failures.isEmpty() && unconsumed == null) {
            Phase phase = started.contains(Phase.TEST_BODY) ? Phase.TEARDOWN : Phase.SETUP;
            failures.put(phase, failure);
            started.add(phase);
        }
        var phases = new ArrayList<PhaseResult>();
        for (Phase phase : Phase.values()) {
            var recorder = recorder(phase);
            Throwable phaseFailure = failures.get(phase);
            if (!started.contains(phase) && recorder.checks().isEmpty() && phaseFailure == null) continue;
            Outcome outcome = recorder.checks().stream().anyMatch(check -> !check.passed())
                    ? Outcome.FAIL : outcome(phaseFailure);
            String diagnostic = phaseFailure == null ? "" : display.display(null,
                    (AssertionValueDisplay.Value) phaseFailure::toString);
            phases.add(new PhaseResult(phase, recorder.checks(), outcome, diagnostic,
                    timers.containsKey(phase) ? timers.get(phase).snapshot() : Optional.empty()));
        }
        return new Result(List.copyOf(phases), outcome(failure), failure, propagate);
    }

    private AssertionError missingAssertion() {
        return new AssertionError(requirement == AssertionRequirement.TEST_BODY
                ? "TEST BODY passed without an evaluated audited assertion"
                : "Component test passed without an evaluated audited assertion");
    }

    private Throwable unconsumedFailure() {
        Throwable assertion = recorders.values().stream().map(RecordedAssertions::failure)
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (assertion != null) return assertion;
        return failures.values().stream().filter(failure -> !(failure instanceof TestAbortedException))
                .findFirst().orElseGet(() -> failures.values().stream().findFirst().orElse(null));
    }

    public static Outcome outcome(Throwable failure) {
        return failure == null ? Outcome.PASS
                : failure instanceof TestAbortedException ? Outcome.ABORTED : Outcome.FAIL;
    }
}
