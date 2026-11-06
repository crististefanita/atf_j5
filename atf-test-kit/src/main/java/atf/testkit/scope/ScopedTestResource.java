package atf.testkit.scope;

import org.junit.jupiter.api.extension.ExtensionContext;
import java.util.function.Supplier;

/** A lifecycle-owned value; acquisition and a failing close are each attempted once. */
public final class ScopedTestResource<T extends AutoCloseable> implements ExtensionContext.Store.CloseableResource {
    private final ScopedTestValue<T> value = new ScopedTestValue<>();
    private boolean closed;

    public synchronized ScopedTestValue.Acquisition<T> getOrCreate(Supplier<? extends T> factory) {
        if (closed) throw new IllegalStateException("Scoped resource is closed");
        return value.getOrCreate(factory);
    }

    @Override public synchronized void close() throws Exception {
        if (closed) return;
        closed = true;
        try {
            if (!value.hasFailed()) {
                var acquired = value.current();
                if (acquired.isPresent()) acquired.get().close();
            }
        } finally {
            value.close();
        }
    }
}
