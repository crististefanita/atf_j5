package atf.rentz.governance.documentation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared discovery and diagnostic vocabulary for documentation governance tests. */
public final class DocumentationGovernanceSupport {
    private static final List<String> IGNORED_DIRECTORY_NAMES = List.of(
            ".git", "target", "artifacts", ".idea", "migration-copy-rejected",
            "migration-quarantine", ".presentation-build");

    private DocumentationGovernanceSupport() {
    }

    public record Violation(String code, Path file, int line, String finding,
                     String rule, String fix) {
        @Override
        public String toString() {
            return "\n[" + code + "] " + file + ":" + line
                    + "\n  found: " + finding
                    + "\n  rule: " + rule
                    + "\n  fix: " + fix;
        }
    }

    public static Violation violation(String code, Path root, Path file, int line,
                               String finding, String rule, String fix) {
        Path relative = root.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize());
        return new Violation(code, relative, line, finding, rule, fix);
    }

    public static void assertNoViolations(String area, List<Violation> violations) {
        assertTrue(violations.isEmpty(), () -> {
            String details = violations.stream().map(Violation::toString).reduce("", String::concat);
            return area + " failed with " + violations.size()
                    + " actionable violation(s)." + details;
        });
    }

    static boolean isReadme(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().equalsIgnoreCase("README.md");
    }

    public static boolean isGovernedMarkdown(Path root, Path path) {
        Path project = root.toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(project) || !Files.isRegularFile(normalized)) return false;
        String relative = project.relativize(normalized).toString().replace('\\', '/');
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var layout = mapper.readTree(project.resolve("config/java-source-layout.json").toFile());
            for (var excluded : layout.required("excludedTrees")) {
                if (relative.equals(excluded.asText()) || relative.startsWith(excluded.asText() + "/")) return false;
            }
            var policy = mapper.readTree(project.resolve("config/documentation-policy.json").toFile());
            for (var archived : policy.required("historical")) {
                if (relative.equals(archived.required("path").asText())) return false;
            }
        } catch (java.io.IOException missing) {
            throw new IllegalStateException("DOC-INVENTORY: declared scope is unreadable", missing);
        }
        return isReadme(normalized)
                || relative.startsWith("lib/README.") && relative.endsWith(".md")
                || relative.startsWith("docs/") && relative.endsWith(".md");
    }
}
