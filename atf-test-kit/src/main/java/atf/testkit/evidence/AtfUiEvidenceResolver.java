package atf.testkit.evidence;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Resolves direct/composed axes before resource acquisition; the nearest declaring class wins. */
public final class AtfUiEvidenceResolver {
    private AtfUiEvidenceResolver() { }

    public static Optional<AtfUiEvidencePlan> resolve(Class<?> owner) {
        Objects.requireNonNull(owner, "owner");
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            var declarations = new ArrayList<AtfUiEvidencePlan>();
            for (Annotation annotation : type.getDeclaredAnnotations()) {
                collect(annotation, declarations, new HashSet<>());
            }
            if (declarations.isEmpty()) continue;
            return Optional.of(new AtfUiEvidencePlan(
                    axis(type, "flow", declarations.stream().map(AtfUiEvidencePlan::flow).toList(), AtfUiEvidenceFlow.CONFIGURED),
                    axis(type, "screenshots", declarations.stream().map(AtfUiEvidencePlan::screenshots).toList(), AtfUiScreenshotPolicy.CONFIGURED),
                    axis(type, "actionDetails", declarations.stream().map(AtfUiEvidencePlan::actionDetails).toList(), AtfUiActionDetails.CONFIGURED)));
        }
        return Optional.empty();
    }

    private static <T> T axis(Class<?> owner, String name, List<T> declarations, T configured) {
        List<T> explicit = declarations.stream().filter(value -> !value.equals(configured)).distinct().toList();
        if (explicit.size() > 1) {
            throw new IllegalStateException("Conflicting UI evidence " + name + " on " + owner.getName() + ": " + explicit);
        }
        return explicit.isEmpty() ? configured : explicit.getFirst();
    }

    private static void collect(Annotation annotation, List<AtfUiEvidencePlan> declarations,
                                Set<Class<?>> visiting) {
        Class<? extends Annotation> type = annotation.annotationType();
        if (annotation instanceof AtfUiEvidence evidence) declarations.add(AtfUiEvidencePlan.from(evidence));
        if (type.getName().startsWith("java.") || type.getName().startsWith("org.junit.") || !visiting.add(type)) return;
        for (Annotation meta : type.getDeclaredAnnotations()) collect(meta, declarations, visiting);
        visiting.remove(type);
    }
}
