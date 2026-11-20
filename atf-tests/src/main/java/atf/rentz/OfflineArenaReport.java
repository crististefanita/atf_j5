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
        var policies = result.effectivePolicies();
        if (policies == null || result.scoreProfile() == null || !protocol.equals(result.declaredProtocol())) {
            throw new IllegalArgumentException("Protocol, effective policies and score profile are required to publish an evaluation");
        }
        try(var report=new ExtentReportSession(output,ReportingSettings.defaults())) {
            for(var family:result.families()) {
                var matchup = policies.families().get(family.family());
                var candidate = policies.candidate().search();
                var control = matchup.control().search();
                String description = "Rentz offline. Laboratory profile. Original application parity is unconfirmed."
                        + " Protocol " + result.protocol() + ", phase " + result.phase() + ", sources " + result.sourceIdentity()
                        + ". Executed score profile " + result.scoreProfile()
                        + ". Evaluation objective " + policies.evaluationObjective() + ". Requested policy "
                        + result.policyFile() + " " + result.candidateSettings() + ". Effective candidate "
                        + policies.candidate() + ". Effective opponents " + matchup.opponent() + ". Effective control "
                        + matchup.control() + ". Bootstrap samples distinct deal seeds.";
                description += matchup.control().equals(matchup.opponent())
                        ? " The control uses the same policy at every seat."
                        : " The control changes the evaluated seat policy while preserving opponents, position and deal.";
                try(var scenario=report.startScenario("Rentz "+protocol.candidate()+" / "+family.family(),description,List.of("offline","rentz","arena",result.phase().toLowerCase(Locale.ROOT)));
                    var phase=scenario.startPhase("Paired distributions and rotated seats");var checks=phase.startAssertionStep("Declared criteria and observed metrics")) {
                    boolean winGuard=family.winCredit()+1e-12>=family.controlWinCredit();
                    boolean progress=family.assessment().promotes()&&winGuard;
                    var cost=family.cost();
                    var assertions = new ArrayList<>(List.of(
                            new AtfAssertion("Complete matches audited for transitions and score",String.valueOf(protocol.players()*protocol.seeds().size()),String.valueOf(family.pairedGames()),family.pairedGames()==protocol.players()*protocol.seeds().size()),
                            new AtfAssertion("Distinct generator states; seat rotations stay in the same cluster",String.valueOf(protocol.seeds().size()),String.valueOf(family.seedClusters()),family.seedClusters()==protocol.seeds().size()),
                            new AtfAssertion("Mean score-margin delta against control","finite secondary metric",String.valueOf(family.meanDelta()),Double.isFinite(family.meanDelta())),
                            new AtfAssertion("Mean final own-points delta","finite separate metric",String.valueOf(family.meanPointsDelta()),Double.isFinite(family.meanPointsDelta())),
                            new AtfAssertion("Control win credit with shared ties","descriptive metric in [0,1]",String.valueOf(family.controlWinCredit()),family.controlWinCredit()>=0&&family.controlWinCredit()<=1),
                            new AtfAssertion("Candidate win credit with shared ties","descriptive metric in [0,1]",String.valueOf(family.winCredit()),family.winCredit()>=0&&family.winCredit()<=1),
                            new AtfAssertion("Win credit does not regress against control",">= "+family.controlWinCredit(),String.valueOf(family.winCredit()),winGuard),
                            new AtfAssertion("Actual simulated transitions; descriptive comparison","nonnegative costs; Normal does not sample worlds","candidate="+cost.candidateTransitions()+"; control="+cost.controlTransitions(),cost.candidateTransitions()>=0&&cost.controlTransitions()>=0),
                            new AtfAssertion("Actual decision time is not a deterministic budget","measured nanoseconds; equal latency is not required","candidate="+cost.candidateNanos()+"; control="+cost.controlNanos(),cost.candidateNanos()>=0&&cost.controlNanos()>=0),
                            new AtfAssertion("Budget limits and fallbacks remain visible","nonnegative counts; incomplete searches remain visible","limited="+cost.candidateLimited()+"/"+cost.controlLimited()+"; fallback="+cost.candidateFallbacks()+"/"+cost.controlFallbacks(),cost.candidateLimited()>=0&&cost.controlLimited()>=0&&cost.candidateFallbacks()>=0&&cost.controlFallbacks()>=0),
                            new AtfAssertion("Primary clustered objective: "+family.assessment().metric(),"at least 8 distinct states and bootstrap lower bound > 0; confidence "+family.assessment().familyAdjustedConfidence(),"clusters="+family.assessment().seedClusters()+"; mean="+family.assessment().mean()+"; bound="+family.assessment().lowerBound(),progress),
                            new AtfAssertion("Lowest seed mean; losses remain visible","finite value; victory is not guaranteed",String.valueOf(family.minimumSeedMean()),Double.isFinite(family.minimumSeedMean()))));
                    if (candidate != null && control != null) {
                        boolean equalBudget = candidate.worlds() == control.worlds()
                                && candidate.maxTransitions() == control.maxTransitions()
                                && Double.compare(candidate.riskWeight(), control.riskWeight()) == 0;
                        assertions.add(new AtfAssertion("Search ceilings and risk for the effective policies",
                                budget(candidate), budget(control), equalBudget));
                    }
                    checks.assertions(assertions);
                    var status=progress?AtfReportStatus.PASS:AtfReportStatus.FAIL;checks.complete(status);phase.complete(status);scenario.complete(status);
                }
            }
        }
    }
    private static String budget(ro.rentzlab.ai.PolicySettings settings) {
        return "worlds=" + settings.worlds() + ", transitions=" + settings.maxTransitions() + ", risk=" + settings.riskWeight();
    }
}
