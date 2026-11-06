package atf.testkit.lifecycle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Test-only acceptance boundary: scope a configuration override, then collect the caller's delivered artifacts. */
public final class ScopedAcceptanceRun {
    private ScopedAcceptanceRun() { }

    /** The caller owns its runner, business assertions, directory and artifact selection. No launcher dependency is required. */
    public static synchronized <T> Result<T> execute(String property, String value, Path directory,
                                                    Supplier<T> invocation, Predicate<Path> artifact) throws IOException {
        Objects.requireNonNull(property, "property"); Objects.requireNonNull(value, "value");
        Objects.requireNonNull(directory, "directory"); Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(artifact, "artifact");
        String previous = System.getProperty(property);
        T outcome;
        try { System.setProperty(property, value); outcome = invocation.get(); }
        finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
        try (var files = Files.walk(directory)) {
            return new Result<>(outcome, files.filter(Files::isRegularFile).filter(artifact).sorted().toList());
        }
    }

    public record Result<T>(T outcome, List<Path> artifacts) {
        public Result { artifacts = List.copyOf(artifacts); }
    }
}
