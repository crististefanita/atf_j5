package atf.rentz.distribution.publication;

import atf.rentz.distribution.retirement.ReleaseRetirement;
import atf.rentz.distribution.retirement.RetirementEvidence;

import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.*;

/** Explicit RAG side effect: original local gates, fresh downloads, then coordinated retirement. */
public final class RetirementCommand {
    public static void main(String[] args) throws IOException {
        if (args.length != 8) throw new IllegalArgumentException("RETIREMENT-ARGUMENTS");
        Path root = Path.of(args[0]).toRealPath(), output = Path.of(args[7]).toAbsolutePath().normalize();
        externalRun(root, output.getParent()); assertNoSymlinkPath(output); Files.createDirectory(output);
        var mapper = new ObjectMapper(); var receipt = mapper.createObjectNode().put("status", "BLOCKED")
                .put("preserveSourceTags", true).put("preserveAuditEvidence", true);
        boolean passed = false;
        try {
            var proof = RetirementEvidence.read(root, Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]), Path.of(args[5]));
            // Argument 6 is the immutable current-tool qualification receipt, distinct from product evidence.
            Path tools = Path.of(args[6]); externalRun(root, tools.getParent());
            var qualification = mapper.readTree(readExternalFile(tools, 4 * 1024 * 1024));
            if (!qualification.path("status").asText().equals("PASS")
                    || !qualification.path("collection").path("name").asText().equals("regression")
                    || !qualification.path("componentExtentQualified").asBoolean(false)) throw new IOException("RETIREMENT-TOOL-QUALIFICATION");
            var current = atf.rentz.distribution.ReleaseReadinessRag.evaluate(root, "retirement tool admission", tools.getParent());
            if (!current.path("candidate").path("worktreeState").asText().equals("CLEAN")
                    || !current.path("candidate").path("sourceCommit").asText().equals(qualification.path("sourceCommit").asText()))
                throw new IOException("RETIREMENT-TOOL-SOURCE");
            for (String gate : List.of("offline-regression", "component-extent-report", "android-jvm")) {
                int count = 0;
                for (var row : current.path("gates")) if (row.path("id").asText().equals(gate) && row.path("status").asText().equals("PASS")) count++;
                if (count != 1) throw new IOException("RETIREMENT-TOOL-QUALIFICATION");
            }
            receipt.put("toolQualificationSha256", sha256(readExternalFile(tools, 4 * 1024 * 1024)))
                    .put("qualificationSha256", proof.qualificationSha256());
            receipt.set("identity", mapper.valueToTree(proof.plan()));
            String token = System.getenv("GH_TOKEN");
            if (token == null || token.isBlank()) token = System.getenv("GITHUB_TOKEN");
            var github = new GitHubPublication(root, token);
            // Download again; historical PASS alone never authorizes today's DELETE.
            PublicDistributionAudit.verify(proof.downloads(), github, proof.plan().releaseId(), token, output.resolve("downloads"));
            Path archive = Files.createDirectory(output.resolve("retired-metadata"));
            var channel = new GitPromotionChannel(root, "https://github.com/" + proof.plan().repository() + ".git");
            var removed = ReleaseRetirement.retire(proof.plan(), new GitHubRetirement(github, channel, archive, proof.plan()));
            receipt.set("removedReleaseIds", mapper.valueToTree(removed)); receipt.put("status", "PASS"); passed = true;
        } catch (IOException | RuntimeException failure) {
            String message = failure.getMessage();
            receipt.put("diagnostic", message != null && message.matches("(RETIREMENT|PUBLICATION|RAG)-[A-Z0-9-]+") ? message : "RETIREMENT-INTERRUPTED");
        }
        var archives = receipt.putArray("archives"); Path archiveRoot = output.resolve("retired-metadata");
        try { if (Files.isDirectory(archiveRoot)) try (var files = Files.list(archiveRoot)) {
            for (var file : files.sorted().toList()) {
                byte[] bytes = readExternalFile(file, 8L * 1024 * 1024);
                archives.addObject().put("file",file.getFileName().toString()).put("bytes",bytes.length).put("sha256",sha256(bytes));
            }
        } } catch (IOException | RuntimeException failure) { passed = false; receipt.put("status","BLOCKED").put("diagnostic","RETIREMENT-AUDIT-CAPTURE"); }
        Files.write(output.resolve("release-retirement.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(receipt), StandardOpenOption.CREATE_NEW);
        AtfReportStatus status = passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        try (var report = new ExtentReportSession(output.resolve("release-retirement-extent.html"), ReportingSettings.defaults());
             var scenario = report.startScenario("Rentz · Release retirement", "Original evidence and current channel authority", List.of("rag", "retirement"));
             var phase = scenario.startPhase("Coordinated retirement"); var step = phase.startAssertionStep("Retirement receipt")) {
            step.assertions(List.of(new AtfAssertion("Qualified retirement", "PASS", receipt.path("status").asText() + " " + receipt.path("diagnostic").asText(), passed)));
            phase.observation(new atf.reporting.model.ReportObservation("Source authority and retained audit", List.of(
                    new atf.reporting.model.ReportField("Product identity",receipt.path("identity").toString()),
                    new atf.reporting.model.ReportField("Original qualification SHA-256",receipt.path("qualificationSha256").asText("-")),
                    new atf.reporting.model.ReportField("Tool qualification SHA-256",receipt.path("toolQualificationSha256").asText("-")),
                    new atf.reporting.model.ReportField("Removed release IDs",receipt.path("removedReleaseIds").toString()),
                    new atf.reporting.model.ReportField("Preserved metadata",archives.toString())),List.of(),List.of()));
            step.complete(status); phase.complete(status); scenario.complete(status);
        }
        if (!passed) throw new IOException(receipt.path("diagnostic").asText());
    }
    private RetirementCommand() { }
}
