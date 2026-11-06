package atf.rentz.distribution;

import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import atf.reporting.model.diagnostic.ReportPhaseDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Explicit real-network acceptance; never included implicitly in the offline JUnit regression. */
public final class ReleaseDownloadAcceptance {
    public static List<AtfAssertion> comparisons(ApkDownloadTarget target,GitHubApkDownload.Proof proof) {
        Objects.requireNonNull(target);Objects.requireNonNull(proof);
        return List.of(new AtfAssertion("Current version",target.version(),proof.version(),target.version().equals(proof.version())),
                new AtfAssertion("Canonical asset",target.publicUrl(),proof.publicUrl(),target.publicUrl().equals(proof.publicUrl())),
                new AtfAssertion("Completed bytes",Long.toString(target.bytes()),Long.toString(proof.bytes()),target.bytes()==proof.bytes()),
                new AtfAssertion("Downloaded SHA-256",target.sha256(),proof.sha256(),target.sha256().equals(proof.sha256())),
                new AtfAssertion("Every ZIP CRC","PASS",proof.zipCrc(),"PASS".equals(proof.zipCrc())),
                new AtfAssertion("Binary HTTP status","200",Integer.toString(proof.httpStatus()),proof.httpStatus()==200));
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Explicit project root and fresh external output directory required");
        Path root=Path.of(args[0]).toAbsolutePath().normalize(),output=Path.of(args[1]).toAbsolutePath().normalize();
        if(output.startsWith(root) || Files.exists(output))throw new IllegalArgumentException("Fresh external acceptance output required");
        Files.createDirectories(output);
        try(var session=new ExtentReportSession(output.resolve("download-extent.html"),ReportingSettings.defaults());
            var scenario=session.startScenario("RentzReleaseDownload","Authenticated current asset; no browser UI",List.of("integration","distribution"));
            var phase=scenario.startPhase("TEST BODY")) {
            try {
                var edition=ReleaseCatalog.verify(root,true);
                if(!edition.state().equals("PUBLISHED"))throw new IllegalStateException("Published current edition required for live acceptance");
                String token=System.getenv("GH_TOKEN");if(token==null || token.isBlank())token=System.getenv("GITHUB_TOKEN");
                var proof=new GitHubApkDownload(token).download(edition.target(),output.resolve("download"));
                var checks=comparisons(edition.target(),proof);
                AssertionAuditReport.publishStep(phase,"Download assertions",checks,AtfReportStatus.INFO);
                phase.observation(new ReportObservation("Actual downloaded file",List.of(
                        new ReportField("Channel","GitHub API; token restricted to API origin"),
                        new ReportField("Current version",proof.version()),new ReportField("Bytes",Long.toString(proof.bytes())),
                        new ReportField("SHA-256",proof.sha256()),new ReportField("Local file",proof.file().toString()),
                        new ReportField("Browser navigation performed","false")),List.of(),List.of()));
                if(checks.stream().anyMatch(c->!c.passed()))throw new IllegalStateException("Actual download differs from the current declared identity");
                phase.complete(AtfReportStatus.PASS);scenario.complete(AtfReportStatus.PASS);
                System.out.println("PASS: six actual current-version download comparisons; report="+session.reportPath());
            } catch(Exception failure) {
                phase.diagnostic(ReportPhaseDiagnostic.Kind.EXECUTION_FAILURE,failure.getClass().getSimpleName()+": "+
                        String.valueOf(failure.getMessage()).replaceAll("https?://[^\\s]+","<transport-url-redacted>"));
                phase.complete(AtfReportStatus.FAIL);scenario.complete(AtfReportStatus.FAIL);throw failure;
            }
        }
    }
    private ReleaseDownloadAcceptance(){ }
}
