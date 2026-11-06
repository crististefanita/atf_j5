package atf.testkit.files;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Test-only file/resource access with explicit, containment-checked paths. */
public final class AtfTestResourceReader {
    private AtfTestResourceReader() {
    }

    public static Path requiredPath(Class<?> owner, String resourceName) {
        Objects.requireNonNull(owner, "owner");
        String name = requiredName(resourceName);
        var resource = owner.getClassLoader().getResource(name);
        if (resource == null) throw new IllegalStateException("Test resource is missing: " + name);
        try {
            Path path = Path.of(resource.toURI()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Test resource is not a file: " + name);
            }
            return path;
        } catch (URISyntaxException | RuntimeException error) {
            throw new IllegalStateException("Cannot resolve test resource: " + name, error);
        }
    }

    public static String readUtf8(Path file) {
        Path path = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Test file is missing: " + path);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read test file: " + path, error);
        }
    }

    public static String readResourceUtf8(Class<?> owner, String resourceName) {
        Objects.requireNonNull(owner, "owner");
        String name = requiredName(resourceName);
        try (InputStream input = owner.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IllegalStateException("Test resource is missing: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read test resource: " + name, error);
        }
    }

    public static Path resolveContained(Path baseDirectory, Path relativePath) {
        Path base = Objects.requireNonNull(baseDirectory, "baseDirectory").toAbsolutePath().normalize();
        Path relative = Objects.requireNonNull(relativePath, "relativePath").normalize();
        if (relative.isAbsolute()) throw new IllegalArgumentException("Test path must be relative: " + relativePath);
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Test path escapes its base directory: " + relativePath);
        }
        return resolved;
    }

    private static String requiredName(String resourceName) {
        String normalized = Objects.requireNonNull(resourceName, "resourceName").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("resourceName must not be blank");
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }
}
