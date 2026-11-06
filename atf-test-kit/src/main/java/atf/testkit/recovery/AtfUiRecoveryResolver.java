package atf.testkit.recovery;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.*;

/** Resolves method and nearest declaring class, including composed declarations, before resources are acquired. */
public final class AtfUiRecoveryResolver {
    private AtfUiRecoveryResolver() { }

    public static AtfUiRecoveryPlan resolve(Class<?> owner, Method method) {
        Objects.requireNonNull(owner, "owner");
        if (method != null && !method.getDeclaringClass().isAssignableFrom(owner)) {
            throw new IllegalArgumentException("Recovery method does not belong to its invocation owner");
        }
        AtfUiRecovery.Mode base = AtfUiRecovery.Mode.CONFIGURED;
        String source = "YAML";
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            var declared = declaration(type);
            if (declared.isPresent()) { base = declared.get(); source = "class " + type.getName(); break; }
        }
        var local = method == null ? Optional.<AtfUiRecovery.Mode>empty() : declaration(method);
        if (local.isEmpty()) return new AtfUiRecoveryPlan(base, source);
        String origin = source + "; method " + method.getName();
        // DISABLED is a restriction: even an explicit READ_ONLY method cannot reopen its class.
        AtfUiRecovery.Mode effective = base == AtfUiRecovery.Mode.DISABLED
                || local.get() == AtfUiRecovery.Mode.DISABLED ? AtfUiRecovery.Mode.DISABLED
                : local.get() == AtfUiRecovery.Mode.CONFIGURED ? base : local.get();
        return new AtfUiRecoveryPlan(effective, origin);
    }

    private static Optional<AtfUiRecovery.Mode> declaration(AnnotatedElement owner) {
        var values = new ArrayList<AtfUiRecovery.Mode>();
        for (Annotation annotation : owner.getDeclaredAnnotations()) collect(annotation, values, new HashSet<>());
        if (values.isEmpty()) return Optional.empty();
        var explicit = values.stream().filter(mode -> mode != AtfUiRecovery.Mode.CONFIGURED).distinct().toList();
        if (explicit.size() > 1) throw new IllegalStateException("Conflicting UI recovery declarations on " + owner);
        return Optional.of(explicit.isEmpty() ? AtfUiRecovery.Mode.CONFIGURED : explicit.getFirst());
    }
    private static void collect(Annotation value, List<AtfUiRecovery.Mode> values, Set<Class<?>> visiting) {
        Class<? extends Annotation> type = value.annotationType();
        if (value instanceof AtfUiRecovery recovery) values.add(recovery.value());
        if (type.getName().startsWith("java.") || type.getName().startsWith("org.junit.") || !visiting.add(type)) return;
        for (Annotation meta : type.getDeclaredAnnotations()) collect(meta, values, visiting);
        visiting.remove(type);
    }
}
