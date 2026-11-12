package atf.rentz.governance.changes;

import atf.rentz.governance.source.GovernanceProject;
import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class TestChangeGovernanceTest {
    @Test void protectedTestDifferencesNeedExactReviewedControlsAgainstActualGit() throws Exception {
        Path stage = GovernanceProject.root();
        Path source = Path.of(System.getProperty("rentz.governance.sourceRoot", stage.toString())).toRealPath();
        var policy = new ObjectMapper().readTree(stage.resolve("config/test-change-controls.json").toFile());
        var snapshot = GitTestChanges.read(source, policy.required("base").textValue());
        assertEquals(List.of(), validate(source, snapshot, policy, LocalDate.now()),
                "GOV-TEST-CHANGE: actual Git differences require exact, current controls");
    }

    static List<String> validate(Path root, GitTestChanges.Snapshot snapshot, JsonNode policy, LocalDate today) throws Exception {
        var findings = new ArrayList<String>();
        if (policy.required("schemaVersion").intValue() != 1) findings.add("unsupported control schema");
        if (!policy.required("base").asText().equals(snapshot.base())) findings.add("baseline mismatch");
        var seen = new HashSet<String>();
        for (var control : policy.required("controls")) {
            String file = control.required("path").asText();
            if (!protectedPath(file) || file.contains("..") || file.contains("*") || !seen.add(file)) {
                findings.add("invalid or duplicate protected path: " + file);
            }
            if (control.required("reason").asText().isBlank() || control.required("criterion").asText().isBlank()
                    || control.required("proof").asText().isBlank() || control.required("owner").asText().isBlank()
                    || LocalDate.parse(control.required("expires").asText()).isBefore(today)) {
                findings.add("incomplete or expired review: " + file);
            }
        }
        for (var change : snapshot.changes()) {
            if (!protectedPath(change.path())) continue;
            var controls = new ArrayList<JsonNode>();
            policy.required("controls").forEach(row -> {
                if (row.required("path").asText().equals(change.path())) controls.add(row);
            });
            if (controls.size() != 1) {
                findings.add("unreviewed difference: " + change.kind() + " " + change.path());
                continue;
            }
            var control = controls.getFirst();
            if (change.kind().equals("D")) {
                if (!control.path("allowDelete").asBoolean(false)) findings.add("deletion forbidden: " + change.path());
            } else if (!Set.of("A", "M").contains(change.kind())) {
                findings.add("unsupported difference: " + change.kind());
            } else {
                Path file = root.resolve(change.path()).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)
                        || !sha(file).equals(control.required("sha256").asText())) {
                    findings.add("reviewed bytes changed: " + change.path());
                }
            }
        }
        return List.copyOf(findings);
    }

    static boolean protectedPath(String path) {
        return path.equals("pom.xml") || path.endsWith("/pom.xml")
                || path.contains("/src/test/") || path.equals("config/test-collections.json")
                || path.equals("config/java-source-layout.json") || path.equals("config/documentation-policy.json")
                || path.equals("config/documentation-validators.json")
                || path.equals("scripts/run-rentz-components.ps1") || path.equals("scripts/TestCollections.psm1")
                || path.equals("scripts/TestSourceInventory.psm1") || path.equals("scripts/RuntimeQualification.psm1")
                || path.equals("config/infrastructure-workspaces.json")
                || path.equals("config/release-rag-corpus.json") || path.equals("scripts/run-release-rag.ps1")
                || path.equals("scripts/verify-release-readme.ps1")
                || path.equals("scripts/InfrastructureWorkspaces.psm1")
                || path.equals("library-provenance.json") || path.startsWith("lib/atf-reporting-")
                || path.equals("atf-tests/src/main/java/atf/rentz/distribution/AtfReportingLibrary.java")
                || path.equals("scripts/build-rentz-android.ps1")
                || path.equals("atf-tests/tools/run-shared-ui-sanity.ps1")
                || path.equals("atf-tests/tools/test-workspace-governance.ps1")
                || path.equals("rentz-lab-app/app/build.gradle") || path.equals("release/index.json")
                || path.equals("atf-tests/src/main/java/atf/rentz/distribution/ReleaseCatalog.java")
                || path.equals("atf-tests/src/main/java/atf/rentz/distribution/ReleaseReadinessRag.java")
                || path.equals("atf-tests/src/main/java/atf/rentz/distribution/editorial/DocumentationReconciliation.java")
                || path.equals("atf-tests/src/main/java/atf/rentz/distribution/evidence/ComponentSuiteEvidence.java")
                || path.startsWith("atf-tests/src/main/java/atf/rentz/reporting/")
                || path.equals("atf-tests/tools/run-arena.ps1") || path.equals("atf-tests/tools/run-android-sanity.ps1")
                || path.equals("atf-tests/tools/test-runtime-controls.ps1")
                || path.equals("atf-tests/tools/test-test-discovery.ps1")
                || path.equals("atf-tests/tools/test-qualified-runtime.ps1")
                || path.startsWith("atf-tests/src/main/java/atf/rentz/discovery/")
                || path.equals("atf-tests/src/main/java/atf/rentz/OfflineArena.java")
                || path.startsWith("atf-tests/src/main/java/atf/rentz/arena/")
                || path.startsWith("atf-tests/src/main/java/atf/rentz/runtime/");
    }

    @Test void releaseRagPolicyRunnerAndRetrieverAreProtectedByExactChangeControls() {
        assertTrue(protectedPath("config/release-rag-corpus.json"));
        assertTrue(protectedPath("scripts/run-release-rag.ps1"));
        assertTrue(protectedPath("scripts/verify-release-readme.ps1"));
        assertTrue(protectedPath("atf-tests/src/main/java/atf/rentz/distribution/ReleaseReadinessRag.java"));
        assertTrue(protectedPath("atf-tests/src/main/java/atf/rentz/distribution/editorial/DocumentationReconciliation.java"));
        assertTrue(protectedPath("atf-tests/src/main/java/atf/rentz/distribution/evidence/ComponentSuiteEvidence.java"));
    }

    @Test void releaseArtifactsReportingAndWorkspaceBoundariesAreProtected() {
        assertTrue(protectedPath("scripts/InfrastructureWorkspaces.psm1"));
        assertTrue(protectedPath("scripts/build-rentz-android.ps1"));
        assertTrue(protectedPath("atf-tests/tools/run-shared-ui-sanity.ps1"));
        assertTrue(protectedPath("atf-tests/tools/test-workspace-governance.ps1"));
        assertTrue(protectedPath("atf-tests/src/main/java/atf/rentz/distribution/ReleaseCatalog.java"));
        assertTrue(protectedPath("atf-tests/src/main/java/atf/rentz/distribution/AtfReportingLibrary.java"));
        assertTrue(protectedPath("library-provenance.json"));
        assertTrue(protectedPath("lib/atf-reporting-1.0.0-j24.jar"));
        assertTrue(protectedPath("atf-tests/src/main/java/atf/rentz/reporting/ComponentSuiteExtentReport.java"));
        assertTrue(protectedPath("rentz-lab-app/app/build.gradle"));
        assertTrue(protectedPath("release/index.json"));
    }

    private static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    @Test void realRepositoryTracksStagedUnstagedUntrackedAndDeletedTests(@TempDir Path root) throws Exception {
        GitTestChanges.command(root, "init", "-b", "main");
        Path tests = Files.createDirectories(root.resolve("module/src/test/java"));
        Path first = tests.resolve("FirstTest.java");
        Path deleted = tests.resolve("DeletedTest.java");
        Files.writeString(first, "class FirstTest {}");
        Files.writeString(deleted, "class DeletedTest {}");
        GitTestChanges.command(root, "add", ".");
        GitTestChanges.command(root, "-c", "user.name=Governance fixture", "-c", "user.email=fixture@invalid",
                "-c", "commit.gpgsign=false", "commit", "-m", "isolated fixture");
        String base = GitTestChanges.command(root, "rev-parse", "HEAD").trim();
        Files.writeString(first, "class FirstTest { int staged; }");
        GitTestChanges.command(root, "add", ".");
        Files.writeString(first, "class FirstTest { int unstaged; }");
        Files.delete(deleted);
        Files.writeString(tests.resolve("NewTest.java"), "class NewTest {}");
        Files.writeString(root.resolve(".gitignore"), "**/src/test/java/IgnoredTest.java\n");
        Path deep = Files.createDirectories(root.resolve("lab/game-core/src/test/java"));
        Files.writeString(deep.resolve("IgnoredTest.java"), "class IgnoredTest {}");
        var snapshot = GitTestChanges.read(root, base);
        assertTrue(snapshot.changes().contains(new GitTestChanges.Change("A", "lab/game-core/src/test/java/IgnoredTest.java")),
                "Ignored tests in nested modules remain protected");
        assertTrue(snapshot.changes().contains(new GitTestChanges.Change("M", "module/src/test/java/FirstTest.java")));
        assertTrue(snapshot.changes().contains(new GitTestChanges.Change("D", "module/src/test/java/DeletedTest.java")));
        assertTrue(snapshot.changes().contains(new GitTestChanges.Change("A", "module/src/test/java/NewTest.java")));
        assertThrows(Exception.class, () -> GitTestChanges.read(root, "absent-ref"));
        var mapper = new ObjectMapper();
        var policy = mapper.createObjectNode().put("schemaVersion", 1).put("base", base);
        policy.putArray("controls");
        assertFalse(validate(root, snapshot, policy, LocalDate.now()).isEmpty());
        var row = policy.withArray("controls").addObject().put("path", "module/src/test/java/FirstTest.java")
                .put("owner", "fixture").put("reason", "Keep criterion").put("criterion", "Exact scoped edit")
                .put("proof", "fixture").put("expires", LocalDate.now().plusDays(1).toString()).put("sha256", sha(first));
        var one = new GitTestChanges.Snapshot(base, List.of(new GitTestChanges.Change("M", "module/src/test/java/FirstTest.java")));
        assertEquals(List.of(), validate(root, one, policy, LocalDate.now()));
        Files.writeString(first, "class FirstTest { int changedAfterReview; }");
        assertFalse(validate(root, one, policy, LocalDate.now()).isEmpty());
        row.put("sha256", sha(first));
        row.put("expires", LocalDate.now().minusDays(1).toString());
        assertFalse(validate(root, one, policy, LocalDate.now()).isEmpty());
        assertFalse(validate(root, new GitTestChanges.Snapshot("0".repeat(40), one.changes()), policy, LocalDate.now()).isEmpty());
        row.put("expires", LocalDate.now().plusDays(1).toString());
        var deletion = new GitTestChanges.Snapshot(base, List.of(new GitTestChanges.Change("D", "module/src/test/java/FirstTest.java")));
        assertFalse(validate(root, deletion, policy, LocalDate.now()).isEmpty());
        row.put("allowDelete", true);
        assertEquals(List.of(), validate(root, deletion, policy, LocalDate.now()));
        policy.withArray("controls").add(row.deepCopy());
        assertFalse(validate(root, one, policy, LocalDate.now()).isEmpty());
    }

    @Test void finalWorktreeStateSupersedesCancelledStagedChanges(@TempDir Path root) throws Exception {
        GitTestChanges.command(root, "init", "-b", "main");
        Path tracked = root.resolve("config/reviewed.json");
        Files.createDirectories(tracked.getParent());
        Files.writeString(tracked, "base\n");
        GitTestChanges.command(root, "add", ".");
        GitTestChanges.command(root, "-c", "user.name=Governance fixture", "-c", "user.email=fixture@invalid",
                "-c", "commit.gpgsign=false", "commit", "-m", "base files");
        String base = GitTestChanges.command(root, "rev-parse", "HEAD").trim();

        Path stagedThenRemoved = root.resolve("lib/atf-reporting-obsolete.jar");
        Files.createDirectories(stagedThenRemoved.getParent());
        Files.write(stagedThenRemoved, new byte[] { 1, 2, 3 });
        GitTestChanges.command(root, "add", "lib/atf-reporting-obsolete.jar");
        Files.delete(stagedThenRemoved);

        Files.writeString(tracked, "staged\n");
        GitTestChanges.command(root, "add", "config/reviewed.json");
        Files.writeString(tracked, "base\n");

        var changes = GitTestChanges.read(root, base).changes();
        assertEquals(List.of(), changes,
                "Change review describes final source bytes; cancelled index changes are not candidate inputs");
    }
}
