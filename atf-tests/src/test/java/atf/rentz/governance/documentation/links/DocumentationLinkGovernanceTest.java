package atf.rentz.governance.documentation.links;

import atf.rentz.governance.documentation.DocumentationGovernanceSupport;
import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static atf.rentz.governance.source.GovernanceProject.locateProjectRoot;

/** Local destinations and fragments are governed for owner READMEs and every project document. */
@AtfComponentTest
public class DocumentationLinkGovernanceTest {
    @Test
    void governedMarkdownKeepsResolvableLocalDestinationsAndFragments() throws Exception {
        var violations = new ArrayList<DocumentationGovernanceSupport.Violation>();
        inspect(locateProjectRoot(), violations);
        DocumentationGovernanceSupport.assertNoViolations("documentation links and fragments", violations);
    }

    public static void inspect(Path root, List<DocumentationGovernanceSupport.Violation> violations) throws Exception {
        try (var files = Files.walk(root)) {
            files.filter(path -> DocumentationGovernanceSupport.isGovernedMarkdown(root, path))
                    .forEach(path -> validate(path, root, violations));
        }
    }

    public static void validate(Path path, Path root, List<DocumentationGovernanceSupport.Violation> violations) {
        try {
            DocumentationLocalLinks.validate(path, root, Files.readAllLines(path), violations);
        } catch (java.io.IOException unreadable) {
            violations.add(DocumentationGovernanceSupport.violation("DOC-READ", root, path, 1,
                    "documentation could not be read: " + unreadable.getMessage(),
                    "local destinations and fragments require readable Markdown",
                    "fix the document encoding or filesystem access"));
        }
    }
}
