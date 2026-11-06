package atf.testkit.config;

import java.nio.file.Path;
import java.util.Objects;

/** Captures process inputs once before a configurable test plan is loaded. */
public record AtfTestKitConfigurationContext(Path startDirectory,
                                             String systemPropertyConfig,
                                             String environmentConfig) {
    public static final String CONFIG_PROPERTY = "atf.testkit.config";
    public static final String CONFIG_ENVIRONMENT_VARIABLE = "ATF_TESTKIT_CONFIG";

    public AtfTestKitConfigurationContext {
        startDirectory = Objects.requireNonNull(startDirectory, "startDirectory")
                .toAbsolutePath().normalize();
        systemPropertyConfig = normalize(systemPropertyConfig);
        environmentConfig = normalize(environmentConfig);
    }

    public static AtfTestKitConfigurationContext fromProcess() {
        return new AtfTestKitConfigurationContext(
                Path.of(System.getProperty("user.dir", ".")),
                System.getProperty(CONFIG_PROPERTY),
                System.getenv(CONFIG_ENVIRONMENT_VARIABLE));
    }

    public Path explicitConfigPath() {
        String configured = systemPropertyConfig != null ? systemPropertyConfig : environmentConfig;
        if (configured == null) return null;
        Path path = Path.of(configured);
        return path.isAbsolute() ? path.normalize() : startDirectory.resolve(path).normalize();
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
