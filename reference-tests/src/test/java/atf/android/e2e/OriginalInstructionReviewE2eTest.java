package atf.android.e2e;

import static org.junit.jupiter.api.Assertions.*;
import atf.android.AdbClient;
import atf.android.e2e.support.AndroidUiFlowTrace;
import atf.reporting.contract.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Read-only menu review: does not start a new original-app match or alter its settings. */
@AndroidE2eTest
class OriginalInstructionReviewE2eTest {
    @Test void readsEveryInstructionSectionFromTheInstalledOriginalMenu()throws Exception{
        Path out=Path.of(System.getProperty("atf.android.artifacts"));Files.createDirectories(out);
        AdbClient adb=AdbClient.fromSystemProperties();var ui=new AndroidUiDriver(adb,out);
        var trace=new AndroidUiFlowTrace(out,"original-instructions-review","Original Instructions — current installed text and component rule contract",getClass().getSimpleName(),"atf.android.e2e","com.qsoft.android.whist",List.of("original","instructions","read-only","rules"));
        try{
            ui.restartToMainMenu("com.qsoft.android.whist");ui.capture("original-menu");
            CompleteOriginalMatchE2eTest.checkpoint(trace,out,"SETUP","Original menu exposes Instructions","original-menu","Read the current installed app; do not start a match",List.of(new AtfAssertion("Instructions available","Instructions","Instructions",ui.hasText(ui.dump(),"Instructions"))));
            ui.tapText("Instructions");ui.awaitContains("Bidding Phase",Duration.ofSeconds(15));
            LinkedHashSet<String> paragraphs=new LinkedHashSet<>();String previous="";int stable=0,pages=0;
            for(int page=0;page<16;page++){
                String xml=ui.dump();assertTrue(ui.hasText(xml,"Instructions"),"Instructions lost foreground; another controller may be using the shared emulator");paragraphs.addAll(ui.texts(xml));String label=String.format("original-instructions-page%02d",page);ui.capture(label);pages++;
                record(trace,out,"TEST BODY","Read original instruction viewport "+(page+1),label,String.join("\n",ui.texts(xml)),List.of());
                if(xml.equals(previous))stable++;else stable=0;if(stable>=2)break;previous=xml;
                adb.run("shell","input","swipe","540","1550","540","450","500").requireSuccess();Thread.sleep(250);
            }
            assertTrue(stable>=2,"Instruction scroll did not reach a stable bottom within the safety bound");
            String text=String.join("\n\n",paragraphs);Files.writeString(out.resolve("original-instructions-full.txt"),text);var checks=new ArrayList<AtfAssertion>();
            for(String rule:List.of("2-5 AI opponents","standard deck of playing cards","exact number of tricks","last player to bid cannot bid to make the total bids equal","dealer's left","same suit if possible","discard an unwanted card or play a trump","highest trump wins","there are no trumps","spades, hearts, clubs, diamonds, no trumps","1 point for every trick","additional 10 points","players' scores so far"))checks.add(new AtfAssertion("Instruction: "+rule,rule,"present="+text.contains(rule),text.contains(rule)));
            for(String section:List.of("Interface:","Device Controls:"))checks.add(new AtfAssertion("Complete lower instruction section",section,"present="+text.contains(section),text.contains(section)));
            ui.capture("original-instructions-complete");record(trace,out,"TEST BODY","All original instruction sections collected across "+pages+" viewports","original-instructions-complete",text,checks);
            ui.back();ui.ensureMainMenu();ui.capture("original-menu-return");CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEARDOWN","Return to the menu without starting or replacing a game","original-menu-return","Only menu navigation and instruction scrolling were performed",List.of(new AtfAssertion("Main menu restored","New Game","New Game",ui.hasText(ui.dump(),"New Game"))));
            trace.finish(AtfReportStatus.PASS);
        }catch(Throwable t){trace.fail(t);throw t;}
    }
    private static void record(AndroidUiFlowTrace trace,Path out,String phase,String title,String label,String text,List<AtfAssertion> checks)throws Exception{
        boolean pass=checks.stream().allMatch(AtfAssertion::passed);var status=pass?AtfReportStatus.PASS:AtfReportStatus.FAIL;
        var observation=new atf.reporting.ui.model.AtfUiActionRecord(trace.nextActionSequence(),"Read original instruction viewport","Original Instructions","retain the current text and PNG; no game action",text,0,status,atf.reporting.ui.model.AtfUiEvidenceSource.OBSERVE);
        trace.checkpoint(phase,"THEN",title,"Original Instructions",out.resolve(label+".png"),List.of(observation),checks,status,List.of(out.resolve(label+".xml")));
        assertTrue(pass,()->"Instruction coverage mismatch: "+checks.stream().filter(a->!a.passed()).toList());
    }
}
