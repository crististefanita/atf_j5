package atf.testkit.lifecycle;

import java.util.Objects;

/** Arms compensation before an external mutation can partially succeed. */
public final class ArmedCleanup implements AutoCloseable {
    private final Runnable cleanup;
    private boolean armed;

    public ArmedCleanup(Runnable cleanup) {
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    public void arm() {
        armed = true;
    }

    /** Disarms after success. A failing compensation remains armed for an explicit retry. */
    @Override
    public void close() {
        if (!armed) return;
        cleanup.run();
        armed = false;
    }
}
