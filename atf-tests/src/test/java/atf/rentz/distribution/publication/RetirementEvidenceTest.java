package atf.rentz.distribution.publication;

import atf.rentz.RentzRulePublication;
import atf.rentz.distribution.retirement.RetirementEvidence;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.ComponentAuditExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static atf.testkit.assertions.AuditedAssertions.*;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256;

@AtfComponentTest
class RetirementEvidenceTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            RetirementEvidenceTest.class, "retirement-proof.html", "Original RAG, Extent and download bytes; no remote mutation");
    @TempDir Path folder;
    static final String SOURCE = "a".repeat(40), MAIN = "b".repeat(40), REPO = "example/rentz", VERSION = "0.29.0";
    static final String APK = "RentzLab-0.29.0.apk", URL = "https://github.com/example/rentz/releases/download/v0.29.0/" + APK;
    final ObjectMapper json = new ObjectMapper();

    @Test void invalidProofProducesABlockedReceiptAndExtentBeforeOpeningTheRemoteChannel() throws Exception {
        var f = fixture(); ((ObjectNode) f.readmeJson.path("retirement")).put("allowed",false); f.saveReadme();
        Path output = folder.resolve("attempt");
        var refused = assertThrows(IOException.class, () -> RetirementCommand.main(new String[]{f.root.toString(), f.rag.toString(),
                f.readme.toString(), folder.toString(), f.components.toString(), f.nativeExtent.toString(),
                folder.resolve("never-read-tool-receipt.json").toString(), output.toString()}));
        assertEquals("RETIREMENT-README-PROOF", refused.getMessage(), "Refused before tools, credentials and transport");
        var receipt = json.readTree(output.resolve("release-retirement.json").toFile());
        assertEquals("BLOCKED", receipt.path("status").asText(), "Refusal remains a separate attempted-operation receipt");
        assertTrue(Files.size(output.resolve("release-retirement-extent.html")) > 0, "The refused command is auditable in Extent");
        assertTrue(Files.notExists(output.resolve("downloads")), "No remote download or mutation was attempted");
    }

    @Test void readmeVerifierAdmitsPublicAndPrivateChannelsWithoutAdmittingStaleOrUnboundMetadata() throws Exception {
        Path root = atf.rentz.distribution.ReleaseCatalog.root(), output = folder.resolve("eligibility.json");
        var process = new ProcessBuilder("pwsh", "-NoLogo", "-NoProfile", "-File",
                root.resolve("atf-tests/src/test/resources/retirement-eligibility.ps1").toString(),
                "-Project", root.toString(), "-Output", output.toString())
                .redirectErrorStream(true).redirectOutput(folder.resolve("eligibility.log").toFile()).start();
        boolean ended = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        if (!ended) process.destroyForcibly();
        assertTrue(ended, "Eligibility fixture deadline");
        assertEquals(0, process.exitValue(), "Actual source-owned PowerShell policy executes offline");
        var rows = json.readTree(output.toFile()); assertEquals(9, rows.size(), "Both valid channels and every negative neighbor execute");
        for (var row : rows) assertEquals(row.path("expected"), row.path("actual"), "Source policy: " + row.path("case").asText());
    }

    @Test void bothOriginalReportsAndBothAcquiredPayloadsAdmitTheExactSourceWithoutChangingAnyProof() throws Exception {
        var fixture = fixture();
        byte[] original = Files.readAllBytes(fixture.rag);
        var admitted = fixture.read();
        assertEquals(SOURCE, admitted.plan().source(), "Retirement binds original product source, not current HEAD");
        assertEquals(MAIN, admitted.plan().main(), "Remote README head is a separate identity");
        assertEquals(sha256(original), admitted.qualificationSha256(), "Original qualification is preserved");
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(fixture.rag)), "Admission never rewrites original proof");
    }
    @ParameterizedTest @ValueSource(strings={"missing-report","report-bytes","report-source","report-status","gate-fail",
            "duplicate-gate","eligibility","qualification-hash","anonymous-bytes","authenticated-bytes","download-sha"})
    void incompleteOrSubstitutedEvidenceIsRefusedBeforeAnyTransportCanBeConstructed(String defect) throws Exception {
        var f = fixture();
        switch (defect) {
            case "missing-report" -> Files.delete(f.nativeExtent);
            case "report-bytes" -> Files.writeString(f.components, "substituted");
            case "anonymous-bytes" -> Files.writeString(folder.resolve("anonymous-" + APK), "other");
            case "authenticated-bytes" -> Files.writeString(folder.resolve("authenticated-" + APK), "other");
            case "report-source" -> { ((ObjectNode) f.ragJson.path("gates").get(1)).put("artifactSourceCommit", MAIN); f.saveRag(); }
            case "report-status" -> { ((ObjectNode) f.readmeJson.path("retirement").path("extentEvidence").get(0)).put("status", "FAIL"); f.saveReadme(); }
            case "gate-fail" -> { ((ObjectNode) f.ragJson.path("gates").get(0)).put("status", "FAIL"); f.saveRag(); }
            case "duplicate-gate" -> { f.ragJson.withArray("gates").add(f.ragJson.path("gates").get(0).deepCopy()); f.saveRag(); }
            case "eligibility" -> { ((ObjectNode) f.readmeJson.path("retirement")).put("allowed", false); f.saveReadme(); }
            case "qualification-hash" -> { ((ObjectNode) f.readmeJson.path("retirement")).put("qualificationSha256", "f".repeat(64)); f.saveReadme(); }
            case "download-sha" -> { var p = (ObjectNode) json.readTree(folder.resolve("public-download.json").toFile()); ((ObjectNode) p.path("assets").get(0)).put("sha256", "f".repeat(64)); write(folder.resolve("public-download.json"), p); }
            default -> throw new AssertionError(defect);
        }
        assertThrows(IOException.class, f::read, "Invalid original proof is fail-closed: " + defect);
    }
    @ParameterizedTest @ValueSource(strings={"duplicate", "stale", "extra-release", "extra-copy"})
    void remoteReadmeCannotSmuggleAnOldOrAdditionalDownload(String defect) throws Exception {
        String good = "## Jocul\n\n- [APK](" + URL + ")\n- [Online](https://crististefanita.github.io/rentz/)\n\n## Documentație\n[Livrare](release/README.md)\n";
        assertEquals(URL, GitHubRetirement.readmeLink(good, URL), "Valid neighboring README");
        String bad = switch (defect) {
            case "duplicate" -> good + "\n## Jocul\n";
            case "stale" -> good.replace("0.29.0", "0.28.0");
            case "extra-release" -> good + "[Old](https://github.com/example/rentz/releases/tag/v0.28.0)";
            default -> good.replace("## Documentație", "extra copy\n## Documentație");
        };
        assertThrows(IOException.class, () -> GitHubRetirement.readmeLink(bad, URL), "Remote README contract: " + defect);
    }
    Fixture fixture() throws Exception {
        var f = new Fixture(); Files.createDirectory(f.root);
        Files.writeString(f.components, "original component Extent"); Files.writeString(f.nativeExtent, "original native Extent");
        byte[] apk = "qualified APK fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8); String digest = sha256(apk);
        byte[] checksum = (digest + "  " + APK).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        f.ragJson.putObject("candidate").put("repository", REPO).put("candidateVersion", VERSION).put("sourceCommit", SOURCE);
        f.ragJson.putObject("releaseWorkflow").put("artifactSha256", digest).put("readyForMainPromotionReview", true); f.ragJson.putArray("conflicts");
        var gates = f.ragJson.putArray("gates");
        for (String id : List.of("offline-regression","component-extent-report","android-jvm","native-ui","native-ui-report","release-build","local-catalog")) {
            var gate = gates.addObject().put("id", id).put("status", "PASS");
            if (id.endsWith("report")) {
                Path report = id.startsWith("component") ? f.components : f.nativeExtent;
                gate.put("receipt", report.getFileName().toString()).put("receiptSha256", sha256(Files.readAllBytes(report)))
                        .put("artifactSourceCommit", SOURCE).put("artifactBytes", Files.size(report));
            }
        }
        f.readmeJson.put("gate","readme-publication").put("status","PASS").put("channel","public").put("version",VERSION)
                .put("sourceCommit",SOURCE).put("readmeCommit",MAIN).put("apkSha256",digest).put("directApkUrl",URL)
                .put("authenticatedDownload","PASS").put("anonymousDownload","PASS");
        var retirement = f.readmeJson.putObject("retirement").put("allowed",true).put("preserveSourceTags",true)
                .put("preserveAuditEvidence",true).put("latestTag","v"+VERSION);
        var proofs = retirement.putArray("extentEvidence");
        for (var gate : gates) if (gate.path("id").asText().endsWith("report")) proofs.addObject().put("gate",gate.path("id").asText())
                .put("status","PASS").put("sourceCommit",SOURCE).put("sha256",gate.path("receiptSha256").asText()).put("bytes",gate.path("artifactBytes").asLong());
        f.saveRag();
        var github = json.createObjectNode().put("stage","PUBLISHED_AND_AUTHENTICATED_DOWNLOAD_VERIFIED").put("repository",REPO)
                .put("sourceCommit",SOURCE).put("tag","v"+VERSION).put("latest","v"+VERSION).put("apkSha256",digest).put("releaseId",29);
        var anonymous = json.createObjectNode().put("stage","ANONYMOUS_DOWNLOAD_VERIFIED").put("authenticated",false).put("bothApkArchivesVerified",true);
        var authAssets = github.putArray("assets"); var anonAssets = anonymous.putArray("assets");
        for (String name : List.of(APK, APK+".sha256")) {
            byte[] bytes = name.equals(APK) ? apk : checksum;
            for (String channel : List.of("authenticated", "anonymous")) Files.write(folder.resolve(channel+"-"+name),bytes);
            authAssets.addObject().put("name",name).put("http",200).put("bytes",bytes.length).put("sha256",sha256(bytes));
            anonAssets.addObject().put("url",URL+(name.equals(APK)?"":".sha256")).put("http",200).put("bytes",bytes.length).put("sha256",sha256(bytes));
        }
        write(folder.resolve("github-release.json"),github); write(folder.resolve("public-download.json"),anonymous); return f;
    }
    void write(Path file, Object value) throws IOException { json.writeValue(file.toFile(), value); }
    final class Fixture {
        final Path root = folder.resolve("source"), rag = folder.resolve("rag.json"), readme = folder.resolve("readme.json"),
                components = folder.resolve("component-suite-extent.html"), nativeExtent = folder.resolve("android-extent.html");
        final ObjectNode ragJson = json.createObjectNode(), readmeJson = json.createObjectNode();
        void saveRag() throws IOException { write(rag, ragJson); ((ObjectNode) readmeJson.path("retirement")).put("qualificationSha256",sha256(Files.readAllBytes(rag))); saveReadme(); }
        void saveReadme() throws IOException { write(readme, readmeJson); }
        RetirementEvidence.Qualified read() throws IOException { return RetirementEvidence.read(root,rag,readme,folder,components,nativeExtent); }
    }
}
