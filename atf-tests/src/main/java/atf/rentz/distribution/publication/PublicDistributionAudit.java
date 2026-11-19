package atf.rentz.distribution.publication;

import atf.rentz.distribution.ApkDownloadTarget;
import atf.rentz.distribution.GitHubApkDownload;
import atf.rentz.distribution.ReleaseDownloadAcceptance;
import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Independent authenticated and anonymous acquisition after source-bound publication. */
final class PublicDistributionAudit {
    private PublicDistributionAudit() { }

    static void verify(ReleasePublication.Plan plan, GitHubPublication remote, long id, String token, Path output)
            throws IOException {
        Files.createDirectory(output);
        var target = new ApkDownloadTarget(plan.releaseRepository(), plan.version(), plan.apk().bytes(), plan.apk().sha256());
        var authenticated = new ArrayList<Map<String, Object>>();
        var anonymous = new ArrayList<Map<String, Object>>();
        var mapper = new ObjectMapper();
        remote.verifyPublishedLatest(plan, id);
        List<ReleasePublication.Asset> assets = remote.assets(plan.releaseRepository(), id);
        if (assets.size() != 2) throw new IOException("PUBLICATION-DOWNLOAD-ASSET");
        try (var report = new ExtentReportSession(output.resolve("download-extent.html"), ReportingSettings.defaults());
             var scenario = report.startScenario("Rentz · Public APK acquisition", "Two independent GitHub access modes", List.of("release", "download"))) {
            for (boolean loggedIn : List.of(true, false)) {
                String channel = loggedIn ? "authenticated" : "anonymous";
                try (var phase = scenario.startPhase(channel)) {
                    try {
                        var client = new GitHubApkDownload(loggedIn ? token : null);
                        Path scratch = Files.createDirectory(output.resolve(channel));
                        var apk = client.download(target, scratch);
                        var checksum = client.downloadChecksum(target, scratch);
                        var checks = ReleaseDownloadAcceptance.comparisons(target, apk);
                        AssertionAuditReport.publishStep(phase, "Complete APK comparisons", checks, AtfReportStatus.INFO);
                        if (checks.stream().anyMatch(check -> !check.passed())) throw new IOException("PUBLICATION-DOWNLOAD-IDENTITY");
                        List<Map<String, Object>> rows = loggedIn ? authenticated : anonymous;
                        for (var proof : List.of(apk, checksum)) {
                            String name = proof.file().getFileName().toString();
                            var expected = name.equals(plan.apk().name()) ? plan.apk() : plan.checksum();
                            verifyAsset(expected, proof, assets);
                            var asset = assets.stream().filter(row -> row.name().equals(name)).toList();
                            Files.move(proof.file(), output.resolve(channel + "-" + name));
                            rows.add(Map.of("id", asset.getFirst().id(), "name", name, "url", proof.publicUrl(),
                                    "http", proof.httpStatus(), "bytes", proof.bytes(), "sha256", proof.sha256()));
                        }
                        Files.delete(scratch);
                        phase.complete(AtfReportStatus.PASS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        phase.complete(AtfReportStatus.FAIL);
                        scenario.complete(AtfReportStatus.FAIL);
                        throw new IOException("PUBLICATION-DOWNLOAD-INTERRUPTED", interrupted);
                    } catch (IOException | RuntimeException failed) {
                        phase.complete(AtfReportStatus.FAIL);
                        scenario.complete(AtfReportStatus.FAIL);
                        throw failed;
                    }
                }
            }
            remote.verifyPublishedLatest(plan, id);
            var github = mapper.createObjectNode().put("stage", "PUBLISHED_AND_AUTHENTICATED_DOWNLOAD_VERIFIED")
                    .put("repository", plan.releaseRepository()).put("sourceCommit", plan.commit()).put("releaseId", id)
                    .put("tag", plan.tag()).put("latest", plan.tag()).put("apkSha256", plan.apk().sha256())
                    .put("releaseUrl", "https://github.com/" + plan.releaseRepository() + "/releases/tag/" + plan.tag());
            github.set("assets", mapper.valueToTree(authenticated));
            var publicProof = mapper.createObjectNode().put("stage", "ANONYMOUS_DOWNLOAD_VERIFIED")
                    .put("authenticated", false).put("bothApkArchivesVerified", true).put("verifiedUtc", Instant.now().toString());
            publicProof.set("assets", mapper.valueToTree(anonymous));
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("github-release.json").toFile(), github);
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("public-download.json").toFile(), publicProof);
            scenario.complete(AtfReportStatus.PASS);
        }
    }

    static void verifyAsset(ReleasePublication.Payload expected, GitHubApkDownload.Proof proof,
                            List<ReleasePublication.Asset> assets) throws IOException {
        String name = proof.file().getFileName().toString();
        var matches = assets.stream().filter(row -> row.name().equals(name)).toList();
        if (!expected.name().equals(name) || matches.size() != 1 || matches.getFirst().bytes() != proof.bytes()
                || !matches.getFirst().sha256().equals(proof.sha256()) || expected.bytes() != proof.bytes()
                || !expected.sha256().equals(proof.sha256())) throw new IOException("PUBLICATION-DOWNLOAD-ASSET");
    }
}
