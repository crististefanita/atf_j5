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
                var candidate=result.candidateSettings();var reference=result.referenceSettings();
                String description="Rentz offline; profil de laborator, paritate cu originalul neconfirmată. Protocol="+result.protocol()+"; etapă="+result.phase()+"; surse="+result.sourceIdentity()+"; obiectiv="+candidate.objective()+"; politici="+result.policyFile()+"; candidat="+candidate+"; reper="+reference+". Plafoanele egale nu înseamnă cost efectiv sau timp identic. Bootstrap-ul operează pe seed-uri; lumile simulate nu sunt probabilități calibrate.";
                description+=(protocol.candidate()==OfflineArena.Kind.HARD||protocol.candidate()==OfflineArena.Kind.HARD_REFERENCE)?" Controlul înlocuiește numai actorul evaluat cu Hard legacy; adversarii, poziția și distribuirea rămân aceleași.":" Controlul este masa uniformă a familiei declarate.";
                try(var scenario=report.startScenario("Rentz "+protocol.candidate()+" / "+family.family(),description,List.of("offline","rentz","arena",result.phase().toLowerCase(Locale.ROOT)));
                    var phase=scenario.startPhase("Evaluare pe distribuiri pereche și locuri rotite");var checks=phase.startAssertionStep("Criterii declarate și metrici observate")) {
                    boolean winGuard=family.winCredit()+1e-12>=family.controlWinCredit();
                    boolean progress=family.assessment().promotes()&&winGuard;
                    var cost=family.cost();boolean equalBudget=candidate.worlds()==reference.worlds()&&candidate.maxTransitions()==reference.maxTransitions()&&Double.compare(candidate.riskWeight(),reference.riskWeight())==0;
                    checks.assertions(List.of(
                            new AtfAssertion("Partide complete auditate pe tranziții și scor",String.valueOf(protocol.players()*protocol.seeds().size()),String.valueOf(family.pairedGames()),family.pairedGames()==protocol.players()*protocol.seeds().size()),
                            new AtfAssertion("Distribuiri distincte; rotațiile nu sunt eșantioane independente",String.valueOf(protocol.seeds().size()),String.valueOf(family.seedClusters()),family.seedClusters()==protocol.seeds().size()),
                            new AtfAssertion("Delta medie a marjei de puncte față de control","metrică secundară finită",String.valueOf(family.meanDelta()),Double.isFinite(family.meanDelta())),
                            new AtfAssertion("Delta medie de puncte finale proprii","metrică distinctă finită",String.valueOf(family.meanPointsDelta()),Double.isFinite(family.meanPointsDelta())),
                            new AtfAssertion("Creditul controlului, egalități împărțite","metrică descriptivă [0,1]",String.valueOf(family.controlWinCredit()),family.controlWinCredit()>=0&&family.controlWinCredit()<=1),
                            new AtfAssertion("Credit de victorie, egalități împărțite","metrică descriptivă [0,1]",String.valueOf(family.winCredit()),family.winCredit()>=0&&family.winCredit()<=1),
                            new AtfAssertion("Creditul de victorie nu regresează față de control",">= "+family.controlWinCredit(),String.valueOf(family.winCredit()),winGuard),
                            new AtfAssertion("Plafoane egale pentru reperul Hard parametrizat","lumi="+candidate.worlds()+"; tranziții="+candidate.maxTransitions()+"; risc="+candidate.riskWeight(),"lumi="+reference.worlds()+"; tranziții="+reference.maxTransitions()+"; risc="+reference.riskWeight(),equalBudget),
                            new AtfAssertion("Tranziții simulate efective; comparație descriptivă","costuri nenegative; Normal nu caută lumi","candidat="+cost.candidateTransitions()+"; control="+cost.controlTransitions(),cost.candidateTransitions()>=0&&cost.controlTransitions()>=0),
                            new AtfAssertion("Timp efectiv în decizii; nu este buget determinist","nanosecunde măsurate, fără egalitate de latență","candidat="+cost.candidateNanos()+"; control="+cost.controlNanos(),cost.candidateNanos()>=0&&cost.controlNanos()>=0),
                            new AtfAssertion("Limitări de buget și fallback-uri rămân vizibile","numere nenegative, fără ascunderea căutărilor incomplete","limitate="+cost.candidateLimited()+"/"+cost.controlLimited()+"; fallback="+cost.candidateFallbacks()+"/"+cost.controlFallbacks(),cost.candidateLimited()>=0&&cost.controlLimited()>=0&&cost.candidateFallbacks()>=0&&cost.controlFallbacks()>=0),
                            new AtfAssertion("Obiectiv primar pe seed-uri: "+family.assessment().metric(),"cel puțin 8 seed-uri și limită inferioară bootstrap > 0; încredere "+family.assessment().familyAdjustedConfidence(),"seed-uri="+family.assessment().seedClusters()+"; medie="+family.assessment().mean()+"; limită="+family.assessment().lowerBound(),progress),
                            new AtfAssertion("Cea mai mică medie pe seed; pierderile rămân vizibile","valoare finită, fără garanție de câștig",String.valueOf(family.minimumSeedMean()),Double.isFinite(family.minimumSeedMean()))));
                    var status=progress?AtfReportStatus.PASS:AtfReportStatus.FAIL;checks.complete(status);phase.complete(status);scenario.complete(status);
                }
            }
        }
    }
}
