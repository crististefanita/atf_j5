package atf.rentz;

import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.*;
import atf.reporting.extent.session.ExtentReportSession;
import atf.testkit.assertions.*;
import java.nio.file.*;
import java.util.*;

/** Maps evaluated Kit checks; never recomputes a business verdict or implements a renderer. */
final class RentzRulePublication implements ComponentAuditExtension.Publication {
    static final class Report implements AutoCloseable {
        final ExtentReportSession session;
        Report() throws Exception {
            Path output=Path.of(System.getProperty("rentz.report.output","target/rentz-rules-extent.html")).toAbsolutePath();
            Files.createDirectories(output.getParent());session=new ExtentReportSession(output,ReportingSettings.defaults());
        }
        public void close(){session.close();}
    }
    private final Report report;
    private final String name;
    RentzRulePublication(Report report,String name){this.report=report;this.name=name;}
    public AssertionValueDisplay display(){return new AssertionValueDisplay(240,(field,value)->value);}
    public ComponentAssertionAudit.AssertionRequirement assertionRequirement(){return ComponentAssertionAudit.AssertionRequirement.TEST_BODY;}
    public void publish(ComponentAssertionAudit.Result result) {
        var status=AtfReportStatus.valueOf(result.outcome()==ComponentAssertionAudit.Outcome.ABORTED?"SKIP":result.outcome().name());
        try(var scenario=report.session.startScenario(name,"Rentz rule contracts – laboratory profile, reference parity unconfirmed",List.of("component","rentz","laboratory"))){
            for(var phaseResult:result.phases()){
                var phaseStatus=AtfReportStatus.valueOf(phaseResult.outcome()==ComponentAssertionAudit.Outcome.ABORTED?"SKIP":phaseResult.outcome().name());
                try(var phase=scenario.startPhase(phaseResult.phase().name());var checks=phase.startAssertionStep("Comparații evaluate prin Test Kit")){
                    checks.assertions(phaseResult.checks().stream().map(c->new AtfAssertion(c.name(),c.expected(),c.actual(),c.passed())).toList());
                    checks.complete(phaseStatus);phase.complete(phaseStatus);
                }
            }
            scenario.complete(status);
        }
    }
    public void close(){ }
}
