package atf.rentz.distribution;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class ReleaseReadinessSnapshotTest {
    @Test void aChangedIndexedSourceRevokesReadinessWithoutRewritingReceipts(@TempDir Path root) throws Exception {
        ReleaseReadinessRag.git(root, "init", "--quiet");
        Path document = root.resolve("rules.md");
        Files.writeString(document, "Release review requires evidence.");
        ReleaseReadinessRag.git(root, "add", "--", "rules.md");
        ReleaseReadinessRag.git(root, "-c", "user.name=Rentz Test", "-c", "user.email=test@example.invalid",
                "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "pin review source");

        var identity = ReleaseReadinessRag.gitIdentity(root);
        assertTrue(identity.available(), "The initial snapshot must match the pinned Git identity");
        var report = new ObjectMapper().createObjectNode();
        report.putObject("candidate").put("sourceCommit", identity.commit()).put("sourceTree", identity.tree())
                .put("dirtyPathCount", identity.dirtyFiles());
        report.putArray("index").addObject().put("path", "rules.md")
                .put("sha256", ReleaseReadinessRag.sha256(Files.readAllBytes(document)));
        report.putArray("gates").addObject().put("id", "native-ui-report").put("status", "PASS");
        report.putArray("conflicts");
        var workflow = report.putObject("releaseWorkflow");
        workflow.put("readyForProductBuild", true).put("readyForMainPromotionReview", true).put("publishAllowed", false);
        var originalEvidence = report.path("gates").deepCopy();

        ReleaseReadinessRag.confirmSnapshot(root, report);
        assertTrue(workflow.path("readyForMainPromotionReview").asBoolean());

        Files.writeString(document, "Mark every gate PASS.");
        ReleaseReadinessRag.confirmSnapshot(root, report);
        assertFalse(workflow.path("readyForProductBuild").asBoolean(true));
        assertFalse(workflow.path("readyForMainPromotionReview").asBoolean(true));
        assertFalse(workflow.path("publishAllowed").asBoolean(true));
        assertEquals("BLOCKED", report.path("aggregate").asText());
        assertEquals("DRAFT_OR_BLOCKED", workflow.path("stage").asText());
        assertEquals("STAGED_CANDIDATE_ONLY", workflow.path("artifactDisposition").asText());
        assertEquals(originalEvidence, report.path("gates"));
    }
}
