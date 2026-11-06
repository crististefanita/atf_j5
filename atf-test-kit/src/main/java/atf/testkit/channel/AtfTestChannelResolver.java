package atf.testkit.channel;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Direct and composed declarations on the concrete class override the nearest superclass. */
public final class AtfTestChannelResolver {
    private AtfTestChannelResolver() { }

    public static Optional<TestChannelPlan> resolve(Class<?> owner) {
        Objects.requireNonNull(owner, "owner");
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            List<TestChannel> channels = new ArrayList<>();
            for (Annotation annotation : type.getDeclaredAnnotations()) {
                collect(annotation, channels, new HashSet<>());
            }
            if (channels.isEmpty()) continue;
            TestChannel channel = channels.get(0);
            if (channels.stream().anyMatch(other -> other != channel)) {
                throw new IllegalStateException("Conflicting test channels on " + type.getName() + ": " + channels);
            }
            return Optional.of(new TestChannelPlan(channel, type));
        }
        return Optional.empty();
    }

    private static void collect(Annotation annotation, List<TestChannel> channels, Set<Class<?>> visiting) {
        Class<? extends Annotation> type = annotation.annotationType();
        if (annotation instanceof AtfTestChannel selected) channels.add(selected.value());
        if (type.getName().startsWith("java.") || type.getName().startsWith("org.junit.")) return;
        if (!visiting.add(type)) return;
        for (Annotation meta : type.getDeclaredAnnotations()) collect(meta, channels, visiting);
        visiting.remove(type);
    }
}
