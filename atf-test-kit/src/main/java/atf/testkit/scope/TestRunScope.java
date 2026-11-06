package atf.testkit.scope;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Locale;
import java.util.Objects;

/** Explicit execution ownership, independent of class names, unique-ID strings and static caches. */
public enum TestRunScope {
    CLASS, SUITE;

    public static final String CONFIGURATION_PARAMETER = "atf.test.run.scope";

    public static TestRunScope selected(ExtensionContext context) {
        String configured = Objects.requireNonNull(context, "context")
                .getConfigurationParameter(CONFIGURATION_PARAMETER).orElse("class");
        return switch (configured.trim().toLowerCase(Locale.ROOT)) {
            case "class" -> CLASS;
            case "suite" -> SUITE;
            default -> throw new IllegalArgumentException(
                    CONFIGURATION_PARAMETER + " must be class or suite");
        };
    }

    public <T> ScopedTestValue<T> value(ExtensionContext context, Class<?> namespaceOwner,
                                      String name, Class<T> valueType) {
        Objects.requireNonNull(namespaceOwner, "namespaceOwner");
        Objects.requireNonNull(valueType, "valueType");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("value name is required");
        var namespace = ExtensionContext.Namespace.create(TestRunScope.class, this, namespaceOwner);
        var key = new ValueKey(name, valueType);
        @SuppressWarnings("unchecked")
        ScopedTestValue<T> selected = owner(context).getStore(namespace).getOrComputeIfAbsent(
                key, ignored -> new ScopedTestValue<T>(), ScopedTestValue.class);
        return selected;
    }

    private ExtensionContext owner(ExtensionContext context) {
        Objects.requireNonNull(context, "context");
        if (this == SUITE) return context.getRoot();
        Class<?> testClass = context.getRequiredTestClass();
        ExtensionContext candidate = context;
        while (candidate.getTestMethod().isPresent()) {
            candidate = candidate.getParent().orElseThrow(
                    () -> new IllegalStateException("Class scope requires a JUnit class owner"));
        }
        if (candidate.getTestClass().filter(testClass::equals).isEmpty()) {
            throw new IllegalStateException("Class scope requires the concrete JUnit class owner");
        }
        return candidate;
    }

    /** Owns disposal as well as the reference; unlike value(), this closes the acquired object. */
    public <T extends AutoCloseable> ScopedTestResource<T> resource(ExtensionContext context, Class<?> namespaceOwner,
                                                                  String name, Class<T> resourceType) {
        Objects.requireNonNull(namespaceOwner, "namespaceOwner");
        Objects.requireNonNull(resourceType, "resourceType");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("resource name is required");
        var namespace = ExtensionContext.Namespace.create(TestRunScope.class, this, namespaceOwner, ScopedTestResource.class);
        @SuppressWarnings("unchecked")
        ScopedTestResource<T> resource = owner(context).getStore(namespace).getOrComputeIfAbsent(
                new ValueKey(name, resourceType), ignored -> new ScopedTestResource<T>(), ScopedTestResource.class);
        return resource;
    }

    private record ValueKey(String name, Class<?> valueType) { }
}
