package atf.testkit.reporting;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Resolves one explicit report-path policy from direct, meta, and superclass declarations. */
public final class AtfReportPathPolicyResolver {
    private AtfReportPathPolicyResolver() { }

    public static AtfReportPath.Policy resolve(Class<?> testClass) {
        Objects.requireNonNull(testClass, "testClass");
        for (Class<?> current = testClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            AtfReportPath direct = current.getDeclaredAnnotation(AtfReportPath.class);
            if (direct != null) return direct.value();
            Set<AtfReportPath.Policy> policies = new LinkedHashSet<>();
            for (Annotation annotation : current.getDeclaredAnnotations()) {
                collect(annotation, policies, new LinkedHashSet<>());
            }
            if (policies.size() > 1) {
                throw new IllegalStateException("Conflicting @AtfReportPath policies on "
                        + current.getName() + ": " + policies);
            }
            if (!policies.isEmpty()) return policies.iterator().next();
        }
        return AtfReportPath.Policy.DISABLED;
    }

    private static void collect(Annotation annotation, Set<AtfReportPath.Policy> policies,
                                Set<Class<?>> visited) {
        Class<? extends Annotation> type = annotation.annotationType();
        if (type == AtfReportPath.class) {
            policies.add(((AtfReportPath) annotation).value());
            return;
        }
        if (!visited.add(type) || type.getName().startsWith("java.lang.annotation.")) return;
        for (Annotation meta : type.getDeclaredAnnotations()) {
            collect(meta, policies, visited);
        }
    }
}
