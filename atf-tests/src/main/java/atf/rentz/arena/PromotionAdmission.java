package atf.rentz.arena;

import atf.rentz.OfflineArena;
import atf.rentz.OfflineArena.Kind;
import atf.rentz.OfflineArena.Protocol;
import atf.rentz.OfflineArena.Summary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.core.ScoreProfile;

/** Admission of external evidence requires independent trusted inputs, not the result's own claims. */
public final class PromotionAdmission {
    public record Expected(String sourceIdentity, PolicySettings configuredSettings, ScoreProfile scoreProfile) {
        public Expected {
            if (sourceIdentity == null || !sourceIdentity.matches("[a-f0-9]{64}") || configuredSettings == null
                    || scoreProfile == null)
                throw new IllegalArgumentException("Trusted source SHA-256, configured policy and score profile required");
        }
    }
    public record Verdict(boolean coherent, boolean promotes, List<String> findings) {
        public Verdict { findings = List.copyOf(findings); }
    }
    private PromotionAdmission() { }

    public static Verdict inspect(Summary result, Protocol protocol, Expected expected) {
        if (result == null || protocol == null || expected == null) return refused("independent context and result required");
        try {
            PromotionEvidence.require(protocol.equals(result.declaredProtocol()), "full protocol declaration missing or different");
            PromotionEvidence.require(protocol.id().equals(result.protocol()) && protocol.phase().equals(result.phase())
                    && expected.sourceIdentity().equals(result.sourceIdentity()) && protocol.policy().equals(result.policyFile()),
                    "source, protocol or policy identity differs");
            PromotionEvidence.require("UNCONFIRMED_LABORATORY_SCORING".equals(result.referenceParity())
                    && "MEASURED_NOT_GLOBAL_OPTIMALITY".equals(result.state()), "evidence scope differs");
            PromotionEvidence.require(expected.scoreProfile().equals(result.scoreProfile()),
                    "score profile missing or different from trusted scoring");
            PolicySettings settings = OfflineArena.settingsFor(protocol, expected.configuredSettings());
            PromotionEvidence.require(settings.equals(result.candidateSettings())
                    && OfflineArena.matchedReferenceSettings(settings).equals(result.referenceSettings()),
                    "requested profile or matched reference differs from trusted policy and budget");
            var policies = ArenaPolicies.resolve(protocol, settings);
            PromotionEvidence.require(policies.equals(result.effectivePolicies()),
                    "effective candidate, opponent, control or evaluation objective differs");
            var population = PromotionEvidence.population(result.games(), protocol, policies, expected.scoreProfile());
            var families = new ArrayList<OfflineArena.FamilySummary>();
            var retained = new ArrayList<String>();
            for (var family : protocol.opponents()) {
                var games = population.get(family);
                var aggregate = FamilyAnalysis.summarize(family, games, protocol.seeds(), protocol.players(), policies.metric(), protocol.opponents().size());
                var assessment = aggregate.assessment();
                PromotionEvidence.require(java.util.stream.DoubleStream.of(aggregate.meanDelta(), aggregate.winCredit(),
                        aggregate.minimumSeedMean(), aggregate.maximumSeedMean(), aggregate.controlWinCredit(),
                        aggregate.meanPointsDelta(), assessment.mean(), assessment.familyAdjustedConfidence()).allMatch(Double::isFinite)
                        && (assessment.lowerBound() == null || Double.isFinite(assessment.lowerBound()))
                        && assessment.clusters().stream().allMatch(c -> Double.isFinite(c.marginDelta())
                                && Double.isFinite(c.pointsDelta()) && Double.isFinite(c.winDelta())),
                        "non-finite aggregate arithmetic");
                families.add(aggregate);
                FamilyAnalysis.retained(games, protocol.retainPerFamily()).forEach(pair -> retained.add(FamilyAnalysis.fileName(pair)));
            }
            PromotionEvidence.require(Objects.equals(families, result.families()),
                    "aggregates, costs, clusters or assessment differ from paired evidence");
            PromotionEvidence.require(Objects.equals(retained, result.retained()), "retention declaration differs from protocol");
            boolean eligible = protocol.phase().equals("FRESH_EVALUATION")
                    && protocol.opponents().containsAll(List.of(Kind.NORMAL, Kind.HARD_REFERENCE))
                    && families.stream().allMatch(f -> f.assessment().sufficient() && f.assessment().promotes()
                            && f.winCredit() + 1e-12 >= f.controlWinCredit());
            return new Verdict(true, eligible, List.of());
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return refused(invalid.getMessage());
        }
    }
    private static Verdict refused(String finding) { return new Verdict(false, false, List.of(finding)); }
}
