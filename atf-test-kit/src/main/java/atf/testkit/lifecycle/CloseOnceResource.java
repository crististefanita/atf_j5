package atf.testkit.lifecycle;

import java.util.Objects;

/** Owns one acquired resource. Even a failing close is attempted only once. */
public final class CloseOnceResource<T extends AutoCloseable> implements AutoCloseable {
    private final T value;
    private boolean closed;

    public CloseOnceResource(T value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public T value() {
        return value;
    }

    @Override
    public void close() throws Exception {
        if (closed) return;
        closed = true;
        value.close();
    }

    /** Preserves an existing test failure; propagates teardown failure only when the test passed. */
    public void closeAfter(Throwable primary) throws Exception {
        Throwable failure = FailureAwareCleanup.closeAfterFailure(primary, this);
        if (primary != null || failure == null) return;
        if (failure instanceof Exception exception) throw exception;
        throw (Error) failure;
    }
}
