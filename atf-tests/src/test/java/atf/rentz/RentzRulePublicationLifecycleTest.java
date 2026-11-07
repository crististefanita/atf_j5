package atf.rentz;

import atf.testkit.AtfComponentTest;

import atf.testkit.assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class RentzRulePublicationLifecycleTest {
    static RentzRulePublicationTest.Probe probe;
    static IllegalStateException primary;
    static boolean openFails;
    static TestExecutionSummary run(Class<?> fixture){
        probe=new RentzRulePublicationTest.Probe();primary=new IllegalStateException("body-primary");
        var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture)).build(),listener);return listener.getSummary();
    }
    static class Base {
        @RegisterExtension static final ComponentAuditExtension audit=new ComponentAuditExtension(Base.class,context->{
            if(openFails)throw new IllegalStateException("prepare-primary");return probe.publisher();
        });
        @Test void body(){AuditedAssertions.assertEquals("ready","ready");}
    }
    static class Setup extends Base {@BeforeEach void before(){throw new IllegalStateException("setup-primary");}}
    static class Body extends Base {@Override @Test void body(){throw primary;}}
    static class Teardown extends Base {@AfterEach void after(){throw new IllegalStateException("teardown-primary");}}
    static class Caught extends Base {@Override @Test void body(){try{AuditedAssertions.assertEquals("ready","different");}catch(AssertionError ignored){}}}
    static class Aborted extends Base {@Override @Test void body(){Assumptions.abort("controlled abort");}}
    static class Combined extends Base {
        @Override @Test void body(){probe.failOn="assertions";probe.closeFails=true;throw primary;}
        @AfterEach void after(){throw new IllegalStateException("teardown-secondary");}
    }
    static class ReportClose extends Base {
        @AfterAll static void closeReport(){probe.closeFails=true;new RentzRulePublication.Report(probe.port(atf.reporting.contract.AtfReportPorts.Session.class)).close();}
    }
    @Test void setupBodyAndTeardownFailuresRemainFailuresInTheirOwnPhases(){
        for(Class<?> fixture:new Class<?>[]{Setup.class,Body.class,Teardown.class}){
            var summary=run(fixture);assertEquals(1,summary.getTestsFailedCount());assertTrue(probe.events.contains("Scenario=FAIL"));assertFalse(probe.diagnostic.isBlank());
        }
    }
    @Test void preparationFailureCannotBecomeAPassingOrPublishedInvocation(){
        openFails=true;try{assertEquals(1,run(Base.class).getTestsFailedCount());assertTrue(probe.events.isEmpty());}finally{openFails=false;}
    }
    @Test void aCaughtFailedComparisonStillFailsTheConsumerInvocation(){
        assertEquals(1,run(Caught.class).getTestsFailedCount());assertTrue(probe.events.contains("Scenario=FAIL"));assertFalse(probe.assertions.getFirst().passed());
    }
    @Test void abortedInvocationsStaySkippedRatherThanInventingPass(){
        assertEquals(1,run(Aborted.class).getTestsAbortedCount());assertTrue(probe.events.contains("Scenario=SKIP"));assertFalse(probe.events.contains("Scenario=PASS"));
    }
    @Test void teardownPublicationAndCloseErrorsDoNotReplaceTheBodyCause(){
        var summary=run(Combined.class);assertEquals(1,summary.getTestsFailedCount());assertSame(primary,summary.getFailures().getFirst().getException());
        assertTrue(contains(primary,"teardown-secondary"));assertTrue(contains(primary,"publication"));assertTrue(contains(primary,"close-"));
        assertTrue(probe.events.containsAll(java.util.List.of("AssertionStep.close","Phase.close","Scenario.close")));
    }
    @Test void sharedReportCloseFailureFailsTheContainerEvenAfterASuccessfulBody(){
        var summary=run(ReportClose.class);assertEquals(1,summary.getTestsSucceededCount());assertEquals(1,summary.getContainersFailedCount());
        assertTrue(contains(summary.getFailures().getFirst().getException(),"close-Session"));assertTrue(probe.events.contains("Phase=FAIL"));assertTrue(probe.diagnostic.contains("close-Session"));
    }
    static boolean contains(Throwable error,String text){if(error.getMessage()!=null&&error.getMessage().contains(text))return true;for(var child:error.getSuppressed())if(contains(child,text))return true;return error.getCause()!=null&&contains(error.getCause(),text);}
}
