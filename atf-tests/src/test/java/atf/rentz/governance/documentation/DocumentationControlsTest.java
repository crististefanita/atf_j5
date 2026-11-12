package atf.rentz.governance.documentation;

import atf.rentz.RentzRulePublication;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.ComponentAuditExtension;
import atf.rentz.governance.documentation.links.DocumentationLinkGovernanceTest;
import atf.rentz.governance.source.GovernanceProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import static atf.testkit.assertions.AuditedAssertions.*;

@AtfComponentTest
class DocumentationControlsTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            DocumentationControlsTest.class, "documentation-governance.html", "Portable documentation contracts and negative controls");

    @TempDir Path root;
    private List<DocumentationGovernanceSupport.Violation> inspect(String text) throws Exception {
        Path file = root.resolve("README.md");
        Files.writeString(file, text);
        var result = new ArrayList<DocumentationGovernanceSupport.Violation>();
        DocumentationReadmeStructureGovernanceTest.validate(file, root, result);
        DocumentationStyleGovernanceTest.validate(file, root, result);
        DocumentationProseGovernanceTest.validate(file, root, result);
        DocumentationDiagramGovernanceTest.validate(file, root, result);
        DynamicPathDocumentationArchitectureTest.validatePaths(file, root, result);
        DocumentationLinkGovernanceTest.validate(file, root, result);
        return result;
    }
    @Test void independentDefectsFailAndTheHealthyDocumentStaysValid() throws Exception {
        String good = "# Contract\n\n## Pe scurt\n\nMecanism stabil.\n";
        String fence = String.valueOf((char)96).repeat(3);
        assertEquals(List.of(), inspect(good));
        for (String defect : List.of(
                "# Alt titlu\n", "#### Salt\n",
                "<span>Text</span>\n",
                "Una. Două. Trei. Patru.\n",
                "[Link](absent.md#inexistent)\n",
                fence + "mermaid\nflowchart LR\nA --> B\n" + fence + "\n",
                "C:" + (char)92 + "Users" + (char)92 + "local\n")) {
            assertFalse(inspect(good + "\n" + defect).isEmpty(), defect);
            assertEquals(List.of(), inspect(good));
        }
    }
    @Test void aHeadingRemovedFromTheOwnerFailsAtTheOriginalLinkLine() throws Exception {
        Path target = root.resolve("owner.md");
        String guide = "# Contract\n\n## Pe scurt\n\n[Contract](owner.md#stable-heading)\n";
        Files.writeString(target, "## Stable heading\n");
        assertEquals(List.of(), inspect(guide));
        Files.writeString(target, "~~~md\n## Stable heading\n~~~\n## Renamed heading\n");
        var violations = inspect(guide);
        assertFalse(violations.isEmpty(), "A heading in a fenced example cannot preserve a removed anchor");
        assertTrue(violations.stream().allMatch(issue -> issue.code().equals("DOC-README-ANCHOR")
                && issue.file().equals(Path.of("README.md")) && issue.line() == 5
                && issue.fix().contains("restore the destination heading")), violations::toString);
    }

    @Test void existingFilesOutsideTheCheckoutCannotSatisfyTheDocumentationContract() throws Exception {
        Path project = Files.createDirectory(root.resolve("project"));
        Path guide = project.resolve("README.md");
        Path owner = Files.writeString(project.resolve("owner.md"), "## Stable heading\n");
        Path external = Files.writeString(root.resolve("sibling.md"), "## Stable heading\n");
        for (String target : List.of("../sibling.md#stable-heading", "%2e%2e/sibling.md",
                external.toString().replace('\\', '/'), external.toUri().toString())) {
            Files.writeString(guide, "# Contract\n\n[External](" + target + ")\n");
            var violations = new ArrayList<DocumentationGovernanceSupport.Violation>();
            DocumentationLinkGovernanceTest.validate(guide, project, violations);
            assertEquals(1, violations.size(), target);
            assertEquals("DOC-LINK-PORTABILITY", violations.getFirst().code(), target);
            assertEquals(3, violations.getFirst().line(), target);
        }

        Files.writeString(guide, "# Contract\n\n[Owner](" + owner.getFileName() + "#stable-heading)\n"
                + "~~~md\n[Example](../sibling.md)\n~~~\n");
        var violations = new ArrayList<DocumentationGovernanceSupport.Violation>();
        DocumentationLinkGovernanceTest.validate(guide, project, violations);
        assertTrue(violations.isEmpty(), violations::toString);
    }

    @Test void adaptedValidatorsKeepTheirDeclaredSourceIdentity() throws Exception {
        Path project = GovernanceProject.root();
        var manifest = new ObjectMapper().readTree(project.resolve("config/documentation-validators.json").toFile());
        assertEquals("documentation-governance-source-adapter-v1", manifest.required("sourceContract").asText());
        var paths = new java.util.HashSet<String>();
        for (var row : manifest.required("sources")) {
            String relative = row.required("path").asText();
            assertTrue(paths.add(relative));
            assertFalse(relative.contains(".."));
            assertTrue(row.required("sha256").asText().matches("[a-f0-9]{64}"));
            Path file = project.resolve("atf-tests/src/test/java/atf/rentz/governance/documentation").resolve(relative);
            String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
            assertEquals(row.required("adaptedSha256").asText(), actual, relative);
        }
        assertEquals(java.util.Set.of("DocumentationDiagramGovernanceTest.java", "DocumentationStyleGovernanceTest.java",
                "DocumentationProseGovernanceTest.java", "DynamicPathDocumentationArchitectureTest.java",
                "DocumentationReadmeStructureGovernanceTest.java", "DocumentationGovernanceSupport.java",
                "links/DocumentationLinkGovernanceTest.java", "links/DocumentationLocalLinks.java",
                "links/MarkdownAnchorIndex.java"), paths);
    }
    @Test void configuredInventoryIncludesEveryActiveGuideAndKeepsRetentionSeparate() throws Exception {
        Path project = GovernanceProject.root();
        var policy = new ObjectMapper().readTree(project.resolve("config/documentation-policy.json").toFile());
        var expected = new java.util.TreeSet<Path>();
        var actual = new java.util.TreeSet<Path>();
        var layout = new ObjectMapper().readTree(project.resolve("config/java-source-layout.json").toFile());
        try (var walk = Files.walk(project)) {
            for (var file : walk.filter(Files::isRegularFile).toList()) {
                if (DocumentationGovernanceSupport.isGovernedMarkdown(project, file)) actual.add(file);
                String relative = project.relativize(file).toString().replace('\\', '/');
                boolean excluded = false;
                for (var entry : layout.required("excludedTrees")) {
                    if (relative.equals(entry.asText()) || relative.startsWith(entry.asText() + "/")) excluded = true;
                }
                for (var row : policy.required("historical")) {
                    if (relative.equals(row.required("path").asText())) excluded = true;
                }
                boolean active = file.getFileName().toString().equalsIgnoreCase("README.md")
                        || relative.startsWith("docs/") && relative.endsWith(".md")
                        || relative.startsWith("lib/README.") && relative.endsWith(".md");
                if (active && !excluded) expected.add(file);
            }
        }
        assertFalse(expected.isEmpty());
        assertEquals(expected, actual, "All active guides must enter the permanent validators");
        for (var row : policy.required("historical")) {
            assertFalse(DocumentationGovernanceSupport.isGovernedMarkdown(project, project.resolve(row.required("path").asText())));
        }
        assertTrue(DocumentationGovernanceSupport.isGovernedMarkdown(project, project.resolve("lib/README.TestKit.md")));
        assertTrue(DocumentationGovernanceSupport.isGovernedMarkdown(project, project.resolve("docs/rentz/ATF_Learning.md")));
    }
}
