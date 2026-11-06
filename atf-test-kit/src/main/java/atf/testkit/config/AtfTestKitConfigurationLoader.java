package atf.testkit.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads the optional, consumer-neutral {@code testKit} YAML section. */
public final class AtfTestKitConfigurationLoader {
    public static final List<String> DEFAULT_CONFIG_NAMES = List.of(
            "atf-test-kit.yaml", "atf-test-kit.yml", "atf.yaml", "atf.yml");
    private static final String TEST_KIT_SECTION = "testKit";
    private static final String SOURCE_ROOTS = "sourceRoots";
    private static final String RESOURCE_ROOTS = "resourceRoots";
    private static final String PLANS = "plans";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private AtfTestKitConfigurationLoader() {
    }

    public static AtfTestKitConfiguration load() {
        return load(AtfTestKitConfigurationContext.fromProcess());
    }

    public static AtfTestKitConfiguration load(AtfTestKitConfigurationContext context) {
        Objects.requireNonNull(context, "context");
        Path explicit = context.explicitConfigPath();
        Path discovered = explicit == null ? discover(context.startDirectory()) : explicit;
        return discovered == null
                ? AtfTestKitConfiguration.defaults(context.startDirectory())
                : load(discovered);
    }

    public static AtfTestKitConfiguration load(Path configFile) {
        Path config = Objects.requireNonNull(configFile, "configFile").toAbsolutePath().normalize();
        if (!Files.isRegularFile(config)) {
            throw new IllegalStateException("ATF test-kit configuration does not exist: " + config);
        }
        try {
            JsonNode root = YAML.readTree(config.toFile());
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("ATF test-kit YAML root must be an object: " + config);
            }
            JsonNode section = root.path(TEST_KIT_SECTION);
            if (section.isMissingNode() || section.isNull()) {
                return AtfTestKitConfiguration.defaults(config.getParent());
            }
            if (!section.isObject()) {
                throw new IllegalStateException("testKit must be an object: " + config);
            }
            Path base = config.getParent() == null ? Path.of(".").toAbsolutePath().normalize() : config.getParent();
            return new AtfTestKitConfiguration(config, base,
                    relativePaths(section, SOURCE_ROOTS, Path.of("src", "test", "java")),
                    relativePaths(section, RESOURCE_ROOTS, Path.of("src", "test", "resources")),
                    plans(section.path(PLANS)));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read ATF test-kit configuration: " + config, error);
        }
    }

    public static Path discover(Path startDirectory) {
        Path current = Objects.requireNonNull(startDirectory, "startDirectory")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(current)) current = current.getParent();
        while (current != null) {
            for (String name : DEFAULT_CONFIG_NAMES) {
                Path candidate = current.resolve(name);
                if (Files.isRegularFile(candidate)) return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static List<Path> relativePaths(JsonNode section, String name, Path fallback) {
        JsonNode values = section.path(name);
        if (values.isMissingNode() || values.isNull()) return List.of(fallback);
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalStateException("testKit." + name + " must be a non-empty array");
        }
        List<Path> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalStateException("testKit." + name + " values must be non-blank paths");
            }
            Path path = Path.of(value.asText().trim()).normalize();
            if (path.isAbsolute()) throw new IllegalStateException("testKit paths must be relative: " + path);
            result.add(path);
        }
        return List.copyOf(result);
    }

    private static Map<String, List<AtfTestSelection>> plans(JsonNode plans) {
        if (plans.isMissingNode() || plans.isNull()) return Map.of();
        if (!plans.isObject()) throw new IllegalStateException("testKit.plans must be an object");
        Map<String, List<AtfTestSelection>> result = new LinkedHashMap<>();
        plans.fields().forEachRemaining(entry -> {
            JsonNode entries = entry.getValue();
            if (!entries.isArray() || entries.isEmpty()) {
                throw new IllegalStateException("testKit.plans." + entry.getKey()
                        + " must be a non-empty selector array");
            }
            List<AtfTestSelection> selections = new ArrayList<>();
            for (JsonNode value : entries) {
                if (!value.isTextual()) {
                    throw new IllegalStateException("testKit.plans entries must use class#method selectors");
                }
                selections.add(AtfTestSelection.parse(value.asText()));
            }
            result.put(entry.getKey(), selections);
        });
        return result;
    }
}
