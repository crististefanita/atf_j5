package atf.testkit.output;

import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure selection and path validation; the consumer owns directory creation and publication. */
public final class ReportOutputDirectory {
    private ReportOutputDirectory() { }

    /** Resolves an arbitrary configured artifact directory below the project root. */
    public static Path resolveProjectRelative(Path projectRoot, String configured,
                                              String defaultRelativePath) {
        Path project = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
        String value = configured == null || configured.isBlank()
                ? Objects.requireNonNull(defaultRelativePath, "defaultRelativePath").trim()
                : configured.trim();
        if (value.isEmpty() || value.startsWith("\\\\") || value.startsWith("//")
                || value.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) {
            throw new IllegalArgumentException("Configured artifact directory must be a relative path: " + value);
        }

        Path relative;
        try {
            relative = Path.of(value).normalize();
        } catch (InvalidPathException error) {
            throw new IllegalArgumentException("Invalid configured artifact directory: " + value, error);
        }
        if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isBlank()) {
            throw new IllegalArgumentException("Configured artifact directory must stay inside the project: " + value);
        }

        Path result = project.resolve(relative).normalize();
        if (!result.startsWith(project) || result.equals(project)) {
            throw new IllegalArgumentException("Configured artifact directory must stay inside the project: " + value);
        }
        verifyExistingAncestors(project, result);
        return result;
    }

    public static Path resolve(Path projectRoot, Path uiReportBase, Class<?> owner) {
        Path project = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
        Path ui = Objects.requireNonNull(uiReportBase, "uiReportBase").toAbsolutePath().normalize();
        if (owner == null) return ui;
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            List<Selection> found = new ArrayList<>();
            for (Annotation annotation : type.getDeclaredAnnotations()) {
                collect(annotation, found, new HashSet<>());
            }
            if (found.isEmpty()) continue;
            Selection selected = found.get(0);
            if (found.stream().anyMatch(other -> !other.equals(selected))) {
                throw new IllegalStateException("Conflicting UI report directory annotations on " + type.getName());
            }
            Path base = selected.projectRelative() ? project : ui;
            String value = selected.value().trim();
            if (value.isEmpty() || value.startsWith("\\\\") || value.startsWith("//")
                    || value.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) {
                throw new IllegalStateException("Invalid UI report directory on " + type.getName() + ": " + value);
            }
            Path relative;
            try {
                relative = Path.of(value);
            } catch (InvalidPathException error) {
                throw new IllegalStateException("Invalid UI report directory path on " + type.getName()
                        + " relative to " + base + ": " + value, error);
            }
            if (relative.isAbsolute()) {
                throw new IllegalStateException("UI report directory must be relative: " + value);
            }
            Path result = base.resolve(relative).normalize();
            if (!result.startsWith(base) || result.equals(base)) {
                throw new IllegalStateException("UI report directory escapes its anchor: " + value);
            }
            verifyExistingAncestors(base, result);
            return result;
        }
        return ui;
    }

    private static void collect(Annotation annotation, List<Selection> found, Set<Class<?>> visiting) {
        Class<? extends Annotation> type = annotation.annotationType();
        if (annotation instanceof UiReportSubdirectory ui) {
            found.add(new Selection(false, ui.value()));
        } else if (annotation instanceof ProjectRelativeUiReportDirectory project) {
            found.add(new Selection(true, project.value()));
        }
        if (type.getName().startsWith("java.") || type.getName().startsWith("org.junit.")) return;
        if (!visiting.add(type)) return;
        for (Annotation meta : type.getDeclaredAnnotations()) collect(meta, found, visiting);
        visiting.remove(type);
    }

    private static void verifyExistingAncestors(Path base, Path result) {
        try {
            Path physicalBase = physicalPath(base);
            Path physicalResult = physicalPath(result);
            if (!physicalResult.startsWith(physicalBase)) {
                throw new IllegalStateException("UI report directory resolves outside its anchor: " + result);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot validate UI report directory: " + result, error);
        }
    }

    private static Path physicalPath(Path path) throws IOException {
        Path ancestor = path;
        while (ancestor != null && !Files.exists(ancestor)) ancestor = ancestor.getParent();
        if (ancestor == null) throw new IOException("No existing ancestor for " + path);
        return ancestor.toRealPath().resolve(ancestor.relativize(path)).normalize();
    }

    private record Selection(boolean projectRelative, String value) { }
}
