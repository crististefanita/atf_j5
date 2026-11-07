package atf.rentz;

import atf.rentz.arena.ArenaPolicies;
import atf.rentz.arena.FamilyAnalysis;
import atf.rentz.arena.PromotionAdmission;
import atf.rentz.evaluation.ClusteredEvaluation;
import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.core.ScoreProfile;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class ArenaAdmissionTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String SOURCE = "a".repeat(64);
    static final PolicySettings SETTINGS = new PolicySettings(1, .05, 3, .3, 2, PolicySettings.Objective.MATCH_WIN_SHARE);
    static final ScoreProfile SCORING = ScoreProfile.laboratory();
    static final OfflineArena.Protocol PROTOCOL = new OfflineArena.Protocol("admission", "FRESH_EVALUATION", 4,
            java.util.stream.LongStream.range(0, 8).boxed().toList(),
            List.of(OfflineArena.Kind.NORMAL, OfflineArena.Kind.HARD_REFERENCE), OfflineArena.Kind.HARD, 1, 0,
            "rentz-match-policy.yaml");

    static OfflineArena.Summary neighbor() {
        var games = new ArrayList<OfflineArena.Paired>();
        var families = new ArrayList<OfflineArena.FamilySummary>();
        for (var family : PROTOCOL.opponents()) {
            var rows = new ArrayList<OfflineArena.Paired>();
            for (long seed : PROTOCOL.seeds()) for (int seat = 0; seat < PROTOCOL.players(); seat++) {
                var work = new OfflineArena.SeatWork(seat, 1, 1, 1, 8, 0, 0, 10);
                rows.add(new OfflineArena.Paired(seed, seat, family, PROTOCOL.candidate(), 0, 10, 10, 1,
                        100, 1, 10, 1, 0, 0, 90, work, work));
            }
            games.addAll(rows);
            families.add(FamilyAnalysis.summarize(family, rows, PROTOCOL.seeds(), 4,
                    ClusteredEvaluation.Metric.WIN_SHARE, 2));
        }
        return new OfflineArena.Summary(PROTOCOL.id(), PROTOCOL.phase(), SOURCE, "UNCONFIRMED_LABORATORY_SCORING",
                "MEASURED_NOT_GLOBAL_OPTIMALITY", families, games, List.of(), PROTOCOL.policy(),
                SETTINGS, OfflineArena.matchedReferenceSettings(SETTINGS), PROTOCOL, ArenaPolicies.resolve(PROTOCOL, SETTINGS), SCORING);
    }
    static boolean admitted(OfflineArena.Summary summary) {
        return OfflineArena.promotable(summary, PROTOCOL, new PromotionAdmission.Expected(SOURCE, SETTINGS, SCORING));
    }
    static ObjectNode game(ObjectNode root) { return (ObjectNode) root.withArray("games").get(0); }
    static ObjectNode family(ObjectNode root) { return (ObjectNode) root.withArray("families").get(0); }
    static Arguments mutation(String label, Consumer<ObjectNode> change) { return Arguments.of(label, change); }
    static Stream<Arguments> corruptions() {
        return Stream.of(
                mutation("games absent", n -> n.putArray("games")),
                mutation("missing rotation", n -> n.withArray("games").remove(0)),
                mutation("duplicate rotation", n -> n.withArray("games").set(0, n.withArray("games").get(1).deepCopy())),
                mutation("extra game", n -> n.withArray("games").add(n.withArray("games").get(0).deepCopy())),
                mutation("unknown seed", n -> game(n).put("seed", 991)),
                mutation("seat outside table", n -> game(n).put("seat", 4)),
                mutation("different candidate", n -> game(n).put("candidate", "NORMAL")),
                mutation("different family", n -> game(n).put("family", "BASELINE")),
                mutation("wrong paired delta", n -> game(n).put("delta", 99)),
                mutation("invalid win credit", n -> game(n).put("winCredit", 2)),
                mutation("source does not match trusted context", n -> n.put("sourceIdentity", "b".repeat(64))),
                mutation("different policy", n -> n.put("policyFile", "rentz-policy.yaml")),
                mutation("null score profile", n -> n.putNull("scoreProfile")),
                mutation("changed finishing rewards", n ->
                        ((ObjectNode)n.get("scoreProfile")).putArray("finishRewards").add(301).add(200).add(100).add(0).add(0).add(0)),
                mutation("budget differs from protocol", n -> {
                    ((ObjectNode)n.get("candidateSettings")).put("worlds", 2);
                    ((ObjectNode)n.get("referenceSettings")).put("worlds", 2);
                }),
                mutation("unapproved candidate weight", n -> ((ObjectNode)n.get("candidateSettings")).put("ownChainWeight", 25)),
                mutation("reference tactic changed", n -> ((ObjectNode)n.get("referenceSettings")).put("tactic", "PUBLIC_TARGET_AWARE")),
                mutation("effective actor absent", n -> n.remove("effectivePolicies")),
                mutation("evaluation objective changed", n -> ((ObjectNode)n.get("effectivePolicies")).put("evaluationObjective", "HAND_MARGIN")),
                mutation("effective tactic changed", n -> ((ObjectNode)n.at("/effectivePolicies/candidate/search")).put("tactic", "PUBLIC_TARGET_AWARE")),
                mutation("control misidentified", n -> ((ObjectNode)n.at("/effectivePolicies/families/NORMAL/control")).put("kind", "HARD")),
                mutation("opponent misidentified", n -> ((ObjectNode)n.at("/effectivePolicies/families/NORMAL/opponent")).put("kind", "BASELINE")),
                mutation("aggregate mean changed", n -> family(n).put("meanDelta", 77)),
                mutation("confidence bound changed", n -> ((ObjectNode)family(n).get("assessment")).put("lowerBound", 77)),
                mutation("cost changed", n -> ((ObjectNode)family(n).get("cost")).put("candidateTransitions", 0)),
                mutation("work assigned to another seat", n -> ((ObjectNode)game(n).get("candidateWork")).put("seat", 3)),
                mutation("negative cost", n -> ((ObjectNode)game(n).get("candidateWork")).put("transitions", -1)));
    }
    @Test void completeCoherentPositiveNeighborCanQualify() { assertTrue(admitted(neighbor())); }

    @Test void historicalSummaryWithoutScoringRemainsReadableButCannotQualify() throws Exception {
        ObjectNode historic = JSON.valueToTree(neighbor());
        historic.remove("scoreProfile");
        assertFalse(admitted(JSON.treeToValue(historic, OfflineArena.Summary.class)));
    }
    @ParameterizedTest(name="{0}") @MethodSource("corruptions")
    void independentCorruptionCannotQualify(String name, Consumer<ObjectNode> change) throws Exception {
        ObjectNode altered = JSON.valueToTree(neighbor());
        change.accept(altered);
        assertFalse(admitted(JSON.treeToValue(altered, OfflineArena.Summary.class)), name);
    }

    @Test void legacyUnboundSummariesAndAContextTakenFromAnotherSourceCannotPromote() throws Exception {
        ObjectNode legacy = JSON.valueToTree(neighbor());
        legacy.remove("declaredProtocol");
        assertFalse(admitted(JSON.treeToValue(legacy, OfflineArena.Summary.class)));
        assertFalse(PromotionAdmission.inspect(neighbor(), PROTOCOL,
                new PromotionAdmission.Expected("b".repeat(64), SETTINGS, SCORING)).coherent());
        assertFalse(PromotionAdmission.inspect(neighbor(), PROTOCOL, new PromotionAdmission.Expected(SOURCE,
                new PolicySettings(1, .05, 7, .3, 2, PolicySettings.Objective.MATCH_WIN_SHARE), SCORING)).coherent());
        assertThrows(IllegalArgumentException.class, () -> new PromotionAdmission.Expected("fixture", SETTINGS, SCORING));
        assertThrows(IllegalArgumentException.class, () -> new PromotionAdmission.Expected(SOURCE, SETTINGS, null));
        assertTrue(java.util.Arrays.stream(OfflineArena.class.getMethods())
                .noneMatch(method -> method.getName().equals("promotable") && method.getParameterCount() == 2),
                "The public API must not provide admission without trusted inputs");
    }
    @Test void fullProtocolBindingIncludesCandidateRetentionPolicyAndBudget() {
        for (var changed : List.of(
                new OfflineArena.Protocol(PROTOCOL.id(), PROTOCOL.phase(), 4, PROTOCOL.seeds(), PROTOCOL.opponents(), OfflineArena.Kind.NORMAL, 1, 0, PROTOCOL.policy()),
                new OfflineArena.Protocol(PROTOCOL.id(), PROTOCOL.phase(), 4, PROTOCOL.seeds(), PROTOCOL.opponents(), PROTOCOL.candidate(), 2, 0, PROTOCOL.policy()),
                new OfflineArena.Protocol(PROTOCOL.id(), PROTOCOL.phase(), 4, PROTOCOL.seeds(), PROTOCOL.opponents(), PROTOCOL.candidate(), 1, 1, PROTOCOL.policy()),
                new OfflineArena.Protocol(PROTOCOL.id(), PROTOCOL.phase(), 4, PROTOCOL.seeds(), PROTOCOL.opponents(), PROTOCOL.candidate(), 1, 0, "rentz-policy.yaml"))) {
            assertFalse(PromotionAdmission.inspect(neighbor(), changed, new PromotionAdmission.Expected(SOURCE, SETTINGS, SCORING)).coherent());
        }
    }
    @Test void completeNegativeEvidenceCanBeCoherentWithoutPromoting() {
        var original = neighbor();
        var games = original.games().stream().map(p -> new OfflineArena.Paired(p.seed(),p.seat(),p.family(),p.candidate(),
                0,0,0,0,90,p.elapsedMillis(),p.maxDecisionNanos(),p.searchedDecisions(),p.exactShortcuts(),0,90,p.candidateWork(),p.controlWork())).toList();
        var families = PROTOCOL.opponents().stream().map(f -> FamilyAnalysis.summarize(f,
                games.stream().filter(p -> p.family() == f).toList(), PROTOCOL.seeds(),4,ClusteredEvaluation.Metric.WIN_SHARE,2)).toList();
        var result = new OfflineArena.Summary(original.protocol(),original.phase(),SOURCE,original.referenceParity(),original.state(),
                families,games,List.of(),PROTOCOL.policy(),SETTINGS,original.referenceSettings(),PROTOCOL,original.effectivePolicies(),SCORING);
        var admission = PromotionAdmission.inspect(result,PROTOCOL,new PromotionAdmission.Expected(SOURCE,SETTINGS,SCORING));
        assertTrue(admission.coherent(), admission.findings().toString());
        assertFalse(admission.promotes());
        assertEquals(List.of(), admission.findings());
    }
    @Test void nullEvidenceAndNonFiniteOrOverflowingMeasurementsFailClosed() throws Exception {
        assertFalse(PromotionAdmission.inspect(null, PROTOCOL, new PromotionAdmission.Expected(SOURCE, SETTINGS, SCORING)).coherent());
        for (Consumer<ObjectNode> mutation : List.<Consumer<ObjectNode>>of(
                n -> n.putNull("games"), n -> n.putNull("families"), n -> n.putNull("candidateSettings"),
                n -> game(n).putNull("candidateWork"), n -> game(n).put("candidateMargin", Double.NaN),
                n -> ((ObjectNode)game(n).get("candidateWork")).put("decisionNanos", Long.MAX_VALUE))) {
            ObjectNode altered = JSON.valueToTree(neighbor()); mutation.accept(altered);
            assertFalse(admitted(JSON.treeToValue(altered, OfflineArena.Summary.class)));
        }
    }
    @Test void overflowInFiniteMarginSamplesCannotBecomeAnInfinitePositiveAggregate() {
        var original = neighbor();
        var games = original.games().stream().map(p -> new OfflineArena.Paired(p.seed(),p.seat(),p.family(),p.candidate(),
                0,Double.MAX_VALUE,Double.MAX_VALUE,p.winCredit(),p.rootScore(),p.elapsedMillis(),p.maxDecisionNanos(),
                p.searchedDecisions(),p.exactShortcuts(),p.controlWinCredit(),p.controlScore(),p.candidateWork(),p.controlWork())).toList();
        var families = PROTOCOL.opponents().stream().map(f -> FamilyAnalysis.summarize(f,
                games.stream().filter(p -> p.family() == f).toList(),PROTOCOL.seeds(),4,ClusteredEvaluation.Metric.WIN_SHARE,2)).toList();
        assertFalse(PromotionAdmission.inspect(new OfflineArena.Summary(original.protocol(),original.phase(),SOURCE,
                original.referenceParity(),original.state(),families,games,List.of(),original.policyFile(),SETTINGS,
                original.referenceSettings(),PROTOCOL,original.effectivePolicies(),SCORING),PROTOCOL,new PromotionAdmission.Expected(SOURCE,SETTINGS,SCORING)).coherent());
    }
    @Test void structuredSerializationPreservesAdmissionAndDevelopmentNeverPromotes() throws Exception {
        var original = neighbor();
        assertTrue(admitted(JSON.readValue(JSON.writeValueAsBytes(original), OfflineArena.Summary.class)));
        var development = new OfflineArena.Protocol(PROTOCOL.id(),"DEVELOPMENT",4,PROTOCOL.seeds(),PROTOCOL.opponents(),PROTOCOL.candidate(),1,0,PROTOCOL.policy());
        var result = new OfflineArena.Summary(original.protocol(),development.phase(),SOURCE,original.referenceParity(),original.state(),
                original.families(),original.games(),original.retained(),original.policyFile(),SETTINGS,original.referenceSettings(),development,original.effectivePolicies(),SCORING);
        var admission = PromotionAdmission.inspect(result,development,new PromotionAdmission.Expected(SOURCE,SETTINGS,SCORING));
        assertTrue(admission.coherent());
        assertFalse(admission.promotes());
    }
}
