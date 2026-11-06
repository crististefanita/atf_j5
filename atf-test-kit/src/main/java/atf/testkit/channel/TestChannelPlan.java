package atf.testkit.channel;

import java.util.Objects;
import java.util.Set;

/** Immutable selection; consumers preflight capabilities before opening resources. */
public record TestChannelPlan(TestChannel channel, Class<?> declaredBy) {
    public TestChannelPlan {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(declaredBy, "declaredBy");
    }

    public void requireSupported(Set<TestChannel> supported) {
        if (!Objects.requireNonNull(supported, "supported").contains(channel)) {
            throw new IllegalStateException(declaredBy.getName() + " selects " + channel
                    + " but the consumer does not support that channel");
        }
    }
}
