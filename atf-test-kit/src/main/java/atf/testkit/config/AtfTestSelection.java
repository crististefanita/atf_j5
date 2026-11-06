package atf.testkit.config;

import java.util.Objects;

/** Immutable reference to one executable test method selected by a plan. */
public record AtfTestSelection(String className, String methodName) {
    public static final String METHOD_SEPARATOR = "#";

    public AtfTestSelection {
        className = required(className, "className");
        methodName = required(methodName, "methodName");
        if (className.contains("#") || methodName.contains("#")) {
            throw new IllegalArgumentException("Test class and method must not contain '#'");
        }
        if (!className.contains(".")) {
            throw new IllegalArgumentException("Test class must be fully qualified: " + className);
        }
    }

    public static AtfTestSelection parse(String value) {
        String selector = required(value, "selector");
        int separator = selector.lastIndexOf(METHOD_SEPARATOR);
        if (separator <= 0 || separator == selector.length() - METHOD_SEPARATOR.length()) {
            throw new IllegalArgumentException(
                    "Test selector must use fully.qualified.Class#method: " + selector);
        }
        return new AtfTestSelection(selector.substring(0, separator),
                selector.substring(separator + METHOD_SEPARATOR.length()));
    }

    public String selector() {
        return className + METHOD_SEPARATOR + methodName;
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
