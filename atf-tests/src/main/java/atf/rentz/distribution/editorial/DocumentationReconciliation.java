package atf.rentz.distribution.editorial;

import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import atf.rentz.distribution.ReleasePaths;
import static atf.rentz.distribution.ReleaseReadinessRag.*;

/** A separate, read-only bridge from a passing regression to an editorial revision. */
public final class DocumentationReconciliation {
    private static final long MAX_RECEIPT = 4L * 1024 * 1024;
    private static final long MAX_EXTENT = 128L * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> DOCUMENTATION_SUITES = Set.of(
            "atf.rentz.governance.DocumentationContractTest",
            "atf.rentz.governance.documentation.DocumentationControlsTest",
            "atf.rentz.governance.documentation.DocumentationDiagramGovernanceTest",
            "atf.rentz.governance.documentation.DocumentationProseGovernanceTest",
            "atf.rentz.governance.documentation.DocumentationReadmeStructureGovernanceTest",
            "atf.rentz.governance.documentation.DocumentationStyleGovernanceTest",
            "atf.rentz.governance.documentation.DocumentationTargetGovernanceTest",
            "atf.rentz.governance.documentation.DynamicPathDocumentationArchitectureTest",
            "atf.rentz.governance.documentation.links.DocumentationLinkGovernanceTest");

    private record Evidence(Path run, Path root, GitIdentity git, JsonNode receipt, String receiptSha,
                            Map<String, String> manifest, String manifestSha, String extentSha, String androidSha) { }

    private DocumentationReconciliation() { }

    public static void write(Path project, Path suppliedOutput, Path regression, Path documentation) throws IOException {
        Path root = project.toRealPath();
        Path output = suppliedOutput.toAbsolutePath().normalize();
        assertExternalDestination(root, output, true);
        Path originalRun = externalRun(root, regression);
        Path documentationRun = externalRun(root, documentation);
        assertExternalDestination(originalRun, output, true);
        assertExternalDestination(documentationRun, output, true);
        Path originalSource = Path.of(JSON.readTree(readExternalFile(originalRun.resolve("result.json"), MAX_RECEIPT))
                .path("source").asText()).toRealPath();
        assertExternalDestination(originalSource, output, true);
        ObjectNode proof = JSON.createObjectNode().put("schemaVersion", 1)
                .put("scope", "Reuse of offline input evidence only")
                .put("mayPublish", false);
        List<AtfAssertion> comparisons = new ArrayList<>();
        try {
            compose(root, originalSource, originalRun, documentationRun, proof, comparisons);
            proof.put("status", "PASS");
        } catch (IOException denied) {
            String message = denied.getMessage();
            String reason = message != null && message.matches("(?:EDITORIAL|RAG)-[A-Z0-9-]+")
                    ? message : "EDITORIAL-EVIDENCE-UNREADABLE";
            proof.put("status", "BLOCKED").put("reason", reason);
            comparisons.add(check("Eligibility", "A proven editorial revision", reason, false));
        }
        assertExternalDestination(root, output, true);
        Files.createDirectory(output);
        Path extent = output.resolve("documentation-reconciliation.html");
        publish(extent, comparisons, proof.path("status").asText().equals("PASS"));
        proof.put("extentSha256", sha256(readExternalFile(extent, MAX_EXTENT)));
        Path receipt = output.resolve("documentation-reconciliation.json");
        Files.write(receipt, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(proof), StandardOpenOption.CREATE_NEW);
        System.out.println("EDITORIAL-" + proof.path("status").asText() + ": " + receipt);
    }

    private static void compose(Path candidate, Path original, Path originalRun, Path docsRun, ObjectNode proof,
                                List<AtfAssertion> comparisons) throws IOException {
        Evidence baseline = verify(original, originalRun, "regression");
        addEvidence(proof.putObject("original"), baseline);
        Evidence revised = verify(candidate, docsRun, "infrastructure");
        addEvidence(proof.putObject("documentation"), revised);
        if (baseline.git().commit().equals(revised.git().commit())
                || !gitValue(original, "rev-parse", "--path-format=absolute", "--git-common-dir")
                        .equals(gitValue(candidate, "rev-parse", "--path-format=absolute", "--git-common-dir"))
                || !gitValue(candidate, "merge-base", baseline.git().commit(), revised.git().commit()).equals(baseline.git().commit()))
            throw new IOException("EDITORIAL-COMMIT-RELATION");

        Set<String> changed = changedDocuments(candidate, baseline.git().commit(), revised.git().commit());
        Set<String> snapshotDiff = new TreeSet<>(baseline.manifest().keySet());
        snapshotDiff.addAll(revised.manifest().keySet());
        snapshotDiff.removeIf(path -> java.util.Objects.equals(baseline.manifest().get(path), revised.manifest().get(path)));
        if (changed.isEmpty() || !snapshotDiff.equals(changed)) throw new IOException("EDITORIAL-MANIFEST-DIFF");

        String testedInputs = sharedDigest(baseline.manifest(), changed);
        String candidateInputs = sharedDigest(revised.manifest(), changed);
        if (!testedInputs.equals(candidateInputs)) throw new IOException("EDITORIAL-INPUT-DIGEST");
        verifyStillPinned(baseline);
        verifyStillPinned(revised);
        proof.put("identicalTestedInputsSha256", testedInputs)
                .put("identicalTestedInputFiles", baseline.manifest().size() - (int) changed.stream().filter(baseline.manifest()::containsKey).count());
        var documents = proof.putArray("changedDocuments");
        for (String path : changed) {
            String before = baseline.manifest().get(path), after = revised.manifest().get(path);
            documents.addObject().put("path", path).put("beforeSha256", before).put("afterSha256", after);
            comparisons.add(check("Editorial path · " + path, before == null ? "absent" : before, after == null ? "absent" : after, true));
        }
        comparisons.addFirst(check("Identical tested inputs", testedInputs, candidateInputs, true));
        comparisons.add(check("Preserved regression receipt", baseline.receiptSha(),
                digest(baseline.run().resolve("result.json"), MAX_RECEIPT), true));
        comparisons.add(check("Fresh documentation governance", revised.git().commit(), revised.receipt().path("sourceCommit").asText(), true));
    }

    private static Evidence verify(Path root, Path suppliedRun, String collection) throws IOException {
        Path run = externalRun(root, suppliedRun);
        GitIdentity git = gitIdentity(root);
        if (!git.available() || git.dirtyFiles() != 0) throw new IOException("EDITORIAL-WORKTREE");
        byte[] receiptBytes = readExternalFile(run.resolve("result.json"), MAX_RECEIPT);
        JsonNode receipt = JSON.readTree(receiptBytes);
        String receiptHash = sha256(receiptBytes);
        JsonNode gates = offlineEvidence(root, git, run, collection);
        boolean original = collection.equals("regression");
        JsonNode regression = findGate(gates, "offline-regression"), extent = findGate(gates, "component-extent-report");
        JsonNode jvm = findGate(gates, "android-jvm");
        if (!pass(regression) || !pass(extent) || (original && !pass(jvm))
                || !receiptHash.equals(regression.path("receiptSha256").asText()))
            throw new IOException(original ? "EDITORIAL-ORIGINAL-QUALIFICATION" : "EDITORIAL-DOCUMENTATION-QUALIFICATION");
        if (!original) {
            Set<String> suites = new TreeSet<>();
            for (JsonNode row : receipt.path("results")) {
                if (row.path("module").asText().equals("atf-tests") && !suites.add(row.path("name").asText()))
                    throw new IOException("EDITORIAL-DOCUMENTATION-POPULATION");
            }
            if (!suites.containsAll(DOCUMENTATION_SUITES)) throw new IOException("EDITORIAL-DOCUMENTATION-POPULATION");
        }
        byte[] manifestBytes = readExternalFile(run.resolve("source-identity.json"), MAX_RECEIPT);
        String manifestHash = sha256(manifestBytes);
        if (!manifestHash.equalsIgnoreCase(receipt.path("sourceManifestSha256").asText()))
            throw new IOException("EDITORIAL-MANIFEST-HASH");
        Map<String, String> manifest = new TreeMap<>();
        for (JsonNode row : JSON.readTree(manifestBytes)) {
            String relative = ReleasePaths.relative(row.path("path").asText());
            String hash = row.path("sha256").asText().toLowerCase(Locale.ROOT);
            if (!hash.matches("[a-f0-9]{64}") || manifest.putIfAbsent(relative, hash) != null)
                throw new IOException("EDITORIAL-MANIFEST-IDENTITY");
        }
        return new Evidence(run, root, git, receipt, receiptHash, manifest, manifestHash,
                extent.path("receiptSha256").asText(), original ? jvm.path("receiptSha256").asText() : "");
    }

    private static JsonNode findGate(JsonNode gates, String id) throws IOException {
        for (JsonNode gate : gates) if (id.equals(gate.path("id").asText())) return gate;
        throw new IOException("EDITORIAL-GATE-MISSING");
    }

    private static boolean pass(JsonNode gate) { return gate.path("status").asText().equals("PASS"); }

    private static Set<String> changedDocuments(Path root, String original, String candidate) throws IOException {
        String raw = gitValue(root, "diff", "--raw", "--no-renames", "-z", original, candidate, "--");
        Set<String> changed = new TreeSet<>();
        if (raw.isEmpty()) return changed;
        String[] entries = raw.split("\\x00", -1);
        if (entries.length % 2 != 1 || !entries[entries.length - 1].isEmpty()) throw new IOException("EDITORIAL-GIT-DIFF");
        for (int i = 0; i < entries.length - 1; i += 2) {
            String path = ReleasePaths.relative(entries[i + 1]);
            String[] meta = entries[i].split(" ");
            boolean regular = meta.length == 5 && switch (meta[4]) {
                case "A" -> meta[0].equals(":000000") && meta[1].equals("100644");
                case "M" -> meta[0].equals(":100644") && meta[1].equals("100644");
                case "D" -> meta[0].equals(":100644") && meta[1].equals("000000");
                default -> false;
            };
            boolean editorial = path.equals("README.md") || path.equals("docs/README.md")
                    || (path.endsWith(".md") && (path.startsWith("docs/rentz/") || path.startsWith("docs/feedback/")));
            if (!regular || !editorial || !changed.add(path)) throw new IOException("EDITORIAL-NON-DOCUMENTATION");
        }
        return changed;
    }

    private static String sharedDigest(Map<String, String> manifest, Set<String> changed) throws IOException {
        var canonical = new StringBuilder();
        manifest.forEach((path, hash) -> {
            if (!changed.contains(path)) canonical.append(path).append('\0').append(hash).append('\n');
        });
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void verifyStillPinned(Evidence evidence) throws IOException {
        GitIdentity now = gitIdentity(evidence.root());
        Path run = evidence.run();
        if (!now.equals(evidence.git()) || !digest(run.resolve("result.json"), MAX_RECEIPT).equals(evidence.receiptSha())
                || !digest(run.resolve("source-identity.json"), MAX_RECEIPT).equals(evidence.manifestSha())
                || !digest(run.resolve("component-suite-extent.html"), MAX_EXTENT).equals(evidence.extentSha())
                || (!evidence.androidSha().isEmpty() && !digest(run.resolve("android-jvm/result.json"), MAX_RECEIPT).equals(evidence.androidSha())))
            throw new IOException("EDITORIAL-EVIDENCE-DRIFT");
    }

    private static void addEvidence(ObjectNode target, Evidence source) {
        target.put("run", source.run().toString()).put("commit", source.git().commit())
                .put("tree", source.git().tree()).put("status", source.receipt().path("status").asText())
                .put("receiptSha256", source.receiptSha()).put("manifestSha256", source.manifestSha())
                .put("extentSha256", source.extentSha());
        if (!source.androidSha().isEmpty()) target.put("androidJvmReceiptSha256", source.androidSha());
    }

    private static String digest(Path file, long limit) throws IOException { return sha256(readExternalFile(file, limit)); }

    private static String gitValue(Path root, String... args) throws IOException {
        try {
            String value = git(root, args);
            return value.indexOf('\0') >= 0 ? value : value.strip();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("EDITORIAL-GIT", interrupted);
        } catch (Exception failure) {
            throw new IOException("EDITORIAL-GIT", failure);
        }
    }

    private static AtfAssertion check(String label, String expected, String actual, boolean passed) {
        return new AtfAssertion(label, expected, actual, passed, passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
    }

    private static void publish(Path extent, List<AtfAssertion> assertions, boolean passed) {
        AtfReportStatus status = passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        try (var report = new ExtentReportSession(extent, ReportingSettings.defaults());
             var scenario = report.startScenario("Documentation reconciliation",
                     "Original regression evidence and fresh documentation governance for a pinned editorial revision.",
                     List.of("component", "editorial", "audit"));
             var phase = scenario.startPhase("Source and evidence");
             var step = phase.startAssertionStep("Editorial reconciliation")) {
            step.assertions(assertions);
            step.complete(status);
            phase.complete(status);
            scenario.complete(status);
        }
    }
}
