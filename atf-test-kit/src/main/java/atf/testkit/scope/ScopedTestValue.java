package atf.testkit.scope;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** One lazy value per lifecycle owner; a failed acquisition is never replayed implicitly. */
public final class ScopedTestValue<T> implements ExtensionContext.Store.CloseableResource {
    private enum State { EMPTY, CREATING, READY, FAILED, CLOSED }

    private State state = State.EMPTY;
    private T value;
    private Throwable failure;

    public record Acquisition<T>(T value, boolean created) {
        public Acquisition { Objects.requireNonNull(value, "value"); }
        @Override public String toString() { return "Acquisition[created=" + created + "]"; }
    }

    public synchronized Acquisition<T> getOrCreate(Supplier<? extends T> factory) {
        Objects.requireNonNull(factory, "factory");
        requireUsable();
        if (state == State.READY) return new Acquisition<>(value, false);
        state = State.CREATING;
        try {
            T created = Objects.requireNonNull(factory.get(), "factory result");
            if (state == State.CLOSED) throw new IllegalStateException("Scoped value closed during acquisition");
            value = created;
            state = State.READY;
            return new Acquisition<>(value, true);
        } catch (RuntimeException | Error error) {
            if (state != State.CLOSED) {
                failure = error;
                state = State.FAILED;
            }
            throw error;
        }
    }

    /** Reads only; absence does not authorize replay after an uncertain acquisition. */
    public synchronized Optional<T> current() {
        if (state == State.CLOSED) return Optional.empty();
        requireUsable();
        return Optional.ofNullable(value);
    }

    public synchronized boolean hasFailed() { return state == State.FAILED; }
    public synchronized boolean isClosed() { return state == State.CLOSED; }

    private void requireUsable() {
        if (state == State.CLOSED) throw new IllegalStateException("Scoped value is closed");
        if (state == State.CREATING) throw new IllegalStateException("Scoped acquisition is already in progress");
        if (state == State.FAILED) throw new IllegalStateException(
                "Scoped acquisition failed; automatic retry is not allowed", failure);
    }

    /** Forgets the reference only; it never disposes or deletes a borrowed external value. */
    @Override public synchronized void close() {
        value = null;
        failure = null;
        state = State.CLOSED;
    }

    @Override public synchronized String toString() { return "ScopedTestValue[state=" + state + "]"; }
}
