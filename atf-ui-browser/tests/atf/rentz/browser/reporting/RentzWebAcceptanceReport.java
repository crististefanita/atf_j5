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
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !"--components-only".equals(args[1])))
            throw new IllegalArgumentException("Usage: RentzWebAcceptanceReport <evidence-root> [--components-only]");
        boolean componentsOnly = args.length == 2;
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        var run=componentsOnly ? JSON.createArrayNode() : JSON.readTree(root.resolve("checkpoints.json").toFile());
        if(!componentsOnly && (!run.isArray() || run.isEmpty())) throw new IllegalArgumentException("Executed MCP checkpoints required");
        try(var report=new ExtentReportSession(root.resolve(componentsOnly ? "web-components-extent.html" : "web-extent.html"),ReportingSettings.defaults())) {
            try(var files=Files.list(root.resolve("components"))) {
                var inputs=files.filter(p->p.toString().endsWith(".json")).sorted().toList();
                if (componentsOnly && inputs.isEmpty()) throw new IllegalArgumentException("Executed component comparisons required");
                for(var file:inputs) {
                    var result=JSON.readTree(file.toFile());
                    var assertions=checks(result.required("checks"));
                    try(var scenario=report.startScenario(result.required("name").asText(),"Fast components: real compiled engine and shared DOM renderer; no browser",List.of("component","web"));
                        var phase=scenario.startPhase("TEST BODY")) {
                        AssertionAuditReport.publishStep(phase,"Evaluated expected/actual comparisons",assertions,AtfReportStatus.INFO);
                        if(Files.exists(root.resolve("source-manifest.json"))) phase.observation(new ReportObservation("Source provenance",List.of(),List.of(new ReportObservation.Body("Manifest",Files.readString(root.resolve("source-manifest.json")),ReportObservation.Format.JSON)),List.of()));
                        var status=status(assertions);phase.complete(status);scenario.complete(status);
                    }
                }
            }
            if (componentsOnly) return;
            var raw=report.startScenario("RentzFourSeatMixedContractWebFlow","Real UI controls: complete Rentz, another contract, Δ/Σ history and reload. Each checkpoint identifies MCP or offline mobile Chrome. APK qualification is separate.",List.of("e2e","web","4-seats"));
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
                        phase.step(AtfGherkinKeyword.THEN,checkpointTitle(point),point.path("source").asText("Browser MCP visible DOM"),Base64.getEncoder().encodeToString(png.toByteArray()),assertions,List.of(),ReportScreenshotPolicy.CapturePoint.CHECKPOINT,status(assertions));
                        phase.observation(new ReportObservation("Original browser observation",List.of(new ReportField("Original capture",original.toString()),new ReportField("Recorded checkpoint title",point.required("title").asText())),
                            List.of(new ReportObservation.Body("Observed UI",point.required("snapshot").asText(),ReportObservation.Format.TEXT),
                                new ReportObservation.Body("Public UI actions",point.required("actions").toString(),ReportObservation.Format.JSON)),List.of()));
                    }
                    if(Files.exists(root.resolve("source-manifest.json"))) phase.observation(new ReportObservation("Source provenance",List.of(),List.of(new ReportObservation.Body("Manifest",Files.readString(root.resolve("source-manifest.json")),ReportObservation.Format.JSON)),List.of()));
                    if(Files.exists(root.resolve("presentation-provenance.json"))) phase.observation(new ReportObservation("Report generation provenance",List.of(new ReportField("Execution","Recorded browser checkpoints; this report does not rerun browser acceptance")),List.of(new ReportObservation.Body("Original execution and current report adapter",Files.readString(root.resolve("presentation-provenance.json")),ReportObservation.Format.JSON)),List.of()));
                    phase.complete(good?AtfReportStatus.PASS:AtfReportStatus.FAIL);
                }
                ui.complete(good?AtfReportStatus.PASS:AtfReportStatus.FAIL);
            }
        }
    }
    private static String checkpointTitle(JsonNode point) {
        String original=point.required("title").asText();
        var legacy=java.util.regex.Pattern.compile("^Complete contract ([0-9]+): .+$").matcher(original);
        return legacy.matches()?"Complete contract "+legacy.group(1):original;
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
