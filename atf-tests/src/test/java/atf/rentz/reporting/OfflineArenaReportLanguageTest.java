package atf.rentz.reporting;

import atf.rentz.OfflineArena;
import atf.rentz.OfflineArenaReport;
import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.core.ScoreProfile;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class OfflineArenaReportLanguageTest {
    @Test void realOfflineMetricsKeepEnglishNarrativeWithoutChangingTheAssessment(@TempDir Path root) throws Exception {
        var protocol=new OfflineArena.Protocol("report-language","DEVELOPMENT",4,List.of(317L),List.of(OfflineArena.Kind.NORMAL),OfflineArena.Kind.NORMAL,1,0);
        var summary=OfflineArena.run(protocol,ScoreProfile.laboratory(),new PolicySettings(1,.05,3,.3,2),root.resolve("games"),root.resolve("registry.json"),"language-component");
        var assessment=summary.families().getFirst().assessment();
        Path output=root.resolve("arena-extent.html");
        OfflineArenaReport.publish(summary,protocol,output);
        String text=Jsoup.parse(Files.readString(output)).text();
        assertTrue(text.contains("Paired distributions and rotated seats"));
        assertTrue(text.contains("Declared criteria and observed metrics"));
        assertTrue(text.contains("Complete matches audited for transitions and score"));
        assertTrue(text.contains("Original application parity is unconfirmed"));
        assertFalse(text.contains("Criterii declarate"));
        assertEquals(assessment,summary.families().getFirst().assessment());
        assertFalse(assessment.promotes(),"One cluster must not become a promoted candidate when report copy changes");
    }
}
