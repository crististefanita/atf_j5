package atf.rentz.governance.documentation;

import atf.rentz.governance.documentation.links.DocumentationLinkGovernanceTest;
import atf.testkit.AtfComponentTest;
import atf.testkit.documentation.DocumentationTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;

import static atf.rentz.governance.source.GovernanceProject.locateProjectRoot;

/** Applies the shared documentation checks to the owner pages for qualification and evidence. */
@AtfComponentTest
@DocumentationTarget("README.md")
@DocumentationTarget("atf-tests/README.md")
@DocumentationTarget("docs/rentz/ATF_Qualification.md")
@DocumentationTarget("docs/rentz/ATF_Android.md")
@DocumentationTarget("docs/rentz/ATF_Release.md")
@DocumentationTarget("release/README.md")
class DocumentationTargetGovernanceTest {
    @Test
    void declaredOwnerPagesPassTheSameChecksAsTheProjectInventory() throws Exception {
        Path root = locateProjectRoot();
        var violations = new ArrayList<DocumentationGovernanceSupport.Violation>();
        for (var annotation : getClass().getAnnotationsByType(DocumentationTarget.class)) {
            Path target = root.resolve(annotation.value()).normalize();
            if (!DocumentationGovernanceSupport.isGovernedMarkdown(root, target)) {
                violations.add(DocumentationGovernanceSupport.violation("DOC-TARGET-SCOPE", root, target, 1,
                        "Declared owner page is missing or outside the governed inventory",
                        "Targets are project-relative governed Markdown files",
                        "Restore the owner page or update its annotation"));
                continue;
            }
            DocumentationDiagramGovernanceTest.validate(target, root, violations);
            DocumentationLinkGovernanceTest.validate(target, root, violations);
            if (DocumentationGovernanceSupport.isReadme(target)) {
                DocumentationReadmeStructureGovernanceTest.validate(target, root, violations);
            }
            DocumentationStyleGovernanceTest.validate(target, root, violations);
            DocumentationProseGovernanceTest.validate(target, root, violations);
            DynamicPathDocumentationArchitectureTest.validatePaths(target, root, violations);
            DynamicPathDocumentationArchitectureTest.validateGeneratedReportNames(target, root, violations);
        }
        DocumentationGovernanceSupport.assertNoViolations("Release documentation targets", violations);
    }
}
