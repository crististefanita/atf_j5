package atf.rentz.governance.documentation;

import atf.testkit.AtfComponentTest;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static atf.rentz.governance.source.GovernanceProject.locateProjectRoot;

/** Keeps project documentation relocatable and free of machine-specific paths. */
@AtfComponentTest
public class DynamicPathDocumentationArchitectureTest {
    private static final Pattern ABSOLUTE_WINDOWS_PATH =
            Pattern.compile("(?i)(?<![a-z0-9])(?:file:///)?[a-z]:[\\\\/]");
    private static final Pattern CONCRETE_TIMESTAMPED_REPORT =
            Pattern.compile("(?i)(?:api|ui)-atf-extent-report[^\\s`)\\]]*20\\d{2}-\\d{2}-\\d{2}[^\\s`)\\]]*\\.html");
    private static final List<String> IGNORED_DIRECTORY_NAMES = List.of(
            ".git", "target", "artifacts", ".idea", "migration-copy-rejected",
            "migration-quarantine");

    @Test
    void markdownAndReadmesDoNotPublishMachineSpecificAbsolutePaths() throws Exception {
        Path root = locateProjectRoot();
        List<DocumentationGovernanceSupport.Violation> violations = new java.util.ArrayList<>();
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> DocumentationGovernanceSupport.isGovernedMarkdown(root, path))
                    .forEach(path -> validatePaths(path, root, violations));
        }
        DocumentationGovernanceSupport.assertNoViolations("documentation relocatability", violations);
    }

    @Test
    void markdownAndReadmesDoNotPublishConcreteGeneratedReportNames() throws Exception {
        Path root = locateProjectRoot();
        List<DocumentationGovernanceSupport.Violation> violations = new java.util.ArrayList<>();
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> DocumentationGovernanceSupport.isGovernedMarkdown(root, path))
                    .forEach(path -> validateGeneratedReportNames(path, root, violations));
        }
        DocumentationGovernanceSupport.assertNoViolations("generated report names in documentation", violations);
    }

    private boolean isDocumentation(Path path) {
        for (Path part : path) {
            if (IGNORED_DIRECTORY_NAMES.contains(part.toString())) return false;
        }
        String name = path.getFileName().toString();
        return name.equalsIgnoreCase("README.md")
                || (name.endsWith(".md") && path.toString().contains("docs"));
    }

    static void validatePaths(Path path, Path root,
                              List<DocumentationGovernanceSupport.Violation> violations) {
        try {
            int lineNumber = 0;
            for (String line : Files.readAllLines(path)) {
                lineNumber++;
                if (ABSOLUTE_WINDOWS_PATH.matcher(line).find()) {
                    violations.add(DocumentationGovernanceSupport.violation(
                            "DOC-PATH-ABSOLUTE", root, path, lineNumber,
                            "documentation exposes a machine-specific absolute path",
                            "normative documentation must be relocatable",
                            "replace the local path with a relative link or a configurable placeholder"));
                }
            }
        } catch (Exception error) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-READ", root, path, 1,
                    "documentation could not be read: " + error.getMessage(),
                    "every owner README must be readable by the governance scan",
                    "fix the file encoding or filesystem access before changing content"));
        }
    }

    static void validateGeneratedReportNames(Path path, Path root,
                                             List<DocumentationGovernanceSupport.Violation> violations) {
        try {
            int lineNumber = 0;
            for (String line : Files.readAllLines(path)) {
                lineNumber++;
                if (CONCRETE_TIMESTAMPED_REPORT.matcher(line).find()) {
                    violations.add(DocumentationGovernanceSupport.violation(
                            "DOC-ARTIFACT-NAME", root, path, lineNumber,
                            "documentation contains a concrete generated report name",
                            "normative docs describe report structure, not one run",
                            "use a placeholder such as <configured-report-file>"));
                }
            }
        } catch (Exception error) {
            violations.add(DocumentationGovernanceSupport.violation(
                    "DOC-READ", root, path, 1,
                    "documentation could not be read: " + error.getMessage(),
                    "every owner README must be readable by the governance scan",
                    "fix the file encoding or filesystem access before changing content"));
        }
    }
}
