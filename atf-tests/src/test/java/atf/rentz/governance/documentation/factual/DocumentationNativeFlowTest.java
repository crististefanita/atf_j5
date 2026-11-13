package atf.rentz.governance.documentation.factual;

import atf.rentz.governance.source.GovernanceProject;
import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class DocumentationNativeFlowTest {
    private static final String RUNNER = "atf-tests/tools/run-shared-ui-sanity.ps1";
    private static final String RAG = "atf-tests/src/main/java/atf/rentz/distribution/evidence/NativeUiEvidence.java";
    private static final List<String> GUIDES = List.of("README.md", "atf-tests/README.md",
            "docs/rentz/ATF_Android.md", "docs/rentz/ATF_Release.md", "docs/rentz/ATF_TestingStrategy.md");
    private static final String CLAIM = "**Traseul Android de release.** 3 mâini complete, cu Rentz prima.";

    @TempDir Path fixture;

    @Test void theLiveReadmeAndGuidesMatchTheExecutableFlow() throws Exception {
        assertEquals(List.of(), DocumentationNativeFlow.inspect(GovernanceProject.root()));
    }

    @Test void reportsWrongCountWrongOpenerMissingAndDuplicateClaimsAtTheirOwner() throws Exception {
        copyExecutableSources();
        for (String guide : GUIDES) write(guide, "# Ghid\n\n" + CLAIM + "\n");
        assertEquals(List.of(), DocumentationNativeFlow.inspect(fixture));
        write("README.md", "# Ghid\n\n" + CLAIM.replace("3 mâini", "5 mâini") + "\n");
        write("atf-tests/README.md", "# Ghid\n\n" + CLAIM.replace("Rentz prima", "Whist prima") + "\n");
        write("docs/rentz/ATF_Android.md", "# Ghid\n\n```markdown\n" + CLAIM + "\n```\n");
        write("docs/rentz/ATF_Release.md", "# Ghid\n\n" + CLAIM + "\n\n" + CLAIM + "\n");
        var findings = DocumentationNativeFlow.inspect(fixture);
        assertEquals(List.of("README.md:3", "atf-tests/README.md:3", "docs/rentz/ATF_Android.md:1",
                "docs/rentz/ATF_Release.md:5"), findings.stream().map(issue ->
                issue.file().toString().replace('\\', '/') + ":" + issue.line()).toList());
    }

    @Test void executableDriftIsDetectedEvenWhenTheDocumentationStillSaysThree() throws Exception {
        copyExecutableSources();
        for (String guide : GUIDES) write(guide, "# Ghid\n\n" + CLAIM + "\n");
        String runner = Files.readString(fixture.resolve(RUNNER));
        write(RUNNER, runner.replace("$consecutiveGameCount=3", "$consecutiveGameCount=5"));
        assertTrue(DocumentationNativeFlow.inspect(fixture).stream().anyMatch(issue -> issue.code().equals("DOC-NATIVE-RAG")));
        write(RUNNER, runner.replace("$deal-eq1-and$contract-ne'RENTZ'", "$deal-eq1-and$contract-ne'WHIST'"));
        assertTrue(DocumentationNativeFlow.inspect(fixture).stream().anyMatch(issue -> issue.code().equals("DOC-NATIVE-RAG")));
    }

    private void copyExecutableSources() throws Exception {
        for (String source : List.of(RUNNER, RAG)) {
            write(source, Files.readString(GovernanceProject.root().resolve(source)));
        }
    }

    private void write(String file, String body) throws Exception {
        Path path = fixture.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, body);
    }
}
