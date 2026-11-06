package atf.testkit.lifecycle;

import java.util.Objects;

/** Resolves an explicit JVM override, configured opt-in and IDE-only fallback in one order. */
public final class AtfE2eActivationPolicy {
    private AtfE2eActivationPolicy() { }

    public static Decision evaluate(String enableProperty, String propertyOverride,
                                    boolean configuredEnabled, String configurationSource,
                                    boolean ideRunnerDetected) {
        String property = Objects.requireNonNull(enableProperty, "enableProperty").trim();
        if (property.isEmpty()) throw new IllegalArgumentException("enableProperty must not be blank");

        if (propertyOverride != null) {
            if ("true".equalsIgnoreCase(propertyOverride)) {
                return new Decision(true, "enabled by JVM property " + property + "=true");
            }
            if ("false".equalsIgnoreCase(propertyOverride)) {
                return new Decision(false, "disabled by explicit JVM property " + property + "=false");
            }
            return new Decision(false, "disabled because JVM property " + property
                    + " must be exactly true or false");
        }

        if (configuredEnabled) {
            String source = Objects.requireNonNull(configurationSource, "configurationSource").trim();
            if (source.isEmpty()) throw new IllegalArgumentException("configurationSource must not be blank");
            return new Decision(true, "enabled by " + source);
        }
        if (ideRunnerDetected) {
            return new Decision(true, "enabled by IntelliJ runner detection heuristic");
        }
        return new Decision(false, "set -D" + property + "=true to run public-site E2E");
    }

    public record Decision(boolean enabled, String reason) { }
}
