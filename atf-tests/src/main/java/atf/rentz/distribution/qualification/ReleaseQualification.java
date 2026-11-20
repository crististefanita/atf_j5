package atf.rentz.distribution.qualification;

import atf.rentz.distribution.AtfReportingLibrary;
import atf.rentz.distribution.ReleaseCatalog;
import atf.rentz.distribution.ReleasePaths;
import atf.rentz.distribution.ReleaseReadinessRag.GitIdentity;
import atf.rentz.distribution.evidence.AndroidSourceEvidence;
import atf.rentz.distribution.evidence.ComponentSuiteEvidence;
import atf.rentz.distribution.evidence.NativeUiEvidence;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static atf.rentz.distribution.ReleasePaths.readMetadata;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.assertNoSymlinkPath;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.exactCount;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.externalRun;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.readExternalFile;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.safeFailure;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256;

/** Release authority over source identity and verified receipts. */
public final class ReleaseQualification {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String INCOMPLETE_ACTION =
            "Complete each missing deterministic gate on this exact snapshot; keep the APK and reports in the registered external candidate workspace.";

    public record EvidenceRuns(Path component, Path productBuild, Path nativeUi, Path uiBuild) { }

    private ReleaseQualification() { }

    public static void evaluate(Path root, GitIdentity git, String version, EvidenceRuns runs,
                                boolean abstained, ObjectNode report) {
        ArrayNode gates = report.putArray("gates");
        for (String id : List.of("offline-regression", "component-extent-report", "android-jvm", "native-ui", "native-ui-report", "release-build", "local-catalog",
                "remote-publication", "authenticated-download", "anonymous-download", "release-retirement")) {
            ObjectNode gate = gates.addObject(); gate.put("id", id); gate.put("status", "NOT_RUN");
            gate.put("expected", "The designated receipt must identify the same candidate.");
            gate.put("actual", "RAG is read-only and received no verifiable receipt for this gate.");
            gate.put("receipt", "");
        }
        gate(gates, "release-build").put("expected", "The signed product APK has the numeric candidate version and exact filename RentzLab-<version>.apk.");
        gate(gates, "release-retirement").put("expected", "The latest qualified release must download from the pushed README with an authorized account before prior releases are retired. Preserve source tags and audit evidence.");
        attachLocalCatalog(root, gates);
        if (runs.component() != null) attachComponentReceipt(root, git, gates, runs.component(), "regression");
        if (runs.productBuild() != null) attachReleaseBuildReceipt(root, git, gates, runs.component(), runs.productBuild());
        if (runs.nativeUi() != null) NativeUiEvidence.attach(root, git.commit(), version, gate(gates, "native-ui"),
                gate(gates, "native-ui-report"), runs.component(), runs.uiBuild(), runs.nativeUi());

        recordDecision(report, gates, git, version, abstained);
    }

    static void recordDecision(ObjectNode report, ArrayNode gates, GitIdentity git, String version, boolean abstained) {
        ArrayNode conflicts = report.putArray("conflicts");
        if (!git.available()) conflicts.add("Git identity unavailable; candidate source cannot be pinned.");
        if (git.dirtyFiles() > 0) conflicts.add("Worktree contains uncommitted or untracked changes; it is not a release snapshot.");
        if (abstained) conflicts.add("No allowlisted evidence supports the question.");

        ArrayNode next = report.putArray("nextActions");
        next.add("Run the remaining deterministic gates for one clean, pinned source snapshot.");
        next.add("Supply original build, native UI, catalog, publication and channel-specific download receipts before changing those gates from NOT_RUN.");
        next.add("Review remote publication and authenticated and anonymous downloads as separate evidence.");
        next.add("Retire previous releases only after the pushed README verifies the authenticated APK of the qualified latest release. Preserve source tags and audit evidence.");
        report.put("responsible", "release operator");
        ObjectNode workflow = report.putObject("releaseWorkflow");
        workflow.put("candidateVersion", version);
        workflow.put("sourceCommit", git.commit());
        workflow.put("sourceTree", git.tree());
        workflow.put("candidateWorktree", git.available() && git.dirtyFiles() == 0 ? "CLEAN_PINNED_SNAPSHOT" : "NOT_QUALIFIED");
        boolean localTestsPass = status(gates, "offline-regression").equals("PASS")
                && status(gates, "component-extent-report").equals("PASS") && status(gates, "android-jvm").equals("PASS");
        boolean readyForProductBuild = localTestsPass && status(gates, "local-catalog").equals("PASS")
                && status(gates, "native-ui").equals("PASS") && status(gates, "native-ui-report").equals("PASS")
                && conflicts.isEmpty();
        boolean candidateReady = readyForProductBuild && status(gates, "release-build").equals("PASS");
        workflow.put("readyForProductBuild", readyForProductBuild);
        workflow.put("stage", candidateReady ? "READY_FOR_MAIN_PROMOTION_REVIEW" : localTestsPass ? "COMPONENTS_QUALIFIED" : "DRAFT_OR_BLOCKED");
        workflow.put("readyForMainPromotionReview", candidateReady);
        workflow.put("nativeUiRequiredForPromotion", true);
        ObjectNode nativeAcceptance = workflow.putObject("nativeUiAcceptance");
        String nativeUiStatus = status(gates, "native-ui");
        String nativeReportStatus = status(gates, "native-ui-report");
        nativeAcceptance.put("status", nativeUiStatus.equals("FAIL") || nativeReportStatus.equals("FAIL") ? "FAIL"
                : nativeUiStatus.equals("PASS") && nativeReportStatus.equals("PASS") ? "PASS" : "BLOCKED");
        nativeAcceptance.put("requiredCompleteGames", NativeUiEvidence.REQUIRED_COMPLETE_GAMES);
        nativeAcceptance.put("requiredDistinctContracts", NativeUiEvidence.REQUIRED_DISTINCT_CONTRACTS);
        nativeAcceptance.put("requiredSequence", "a complete Rentz opener followed by two other complete games in the same match");
        nativeAcceptance.put("requiredDisplayEvidence", "720x1280 logical viewport, density 360, font scale 1.5, stable four-seat geometry and inline native screenshots");
        nativeAcceptance.put("receiptGate", "native-ui + native-ui-report");
        workflow.put("artifactDisposition", candidateReady ? "ELIGIBLE_FOR_PROMOTION_REVIEW" : "STAGED_CANDIDATE_ONLY");
        workflow.put("publishAllowed", false);
        workflow.put("artifactSha256", gate(gates, "release-build").path("artifactSha256").asText(""));
        ObjectNode retirement = workflow.putObject("retirement");
        retirement.put("allowed", false);
        retirement.put("requiredReceipt", "readme-publication.retirement.allowed");
        retirement.put("preserveSourceTags", true);
        retirement.put("preserveAuditEvidence", true);
        workflow.put("nextAction", candidateReady
                ? "Review and promote this exact commit to the remote default branch; only then prepare the published catalog and verify both download channels."
                : INCOMPLETE_ACTION);
        boolean allGatesPass = true;
        for (JsonNode gate : gates) if (!gate.path("status").asText().equals("PASS")) allGatesPass = false;
        report.put("aggregate", allGatesPass && git.available() && git.dirtyFiles() == 0 && !abstained
                ? "READY_FOR_HUMAN_REVIEW" : "BLOCKED");
        report.put("mayPublish", false);
    }

    public static void blockSourceDrift(ObjectNode report) {
        report.withArray("conflicts").add("An indexed source changed while the report was being written.");
        report.put("aggregate", "BLOCKED");
        report.put("mayPublish", false);
        ObjectNode workflow = (ObjectNode) report.required("releaseWorkflow");
        workflow.put("candidateWorktree", "NOT_QUALIFIED");
        workflow.put("stage", "DRAFT_OR_BLOCKED");
        workflow.put("readyForProductBuild", false);
        workflow.put("readyForMainPromotionReview", false);
        workflow.put("artifactDisposition", "STAGED_CANDIDATE_ONLY");
        workflow.put("publishAllowed", false);
        workflow.put("nextAction", INCOMPLETE_ACTION);
    }

    private static String status(ArrayNode gates, String id) { return gate(gates, id).path("status").asText("NOT_RUN"); }

    private static void attachLocalCatalog(Path root, ArrayNode gates) {
        ObjectNode catalog = gate(gates, "local-catalog");
        catalog.put("expected", "The published edition and its APK/checksum verify locally; the next minor or major candidate has a higher Android versionCode.");
        try {
            ReleaseCatalog.Edition edition = ReleaseCatalog.verify(root, false);
            if (!edition.state().equals("PUBLISHED") || edition.target() == null)
                throw new IOException("RELEASE-CURRENT-NOT-PUBLISHED");
            JsonNode index = readMetadata(root, "release/index.json", 65_536);
            String version = index.path("currentVersion").asText();
            Path manifestPath = root.resolve("release").resolve(version).resolve("release-validation.json");
            byte[] indexBytes = Files.readAllBytes(root.resolve("release/index.json"));
            byte[] manifestBytes = Files.readAllBytes(manifestPath);
            catalog.put("status", "PASS");
            String pending = index.path("candidate").path("version").asText();
            catalog.put("actual", "Current " + version + " is PUBLISHED and locally verified."
                    + (pending.isEmpty() ? "" : " Candidate " + pending + " passes the version and versionCode policy."));
            catalog.put("receipt", "release/index.json + release/" + version + "/release-validation.json");
            catalog.put("indexSha256", sha256(indexBytes));
            catalog.put("manifestSha256", sha256(manifestBytes));
            catalog.put("artifactSha256", edition.target().sha256());
            catalog.put("artifactBytes", edition.target().bytes());
        } catch (Exception invalid) {
            catalog.put("status", "BLOCKED");
            String message = invalid.getMessage();
            catalog.put("actual", message != null && message.matches("RELEASE-[A-Z0-9-]+")
                    ? message : "RAG-LOCAL-CATALOG-INVALID");
        }
    }

    public static ArrayNode offlineEvidence(Path root, GitIdentity git, Path run, String collection) {
        ArrayNode gates = JSON.createArrayNode();
        for (String id : List.of("offline-regression", "component-extent-report", "android-jvm")) {
            gates.addObject().put("id", id).put("status", "NOT_RUN");
        }
        attachComponentReceipt(root, git, gates, run, collection);
        return gates;
    }

    private static void attachComponentReceipt(Path root, GitIdentity git, ArrayNode gates, Path suppliedRun, String collection) {
        boolean regressionCollection = collection.equals("regression");
        String identityFailure = "";
        String verifiedReceiptHash = "";
        Path run = null;
        JsonNode result = null;
        long componentExtentBytes = 0;
        try {
            assertNoSymlinkPath(suppliedRun.toAbsolutePath().normalize());
            run = suppliedRun.toRealPath();
            if (run.startsWith(root) || !Files.isDirectory(run, LinkOption.NOFOLLOW_LINKS)) throw new IOException("RAG-RECEIPT-BOUNDARY");
            Path receipt = run.resolve("result.json");
            Path manifest = run.resolve("source-identity.json");
            assertNoSymlinkPath(receipt); assertNoSymlinkPath(manifest);
            if (Files.isSymbolicLink(receipt) || Files.isSymbolicLink(manifest) || !Files.isRegularFile(receipt) || !Files.isRegularFile(manifest)
                    || Files.size(receipt) > 4L * 1024 * 1024 || Files.size(manifest) > 4L * 1024 * 1024)
                throw new IOException("RAG-RECEIPT-MISSING");
            byte[] receiptBytes = Files.readAllBytes(receipt);
            byte[] manifestBytes = Files.readAllBytes(manifest);
            if (receiptBytes.length > 4L * 1024 * 1024 || manifestBytes.length > 4L * 1024 * 1024)
                throw new IOException("RAG-RECEIPT-LIMIT");
            verifiedReceiptHash = sha256(receiptBytes);
            result = JSON.readTree(receiptBytes);
            String source = result.path("source").asText();
            if (!Path.of(source).toRealPath().equals(root)) throw new IOException("RAG-RECEIPT-SOURCE");
            if (!result.path("collection").path("name").asText().equals(collection)) throw new IOException("RAG-RECEIPT-COLLECTION");
            if (!Set.of("PASS", "FAIL").contains(result.path("status").asText())) throw new IOException("RAG-RECEIPT-STATUS");
            if (!result.path("sourceManifestSha256").asText().equalsIgnoreCase(sha256(manifestBytes)))
                throw new IOException("RAG-RECEIPT-MANIFEST");
            if (!result.path("sourceCommit").asText().equals(git.commit()))
                throw new IOException("RAG-RECEIPT-COMMIT");
            if (!result.path("drift").isArray() || !result.path("populationChanges").isArray()
                    || result.path("drift").size() != 0 || result.path("populationChanges").size() != 0)
                throw new IOException("RAG-RECEIPT-DRIFT");
            if (!result.path("results").isArray() || result.path("results").isEmpty()
                    || (regressionCollection && result.path("status").asText().equals("PASS")
                    && (!result.path("androidJvm").isTextual() || result.path("androidJvm").asText().isBlank())))
                throw new IOException("RAG-RECEIPT-POPULATION");
            JsonNode sourceRows = JSON.readTree(manifestBytes);
            componentExtentBytes = validateComponentExtent(root, run, result);
            if (!sourceRows.isArray() || sourceRows.isEmpty()) throw new IOException("RAG-RECEIPT-MANIFEST-EMPTY");
            AtfReportingLibrary reporting = AtfReportingLibrary.resolve(root);
            String reportingHash = reporting.sha256();
            String reportingPath = root.relativize(reporting.jar()).toString().replace('\\', '/');
            boolean reportingPinned = false;
            for (JsonNode row : sourceRows) if (row.path("path").asText().equals(reportingPath)
                    && row.path("sha256").asText().equalsIgnoreCase(reportingHash)) reportingPinned = true;
            if (!reportingPinned || !result.path("componentExtentReportingLibrary").asText().equals(reporting.artifactName())
                    || !result.path("componentExtentReportingLibrarySha256").asText().equalsIgnoreCase(reportingHash))
                throw new IOException("RAG-COMPONENT-REPORTING-LIBRARY");
            for (JsonNode row : sourceRows) {
                String relative = ReleasePaths.relative(row.path("path").asText());
                Path file = root.resolve(relative).normalize();
                assertNoSymlinkPath(file);
                if (!file.startsWith(root) || Files.isSymbolicLink(file) || !Files.isRegularFile(file)
                        || !row.path("sha256").asText().matches("(?i)[a-f0-9]{64}")
                        || !sha256(Files.readAllBytes(file)).equalsIgnoreCase(row.path("sha256").asText()))
                    throw new IOException("RAG-RECEIPT-SOURCE-DRIFT");
            }
        } catch (Exception invalid) { identityFailure = safeFailure(invalid, "RAG-RECEIPT-INVALID"); }

        ObjectNode regression = gate(gates, "offline-regression");
        ObjectNode extentGate = gate(gates, "component-extent-report");
        ObjectNode androidGate = gate(gates, "android-jvm");
        if (!identityFailure.isBlank()) {
            regression.put("status", "BLOCKED"); regression.put("actual", identityFailure);
            extentGate.put("status", "BLOCKED"); extentGate.put("actual", identityFailure);
            androidGate.put("status", "BLOCKED"); androidGate.put("actual", "The parent regression receipt identity is invalid.");
            return;
        }
        Path receipt = run.resolve("result.json");
        if (verifiedReceiptHash.isBlank()) { regression.put("status", "BLOCKED"); regression.put("actual", "RAG-RECEIPT-INVALID"); return; }
        regression.put("receipt", "result.json"); regression.put("receiptSha256", verifiedReceiptHash);
        extentGate.put("status", "PASS"); extentGate.put("actual", "Every component suite matches the execution receipt, original Surefire cases and source-bound Extent.");
        extentGate.put("receipt", "component-suite-extent.html");
        extentGate.put("receiptSha256", result.path("componentExtentReportSha256").asText().toLowerCase(Locale.ROOT));
        extentGate.put("artifactBytes", componentExtentBytes);
        extentGate.put("artifactSourceCommit", git.commit());
        extentGate.put("suiteExecutionReceipt", "component-suite-input.json");
        extentGate.put("suiteExecutionReceiptSha256", result.path("componentSuiteInputSha256").asText());
        extentGate.put("sourceManifestSha256", result.path("sourceManifestSha256").asText());
        JsonNode maven = result.path("results"); boolean hasSuites = maven.isArray() && !maven.isEmpty();
        boolean mavenPass = hasSuites && result.path("status").asText().equals("PASS")
                && result.path("componentExtentQualified").asBoolean(false) && result.path("runtimeQualificationFailure").asText().isBlank()
                && result.path("selectedPopulationFailure").asText().isBlank();
        boolean mavenFail = result.path("status").asText().equals("FAIL") || !result.path("componentExtentQualified").asBoolean(false)
                || !result.path("runtimeQualificationFailure").asText().isBlank()
                || !result.path("selectedPopulationFailure").asText().isBlank();
        try {
            for (JsonNode suite : maven) {
                int tests = exactCount(suite.path("tests")), failures = failureCount(suite);
                int errors = exactCount(suite.path("errors")), skipped = exactCount(suite.path("skipped"));
                boolean passed = tests > 0 && failures == 0 && errors == 0 && skipped == 0;
                mavenPass &= passed;
                mavenFail |= failures > 0 || errors > 0 || skipped > 0 || tests == 0;
            }
        } catch (IOException invalidCount) {
            regression.put("status", "BLOCKED"); regression.put("actual", "RAG-RECEIPT-COUNT");
            androidGate.put("status", "BLOCKED"); androidGate.put("actual", "The parent regression receipt contains a non-integral or out-of-range test count.");
            return;
        }
        regression.put("status", mavenPass ? "PASS" : mavenFail ? "FAIL" : "NOT_RUN");
        regression.put("actual", mavenPass ? "Surefire regression receipts are non-empty and have zero failures, errors and skips."
                : mavenFail ? "A Surefire regression receipt reports a failure, error, skip or zero-test suite."
                : "No complete Surefire regression population is present.");
        if (!regressionCollection) return;
        if (!result.path("androidJvm").isTextual() || result.path("androidJvm").asText().isBlank()) {
            androidGate.put("status", "NOT_RUN"); androidGate.put("actual", "The Android JVM stage did not produce a receipt.");
            return;
        }

        try {
            Path suppliedAndroid = Path.of(result.path("androidJvm").asText()).toAbsolutePath().normalize();
            assertNoSymlinkPath(suppliedAndroid);
            Path androidRun = Path.of(result.path("androidJvm").asText()).toRealPath();
            if (!androidRun.startsWith(run)) throw new IOException("RAG-ANDROID-RECEIPT-BOUNDARY");
            assertNoSymlinkPath(androidRun);
            Path androidFile = androidRun.resolve("result.json");
            assertNoSymlinkPath(androidFile);
            if (Files.isSymbolicLink(androidFile) || !Files.isRegularFile(androidFile) || Files.size(androidFile) > 1_048_576)
                throw new IOException("RAG-ANDROID-RECEIPT-MISSING");
            byte[] androidBytes = Files.readAllBytes(androidFile);
            if (androidBytes.length > 1_048_576) throw new IOException("RAG-ANDROID-RECEIPT-LIMIT");
            JsonNode mobileReceipt = JSON.readTree(androidBytes);
            boolean hasAndroidSuites = mobileReceipt.path("tests").isArray() && !mobileReceipt.path("tests").isEmpty();
            boolean androidPass = hasAndroidSuites, androidFail = mobileReceipt.path("status").asText().equals("FAIL");
            Path expectedStage = Path.of(result.path("stage").asText()).toRealPath();
            if (!Path.of(mobileReceipt.path("source").asText()).toRealPath().equals(expectedStage)) throw new IOException("RAG-ANDROID-SOURCE");
            boolean stageIsRunLocal = expectedStage.startsWith(run);
            if ((!stageIsRunLocal && !registeredComponentsWorkspace(root, expectedStage)) || exactCount(mobileReceipt.path("exitCode")) != 0
                    || !mobileReceipt.path("drift").isArray() || !mobileReceipt.path("populationChanges").isArray()
                    || mobileReceipt.path("drift").size() != 0 || mobileReceipt.path("populationChanges").size() != 0)
                throw new IOException("RAG-ANDROID-QUALIFICATION");
            for (JsonNode suite : mobileReceipt.path("tests")) {
                int tests = exactCount(suite.path("tests")), failures = failureCount(suite);
                int errors = exactCount(suite.path("errors")), skipped = exactCount(suite.path("skipped"));
                boolean passed = tests > 0 && failures == 0 && errors == 0 && skipped == 0;
                androidPass &= passed;
                androidFail |= failures > 0 || errors > 0 || skipped > 0 || tests == 0;
            }
            androidFail |= exactCount(mobileReceipt.path("exitCode")) != 0;
            androidPass &= mobileReceipt.path("status").asText().equals("PASS");
            androidGate.put("status", androidPass ? "PASS" : androidFail ? "FAIL" : "NOT_RUN");
            androidGate.put("actual", androidPass ? "Android JVM receipts are non-empty and have zero failures, errors and skips."
                    : androidFail ? "An Android JVM receipt reports a failure, error, skip or zero-test suite."
                    : "No complete Android JVM receipt is present.");
            androidGate.put("receipt", "android-jvm/result.json");
            androidGate.put("receiptSha256", sha256(androidBytes));
        } catch (Exception invalid) {
            androidGate.put("status", "BLOCKED");
            androidGate.put("actual", safeFailure(invalid, "RAG-ANDROID-RECEIPT-INVALID"));
        }
    }

    private static long validateComponentExtent(Path root, Path run, JsonNode receipt) throws IOException {
        if (!receipt.path("componentExtentReportFailure").asText().isBlank()) throw new IOException("RAG-COMPONENT-EXTENT-FAILED");
        if (!receipt.path("componentExtentReport").isTextual() || !receipt.path("componentExtentReportSha256").asText().matches("(?i)[a-f0-9]{64}"))
            throw new IOException("RAG-COMPONENT-EXTENT-MISSING");
        Path report = Path.of(receipt.path("componentExtentReport").asText()).toAbsolutePath().normalize();
        if (!report.equals(run.resolve("component-suite-extent.html"))) throw new IOException("RAG-COMPONENT-EXTENT-BOUNDARY");
        byte[] html = readExternalFile(report, 128L * 1024 * 1024);
        if (html.length < 1024) throw new IOException("RAG-COMPONENT-EXTENT-BOUNDARY");
        if (!sha256(html).equalsIgnoreCase(receipt.path("componentExtentReportSha256").asText()))
            throw new IOException("RAG-COMPONENT-EXTENT-HASH");
        if (!receipt.path("componentExtentQualified").isBoolean()) throw new IOException("RAG-COMPONENT-EXTENT-STATUS");
        AtfReportingLibrary reporting = AtfReportingLibrary.resolve(root);
        if (!receipt.path("componentExtentReportingLibrary").asText().equals(reporting.artifactName())
                || !receipt.path("componentExtentReportingLibrarySha256").asText().equalsIgnoreCase(reporting.sha256()))
            throw new IOException("RAG-COMPONENT-REPORTING-LIBRARY");
        ComponentSuiteEvidence.verify(run, report, receipt);
        if (!sha256(readExternalFile(report, 128L * 1024 * 1024))
                .equalsIgnoreCase(receipt.path("componentExtentReportSha256").asText()))
            throw new IOException("RAG-COMPONENT-EXTENT-HASH");
        return html.length;
    }

    private static void attachReleaseBuildReceipt(Path root, GitIdentity git, ArrayNode gates, Path componentRun, Path suppliedRun) {
        ObjectNode buildGate = gate(gates, "release-build");
        if (componentRun == null) { buildGate.put("status", "BLOCKED"); buildGate.put("actual", "A component regression receipt is required to bind the APK build."); return; }
        Path run = null; JsonNode receipt = null; String receiptHash = "";
        try {
            run = externalRun(root, suppliedRun);
            Path file = run.resolve("build-qualification.json");
            byte[] bytes = readExternalFile(file, 4L * 1024 * 1024);
            receiptHash = sha256(bytes); receipt = JSON.readTree(bytes);
            if (!receipt.isObject() || !receipt.path("sourceCommit").asText().equals(git.commit())) throw new IOException("RAG-BUILD-COMMIT");
            Path builder = root.resolve("scripts/build-rentz-android.ps1").normalize();
            assertNoSymlinkPath(builder);
            if (!builder.startsWith(root) || !Files.isRegularFile(builder, LinkOption.NOFOLLOW_LINKS)
                    || !sha256(Files.readAllBytes(builder)).equalsIgnoreCase(receipt.path("builderSha256").asText()))
                throw new IOException("RAG-BUILD-TOOL-IDENTITY");
            if (!receipt.path("componentRun").isTextual()
                    || !Path.of(receipt.path("componentRun").asText()).toRealPath().equals(componentRun.toRealPath()))
                throw new IOException("RAG-BUILD-COMPONENT");
            Path componentReceipt = componentRun.toRealPath().resolve("result.json");
            JsonNode component = JSON.readTree(readExternalFile(componentReceipt, 4L * 1024 * 1024));
            if (!component.path("status").asText().equals("PASS") || !component.path("sourceCommit").asText().equals(git.commit()))
                throw new IOException("RAG-BUILD-REGRESSION");
            String declaredStatus = receipt.path("status").asText();
            if (declaredStatus.equals("FAIL")) {
                buildGate.put("status", "FAIL"); buildGate.put("actual", "The Release build receipt records FAIL for this source snapshot.");
                buildGate.put("receipt", "build-qualification.json"); buildGate.put("receiptSha256", receiptHash); return;
            }
            if (!declaredStatus.equals("PASS") || !receipt.path("configuration").asText().equalsIgnoreCase("Release")
                    || receipt.path("productDebuggable").asBoolean(true) || !receipt.path("qualifiedCoreMatches").asBoolean(false))
                throw new IOException("RAG-BUILD-QUALIFICATION");
            if (!receipt.path("productApplicationId").asText().equals("ro.rentzlab.app")) throw new IOException("RAG-BUILD-PACKAGE");
            String version = receipt.path("productVersionName").asText();
            JsonNode releaseIndex = readMetadata(root, "release/index.json", 65_536);
            String candidateVersion = releaseIndex.path("candidate").path("version").asText(releaseIndex.path("currentVersion").asText());
            if (!ReleasePaths.isReleaseVersion(version) || !version.equals(candidateVersion))
                throw new IOException("RAG-BUILD-VERSION");
            AndroidSourceEvidence.verify(run, componentRun, receipt);
            Path apk = Path.of(receipt.path("productApk").asText()).toAbsolutePath().normalize();
            if (!apk.startsWith(run) || !Files.isRegularFile(apk, LinkOption.NOFOLLOW_LINKS)
                    || !apk.getFileName().toString().equals("RentzLab-" + version + ".apk")) throw new IOException("RAG-BUILD-APK-BOUNDARY");
            byte[] apkBytes = readExternalFile(apk, 256L * 1024 * 1024);
            String apkHash = sha256(apkBytes);
            if (!apkHash.equalsIgnoreCase(receipt.path("productApkSha256").asText())
                    || exactCount(receipt.path("productApkBytes")) != apkBytes.length) throw new IOException("RAG-BUILD-APK-HASH");
            if (!receipt.path("tests").isArray() || receipt.path("tests").isEmpty()) throw new IOException("RAG-BUILD-TESTS");
            for (JsonNode test : receipt.path("tests")) {
                if (exactCount(test.path("tests")) < 1 || failureCount(test) != 0 || exactCount(test.path("errors")) != 0
                        || exactCount(test.path("skipped")) != 0) throw new IOException("RAG-BUILD-TESTS");
            }
            if (!receipt.path("productCertificate").isArray() || receipt.path("productCertificate").isEmpty()
                    || !receipt.path("productCertificate").toString().matches("(?is).*certificate SHA-256 digest: ?[a-f0-9]{64}.*"))
                throw new IOException("RAG-BUILD-CERTIFICATE");
            buildGate.put("status", "PASS"); buildGate.put("actual", "The signed Release APK, package, version, certificate, test rows and source receipt match the candidate.");
            buildGate.put("receipt", "build-qualification.json"); buildGate.put("receiptSha256", receiptHash);
            buildGate.put("artifact", apk.getFileName().toString()); buildGate.put("artifactSha256", apkHash);
            buildGate.put("artifactBytes", apkBytes.length); buildGate.put("version", version);
        } catch (Exception invalid) {
            buildGate.put("status", "BLOCKED"); buildGate.put("actual", safeFailure(invalid, "RAG-BUILD-RECEIPT-INVALID"));
            if (!receiptHash.isBlank()) { buildGate.put("receipt", "build-qualification.json"); buildGate.put("receiptSha256", receiptHash); }
        }
    }

    private static boolean registeredComponentsWorkspace(Path root, Path stage) throws IOException {
        String configured = System.getProperty("rentz.data.root");
        if (configured == null || configured.isBlank()) configured = System.getenv("RENTZ_DATA_ROOT");
        if (configured == null || configured.isBlank()) return false;
        Path dataRoot = Path.of(configured).toAbsolutePath().normalize();
        assertNoSymlinkPath(dataRoot);
        Path workspaceRoot = dataRoot.resolve("infrastructure/workspaces");
        assertNoSymlinkPath(workspaceRoot);
        if (!Files.isDirectory(workspaceRoot, LinkOption.NOFOLLOW_LINKS)) return false;
        Path realWorkspaceRoot = workspaceRoot.toRealPath();
        Path realStage = stage.toRealPath();
        if (!realStage.getParent().equals(realWorkspaceRoot)) return false;

        JsonNode policy = readMetadata(root, "config/infrastructure-workspaces.json", 65_536);
        Path descriptor = realStage.resolve(".atf-workspace.json");
        byte[] bytes = readExternalFile(descriptor, 65_536);
        JsonNode workspace = JSON.readTree(bytes);
        String id = workspace.path("id").asText();
        return workspace.path("schemaVersion").asInt(-1) == 1
                && workspace.path("owner").asText().equals(policy.path("owner").asText())
                && workspace.path("kind").asText().equals("components")
                && workspace.path("sourceRoot").asText().equals(root.toRealPath().toString())
                && id.matches("[a-fA-F0-9]{32}")
                && realStage.getFileName().toString().equals("components-" + id);
    }

    private static int failureCount(JsonNode suite) throws IOException {
        boolean canonical = suite.has("failures"), legacy = suite.has("failed");
        if (!canonical && !legacy) throw new IOException("RAG-RECEIPT-COUNT");
        int value = exactCount(canonical ? suite.path("failures") : suite.path("failed"));
        if (canonical && legacy && value != exactCount(suite.path("failed"))) throw new IOException("RAG-RECEIPT-COUNT");
        return value;
    }

    private static ObjectNode gate(ArrayNode gates, String id) {
        for (JsonNode row : gates) if (row.path("id").asText().equals(id)) return (ObjectNode) row;
        throw new IllegalStateException("RAG-GATE-REGISTRY: " + id);
    }

}
