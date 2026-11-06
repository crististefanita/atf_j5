package atf.testkit.console;

import atf.testkit.AtfConsoleMessages;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Resolves one consistent console policy from direct, meta, and superclass declarations. */
public final class AtfConsoleMessagePolicyResolver {
    private AtfConsoleMessagePolicyResolver() { }

    public static AtfConsoleMessages.Policy resolve(Class<?> testClass) {
        Objects.requireNonNull(testClass, "testClass");
        for (Class<?> current = testClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            Set<AtfConsoleMessages.Policy> policies = new LinkedHashSet<>();
            for (Annotation annotation : current.getDeclaredAnnotations()) {
                collect(annotation, policies, new LinkedHashSet<>());
            }
            if (policies.size() > 1) {
                throw new IllegalStateException("Conflicting @AtfConsoleMessages policies on "
                        + current.getName() + ": " + policies);
            }
            if (!policies.isEmpty()) return policies.iterator().next();
        }
        return AtfConsoleMessages.Policy.INHERIT;
    }

    private static void collect(Annotation annotation,
                                Set<AtfConsoleMessages.Policy> policies,
                                Set<Class<?>> visited) {
        Class<? extends Annotation> type = annotation.annotationType();
        if (type == AtfConsoleMessages.class) {
            policies.add(((AtfConsoleMessages) annotation).value());
            return;
        }
        if (!visited.add(type) || type.getName().startsWith("java.lang.annotation.")) return;
        for (Annotation meta : type.getDeclaredAnnotations()) {
            collect(meta, policies, visited);
        }
    }
}
