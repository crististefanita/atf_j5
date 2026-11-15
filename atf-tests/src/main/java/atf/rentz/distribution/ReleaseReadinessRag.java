package atf.rentz.distribution;

import atf.rentz.distribution.editorial.DocumentationReconciliation;
import atf.rentz.distribution.evidence.MavenFailureExtent;
import atf.rentz.distribution.evidence.ReleaseEvidenceFiles;
import atf.rentz.distribution.qualification.ReleaseQualification;
import atf.rentz.distribution.retrieval.ReleaseCorpusSearch;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.assertNoSymlinkPath;
import static atf.rentz.distribution.ReleasePaths.readMetadata;

/** Composes local corpus search and independent release qualification. */
public final class ReleaseReadinessRag {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record GitIdentity(String branch, String commit, String tree, boolean available, int dirtyFiles) { }

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
        confirmSnapshot(root, report);
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
        JsonNode releaseIndex = readMetadata(root, "release/index.json", 65_536);
        String publishedVersion = releaseIndex.path("currentVersion").asText();
        String version = releaseIndex.path("candidate").path("version").asText(publishedVersion);
        if (!ReleasePaths.isReleaseVersion(publishedVersion) || !ReleasePaths.isReleaseVersion(version))
            throw new IOException("RAG-RELEASE-VERSION");
        var search = ReleaseCorpusSearch.search(root, question, publishedVersion, version);
        GitIdentity git = gitIdentity(root);
        String repository = releaseIndex.path("repository").asText();
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IOException("RAG-REPOSITORY-IDENTITY");
        JsonNode manifest = readMetadata(root, "release/" + publishedVersion + "/release-validation.json", 262_144);

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
        candidate.put("indexedSnapshotSha256", search.indexHash());
        candidate.put("indexedSourceCount", search.sourceCount());
        candidate.put("currentReleaseVersion", publishedVersion);
        candidate.put("candidateVersion", version);
        candidate.put("lastPublishedVersion", publishedVersion);
        candidate.put("currentReleaseDeclaredState", manifest.path("publication").path("state").asText("UNKNOWN"));
        candidate.put("lastPublishedSourceCommit", manifest.path("release").path("sourceCommit").asText("UNKNOWN"));

        search.writeTo(report, repository, git.commit());
        ReleaseQualification.evaluate(root, git, version,
                new ReleaseQualification.EvidenceRuns(componentRun, buildRun, nativeUiRun, uiBuildRun),
                search.abstained(), report);
        return report;
    }

    static void confirmSnapshot(Path root, ObjectNode report) {
        if (!snapshotStillMatches(root, report)) ReleaseQualification.blockSourceDrift(report);
    }

    public static ArrayNode offlineEvidence(Path root, GitIdentity git, Path run, String collection) {
        return ReleaseQualification.offlineEvidence(root, git, run, collection);
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

    private static boolean snapshotStillMatches(Path root, JsonNode report) {
        try {
            JsonNode candidate = report.path("candidate");
            GitIdentity current = gitIdentity(root);
            if (!current.available() || !current.commit().equals(candidate.path("sourceCommit").asText())
                    || !current.tree().equals(candidate.path("sourceTree").asText())
                    || current.dirtyFiles() != candidate.path("dirtyPathCount").asInt(-1)) return false;
            return ReleaseCorpusSearch.matchesIndex(root, report.path("index"));
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
