package atf.android.e2e;

import atf.android.e2e.support.AndroidUiFlowTrace;
import atf.android.AdbClient;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.ui.model.AtfUiActionRecord;
import atf.reporting.ui.model.AtfUiEvidenceSource;
import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class OriginalRomanianTraceReplayTest {
    @TempDir Path temp;
    private AndroidUiFlowTrace trace(Path out)throws Exception{return new AndroidUiFlowTrace(out,"original-replay","Original game","Test","atf.android.e2e","com.qsoft.android.whist",List.of("original"));}
    private AndroidUiFlowTrace prior()throws Exception {
        var flow=trace(temp.resolve("prior"));Path image=flow.manifest().getParent().resolve("captured.png"),support=image.resolveSibling("captured.xml");Files.write(image,new byte[]{1});Files.writeString(support,"<hierarchy/>");
        var ui=driver(flow);ui.recordAction("Submit bid","Bid1","accepted","tap accepted",7,AtfReportStatus.PASS);
        flow.checkpoint("TEST BODY","THEN","Observed bid","Original Android",image,List.of(new AtfUiActionRecord(flow.nextActionSequence(),"Observe","Original Android","bid1","bid1",2,AtfReportStatus.PASS,AtfUiEvidenceSource.OBSERVE)),List.of(new AtfAssertion("Bid accepted","1","1",true)),AtfReportStatus.PASS,List.of(support));
        ui.recordAction("Uncheckpointed input","own card","accepted","tap returned",5,AtfReportStatus.PASS);flow.fail(new IllegalStateException("capture failed after input"));return flow;
    }
    private AndroidUiDriver driver(AndroidUiFlowTrace flow)throws Exception {
        var ui=new AndroidUiDriver(new AndroidUiDriver.Device(){
            public AdbClient.Result run(String... arguments){throw new AssertionError("This component must not issue a device command");}
            public AdbClient.BinaryResult runBinary(String... arguments){throw new AssertionError("This component must not issue a device command");}
        },flow.manifest().getParent());ui.recordActionsAs(flow::recordAction);return ui;
    }
    private void copyEvidence(Path out)throws Exception {Files.createDirectories(out);for(String name:List.of("captured.png","captured.xml"))Files.copy(temp.resolve("prior").resolve(name),out.resolve(name));}

    @Test void importsExactCheckpointEvidenceKeepsPriorFailureSeparateAndAdvancesPastPendingSequences()throws Exception {
        var old=prior();Path out=temp.resolve("continued");copyEvidence(out);var resumed=trace(out);
        var result=OriginalRomanianTraceReplay.append(old.manifest(),out,resumed);
        assertEquals(AtfReportStatus.FAIL,result.priorStatus());assertTrue(result.priorFailure().contains("capture failed"));assertEquals(1,result.checkpoints());assertEquals(3,result.maxActionSequence());
        var before=old.phaseSnapshots().getFirst().checkpoints().getFirst();var after=resumed.phaseSnapshots().getFirst().checkpoints().getFirst();
        assertEquals(before.actions(),after.actions());assertEquals(before.assertions(),after.assertions());assertEquals(before.status(),after.status());assertEquals(out.resolve("captured.png").toAbsolutePath(),after.screenshot());
        assertEquals("",resumed.failure());assertTrue(resumed.pendingActions().isEmpty());
        driver(resumed).recordAction("Recovered input receipt","own card","accepted once","public receipt verified",1,AtfReportStatus.PASS);
        assertEquals(4,resumed.pendingActions().getFirst().sequence(),"A fresh driver's positive sequence is renumbered after all prior captured and pending actions");
        resumed.checkpoint("TEST BODY","WHEN","Recovered observed card","Original Android",out.resolve("captured.png"),List.of(),List.of(new AtfAssertion("Accepted once","1","1",true)),AtfReportStatus.PASS,List.of());
        assertEquals(4,resumed.phaseSnapshots().getFirst().checkpoints().get(1).actions().getFirst().sequence());assertEquals(5,resumed.nextActionSequence());
        resumed.finish(AtfReportStatus.PASS);assertEquals(AtfReportStatus.PASS,resumed.status());
        String recovery=Files.readString(result.recoveryMetadata());assertTrue(recovery.contains("capture failed"));assertTrue(recovery.contains("Uncheckpointed input"));assertEquals(AtfReportStatus.FAIL,old.status());
    }
    @Test void unknownStatusesAndEvidenceOutsideTheNewOutputAreRejectedBeforeAnyCheckpointImport()throws Exception {
        var old=prior();var p=new Properties();try(var in=Files.newInputStream(old.manifest())){p.load(in);}
        for(String value:List.of("UNKNOWN_STATUS","../outside.png")) {
            Path out=temp.resolve("rejected-"+value.hashCode());copyEvidence(out);var target=trace(out);
            var changed=new Properties();changed.putAll(p);changed.setProperty(value.equals("UNKNOWN_STATUS")?"phase.0.step.0.status":"phase.0.step.0.screenshot",value);
            Path file=temp.resolve("prior-"+value.hashCode()+".properties");try(var stream=Files.newOutputStream(file)){changed.store(stream,"Invalid input");}
            assertThrows(IOException.class,()->OriginalRomanianTraceReplay.append(file,out,target));assertTrue(target.phaseSnapshots().isEmpty());
        }
    }
}
