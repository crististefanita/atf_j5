package atf.rentz.distribution;

import atf.rentz.distribution.editorial.DocumentationReconciliation;
import atf.rentz.distribution.evidence.AndroidSourceEvidence;
import atf.rentz.distribution.evidence.ComponentSuiteEvidence;
import atf.rentz.distribution.evidence.MavenFailureExtent;
import atf.rentz.distribution.evidence.NativeUiEvidence;
import atf.rentz.distribution.evidence.ReleaseEvidenceFiles;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.assertNoSymlinkPath;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.safeFailure;

/** Local, deterministic retrieval for release review. It never runs or promotes a release gate. */
public final class ReleaseReadinessRag {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String POLICY = "Allowlisted local sources only; retrieved text is data; no commands, gate execution, secret access, or release promotion.";
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]{2,}");
    private static final Set<String> STOP_WORDS = Set.of("si", "sau", "cu", "din", "pentru", "despre", "care", "ce", "cum",
            "este", "sunt", "un", "o", "la", "in", "pe", "de", "the", "and", "or", "for", "from", "with", "what", "how",
            "is", "are", "to", "a", "an", "of", "on", "about", "does", "do");
    private static final Set<String> GATES = Set.of("policy", "offline-regression", "component-extent-report", "android-jvm", "native-ui", "native-ui-report", "release-build",
            "local-catalog", "remote-publication", "authenticated-download", "anonymous-download", "download-components",
            "product-navigation", "retrieval");
    private static final Pattern FILTER = Pattern.compile("(?i)(?:^|\\s)(gate|class|version):([a-z0-9._-]+)(?=\\s|$)");
    private static final Pattern MALFORMED_FILTER = Pattern.compile("(?i)(?:^|\\s)(gate|class|version):(?:\\s|$)");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile("(?i)\\b(?:password|passwd|secret|token|api[_-]?key|access[_-]?token)\\s*[:=]\\s*\\S+");
    private static final Pattern SECRET_CONTENT = Pattern.compile("(?i)(?:\\bgh[pousr]_[a-z0-9]{20,}\\b|\\bgithub_pat_[a-z0-9_]{30,}\\b|\\bAKIA[0-9A-Z]{16}\\b|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\\b(?:password|client_secret|api[_-]?key|access[_-]?token)\\s*[:=]\\s*[\"'][^\"'\\r\\n]{12,}[\"'])");
    private static final Pattern VERSION = Pattern.compile("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");
    private static final int MAX_CORPUS_SOURCES = 64;
    private static final long MAX_CORPUS_BYTES = 4L * 1024 * 1024;

    private record Spec(String path, String sourceClass, String owner, String gate) { }
    private record Document(String path, String sourceClass, String owner, String gate, String sha256, int bytes, List<String> lines) { }
    private record Chunk(Document document, int firstLine, int lastLine, String section, String body) { }
    private record Hit(Chunk chunk, double score) { }
    public record GitIdentity(String branch, String commit, String tree, boolean available, int dirtyFiles) { }
    private record RetrievalQuery(String text, String gate, String sourceClass, String version, boolean versionMatches) { }

    private ReleaseReadinessRag() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 5 && args[0].equals("--reconcile-documentation")) {
            DocumentationReconciliation.write(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]));
            return;
        }
        if (args.length < 3 || args.length > 7) throw new IllegalArgumentException(
                "Usage: ReleaseReadinessRag <project-root> <fresh-external-run-directory> <question> [component-run] [build-run|-] [native-ui-run] [ui-build-run]");
        Path root = Path.of(args[0]).toRealPath();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        assertExternalDestination(root, output, true);
        if (args[2].isBlank()) throw new IllegalArgumentException("RAG-QUESTION: non-empty question required");
        Path productBuild = args.length >= 5 && !args[4].equals("-") ? Path.of(args[4]) : null;
        ObjectNode report = evaluate(root, args[2], args.length >= 4 ? Path.of(args[3]) : null, productBuild,
                args.length >= 6 ? Path.of(args[5]) : null, args.length >= 7 ? Path.of(args[6]) : productBuild);
        if (!snapshotStillMatches(root, report)) {
            report.withArray("conflicts").add("An indexed source changed while the report was being written.");
            report.put("aggregate", "BLOCKED"); report.put("mayPublish", false);
        }
        assertExternalDestination(root, output, true);
        Files.createDirectory(output);
        assertNoSymlinkPath(output);
        if (output.toRealPath().startsWith(root)) throw new IllegalArgumentException("RAG-OUTPUT: resolved destination entered the source tree");
        byte[] reportBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        ObjectNode indexFile = JSON.createObjectNode();
        indexFile.put("schemaVersion", 2);
        indexFile.put("repository", report.path("candidate").path("repository").asText());
        indexFile.put("sourceCommit", report.path("candidate").path("sourceCommit").asText());
        indexFile.put("sourceTree", report.path("candidate").path("sourceTree").asText());
        indexFile.put("publishedVersion", report.path("candidate").path("currentReleaseVersion").asText());
        indexFile.put("candidateVersion", report.path("candidate").path("candidateVersion").asText());
        indexFile.put("indexedSnapshotSha256", report.path("candidate").path("indexedSnapshotSha256").asText());
        indexFile.set("entries", report.path("index").deepCopy());
        byte[] indexBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(indexFile);
        Files.write(output.resolve("release-rag-report.json"), reportBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        Files.write(output.resolve("release-rag-index.json"), indexBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        MavenFailureExtent.publish(output.resolve("release-rag-failure-extent.html"), report, sha256(reportBytes));
        System.out.println("RAG-" + report.path("aggregate").asText() + ": " + output.resolve("release-rag-report.json"));
    }

    /** Testable core. The report contains only excerpts from the allowlisted source snapshot. */
    public static ObjectNode evaluate(Path projectRoot, String question) throws IOException {
        return evaluate(projectRoot, question, null);
    }

    /** Optional regression receipt is read and checked; no test or other release gate is launched. */
    public static ObjectNode evaluate(Path projectRoot, String question, Path componentRun) throws IOException {
        return evaluate(projectRoot, question, componentRun, null, null);
    }

    /** Optional read-only receipts for the exact Release APK and native multi-subgame UI flow. */
    public static ObjectNode evaluate(Path projectRoot, String question, Path componentRun, Path buildRun, Path nativeUiRun) throws IOException {
        return evaluate(projectRoot, question, componentRun, buildRun, nativeUiRun, buildRun);
    }

    public static ObjectNode evaluate(Path projectRoot, String question, Path componentRun, Path buildRun,
                                      Path nativeUiRun, Path uiBuildRun) throws IOException {
        Path root = projectRoot.toRealPath();
        if (question == null || question.isBlank()) throw new IOException("RAG-QUESTION");
        JsonNode config = readSafeJson(root, "config/release-rag-corpus.json", 65_536);
        if (!config.isObject() || config.size() != 4 || exactCount(config.path("schemaVersion")) != 1 || exactCount(config.path("topK")) != 5
                || !config.path("sources").isArray()) throw new IOException("RAG-CORPUS-SCHEMA");
        int maxBytes = exactCount(config.path("maxSourceBytes"));
        if (maxBytes < 1024 || maxBytes > 1_048_576) throw new IOException("RAG-CORPUS-LIMIT");

        JsonNode releaseIndex = readSafeJson(root, "release/index.json", 65_536);
        String publishedVersion = releaseIndex.path("currentVersion").asText();
        String version = releaseIndex.path("candidate").path("version").asText(publishedVersion);
        if (!VERSION.matcher(publishedVersion).matches() || !VERSION.matcher(version).matches())
            throw new IOException("RAG-RELEASE-VERSION");
        List<Spec> specs = specs(config, publishedVersion);
        List<Document> documents = loadDocuments(root, specs, maxBytes);
        List<Chunk> chunks = chunks(documents);
        RetrievalQuery query = parseQuery(question, version);
        List<Hit> hits = rank(query, chunks, 5);
        GitIdentity git = gitIdentity(root);
        String indexHash = indexHash(documents);
        String repository = releaseIndex.path("repository").asText();
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IOException("RAG-REPOSITORY-IDENTITY");
        JsonNode manifest = readSafeJson(root, "release/" + publishedVersion + "/release-validation.json", 262_144);

        ObjectNode report = JSON.createObjectNode();
        report.put("schemaVersion", 1);
        report.put("generatedUtc", Instant.now().toString());
        report.put("question", question);
        ObjectNode candidate = report.putObject("candidate");
        candidate.put("repository", repository);
        candidate.put("branch", git.branch());
        candidate.put("sourceCommit", git.commit());
        candidate.put("sourceTree", git.tree());
        candidate.put("worktreeState", git.available() ? (git.dirtyFiles() == 0 ? "CLEAN" : "DIRTY") : "UNKNOWN");
        candidate.put("dirtyPathCount", git.dirtyFiles());
        candidate.put("indexedSnapshotSha256", indexHash);
        candidate.put("indexedSourceCount", documents.size());
        candidate.put("currentReleaseVersion", publishedVersion);
        candidate.put("candidateVersion", version);
        candidate.put("lastPublishedVersion", publishedVersion);
        candidate.put("currentReleaseDeclaredState", manifest.path("publication").path("state").asText("UNKNOWN"));
        candidate.put("lastPublishedSourceCommit", manifest.path("release").path("sourceCommit").asText("UNKNOWN"));

        ObjectNode generation = report.putObject("generation");
        generation.put("provider", "local-deterministic");
        generation.put("model", "none");
        generation.put("retrieval", "allowlisted corpus; exact gate/class/version filters; normalized token overlap");
        generation.put("topK", 5);
        generation.put("policySha256", sha256(POLICY.getBytes(StandardCharsets.UTF_8)));
        generation.put("externalServicesUsed", false);

        ObjectNode filters = report.putObject("retrievalFilters");
        filters.put("gate", query.gate().isBlank() ? "ALL" : query.gate());
        filters.put("class", query.sourceClass().isBlank() ? "ALL" : query.sourceClass());
        filters.put("version", query.version().isBlank() ? version : query.version());
        filters.put("versionMatchesCandidate", query.versionMatches());

        ArrayNode index = report.putArray("index");
        for (Document document : documents) {
            ObjectNode row = index.addObject();
            row.put("path", document.path()); row.put("class", document.sourceClass()); row.put("owner", document.owner()); row.put("gate", document.gate());
            row.put("commit", git.commit()); row.put("sha256", document.sha256()); row.put("bytes", document.bytes());
            row.put("scannedUtc", Instant.now().toString());
        }

        ArrayNode retrieved = report.putArray("retrieval");
        for (Hit hit : hits) {
            Chunk chunk = hit.chunk(); ObjectNode row = retrieved.addObject();
            row.put("score", hit.score()); row.put("excerpt", chunk.body());
            ObjectNode citation = row.putObject("citation");
            citation.put("repository", repository); citation.put("commit", git.commit());
            citation.put("path", chunk.document().path()); citation.put("lineStart", chunk.firstLine());
            citation.put("lineEnd", chunk.lastLine()); citation.put("sha256", chunk.document().sha256());
            citation.put("sourceClass", chunk.document().sourceClass()); citation.put("owner", chunk.document().owner());
            citation.put("gate", chunk.document().gate());
        }
        report.put("answer", hits.isEmpty() ? "Nu există sursă allowlistată suficient de relevantă pentru această întrebare."
                : "Fragmentele de mai jos sunt extrase exacte pentru revizuire; nu reprezintă verdictul unei porți.");
        report.put("abstained", hits.isEmpty());

        ArrayNode gates = report.putArray("gates");
        for (String id : List.of("offline-regression", "component-extent-report", "android-jvm", "native-ui", "native-ui-report", "release-build", "local-catalog",
                "remote-publication", "authenticated-download", "anonymous-download", "release-retirement")) {
            ObjectNode gate = gates.addObject(); gate.put("id", id); gate.put("status", "NOT_RUN");
            gate.put("expected", "Receiptul desemnat trebuie să identifice același candidat.");
            gate.put("actual", "RAG-ul este read-only și nu a primit un receipt verificabil pentru această poartă.");
            gate.put("receipt", "");
        }
        gate(gates, "release-build").put("expected", "The signed product APK has the numeric candidate version and exact filename RentzLab-<version>.apk.");
        gate(gates, "release-retirement").put("expected", "The latest qualified release must download from the pushed README with an authorized account before prior releases are retired. Preserve source tags and audit evidence.");
        attachLocalCatalog(root, gates);
        if (componentRun != null) attachComponentReceipt(root, git, gates, componentRun, "regression");
        if (buildRun != null) attachReleaseBuildReceipt(root, git, gates, componentRun, buildRun);
        if (nativeUiRun != null) NativeUiEvidence.attach(root, git.commit(), version, gate(gates, "native-ui"),
                gate(gates, "native-ui-report"), componentRun, uiBuildRun, nativeUiRun);

        ArrayNode conflicts = report.putArray("conflicts");
        if (!git.available()) conflicts.add("Git identity unavailable; candidate source cannot be pinned.");
        if (git.dirtyFiles() > 0) conflicts.add("Worktree contains uncommitted or untracked changes; it is not a release snapshot.");
        if (hits.isEmpty()) conflicts.add("No allowlisted evidence supports the question.");

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
                : "Complete each missing deterministic gate on this exact snapshot; keep the APK and reports in the registered external candidate workspace.");
        boolean allGatesPass = true;
        for (JsonNode gate : gates) if (!gate.path("status").asText().equals("PASS")) allGatesPass = false;
        report.put("aggregate", allGatesPass && git.available() && git.dirtyFiles() == 0 && !hits.isEmpty()
                ? "READY_FOR_HUMAN_REVIEW" : "BLOCKED");
        report.put("mayPublish", false);
        return report;
    }

    private static String status(ArrayNode gates, String id) { return gate(gates, id).path("status").asText("NOT_RUN"); }

    private static void attachLocalCatalog(Path root, ArrayNode gates) {
        ObjectNode catalog = gate(gates, "local-catalog");
        catalog.put("expected", "The published edition and its APK/checksum verify locally; the next minor or major candidate has a higher Android versionCode.");
        try {
            ReleaseCatalog.Edition edition = ReleaseCatalog.verify(root, false);
            if (!edition.state().equals("PUBLISHED") || edition.target() == null)
                throw new IOException("RELEASE-CURRENT-NOT-PUBLISHED");
            JsonNode index = readSafeJson(root, "release/index.json", 65_536);
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
            JsonNode releaseIndex = readSafeJson(root, "release/index.json", 65_536);
            String candidateVersion = releaseIndex.path("candidate").path("version").asText(releaseIndex.path("currentVersion").asText());
            if (!VERSION.matcher(version).matches() || !version.equals(candidateVersion))
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

        JsonNode policy = readSafeJson(root, "config/infrastructure-workspaces.json", 65_536);
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

    private static List<Spec> specs(JsonNode config, String version) throws IOException {
        var result = new ArrayList<Spec>(); var seen = new HashSet<String>();
        if (config.path("sources").size() > MAX_CORPUS_SOURCES) throw new IOException("RAG-CORPUS-SOURCE-LIMIT");
        for (JsonNode row : config.path("sources")) {
            if (!row.isObject() || row.size() != 4) throw new IOException("RAG-CORPUS-ENTRY");
            String path = ReleasePaths.relative(row.path("path").asText().replace("{currentVersion}", version));
            String sourceClass = row.path("class").asText(), owner = row.path("owner").asText(), gate = row.path("gate").asText();
            validateCorpusPath(path);
            if (!sourceClass.matches("[a-z][a-z0-9-]*") || !owner.matches("[a-z][a-z0-9-]*")
                    || !GATES.contains(gate) || !seen.add(path)) throw new IOException("RAG-CORPUS-ENTRY");
            result.add(new Spec(path, sourceClass, owner, gate));
        }
        if (result.isEmpty()) throw new IOException("RAG-CORPUS-EMPTY");
        return List.copyOf(result);
    }

    private static List<Document> loadDocuments(Path root, List<Spec> specs, int maxBytes) throws IOException {
        var result = new ArrayList<Document>();
        long totalBytes = 0;
        for (Spec spec : specs) {
            Path path = root.resolve(spec.path()).normalize();
            if (!path.startsWith(root) || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("RAG-SOURCE-MISSING-OR-LINKED: " + spec.path());
            Path cursor = root;
            for (String part : spec.path().split("/")) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor)) throw new IOException("RAG-SOURCE-LINKED: " + spec.path());
            }
            Path real = path.toRealPath();
            if (!real.startsWith(root) || Files.size(real) > maxBytes) throw new IOException("RAG-SOURCE-BOUNDARY: " + spec.path());
            byte[] bytes = Files.readAllBytes(real);
            totalBytes += bytes.length;
            if (bytes.length > maxBytes || totalBytes > MAX_CORPUS_BYTES) throw new IOException("RAG-CORPUS-BYTE-LIMIT");
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.indexOf('\u0000') >= 0) throw new IOException("RAG-BINARY-SOURCE: " + spec.path());
        if (SECRET_CONTENT.matcher(text).find()) throw new IOException("RAG-SECRET-LIKE-CONTENT");
            result.add(new Document(spec.path(), spec.sourceClass(), spec.owner(), spec.gate(), sha256(bytes), bytes.length, List.of(text.split("\\R", -1))));
        }
        return List.copyOf(result);
    }

    private static List<Chunk> chunks(List<Document> documents) {
        var result = new ArrayList<Chunk>();
        for (Document document : documents) {
            String section = ""; StringBuilder body = new StringBuilder(); int start = 0, last = 0, nonBlank = 0;
            for (int i = 0; i < document.lines().size(); i++) {
                String line = document.lines().get(i);
                if (line.stripLeading().startsWith("#")) section = line.strip();
                if (line.isBlank()) {
                    if (body.length() > 0) { result.add(new Chunk(document, start + 1, last + 1, section, body.toString().strip())); body.setLength(0); nonBlank = 0; }
                    continue;
                }
                if (body.length() == 0) start = i;
                if (body.length() > 0 && (body.length() + line.length() > 1600 || nonBlank >= 12)) {
                    result.add(new Chunk(document, start + 1, last + 1, section, body.toString().strip())); body.setLength(0); nonBlank = 0; start = i;
                }
                if (body.length() > 0) body.append('\n');
                body.append(line); last = i; nonBlank++;
            }
            if (body.length() > 0) result.add(new Chunk(document, start + 1, last + 1, section, body.toString().strip()));
        }
        return List.copyOf(result);
    }

    private static RetrievalQuery parseQuery(String question, String currentVersion) throws IOException {
        if (question == null || question.isBlank()) throw new IOException("RAG-QUESTION");
        if (SECRET_ASSIGNMENT.matcher(question).find() || SECRET_CONTENT.matcher(question).find())
            throw new IOException("RAG-QUESTION-REDACTED");
        if (MALFORMED_FILTER.matcher(question).find()) throw new IOException("RAG-FILTER-SYNTAX");
        var matcher = FILTER.matcher(question);
        String gate = "", sourceClass = "", version = "";
        var remainder = new StringBuilder(); int end = 0;
        while (matcher.find()) {
            remainder.append(question, end, matcher.start()).append(' ');
            String key = matcher.group(1).toLowerCase(Locale.ROOT), value = matcher.group(2).toLowerCase(Locale.ROOT);
            String prior = switch (key) { case "gate" -> gate; case "class" -> sourceClass; default -> version; };
            if (!prior.isBlank() && !prior.equals(value)) throw new IOException("RAG-FILTER-CONFLICT: " + key);
            switch (key) { case "gate" -> gate = value; case "class" -> sourceClass = value; default -> version = value; }
            end = matcher.end();
        }
        remainder.append(question.substring(end));
        boolean matches = version.isBlank() || version.equals("current") || version.equals(currentVersion);
        if (!gate.isBlank() && !GATES.contains(gate)) matches = false;
        return new RetrievalQuery(remainder.toString().strip(), gate, sourceClass,
                version.equals("current") ? currentVersion : version, matches);
    }

    private static List<Hit> rank(RetrievalQuery query, List<Chunk> chunks, int topK) {
        Set<String> terms = tokens(query.text());
        boolean metadataOnly = terms.isEmpty() && (!query.gate().isBlank() || !query.sourceClass().isBlank() || !query.version().isBlank());
        if ((terms.isEmpty() && !metadataOnly) || !query.versionMatches()) return List.of();
        var hits = new ArrayList<Hit>();
        for (Chunk chunk : chunks) {
            if (!query.gate().isBlank() && !chunk.document().gate().equals(query.gate())) continue;
            if (!query.sourceClass().isBlank() && !chunk.document().sourceClass().equals(query.sourceClass())) continue;
            Set<String> body = tokens(chunk.body()); Set<String> metadata = tokens(chunk.document().path() + " " + chunk.section()
                    + " " + chunk.document().sourceClass() + " " + chunk.document().owner() + " " + chunk.document().gate());
            double score = metadataOnly ? 1.0 : 0;
            for (String term : terms) {
                if (body.contains(term)) score += metadata.contains(term) ? 2.0 : 1.0;
                else if (metadata.contains(term)) score += 0.35;
            }
            if (score > 0) hits.add(new Hit(chunk, score));
        }
        var ranked = hits.stream().sorted(Comparator.comparingDouble(Hit::score).reversed()
                .thenComparing(hit -> hit.chunk().document().path()).thenComparingInt(hit -> hit.chunk().firstLine())).toList();
        var selected = new ArrayList<Hit>();
        var selectedSources = new HashSet<String>();
        for (Hit hit : ranked) {
            if (selectedSources.add(hit.chunk().document().path())) selected.add(hit);
            if (selected.size() == topK) break;
        }
        return List.copyOf(selected);
    }

    private static void validateCorpusPath(String relative) throws IOException {
        String lower = relative.toLowerCase(Locale.ROOT);
        String extension = lower.substring(lower.lastIndexOf('.') + 1);
        if (!Set.of("md", "java", "ps1", "json").contains(extension)
                || lower.startsWith(".git/") || lower.contains("/.git/") || lower.startsWith(".gradle/")
                || lower.contains("/target/") || lower.contains("/build/") || lower.contains("/node_modules/")
                || lower.contains("/artifacts/") || lower.contains("/reports/")
                || lower.matches(".*(?:^|/)(?:\\.env[^/]*|local\\.properties|gradle\\.properties|[^/]*(?:secret|credential|token|keystore|signing)[^/]*)$"))
            throw new IOException("RAG-CORPUS-PATH-FORBIDDEN: " + relative);
    }

    private static JsonNode readSafeJson(Path root, String relative, int maxBytes) throws IOException {
        String safe = ReleasePaths.relative(relative);
        validateCorpusPath(safe);
        Path file = root.resolve(safe).normalize();
        assertNoSymlinkPath(file);
        if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > maxBytes)
            throw new IOException("RAG-METADATA-BOUNDARY: " + safe);
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > maxBytes) throw new IOException("RAG-METADATA-LIMIT: " + safe);
        return JSON.readTree(bytes);
    }

    public static void assertExternalDestination(Path root, Path output, boolean requireFresh) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        if (absolute.startsWith(root) || absolute.equals(root)) throw new IllegalArgumentException("RAG-OUTPUT: destination inside source tree");
        assertNoSymlinkPath(absolute);
        Path ancestor = absolute;
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
        if (ancestor == null || !Files.isDirectory(ancestor, LinkOption.NOFOLLOW_LINKS) || ancestor.toRealPath().startsWith(root))
            throw new IllegalArgumentException("RAG-OUTPUT: resolved destination must remain outside the source tree");
        if (requireFresh && Files.exists(absolute, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("RAG-OUTPUT: fresh destination required");
    }

    private static Set<String> tokens(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        var terms = new HashSet<String>();
        var matcher = TOKEN.matcher(normalized);
        while (matcher.find()) {
            String term = matcher.group();
            if (!STOP_WORDS.contains(term)) terms.add(term);
        }
        return Set.copyOf(terms);
    }

    public static GitIdentity gitIdentity(Path root) {
        try {
            String branch = git(root, "branch", "--show-current").strip();
            String commit = git(root, "rev-parse", "HEAD").strip();
            String tree = git(root, "rev-parse", "HEAD^{tree}").strip();
            int dirty = (int) git(root, "status", "--porcelain", "--untracked-files=all").lines().filter(line -> !line.isBlank()).count();
            return new GitIdentity(branch.isBlank() ? "DETACHED" : branch, commit, tree, true, dirty);
        } catch (Exception unavailable) {
            return new GitIdentity("UNKNOWN", "UNKNOWN", "UNKNOWN", false, -1);
        }
    }

    public static String git(Path root, String... arguments) throws Exception {
        var command = new ArrayList<>(List.of("git", "--no-optional-locks", "-C", root.toString()));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        var output = new java.util.concurrent.FutureTask<>(() -> {
            try (var stream = process.getInputStream()) {
                return stream.readNBytes(4 * 1024 * 1024 + 1);
            }
        });
        Thread.startVirtualThread(output);
        byte[] bytes;
        try {
            bytes = output.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (bytes.length > 4 * 1024 * 1024) throw new IOException("RAG-GIT-OUTPUT-LIMIT");
            if (!process.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                throw new IOException("RAG-GIT-TIMEOUT: " + arguments[0]);
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw new IOException("RAG-GIT-TIMEOUT: " + arguments[0], timeout);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            output.cancel(true);
        }
        if (process.exitValue() != 0) throw new IOException("RAG-GIT-IDENTITY: " + arguments[0]);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String indexHash(List<Document> documents) throws IOException {
        var canonical = new StringBuilder();
        documents.stream().sorted(Comparator.comparing(Document::path)).forEach(row ->
                canonical.append(row.path()).append('\0').append(row.sourceClass()).append('\0').append(row.owner())
                        .append('\0').append(row.gate()).append('\0').append(row.sha256()).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean snapshotStillMatches(Path root, JsonNode report) {
        try {
            JsonNode candidate = report.path("candidate");
            GitIdentity current = gitIdentity(root);
            if (!current.available() || !current.commit().equals(candidate.path("sourceCommit").asText())
                    || !current.tree().equals(candidate.path("sourceTree").asText())
                    || current.dirtyFiles() != candidate.path("dirtyPathCount").asInt(-1)) return false;
            JsonNode index = report.path("index");
            for (JsonNode row : index) {
                String relative = ReleasePaths.relative(row.path("path").asText());
                Path file = root.resolve(relative).normalize();
                assertNoSymlinkPath(file);
                if (!file.startsWith(root) || Files.isSymbolicLink(file) || !Files.isRegularFile(file)
                        || !sha256(Files.readAllBytes(file)).equals(row.path("sha256").asText())) return false;
            }
            return true;
        } catch (Exception invalid) { return false; }
    }

    public static Path externalRun(Path root, Path supplied) throws IOException {
        return ReleaseEvidenceFiles.externalRun(root, supplied);
    }

    public static byte[] readExternalFile(Path file, long maxBytes) throws IOException {
        return ReleaseEvidenceFiles.readExternalFile(file, maxBytes);
    }

    static int exactCount(JsonNode value) throws IOException {
        return ReleaseEvidenceFiles.exactCount(value);
    }

    public static String sha256(byte[] bytes) throws IOException {
        return ReleaseEvidenceFiles.sha256(bytes);
    }
}
