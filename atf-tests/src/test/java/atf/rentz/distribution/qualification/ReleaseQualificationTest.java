package atf.rentz.distribution.qualification;

import atf.rentz.distribution.ReleaseReadinessRag.GitIdentity;
import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class ReleaseQualificationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VERSION = "0.26.0";

    @Test void searchSupportCanOnlyRemoveTheAbstentionVeto() {
        var identity = new GitIdentity("main", "a".repeat(40), "b".repeat(40), true, 0);
        var supported = decided(identity, false, "PASS");
        var abstained = decided(identity, true, "PASS");
        var invalidReport = decided(identity, false, "FAIL");

        assertTrue(supported.path("releaseWorkflow").path("readyForProductBuild").asBoolean());
        assertTrue(supported.path("releaseWorkflow").path("readyForMainPromotionReview").asBoolean());
        assertFalse(supported.path("mayPublish").asBoolean());
        assertEquals("BLOCKED", supported.path("aggregate").asText(), "Remote gates remain unrun");
        assertEquals(supported.path("gates"), abstained.path("gates"));
        assertReadinessBlocked(abstained);
        assertReadinessBlocked(invalidReport);
        assertEquals("FAIL", invalidReport.path("releaseWorkflow").path("nativeUiAcceptance").path("status").asText());
    }

    private static ObjectNode decided(GitIdentity identity, boolean abstained, String nativeExtent) {
        ObjectNode report = JSON.createObjectNode();
        var gates = report.putArray("gates");
        for (String id : List.of("offline-regression", "component-extent-report", "android-jvm", "native-ui",
                "release-build", "local-catalog")) {
            gates.addObject().put("id", id).put("status", "PASS");
        }
        gates.addObject().put("id", "native-ui-report").put("status", nativeExtent);
        for (String id : List.of("remote-publication", "authenticated-download", "anonymous-download", "release-retirement")) {
            gates.addObject().put("id", id).put("status", "NOT_RUN");
        }
        ReleaseQualification.recordDecision(report, gates, identity, VERSION, abstained);
        return report;
    }

    private static void assertReadinessBlocked(ObjectNode report) {
        var workflow = report.path("releaseWorkflow");
        assertFalse(workflow.path("readyForProductBuild").asBoolean(true));
        assertFalse(workflow.path("readyForMainPromotionReview").asBoolean(true));
        assertFalse(workflow.path("publishAllowed").asBoolean(true));
        assertEquals("BLOCKED", report.path("aggregate").asText());
    }
}
