package atf.android.e2e;

import static org.junit.jupiter.api.Assertions.*;
import atf.android.AdbClient;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@AndroidE2eTest
class OriginalBackupObservationE2eTest {
    @Test void repeatedReadOnlyBackupsResumeTheSameSavedHand()throws Exception {
        Path out=Path.of(System.getProperty("atf.match.evidenceDir"));
        AdbClient adb=AdbClient.fromSystemProperties();AndroidUiDriver ui=new AndroidUiDriver(adb,out);
        ui.restartToMainMenu("com.qsoft.android.whist");ui.tapText("Resume Game");
        OriginalWhistProbe probe=new OriginalWhistProbe(adb,ui,out);
        OriginalWhistState first=probe.observe("probe-first");
        OriginalWhistState second=probe.observe("probe-second");
        assertEquals(first.summary(),second.summary());
        for(int seat=0;seat<first.players;seat++)assertEquals(first.hand(seat),second.hand(seat));
    }
}
