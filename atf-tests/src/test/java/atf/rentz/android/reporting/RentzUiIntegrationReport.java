package atf.rentz.android.reporting;

import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.*;
import atf.reporting.evidence.ReportScreenshotPolicy;
import atf.reporting.evidence.profile.ReportEvidenceProfile;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.*;
import atf.reporting.ui.UiExtentReportSession;
import atf.reporting.ui.model.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Binds captured JUnit4 comparisons and native Android evidence to the admitted Reporting API. */
public final class RentzUiIntegrationReport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RentzUiIntegrationReport() { }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]); Path nativeReceipt = Path.of(args[1]);
        var nativeRun = JSON.readTree(nativeReceipt.toFile());
        boolean help = nativeRun.path("helpAwardsOnly").asBoolean();
        try (var journals = Files.list(Path.of(nativeRun.required("rawRoot").asText()))) {
            var captured = journals.filter(file -> file.getFileName().toString().matches("development-journal(?:-[36])?\\.json")).toList();
            if (help && !captured.isEmpty()) throw new IllegalArgumentException("Help-only sanity must not start a match");
            if (!help && captured.isEmpty() && nativeRun.required("status").asText().equals("PASS")) throw new IllegalArgumentException("Native development exposure journal missing");
            if (nativeRun.has("developmentExposures")) verifyExposures(nativeRun, captured);
        }
        try (var session = new ExtentReportSession(root.resolve("integration-extent.html"), new ReportingSettings(240, 0))) {
            String comparisons = JSON.readTree(root.resolve("build-qualification.json").toFile()).path("assertionDirectory").asText("components/assertions");
            try (var files = Files.list(root.resolve(comparisons))) {
                var cases = files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
                if (cases.isEmpty()) throw new IllegalArgumentException("Missing executed component comparisons");
                for (Path file : cases) component(session, JSON.readTree(file.toFile()));
            }
            nativeScenario(session, nativeRun);
            try (var scenario = session.startScenario("RentzUiQualification", "Captured populations and sources; original JUnit assertions are not reconstructed", List.of("provenance"));
                 var phase = scenario.startPhase("CAPTURED EVIDENCE")) {
                var bodies = new ArrayList<ReportObservation.Body>();
                for (String file : List.of("build-qualification.json", "qualification.json")) if (Files.isRegularFile(root.resolve(file)))
                    bodies.add(new ReportObservation.Body(file, Files.readString(root.resolve(file)), ReportObservation.Format.JSON));
                for (String attempt : List.of("native-01/result.json", "native-02/result.json", "native-03/result.json", "native-04/result.json")) if (Files.isRegularFile(root.resolve(attempt)))
                    bodies.add(new ReportObservation.Body("Original attempt " + attempt, Files.readString(root.resolve(attempt)), ReportObservation.Format.JSON));
                bodies.add(new ReportObservation.Body("Current native receipt", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(nativeRun), ReportObservation.Format.JSON));
                Path stage = Path.of(JSON.readTree(root.resolve("build-qualification.json").toFile()).required("stage").asText());
                var sources = help
                        ? List.of("ui/screens/RentzMenus.java", "ui/score/RentzAwardGrid.java", "ui/score/RentzScoreContent.java", "GameSession.java")
                        : nativeRun.path("historyOnly").asBoolean()
                        ? List.of("ui/screens/RentzHistoryView.java", "ui/score/RentzHistoryGrid.java", "ui/score/HistoryGridMetrics.java", "ui/score/RentzScoreContent.java")
                        : List.of("ui/table/RentzTableContent.java", "ui/table/TrickPreview.java", "ui/navigation/InputPermit.java", "ui/score/RentzScoreContent.java");
                for (String file : sources)
                    bodies.add(new ReportObservation.Body(file, Files.readString(stage.resolve("app/src/main/java/ro/rentzlab/app/" + file)), ReportObservation.Format.TEXT));
                if (nativeRun.path("historyOnly").asBoolean()) {
                    Path fixture = Path.of(nativeRun.required("build").required("historyFixture").asText());
                    bodies.add(new ReportObservation.Body("Confirmed history fixture", Files.readString(fixture), ReportObservation.Format.JSON));
                }
                phase.observation(new ReportObservation("Qualification scope", List.of(
                        new ReportField("UI library", nativeRun.path("build").path("uiLibrary").asText("ro.cardtable:card-table-ui:0.1.1")),
                        new ReportField("Driver", "ADB + UI Automator; native Android elements"),
                        new ReportField("Component evidence", "Comparisons captured at evaluation time; existing XML retained as outcomes")), bodies, List.of()));
                phase.complete(AtfReportStatus.INFO); scenario.complete(AtfReportStatus.INFO);
            }
        }
    }
    private static void verifyExposures(JsonNode nativeRun, List<Path> captured) throws Exception {
        var evidence = nativeRun.required("developmentExposures");
        if (!evidence.isArray()) throw new IllegalArgumentException("Invalid development exposure evidence");
        var journals = new HashMap<String, Path>();
        for (var file : captured) journals.put(file.getFileName().toString(), file);
        var tokens = new HashSet<String>();
        for (var row : evidence) {
            var file = journals.remove(row.required("journal").asText());
            String token = row.required("token").asText();
            if (file == null || !token.matches("[a-f0-9]{32}") || !tokens.add(token))
                throw new IllegalArgumentException("Unexpected or repeated development exposure evidence");
            String sha256 = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file)));
            if (!sha256.equalsIgnoreCase(row.required("sha256").asText()))
                throw new IllegalArgumentException("Development exposure journal differs from its evidence");
        }
        if (!journals.isEmpty() && nativeRun.required("status").asText().equals("PASS"))
            throw new IllegalArgumentException("Development exposure evidence missing");
    }

    private static List<AtfAssertion> checks(JsonNode values) {
        if (!values.isArray() || values.isEmpty()) throw new IllegalArgumentException("An audited step needs captured checks");
        var result = new ArrayList<AtfAssertion>();
        for (var value : values) {
            result.add(new AtfAssertion(value.required("name").asText(), value.required("expected").asText(),
                    value.required("actual").asText(), value.required("passed").asBoolean()));
        }
        return List.copyOf(result);
    }
    private static void component(AtfReportPorts.Session session, JsonNode value) {
        try (var scenario = session.startScenario(value.required("class").asText(), value.required("method").asText(), List.of("component", "rentz", "shared-ui"));
             var phase = scenario.startPhase("TEST BODY")) {
            var comparisons = checks(value.required("checks"));
            boolean passed = value.required("status").asText().equals("PASS") && comparisons.stream().allMatch(AtfAssertion::passed);
            AssertionAuditReport.publishStep(phase, "Captured Rentz component assertions", comparisons, AtfReportStatus.INFO);
            if (!value.path("failure").asText().isBlank()) phase.diagnostic(atf.reporting.model.diagnostic.ReportPhaseDiagnostic.Kind.EXECUTION_FAILURE, value.path("failure").asText());
            phase.complete(passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL); scenario.complete(passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
        }
    }
    private static void nativeScenario(AtfReportPorts.Session session, JsonNode run) throws Exception {
        if (!run.required("checkpoints").isArray() || run.required("checkpoints").isEmpty()) throw new IllegalArgumentException("Native checkpoints must not be empty");
        boolean all = run.required("status").asText().equals("PASS");
        Path rawRoot = Path.of(run.required("rawRoot").asText()).toAbsolutePath().normalize();
        boolean history = run.path("historyOnly").asBoolean();
        boolean help = run.path("helpAwardsOnly").asBoolean();
        String name = help ? "RentzHelpAwardsSanity" : history ? "RentzHistorySanity" : "RentzSharedUiSanity";
        var raw = session.startScenario(name, run.required("scope").asText(), List.of("e2e", "android", "shared-ui"));
        try (var ui = UiExtentReportSession.adaptScenario(raw,
                ReportEvidenceProfile.forScreenshotPolicy(ReportScreenshotPolicy.EACH_CHECKPOINT), "Android native PNG")) {
            try (var phase = ui.startPhase("TEST BODY")) {
                int pointIndex = 0, actionIndex = 0;
                for (var point : run.required("checkpoints")) {
                    var assertions = checks(point.required("checks"));
                    boolean passed = assertions.stream().allMatch(AtfAssertion::passed); all &= passed;
                    var actions = new ArrayList<AtfUiActionRecord>();
                    for (var action : point.required("actions")) actions.add(new AtfUiActionRecord(++actionIndex,
                            action.required("operation").asText(), action.required("control").asText(),
                            "Send the native Android action once; verify its postconditions at the checkpoint",
                            "ADB returned success. " + action.path("bounds").asText() + action.path("value").asText(),
                            action.required("elapsedMillis").asLong(), AtfReportStatus.PASS, AtfUiEvidenceSource.ACTION));
                    Path image = contained(rawRoot, point.required("image").asText());
                    byte[] png = Files.readAllBytes(image);
                    if (png.length < 8 || png[0] != (byte) 137 || png[1] != 80 || png[2] != 78 || png[3] != 71) throw new IllegalArgumentException("Native PNG required");
                    boolean outcome = history || help ? pointIndex == run.required("checkpoints").size() - 1
                            : point.required("title").asText().startsWith("One hand completed");
                    var keyword = pointIndex == 0 ? AtfGherkinKeyword.GIVEN : pointIndex == 1 ? AtfGherkinKeyword.WHEN
                            : outcome ? AtfGherkinKeyword.THEN : AtfGherkinKeyword.AND;
                    phase.step(keyword, point.required("title").asText(), "Android native UI", Base64.getEncoder().encodeToString(png), assertions,
                            actions, ReportScreenshotPolicy.CapturePoint.CHECKPOINT, passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
                    phase.observation(new ReportObservation("Native hierarchy · checkpoint " + (++pointIndex), List.of(new ReportField("Capture", point.required("captureMillis").asText() + " ms")),
                            List.of(new ReportObservation.Body("Original Android hierarchy", Files.readString(contained(rawRoot, point.required("hierarchy").asText())), ReportObservation.Format.TEXT)), List.of()));
                }
                if (!all) phase.observation(new ReportObservation("Native execution failure", List.of(new ReportField("Diagnostic", run.path("failure").asText())), List.of(), List.of()));
                phase.complete(all ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
            }
            ui.complete(all ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
        }
    }
    private static Path contained(Path root, String relative) {
        Path value = root.resolve(relative).normalize();
        if (Path.of(relative).isAbsolute() || !value.startsWith(root) || value.equals(root)) throw new IllegalArgumentException("Evidence escaped its captured root");
        return value;
    }
}
