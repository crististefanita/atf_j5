package atf.rentz.android.reporting;

import atf.rentz.EvaluationRegistry;
import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfGherkinKeyword;
import atf.reporting.contract.AtfReportPorts;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.evidence.ReportScreenshotPolicy;
import atf.reporting.evidence.profile.ReportEvidenceProfile;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import atf.reporting.ui.UiExtentReportSession;
import atf.reporting.ui.model.AtfUiActionRecord;
import atf.reporting.ui.model.AtfUiEvidenceSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Reports decoded device frames and the measured geometry of the shared Android seat component. */
public final class RentzCollectionMotionReport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RentzCollectionMotionReport() { }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("register")) {
            register(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            return;
        }
        Path root = Path.of(args[0]).toRealPath();
        try (var session = new ExtentReportSession(root.resolve("collection-extent.html"), new ReportingSettings(240, 0))) {
            seats(session, read(root.resolve(args[1]).resolve("seats.json")));
            for (int index = 2; index < args.length; index++) {
                Path directory = root.resolve(args[index]).toRealPath();
                var proof = read(directory.resolve("measurements.json"));
                playback(session, directory, proof);
                if (proof.required("mode").asText().equals("Rentz")) features(session, directory, proof);
            }
        }
    }

    private static void register(Path journal, Path ledger, Path output) throws Exception {
        String sha = hash(journal);
        String token = UUID.randomUUID().toString().replace("-", "");
        EvaluationRegistry.beginNativeExposure(ledger, token);
        EvaluationRegistry.bindNativeExposure(ledger, token, sha);
        EvaluationRegistry.completeNativeExposure(ledger, token, journal);
        if (!sha.equals(hash(journal))) throw new IllegalArgumentException("Fixture changed during exposure registration");
        JSON.writeValue(output.toFile(), JSON.createObjectNode().put("sha256", sha).put("token", token));
    }

    private static void seats(AtfReportPorts.Session session, JsonNode result) {
        var matrix = result.required("cases");
        boolean passed = matrix.size() == 36 && result.required("failures").intValue() == 0 && !result.has("error");
        try (var scenario = session.startScenario("Shared seat frame", "Android text layout at 3–6 seats and enlarged fonts", List.of("component", "android"));
             var phase = scenario.startPhase("MEASURED ANDROID VIEWS")) {
            for (var entry : matrix) {
                var assertions = new ArrayList<AtfAssertion>();
                assertions.add(new AtfAssertion("Seat frames within board and complete text on three rows", "true",
                        entry.required("passed").asText(), entry.required("passed").asBoolean()));
                for (var row : entry.required("rows"))
                    assertions.add(new AtfAssertion("Seat " + row.required("seat").asInt() + " · " + row.required("text").asText(),
                            "One line within " + row.required("availablePx").asInt() + " px",
                            row.required("usedPx").asText() + " px", row.required("intact").asBoolean()));
                AssertionAuditReport.publishStep(phase, entry.required("players").asInt() + " players · "
                        + entry.required("tableWidthDp").asInt() + " dp · font " + entry.required("fontScale").asText(),
                        assertions, AtfReportStatus.INFO);
            }
            var status = passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            phase.complete(status);
            scenario.complete(status);
        }
    }

    private static void playback(AtfReportPorts.Session session, Path root, JsonNode proof) throws Exception {
        var runs = proof.required("runs");
        if (runs.size() != 2) throw new IllegalArgumentException("Each game needs live and restored recordings");
        String game = proof.required("mode").asText();
        var raw = session.startScenario(game + " final trick", "Android API " + proof.required("api").asText()
                + " · winning seat " + proof.required("winnerName").asText(), List.of("e2e", "android", "motion"));
        try (var ui = UiExtentReportSession.adaptScenario(raw, ReportEvidenceProfile.forScreenshotPolicy(
                ReportScreenshotPolicy.EACH_CHECKPOINT), "Decoded Android video frame");
             var phase = ui.startPhase("FINAL TRICK AND COLD RESTART")) {
            boolean passed = proof.required("passed").asBoolean();
            int sequence = 0;
            for (var run : runs) {
                var video = verified(root, run, "video", "sha256");
                var trace = verified(root, run, "trace", "traceSha256");
                var assertions = assertions(run.required("checks"));
                var actions = new ArrayList<AtfUiActionRecord>();
                for (var action : run.required("actions"))
                    actions.add(new AtfUiActionRecord(++sequence, action.required("operation").asText(),
                            action.required("control").asText(), "Execute one native tap", action.required("bounds").asText(),
                            action.required("elapsedMillis").asLong(), AtfReportStatus.PASS, AtfUiEvidenceSource.ACTION));
                boolean good = !assertions.isEmpty() && assertions.stream().allMatch(AtfAssertion::passed);
                passed &= good;
                var images = run.required("frameImages");
                String hold = images.size() == 2 ? png(root, images.get(0)) : png(root, run.required("image"));
                String movement = images.size() == 2 ? png(root, images.get(1)) : hold;
                String label = run.required("name").asText().equals("live") ? "Last card played" : "Saved hand reopened after process stop";
                phase.step(AtfGherkinKeyword.WHEN, label, "Four public cards", hold, List.of(assertions.getFirst()),
                        actions, ReportScreenshotPolicy.CapturePoint.CHECKPOINT, good ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
                phase.step(AtfGherkinKeyword.THEN, "Collect towards " + proof.required("winnerName").asText(),
                        "Decoded frame trajectories", movement, assertions.subList(1, assertions.size()), List.of(),
                        ReportScreenshotPolicy.CapturePoint.CHECKPOINT, good ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
                phase.observation(new ReportObservation("Frame evidence · " + run.required("name").asText(), List.of(
                        new ReportField("APK SHA-256", proof.required("apkSha256").asText()),
                        new ReportField("Video", video.getFileName().toString()),
                        new ReportField("Video SHA-256", run.required("sha256").asText()),
                        new ReportField("Decoded frames", run.required("frameCount").asText()),
                        new ReportField("Trace", trace.getFileName().toString()),
                        new ReportField("Trace SHA-256", run.required("traceSha256").asText())),
                        List.of(new ReportObservation.Body("Measured times in milliseconds", run.required("timing").toPrettyString(),
                                ReportObservation.Format.JSON)), List.of()));
            }
            var status = passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            phase.complete(status);
            ui.complete(status);
        }
    }

    private static void features(AtfReportPorts.Session session, Path root, JsonNode proof) throws Exception {
        var raw = session.startScenario("Rentz settlement and AI hint", "Score page, table and explicit advice",
                List.of("e2e", "android", "rentz"));
        try (var ui = UiExtentReportSession.adaptScenario(raw,
                ReportEvidenceProfile.forScreenshotPolicy(ReportScreenshotPolicy.EACH_CHECKPOINT), "Android native PNG");
             var phase = ui.startPhase("SCORE AND CHOICE")) {
            var features = proof.required("features");
            boolean passed = features.size() == 4 && proof.required("nativeFailure").asText().isEmpty();
            int sequence = 0;
            for (var feature : features) {
                var assertions = assertions(feature.required("checks"));
                var actions = new ArrayList<AtfUiActionRecord>();
                for (var action : feature.required("actions"))
                    actions.add(new AtfUiActionRecord(++sequence, action.required("operation").asText(),
                            action.required("control").asText(), "Execute one native tap", action.required("bounds").asText(),
                            action.required("elapsedMillis").asLong(), AtfReportStatus.PASS, AtfUiEvidenceSource.ACTION));
                boolean good = assertions.stream().allMatch(AtfAssertion::passed);
                passed &= good;
                phase.step(AtfGherkinKeyword.THEN, feature.required("name").asText(), "Android native checkpoint",
                        png(root, feature.required("image")), assertions, actions,
                        ReportScreenshotPolicy.CapturePoint.CHECKPOINT, good ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
            }
            var status = passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            phase.complete(status);
            ui.complete(status);
        }
    }

    private static List<AtfAssertion> assertions(JsonNode checks) {
        if (!checks.isArray() || checks.isEmpty()) throw new IllegalArgumentException("Missing captured assertions");
        var assertions = new ArrayList<AtfAssertion>();
        for (var check : checks)
            assertions.add(new AtfAssertion(check.required("name").asText(), check.required("expected").asText(),
                    check.required("actual").asText(), check.required("passed").asBoolean()));
        return List.copyOf(assertions);
    }

    private static JsonNode read(Path path) throws Exception { return JSON.readTree(path.toFile()); }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static Path contained(Path root, JsonNode field) throws Exception {
        Path file = Path.of(field.asText()).toRealPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalArgumentException("Evidence escaped its capture directory");
        return file;
    }
    private static Path verified(Path root, JsonNode run, String file, String sha) throws Exception {
        Path value = contained(root, run.required(file));
        if (!hash(value).equalsIgnoreCase(run.required(sha).asText())) throw new IllegalArgumentException("Changed " + file + " bytes");
        return value;
    }
    private static String png(Path root, JsonNode field) throws Exception {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(contained(root, field)));
    }
}
