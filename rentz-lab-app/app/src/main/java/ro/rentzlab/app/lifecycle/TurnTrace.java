package ro.rentzlab.app.lifecycle;

import java.util.Objects;
import java.util.function.Consumer;

/** Optional diagnostic sink for UI turn scheduling; component tests default to no-op. */
public final class TurnTrace {
    private static final Consumer<String> NO_OP = ignored -> { };
    private static volatile Consumer<String> sink = NO_OP;

    private TurnTrace() { }

    public static void install(Consumer<String> logger) {
        sink = Objects.requireNonNull(logger);
    }

    public static void reset() {
        sink = NO_OP;
    }

    public static void record(String event) {
        try { sink.accept(event); }
        catch (RuntimeException ignored) { }
    }
}
