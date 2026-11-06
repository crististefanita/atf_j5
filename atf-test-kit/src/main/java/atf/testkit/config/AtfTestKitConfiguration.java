package atf.testkit.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, consumer-neutral test-kit input resolved once at a composition boundary.
 * It describes where a consumer keeps tests/resources and which exact methods
 * belong to a named plug-and-play plan. It does not execute tests.
 */
public record AtfTestKitConfiguration(Path configFile,
                                      Path baseDirectory,
                                      List<Path> sourceRoots,
                                      List<Path> resourceRoots,
                                      Map<String, List<AtfTestSelection>> plans) {
    public AtfTestKitConfiguration {
        baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory")
                .toAbsolutePath().normalize();
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
        resourceRoots = List.copyOf(Objects.requireNonNull(resourceRoots, "resourceRoots"));
        if (sourceRoots.isEmpty() || resourceRoots.isEmpty()) {
            throw new IllegalArgumentException("test-kit source/resource roots must not be empty");
        }
        plans = plans == null ? Map.of() : plans.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> normalizePlanName(entry.getKey()),
                        entry -> List.copyOf(Objects.requireNonNull(entry.getValue(), "plan entries"))));
        if (sourceRoots.stream().anyMatch(Path::isAbsolute)
                || resourceRoots.stream().anyMatch(Path::isAbsolute)) {
            throw new IllegalArgumentException("test-kit source/resource roots must be relative");
        }
        if (plans.keySet().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("test-kit plan names must not be blank");
        }
    }

    public List<AtfTestSelection> selections(String planName) {
        return plans.getOrDefault(normalizePlanName(planName), List.of());
    }

    public Path resolve(Path relativePath) {
        Path relative = Objects.requireNonNull(relativePath, "relativePath").normalize();
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("test-kit paths must be relative: " + relativePath);
        }
        Path resolved = baseDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("test-kit path escapes its configuration directory: " + relativePath);
        }
        return resolved;
    }

    public static AtfTestKitConfiguration defaults(Path baseDirectory) {
        return new AtfTestKitConfiguration(null, baseDirectory,
                List.of(Path.of("src", "test", "java")),
                List.of(Path.of("src", "test", "resources")), Map.of());
    }

    private static String normalizePlanName(String name) {
        String normalized = Objects.requireNonNull(name, "planName").trim()
                .toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        if (normalized.isBlank()) throw new IllegalArgumentException("planName must not be blank");
        return normalized;
    }
}
