package atf.rentz;

import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.*;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.timing.ReportPhaseTiming;
import atf.reporting.model.diagnostic.ReportPhaseDiagnostic;
import atf.testkit.assertions.*;
import java.nio.file.*;
import java.util.*;

/** Maps evaluated Kit checks; never recomputes a business verdict or implements a renderer. */
public final class RentzRulePublication implements ComponentAuditExtension.Publication {
    public static final class Report implements AutoCloseable {
        final AtfReportPorts.Session session;
        public Report(AtfReportPorts.Session session) { this.session=Objects.requireNonNull(session); }
        Report() throws Exception {
            Path output=Path.of(System.getProperty("rentz.report.output","target/rentz-rules-extent.html")).toAbsolutePath();
            Files.createDirectories(output.getParent());session=new ExtentReportSession(output,ReportingSettings.defaults());
        }
        public void close(){session.close();}
    }
    private final Report report;
    private final String name;
    private final String scope;
    public RentzRulePublication(Report report,String name){this(report,name,"Rentz rule contracts – laboratory profile, reference parity unconfirmed");}
    public RentzRulePublication(Report report,String name,String scope){this.report=report;this.name=name;this.scope=scope;}
    public static ComponentAuditExtension scoped(Class<?> owner,String filename,String scope){
        if(!filename.matches("[a-z0-9-]+\\.html"))throw new IllegalArgumentException("Safe report name required");
        return ComponentAuditExtension.scoped(owner,Report.class,context->{
            Path directory=Path.of(System.getProperty("rentz.release.report.directory","target/release-contracts")).toAbsolutePath();
            try {
                Files.createDirectories(directory);
                return new Report(new ExtentReportSession(directory.resolve(filename),ReportingSettings.defaults()));
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException("Cannot initialize report directory: " + directory, failure);
            }
        },(report,context)->new RentzRulePublication(report,context.getDisplayName(),scope));
    }
    public AssertionValueDisplay display(){return new AssertionValueDisplay(240,(field,value)->value);}
    public ComponentAssertionAudit.AssertionRequirement assertionRequirement(){return ComponentAssertionAudit.AssertionRequirement.TEST_BODY;}
    public void publish(ComponentAssertionAudit.Result result) {
        var status=AtfReportStatus.valueOf(result.outcome()==ComponentAssertionAudit.Outcome.ABORTED?"SKIP":result.outcome().name());
        try(var scenario=report.session.startScenario(name,scope,List.of("component","rentz","laboratory"))){
            for(var phaseResult:result.phases()){
                var phaseStatus=AtfReportStatus.valueOf(phaseResult.outcome()==ComponentAssertionAudit.Outcome.ABORTED?"SKIP":phaseResult.outcome().name());
                try(var phase=scenario.startPhase(phaseResult.phase().name());var checks=phase.startAssertionStep("Comparații evaluate prin Test Kit")){
                    phaseResult.timing().ifPresent(timing->phase.executionTiming(new ReportPhaseTiming(timing.elapsed(),timing.intervals())));
                    if(!phaseResult.diagnostic().isBlank())phase.diagnostic(phaseResult.outcome()==ComponentAssertionAudit.Outcome.ABORTED?ReportPhaseDiagnostic.Kind.EXECUTION_ABORTED:ReportPhaseDiagnostic.Kind.EXECUTION_FAILURE,phaseResult.diagnostic());
                    checks.assertions(phaseResult.checks().stream().map(c->new AtfAssertion(c.name(),c.expected(),c.actual(),c.passed())).toList());
                    checks.complete(phaseStatus);phase.complete(phaseStatus);
                }
            }
            scenario.complete(status);
        }
    }
    public void close(){ }
}
