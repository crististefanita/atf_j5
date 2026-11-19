package atf.rentz.distribution.publication;

import atf.rentz.distribution.ReleaseReadinessRag;
import atf.rentz.distribution.publication.ReleasePublication.Payload;
import atf.rentz.distribution.publication.ReleasePublication.Plan;
import atf.rentz.distribution.publication.ReleasePublication.Remote;
import atf.rentz.distribution.publication.ReleasePublication.Step;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.assertNoSymlinkPath;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.externalRun;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.readExternalFile;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256;

/** Explicit write entry point. Qualification is recomputed from original receipts before opening GitHub. */
public final class PublicationCommand {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> REQUIRED = List.of("offline-regression", "component-extent-report", "android-jvm",
            "local-catalog", "native-ui", "native-ui-report", "release-build");
    private static final int APK_LIMIT = 256 * 1024 * 1024;
    private static final Comparator<JsonNode> RECEIPT_VALUES = (recorded, live) -> {
        if (recorded.isIntegralNumber() && live.isIntegralNumber()) {
            return recorded.bigIntegerValue().compareTo(live.bigIntegerValue());
        }
        return recorded.equals(live) ? 0 : 1;
    };

    private PublicationCommand() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 9) throw new IllegalArgumentException("PUBLICATION-ARGUMENTS");
        Path root = Path.of(args[0]).toRealPath();
        Path report = Path.of(args[1]).toAbsolutePath().normalize();
        Path output = Path.of(args[8]).toAbsolutePath().normalize();
        externalRun(root, output.getParent());
        assertNoSymlinkPath(output);
        Files.createDirectory(output);
        ObjectNode result = JSON.createObjectNode().put("schemaVersion", 1).put("status", "BLOCKED")
                .put("scope", "Public same-repository GitHub publication; download verification is a separate step");
        List<Step> steps = new ArrayList<>();
        boolean published = false;
        try {
            Path components = externalRun(root, Path.of(args[2]));
            Path build = externalRun(root, Path.of(args[3]));
            Path nativeUi = externalRun(root, Path.of(args[4]));
            Path uiBuild = externalRun(root, Path.of(args[5]));
            Path checksum = Path.of(args[6]).toAbsolutePath().normalize();
            externalRun(root, checksum.getParent());
            Path journal = Path.of(args[7]).toAbsolutePath().normalize();
            externalRun(root, journal.getParent());
            require(!journal.equals(checksum) && !journal.startsWith(output.getParent())
                    && !journal.startsWith(components) && !journal.startsWith(build)
                    && !journal.startsWith(nativeUi) && !journal.startsWith(uiBuild), "JOURNAL-BOUNDARY");
            externalRun(root, report.getParent());
            byte[] reportBytes = readExternalFile(report, 4 * 1024 * 1024);
            result.put("qualificationSha256", sha256(reportBytes));
            JsonNode original = JSON.readTree(reportBytes);
            var current = ReleaseReadinessRag.evaluate(root, "release publication", components, build, nativeUi, uiBuild);
            Plan plan = qualifiedPlan(original, current, build, checksum);
            result.set("identity", identity(plan));
            var git = ReleaseReadinessRag.gitIdentity(root);
            require(git.available() && git.dirtyFiles() == 0 && plan.commit().equals(git.commit())
                    && git.tree().equals(current.path("candidate").path("sourceTree").asText()), "WORKTREE-DRIFT");
            String token = System.getenv("GH_TOKEN");
            if (token == null || token.isBlank()) token = System.getenv("GITHUB_TOKEN");
            var remote = new GitHubPublication(root, token);
            long releaseId = resumeWithJournal(plan, remote, journal, steps::add);
            result.put("releaseId", releaseId);
            PublicDistributionAudit.verify(plan, remote, releaseId, token, output.resolve("distribution"));
            steps.add(new Step("Independent downloads", "Authenticated and anonymous APK + checksum; SHA-256 and ZIP CRC verified"));
            result.put("releaseId", releaseId).put("status", "PASS");
            published = true;
        } catch (IOException | RuntimeException failure) {
            String message = failure.getMessage();
            result.put("diagnostic", message != null && message.matches("(PUBLICATION|RAG)-[A-Z0-9-]+")
                    ? message : "PUBLICATION-RECONCILIATION-INTERRUPTED");
        }

        result.set("steps", JSON.valueToTree(steps));
        Path receipt = output.resolve("publication-resume.json");
        Files.write(receipt, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(result), StandardOpenOption.CREATE_NEW);
        publishExtent(output.resolve("publication-resume-extent.html"), result, steps, published);
        if (!published) throw new IOException(result.required("diagnostic").asText());
    }

    static Plan qualifiedPlan(JsonNode original, JsonNode current, Path build, Path checksum) throws IOException {
        JsonNode candidate = current.path("candidate");
        JsonNode workflow = current.path("releaseWorkflow");
        require(original.path("candidate").equals(candidate) && original.path("releaseWorkflow").equals(workflow)
                && original.path("conflicts").isEmpty() && current.path("conflicts").isEmpty()
                && workflow.path("readyForMainPromotionReview").isBoolean()
                && workflow.path("readyForMainPromotionReview").booleanValue()
                && "CLEAN".equals(candidate.path("worktreeState").asText()), "QUALIFICATION");
        for (String id : REQUIRED) {
            JsonNode gate = gate(current, id);
            require(gate.equals(RECEIPT_VALUES, gate(original, id))
                    && "PASS".equals(gate.path("status").asText()), "QUALIFICATION-GATE");
        }
        String repository = candidate.path("repository").asText();
        String version = candidate.path("candidateVersion").asText();
        String commit = candidate.path("sourceCommit").asText();
        require(repository.matches("[A-Za-z0-9-]+/rentz") && commit.matches("[a-f0-9]{40}")
                && version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
                && !version.equals(candidate.path("currentReleaseVersion").asText()), "CANDIDATE");
        JsonNode qualified = gate(current, "release-build");
        byte[] buildBytes = readExternalFile(build.resolve("build-qualification.json"), 4 * 1024 * 1024);
        require(sha256(buildBytes).equals(qualified.path("receiptSha256").asText()), "BUILD-RECEIPT");
        Path apk = Path.of(JSON.readTree(buildBytes).path("productApk").asText()).toAbsolutePath().normalize();
        String name = "RentzLab-" + version + ".apk";
        require(apk.startsWith(build) && apk.getFileName().toString().equals(name)
                && name.equals(qualified.path("artifact").asText())
                && checksum.getFileName().toString().equals(name + ".sha256"), "ARTIFACT-NAME");
        Payload product = new Payload(name, apk, qualified.path("artifactBytes").asLong(), qualified.path("artifactSha256").asText());
        payloadBytes(product);
        byte[] checksumBytes = readExternalFile(checksum, 512);
        require(new String(checksumBytes, StandardCharsets.US_ASCII).strip().equals(product.sha256() + "  " + name), "CHECKSUM");
        return new Plan(repository, repository, version, commit, product,
                new Payload(name + ".sha256", checksum, checksumBytes.length, sha256(checksumBytes)));
    }

    static byte[] payloadBytes(Payload payload) throws IOException {
        require(payload.bytes() > 0 && payload.bytes() <= APK_LIMIT && payload.sha256().matches("[a-f0-9]{64}"), "PAYLOAD");
        byte[] bytes = readExternalFile(payload.file(), payload.bytes());
        require(bytes.length == payload.bytes() && sha256(bytes).equals(payload.sha256()), "PAYLOAD-DRIFT");
        return bytes;
    }

    static long resumeWithJournal(Plan plan, Remote remote, Path journal, Consumer<Step> trace) throws IOException {
        Path lockFile = journal.resolveSibling(journal.getFileName() + ".lock");
        assertNoSymlinkPath(journal);
        assertNoSymlinkPath(lockFile);
        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             var lock = channel.tryLock()) {
            require(lock != null, "JOURNAL-IN-USE");
            ObjectNode expected = identity(plan);
            long knownId = 0;
            if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
                JsonNode state = JSON.readTree(readExternalFile(journal, 8192));
                require(expected.equals(state.path("identity")) && state.path("releaseId").isIntegralNumber()
                        && state.path("releaseId").canConvertToLong() && state.path("releaseId").longValue() >= 0, "JOURNAL-IDENTITY");
                knownId = state.path("releaseId").longValue();
            } else {
                save(journal, expected, 0);
            }
            return ReleasePublication.resume(plan, remote, knownId, id -> save(journal, expected, id), trace);
        } catch (OverlappingFileLockException locked) {
            throw new IOException("PUBLICATION-JOURNAL-IN-USE");
        }
    }

    private static void save(Path journal, ObjectNode identity, long id) throws IOException {
        ObjectNode state = JSON.createObjectNode().put("schemaVersion", 1).put("releaseId", id);
        state.set("identity", identity);
        Path partial = Files.createTempFile(journal.getParent(), ".rentz-publication-", ".tmp");
        try {
            Files.write(partial, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(state), StandardOpenOption.SYNC);
            Files.move(partial, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private static ObjectNode identity(Plan plan) {
        return JSON.createObjectNode().put("sourceRepository", plan.sourceRepository())
                .put("releaseRepository", plan.releaseRepository()).put("tag", plan.tag()).put("commit", plan.commit())
                .put("apkSha256", plan.apk().sha256()).put("checksumSha256", plan.checksum().sha256());
    }

    private static JsonNode gate(JsonNode report, String id) throws IOException {
        JsonNode found = null;
        for (JsonNode row : report.path("gates")) {
            if (!id.equals(row.path("id").asText())) continue;
            require(found == null, "QUALIFICATION-GATE");
            found = row;
        }
        require(found != null, "QUALIFICATION-GATE");
        return found;
    }

    private static void publishExtent(Path output, JsonNode result, List<Step> steps, boolean passed) {
        AtfReportStatus status = passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        try (var session = new ExtentReportSession(output, new ReportingSettings(240, ReportingSettings.DISABLE_BODY_DISPLAY_LIMIT));
             var scenario = session.startScenario("Rentz · GitHub publication", "Reconciliation of GitHub side effects", List.of("release", "rag"));
             var phase = scenario.startPhase("Reconciled publication");
             var comparison = phase.startAssertionStep("Remote verdict")) {
            comparison.assertions(List.of(new AtfAssertion("Published exact release", "PASS", result.path("status").asText(), passed)));
            var fields = new ArrayList<ReportField>();
            fields.add(new ReportField("Qualification SHA-256", result.path("qualificationSha256").asText("-")));
            fields.add(new ReportField("Diagnostic", result.path("diagnostic").asText("-")));
            for (Step step : steps) fields.add(new ReportField(step.name(), step.actual()));
            phase.observation(new ReportObservation("Remote observations and attempted operations", fields, List.of(), List.of()));
            comparison.complete(status);
            phase.complete(status);
            scenario.complete(status);
        }
    }

    private static void require(boolean condition, String code) throws IOException {
        if (!condition) throw new IOException("PUBLICATION-" + code);
    }
}
