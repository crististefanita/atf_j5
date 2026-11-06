package atf.testkit.lifecycle;

import java.util.Objects;

/** Test-only cleanup primitives that preserve the primary failure. */
public final class FailureAwareCleanup {
    private FailureAwareCleanup() { }

    @FunctionalInterface
    public interface Stage {
        void run() throws Exception;
    }

    /** Runs every stage in declaration order, retaining later failures as suppressed. */
    public static Throwable runAll(Throwable primary, Stage... stages) {
        Objects.requireNonNull(stages, "stages");
        Throwable failure = primary;
        for (Stage stage : stages) {
            try {
                Objects.requireNonNull(stage, "stage").run();
            } catch (Exception | Error stageFailure) {
                failure = combine(failure, stageFailure);
            }
        }
        return failure;
    }

    /** Closes acquired resources in reverse order without replacing the original failure. */
    public static Throwable closeAfterFailure(Throwable primary, AutoCloseable... resources) {
        Objects.requireNonNull(resources, "resources");
        Throwable failure = primary;
        for (int index = resources.length - 1; index >= 0; index--) {
            try {
                AutoCloseable resource = resources[index];
                if (resource != null) resource.close();
            } catch (Exception | Error closeFailure) {
                failure = combine(failure, closeFailure);
            }
        }
        return failure;
    }

    /** Combines failures without self-suppression; the first failure remains primary. */
    public static Throwable combine(Throwable primary, Throwable secondary) {
        if (primary == null) return secondary;
        if (secondary != null && primary != secondary) primary.addSuppressed(secondary);
        return primary;
    }
}
