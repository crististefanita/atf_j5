package atf.testkit.recovery;

import java.util.Objects;

/** No YAML, browser or reporting dependency; a declaration cannot increase a configured capability. */
public record AtfUiRecoveryPlan(AtfUiRecovery.Mode mode, String source) {
    public AtfUiRecoveryPlan { Objects.requireNonNull(mode); Objects.requireNonNull(source); }
    public boolean permitsConfiguredRecovery() { return mode != AtfUiRecovery.Mode.DISABLED; }
}
