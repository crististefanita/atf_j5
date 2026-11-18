package atf.rentz.browser.reporting;

import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.*;
import atf.reporting.evidence.ReportScreenshotPolicy;
import atf.reporting.evidence.profile.ReportEvidenceProfile;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.*;
import atf.reporting.ui.UiExtentReportSession;
import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Publishes actual MCP checkpoints and evaluated component comparisons through the declared Reporting JAR. */
public final class RentzWebAcceptanceReport {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        var run=JSON.readTree(root.resolve("checkpoints.json").toFile());
        if(!run.isArray() || run.isEmpty()) throw new IllegalArgumentException("Executed MCP checkpoints required");
        try(var report=new ExtentReportSession(root.resolve("web-extent.html"),ReportingSettings.defaults())) {
            try(var files=Files.list(root.resolve("components"))) {
                for(var file:files.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                    var result=JSON.readTree(file.toFile());
                    var assertions=checks(result.required("checks"));
                    try(var scenario=report.startScenario(result.required("name").asText(),"Fast components: real compiled engine and shared DOM renderer; no browser",List.of("component","web"));
                        var phase=scenario.startPhase("TEST BODY")) {
                        AssertionAuditReport.publishStep(phase,"Evaluated expected/actual comparisons",assertions,AtfReportStatus.INFO);
                        var status=status(assertions);phase.complete(status);scenario.complete(status);
                    }
                }
            }
            var raw=report.startScenario("RentzFourSeatMixedContractWebFlow","Real UI controls: complete Rentz, another contract, P/T history and reload. Each checkpoint identifies MCP or offline mobile Chrome. APK qualification is separate.",List.of("e2e","web","4-seats"));
            try(var ui=UiExtentReportSession.adaptScenario(raw,ReportEvidenceProfile.forScreenshotPolicy(ReportScreenshotPolicy.EACH_CHECKPOINT),"Original browser JPEG/PNG; decoded losslessly to PNG for the reporting contract")) {
                boolean good=true;
                try(var phase=ui.startPhase("TEST BODY")) {
                    for(var point:run) {
                        var assertions=checks(point.required("checks"));
                        good &= status(assertions)==AtfReportStatus.PASS;
                        Path original=contained(root,point.required("image").asText());
                        var decoded=ImageIO.read(original.toFile());
                        if(decoded==null) throw new IllegalArgumentException("Original browser screenshot required");
                        var png=new ByteArrayOutputStream();ImageIO.write(decoded,"png",png);
                        phase.step(AtfGherkinKeyword.THEN,point.required("title").asText(),point.path("source").asText("Browser MCP visible DOM"),Base64.getEncoder().encodeToString(png.toByteArray()),assertions,List.of(),ReportScreenshotPolicy.CapturePoint.CHECKPOINT,status(assertions));
                        phase.observation(new ReportObservation("Original browser observation",List.of(new ReportField("Original capture",original.toString())),
                            List.of(new ReportObservation.Body("Observed UI",point.required("snapshot").asText(),ReportObservation.Format.TEXT),
                                new ReportObservation.Body("Public UI actions",point.required("actions").toString(),ReportObservation.Format.JSON)),List.of()));
                    }
                    if(Files.exists(root.resolve("source-manifest.json"))) phase.observation(new ReportObservation("Source provenance",List.of(),List.of(new ReportObservation.Body("Manifest",Files.readString(root.resolve("source-manifest.json")),ReportObservation.Format.JSON)),List.of()));
                    phase.complete(good?AtfReportStatus.PASS:AtfReportStatus.FAIL);
                }
                ui.complete(good?AtfReportStatus.PASS:AtfReportStatus.FAIL);
            }
        }
    }
    private static AtfReportStatus status(List<AtfAssertion> checks) { return checks.stream().allMatch(AtfAssertion::passed)?AtfReportStatus.PASS:AtfReportStatus.FAIL; }
    private static List<AtfAssertion> checks(JsonNode rows) {
        if(!rows.isArray() || rows.isEmpty()) throw new IllegalArgumentException("Captured comparisons required");
        var checks=new ArrayList<AtfAssertion>();
        for(var row:rows) checks.add(new AtfAssertion(row.required("name").asText(),row.required("expected").asText(),row.required("actual").asText(),row.required("passed").asBoolean()));
        return checks;
    }
    private static Path contained(Path root,String name) {
        Path path=root.resolve(name).normalize();
        if(Path.of(name).isAbsolute() || !path.startsWith(root) || path.equals(root)) throw new IllegalArgumentException("Evidence must stay inside the run directory");
        return path;
    }
}
