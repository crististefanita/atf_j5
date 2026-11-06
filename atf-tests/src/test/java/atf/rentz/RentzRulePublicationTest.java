package atf.rentz;

import atf.reporting.contract.*;
import atf.reporting.model.timing.ReportPhaseTiming;
import atf.testkit.assertions.*;
import atf.testkit.timing.InvocationTimer;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.html.HtmlReportSession;
import atf.reporting.artifact.*;
import static org.junit.jupiter.api.Assertions.*;

class RentzRulePublicationTest {
    static final class Probe {
        final List<String> events=new ArrayList<>();
        final List<AtfAssertion> assertions=new ArrayList<>();
        ReportPhaseTiming timing; String diagnostic=""; String failOn=""; boolean closeFails;
        @SuppressWarnings("unchecked") <T>T port(Class<T> type){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            String name=method.getName();events.add(type.getSimpleName()+"."+name);
            if(name.equals(failOn))throw new IllegalStateException("publication");
            if(name.equals("close")&&closeFails)throw new IllegalStateException("close-"+type.getSimpleName());
            if(name.equals("startScenario"))return port(AtfReportPorts.Scenario.class);
            if(name.equals("startPhase"))return port(AtfReportPorts.Phase.class);
            if(name.equals("startAssertionStep"))return port(AtfReportPorts.AssertionStep.class);
            if(name.equals("assertions"))assertions.addAll((Collection<AtfAssertion>)args[0]);
            if(name.equals("executionTiming"))timing=(ReportPhaseTiming)args[0];
            if(name.equals("diagnostic"))diagnostic=(String)args[1];
            if(name.equals("complete"))events.add(type.getSimpleName()+"="+args[0]);
            return method.getReturnType()==boolean.class?false:null;
        });}
        RentzRulePublication publisher(){return new RentzRulePublication(new RentzRulePublication.Report(port(AtfReportPorts.Session.class)),"fixture");}
    }
    static ComponentAssertionAudit.Result result(ComponentAssertionAudit.Phase phase,Throwable failure){
        var audit=new ComponentAssertionAudit(new AssertionValueDisplay(240,(field,value)->value));audit.start(phase);
        try{audit.recorder(phase).equals("unicode – ș ț","expected",failure==null?"expected":"actual");}catch(AssertionError expected){}
        audit.timing(phase,new InvocationTimer.Snapshot(Duration.ofNanos(123456789),2));
        if(failure!=null)audit.failed(phase,failure);return audit.finish(failure);
    }
    @Test void forwardsExecutionTimingWithoutMeasuringRendererAsExecution(){
        var probe=new Probe();probe.publisher().publish(result(ComponentAssertionAudit.Phase.TEST_BODY,null));
        assertEquals(new ReportPhaseTiming(Duration.ofNanos(123456789),2),probe.timing);
    }
    @Test void publishesTheDiagnosticInTheActualFailedLifecyclePhase(){
        var probe=new Probe();probe.publisher().publish(result(ComponentAssertionAudit.Phase.SETUP,new IllegalStateException("setup failed")));
        assertTrue(probe.diagnostic.contains("setup failed"));assertTrue(probe.events.contains("Phase=FAIL"));
    }
    @Test void keepsEvaluatedValuesAndScenarioStatusWithoutRecomputingThem(){
        var probe=new Probe();probe.publisher().publish(result(ComponentAssertionAudit.Phase.TEST_BODY,new AssertionError("mismatch")));
        assertEquals(1,probe.assertions.size());assertTrue(probe.assertions.getFirst().toString().contains("expected"));
        assertTrue(probe.assertions.getFirst().toString().contains("actual"));assertTrue(probe.events.contains("Scenario=FAIL"));
        assertFalse(probe.events.contains("Session.close"),"Each invocation must not close the shared report");
    }
    @Test void publicationFailureKeepsItsCauseAndClosesEveryAcquiredScope(){
        var probe=new Probe();probe.failOn="assertions";probe.closeFails=true;
        var failure=assertThrows(IllegalStateException.class,()->probe.publisher().publish(result(ComponentAssertionAudit.Phase.TEST_BODY,null)));
        assertEquals("publication",failure.getMessage());assertEquals(3,failure.getSuppressed().length);
        assertTrue(probe.events.containsAll(List.of("AssertionStep.close","Phase.close","Scenario.close")));
    }
    @Test void physicalJarPublishesTwoInvocationsInExtentAndFallback(@TempDir Path folder)throws Exception{
        for(var renderer:ReportArtifactProfile.Renderer.values()){
            Path output=folder.resolve(renderer.name()+".html");
            AtfReportPorts.Session session=renderer==ReportArtifactProfile.Renderer.EXTENT?new ExtentReportSession(output,ReportingSettings.defaults()):new HtmlReportSession(output,ReportingSettings.defaults());
            try(var report=new RentzRulePublication.Report(session)){
                var publisher=new RentzRulePublication(report,"fixture");publisher.publish(result(ComponentAssertionAudit.Phase.TEST_BODY,null));publisher.publish(result(ComponentAssertionAudit.Phase.TEST_BODY,new AssertionError("controlled mismatch")));
            }
            var scenarios=new ArrayList<ReportArtifactProfile.Scenario>();
            for(var status:List.of(AtfReportStatus.PASS,AtfReportStatus.FAIL))scenarios.add(new ReportArtifactProfile.Scenario("fixture","Rentz rule contracts – laboratory profile, reference parity unconfirmed",status,List.of(new ReportArtifactProfile.Phase("TEST_BODY",status,List.of(new ReportArtifactProfile.Assertions("Comparații evaluate prin Test Kit",List.of(new ReportArtifactProfile.Comparison("unicode – ș ț",status))))))));
            var inspection=ReportArtifactInspector.inspect(output,new ReportArtifactProfile(renderer,scenarios));
            assertTrue(inspection.isCompliant(),()->renderer+": "+inspection.violations()+" "+inspection.diagnostics());
            var html=Files.readString(output);assertTrue(html.contains("expected"));assertTrue(html.contains("actual"));
            Path changed=folder.resolve(renderer+"-mutant.html");Files.writeString(changed,html.replace("</head>","<script src=\"file:///missing.js\"></script></head>"));
            assertFalse(ReportArtifactInspector.inspect(changed,new ReportArtifactProfile(renderer,scenarios)).isCompliant(),"External resource mutant must be refused");
        }
    }
}
