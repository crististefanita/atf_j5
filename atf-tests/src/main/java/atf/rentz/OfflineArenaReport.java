package atf.rentz;

import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.*;
import atf.reporting.extent.session.ExtentReportSession;
import java.nio.file.Path;
import java.util.*;

/** Consumer data only; scoring, comparison and renderer have separate owners. */
public final class OfflineArenaReport {
    private OfflineArenaReport() { }
    public static void publish(OfflineArena.Summary result,OfflineArena.Protocol protocol,Path output) {
        try(var report=new ExtentReportSession(output,ReportingSettings.defaults())) {
            for(var family:result.families()) {
                String description="Rentz offline; profil de laborator, paritate cu originalul neconfirmată. Protocol="+result.protocol()+"; etapă="+result.phase()+"; surse="+result.sourceIdentity()+". Rotațiile sunt corelate; intervalele între seed-uri sunt descriptive.";
                try(var scenario=report.startScenario("Rentz "+protocol.candidate()+" / "+family.family(),description,List.of("offline","rentz","arena",result.phase().toLowerCase(Locale.ROOT)));
                    var phase=scenario.startPhase("Evaluare pe distribuiri pereche și locuri rotite");var checks=phase.startAssertionStep("Criterii declarate și metrici observate")) {
                    boolean progress=family.meanDelta()>0;
                    checks.assertions(List.of(
                            new AtfAssertion("Partide complete auditate pe tranziții și scor",String.valueOf(protocol.players()*protocol.seeds().size()),String.valueOf(family.pairedGames()),family.pairedGames()==protocol.players()*protocol.seeds().size()),
                            new AtfAssertion("Distribuiri distincte; rotațiile nu sunt eșantioane independente",String.valueOf(protocol.seeds().size()),String.valueOf(family.seedClusters()),family.seedClusters()==protocol.seeds().size()),
                            new AtfAssertion("Delta medie a marjei de puncte față de control","> 0 pentru progres observat",String.valueOf(family.meanDelta()),progress),
                            new AtfAssertion("Credit de victorie, egalități împărțite","metrică descriptivă [0,1]",String.valueOf(family.winCredit()),family.winCredit()>=0&&family.winCredit()<=1),
                            new AtfAssertion("Cea mai mică medie pe seed; pierderile rămân vizibile","valoare finită, fără garanție de câștig",String.valueOf(family.minimumSeedMean()),Double.isFinite(family.minimumSeedMean()))));
                    var status=progress?AtfReportStatus.PASS:AtfReportStatus.FAIL;checks.complete(status);phase.complete(status);scenario.complete(status);
                }
            }
        }
    }
}
