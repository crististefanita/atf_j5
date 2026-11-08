package atf.rentz.distribution;

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
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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
    private static final Set<String> GATES = Set.of("policy", "offline-regression", "android-jvm", "native-ui", "release-build",
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
    private record GitIdentity(String branch, String commit, String tree, boolean available, int dirtyFiles) { }
    private record RetrievalQuery(String text, String gate, String sourceClass, String version, boolean versionMatches) { }

    private ReleaseReadinessRag() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) throw new IllegalArgumentException(
                "Usage: ReleaseReadinessRag <project-root> <fresh-external-run-directory> <question> [component-run]");
        Path root = Path.of(args[0]).toRealPath();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        assertExternalDestination(root, output, true);
        if (args[2].isBlank()) throw new IllegalArgumentException("RAG-QUESTION: non-empty question required");
        ObjectNode report = evaluate(root, args[2], args.length == 4 ? Path.of(args[3]) : null);
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
        indexFile.put("schemaVersion", 1);
        indexFile.put("repository", report.path("candidate").path("repository").asText());
        indexFile.put("sourceCommit", report.path("candidate").path("sourceCommit").asText());
        indexFile.put("sourceTree", report.path("candidate").path("sourceTree").asText());
        indexFile.put("releaseVersion", report.path("candidate").path("currentReleaseVersion").asText());
        indexFile.put("indexedSnapshotSha256", report.path("candidate").path("indexedSnapshotSha256").asText());
        indexFile.set("entries", report.path("index").deepCopy());
        byte[] indexBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(indexFile);
        Files.write(output.resolve("release-rag-report.json"), reportBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        Files.write(output.resolve("release-rag-index.json"), indexBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        System.out.println("RAG-" + report.path("aggregate").asText() + ": " + output.resolve("release-rag-report.json"));
    }

    /** Testable core. The report contains only excerpts from the allowlisted source snapshot. */
    public static ObjectNode evaluate(Path projectRoot, String question) throws IOException {
        return evaluate(projectRoot, question, null);
    }

    /** Optional regression receipt is read and checked; no test or other release gate is launched. */
    public static ObjectNode evaluate(Path projectRoot, String question, Path componentRun) throws IOException {
        Path root = projectRoot.toRealPath();
        if (question == null || question.isBlank()) throw new IOException("RAG-QUESTION");
        JsonNode config = readSafeJson(root, "config/release-rag-corpus.json", 65_536);
        if (!config.isObject() || config.size() != 4 || config.path("schemaVersion").asInt(-1) != 1 || config.path("topK").asInt(-1) != 5
                || !config.path("sources").isArray()) throw new IOException("RAG-CORPUS-SCHEMA");
        int maxBytes = config.path("maxSourceBytes").asInt(-1);
        if (maxBytes < 1024 || maxBytes > 1_048_576) throw new IOException("RAG-CORPUS-LIMIT");

        JsonNode releaseIndex = readSafeJson(root, "release/index.json", 65_536);
        String version = releaseIndex.path("currentVersion").asText();
        if (!VERSION.matcher(version).matches())
            throw new IOException("RAG-RELEASE-VERSION");
        List<Spec> specs = specs(config, version);
        List<Document> documents = loadDocuments(root, specs, maxBytes);
        List<Chunk> chunks = chunks(documents);
        RetrievalQuery query = parseQuery(question, version);
        List<Hit> hits = rank(query, chunks, 5);
        GitIdentity git = gitIdentity(root);
        String indexHash = indexHash(documents);
        String repository = releaseIndex.path("repository").asText();
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IOException("RAG-REPOSITORY-IDENTITY");
        JsonNode manifest = readSafeJson(root, "release/" + version + "/release-validation.json", 262_144);

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
        candidate.put("currentReleaseVersion", version);
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
        for (String id : List.of("offline-regression", "android-jvm", "native-ui", "release-build", "local-catalog",
                "remote-publication", "authenticated-download", "anonymous-download")) {
            ObjectNode gate = gates.addObject(); gate.put("id", id); gate.put("status", "NOT_RUN");
            gate.put("expected", "Receiptul desemnat trebuie să identifice același candidat.");
            gate.put("actual", "RAG-ul este read-only și nu a primit un receipt verificabil pentru această poartă.");
            gate.put("receipt", "");
        }
        if (componentRun != null) attachRegressionReceipt(root, git, gates, componentRun);

        ArrayNode conflicts = report.putArray("conflicts");
        if (!git.available()) conflicts.add("Git identity unavailable; candidate source cannot be pinned.");
        if (git.dirtyFiles() > 0) conflicts.add("Worktree contains uncommitted or untracked changes; it is not a release snapshot.");
        if (hits.isEmpty()) conflicts.add("No allowlisted evidence supports the question.");

        ArrayNode next = report.putArray("nextActions");
        next.add("Run the remaining deterministic gates for one clean, pinned source snapshot.");
        next.add("Supply original build, native UI, catalog, publication and channel-specific download receipts before changing those gates from NOT_RUN.");
        next.add("Review remote publication and authenticated and anonymous downloads as separate evidence.");
        report.put("responsible", "release operator");
        boolean allGatesPass = true;
        for (JsonNode gate : gates) if (!gate.path("status").asText().equals("PASS")) allGatesPass = false;
        report.put("aggregate", allGatesPass && git.available() && git.dirtyFiles() == 0 && !hits.isEmpty()
                ? "READY_FOR_HUMAN_REVIEW" : "BLOCKED");
        report.put("mayPublish", false);
        return report;
    }

    private static void attachRegressionReceipt(Path root, GitIdentity git, ArrayNode gates, Path suppliedRun) {
        String identityFailure = "";
        String verifiedReceiptHash = "";
        Path run = null;
        JsonNode result = null;
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
            if (!result.path("collection").path("name").asText().equals("regression")) throw new IOException("RAG-RECEIPT-COLLECTION");
            if (!result.path("status").asText().equals("PASS")) throw new IOException("RAG-RECEIPT-STATUS");
            if (!result.path("sourceManifestSha256").asText().equalsIgnoreCase(sha256(manifestBytes)))
                throw new IOException("RAG-RECEIPT-MANIFEST");
            if (!result.path("sourceCommit").asText().equals(git.commit()))
                throw new IOException("RAG-RECEIPT-COMMIT");
            if (!result.path("drift").isArray() || !result.path("populationChanges").isArray()
                    || result.path("drift").size() != 0 || result.path("populationChanges").size() != 0)
                throw new IOException("RAG-RECEIPT-DRIFT");
            if (!result.path("results").isArray() || result.path("results").isEmpty()
                    || !result.path("androidJvm").isTextual() || result.path("androidJvm").asText().isBlank())
                throw new IOException("RAG-RECEIPT-POPULATION");
            JsonNode sourceRows = JSON.readTree(manifestBytes);
            if (!sourceRows.isArray() || sourceRows.isEmpty()) throw new IOException("RAG-RECEIPT-MANIFEST-EMPTY");
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
        ObjectNode androidGate = gate(gates, "android-jvm");
        if (!identityFailure.isBlank()) {
            regression.put("status", "BLOCKED"); regression.put("actual", identityFailure);
            androidGate.put("status", "BLOCKED"); androidGate.put("actual", "The parent regression receipt identity is invalid.");
            return;
        }
        Path receipt = run.resolve("result.json");
        if (verifiedReceiptHash.isBlank()) { regression.put("status", "BLOCKED"); regression.put("actual", "RAG-RECEIPT-INVALID"); return; }
        regression.put("receipt", "result.json"); regression.put("receiptSha256", verifiedReceiptHash);
        JsonNode maven = result.path("results"); boolean hasSuites = maven.isArray() && !maven.isEmpty();
        boolean mavenPass = hasSuites && result.path("runtimeQualificationFailure").asText().isBlank()
                && result.path("selectedPopulationFailure").asText().isBlank();
        boolean mavenFail = !result.path("runtimeQualificationFailure").asText().isBlank()
                || !result.path("selectedPopulationFailure").asText().isBlank();
        for (JsonNode suite : maven) {
            boolean passed = suite.path("tests").asInt(0) > 0 && suite.path("failures").asInt(-1) == 0
                    && suite.path("errors").asInt(-1) == 0 && suite.path("skipped").asInt(-1) == 0;
            mavenPass &= passed;
            mavenFail |= suite.path("failures").asInt(0) > 0 || suite.path("errors").asInt(0) > 0
                    || suite.path("skipped").asInt(0) > 0;
        }
        regression.put("status", mavenPass ? "PASS" : mavenFail ? "FAIL" : "NOT_RUN");
        regression.put("actual", mavenPass ? "Surefire regression receipts are non-empty and have zero failures, errors and skips."
                : mavenFail ? "A Surefire regression receipt reports a failure, error, skip or zero-test suite."
                : "No complete Surefire regression population is present.");

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
            boolean androidPass = hasAndroidSuites, androidFail = false;
            Path expectedStage = Path.of(result.path("stage").asText()).toRealPath();
            if (!Path.of(mobileReceipt.path("source").asText()).toRealPath().equals(expectedStage)) throw new IOException("RAG-ANDROID-SOURCE");
            if (!expectedStage.startsWith(run) || mobileReceipt.path("exitCode").asInt(-1) != 0
                    || !mobileReceipt.path("drift").isArray() || !mobileReceipt.path("populationChanges").isArray()
                    || mobileReceipt.path("drift").size() != 0 || mobileReceipt.path("populationChanges").size() != 0)
                throw new IOException("RAG-ANDROID-QUALIFICATION");
            for (JsonNode suite : mobileReceipt.path("tests")) {
                boolean passed = suite.path("tests").asInt(0) > 0 && suite.path("failures").asInt(-1) == 0
                        && suite.path("errors").asInt(-1) == 0 && suite.path("skipped").asInt(-1) == 0;
                androidPass &= passed;
                androidFail |= suite.path("failures").asInt(0) > 0 || suite.path("errors").asInt(0) > 0
                        || suite.path("skipped").asInt(0) > 0;
            }
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

    private static String safeFailure(Exception failure, String fallback) {
        String message = failure.getMessage();
        if (message == null) return fallback;
        int separator = message.indexOf(':');
        String code = separator < 0 ? message : message.substring(0, separator);
        return code.matches("RAG-[A-Z0-9-]+") ? code : fallback;
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

    private static void assertNoSymlinkPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path cursor = absolute.getRoot();
        for (Path part : absolute) {
            cursor = cursor.resolve(part);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor))
                throw new IOException("RAG-SYMLINK-PATH: " + cursor);
        }
    }

    private static void assertExternalDestination(Path root, Path output, boolean requireFresh) throws IOException {
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

    private static GitIdentity gitIdentity(Path root) {
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

    private static String git(Path root, String... arguments) throws Exception {
        var command = new ArrayList<String>(); command.add("git"); command.add("-C"); command.add(root.toString());
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("RAG-GIT-TIMEOUT"); }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IOException("RAG-GIT-IDENTITY");
        return output;
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

    private static String sha256(byte[] bytes) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException("SHA-256 unavailable", impossible); }
    }
}
