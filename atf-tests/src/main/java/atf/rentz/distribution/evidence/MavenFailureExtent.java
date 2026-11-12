package atf.rentz.distribution.evidence;

import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.nio.file.Path;
import java.util.List;

/** Presents the verified failed-run decision without altering the original component evidence. */
public final class MavenFailureExtent {
    private MavenFailureExtent() { }

    public static void publish(Path output, JsonNode report, String reportSha256) {
        JsonNode maven = gate(report, "offline-regression");
        JsonNode jvm = gate(report, "android-jvm");
        if (!maven.path("status").asText().equals("FAIL") || !jvm.path("status").asText().equals("NOT_RUN")) return;

        try (var session = new ExtentReportSession(output, new ReportingSettings(240, ReportingSettings.DISABLE_BODY_DISPLAY_LIMIT));
             var scenario = session.startScenario("Release RAG · failed offline regression",
                     "Verified Maven evidence with Android JVM absent", List.of("release", "offline", "rag"));
             var phase = scenario.startPhase("Release decision");
             var assertions = phase.startAssertionStep("Gate outcomes")) {
            assertions.assertions(List.of(
                    new AtfAssertion("Maven regression", "PASS", maven.path("status").asText(), false),
                    new AtfAssertion("Android JVM", "NOT_RUN", jvm.path("status").asText(), true),
                    comparison("Release aggregate", "BLOCKED", report.path("aggregate").asText()),
                    comparison("Publication permission", "false", report.path("mayPublish").asText())));
            phase.observation(new ReportObservation("Original execution evidence", List.of(
                    new ReportField("Source commit", report.path("candidate").path("sourceCommit").asText()),
                    new ReportField("Original Maven receipt SHA-256", maven.path("receiptSha256").asText()),
                    new ReportField("Original component Extent SHA-256", gate(report, "component-extent-report").path("receiptSha256").asText()),
                    new ReportField("Maven evidence", maven.path("actual").asText()),
                    new ReportField("Android JVM evidence", jvm.path("actual").asText()),
                    new ReportField("Release RAG JSON SHA-256", reportSha256)), List.of(), List.of()));
            assertions.complete(AtfReportStatus.FAIL);
            phase.complete(AtfReportStatus.FAIL);
            scenario.complete(AtfReportStatus.FAIL);
        }
    }

    private static AtfAssertion comparison(String label, String expected, String actual) {
        return new AtfAssertion(label, expected, actual, expected.equals(actual));
    }

    private static JsonNode gate(JsonNode report, String id) {
        for (JsonNode gate : report.path("gates")) if (gate.path("id").asText().equals(id)) return gate;
        return MissingNode.getInstance();
    }
}
