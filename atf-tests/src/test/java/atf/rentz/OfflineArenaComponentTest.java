package atf.rentz;

import ro.rentzlab.ai.*;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ro.rentzlab.core.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class OfflineArenaComponentTest {
    @Test void referenceMatchesCandidateWorldsRiskAndTransitionLimitWithoutAdoptingItsTactic(){
        var candidate=new PolicySettings(1,0,3,.3,2,PolicySettings.Objective.MATCH_WIN_SHARE,PolicySettings.Tactic.PUBLIC_TARGET_AWARE,127);
        var reference=OfflineArena.matchedReferenceSettings(candidate);
        assertEquals(candidate.worlds(),reference.worlds());assertEquals(candidate.riskWeight(),reference.riskWeight());assertEquals(candidate.maxTransitions(),reference.maxTransitions());
        assertEquals(PolicySettings.Tactic.LEGACY,reference.tactic());assertEquals(PolicySettings.Objective.HAND_MARGIN,reference.objective());
    }
    @TempDir Path directory;
    @Test void hardIsPairedWithLegacyHardAtTheSameSeatAlsoAmongNormalOpponents()throws Exception{
        var protocol=new OfflineArena.Protocol("matched-root","DEVELOPMENT",3,List.of(9017L),List.of(OfflineArena.Kind.NORMAL),OfflineArena.Kind.HARD,1,0);
        var settings=new PolicySettings(1,.05,3,.3,2);var summary=OfflineArena.run(protocol,ScoreProfile.laboratory(),settings,directory.resolve("paired-root"),directory.resolve("paired-root-registry.json"),"paired-root-fixture");
        for(var pair:summary.games()){
            assertTrue(pair.controlWork().searches()>0,"Control is Hard at the same root seat, not a uniform Normal table");
            assertEquals(0,pair.delta(),1e-9,"An unchanged legacy candidate must equal its matched legacy control");
            assertEquals(pair.controlScore(),pair.rootScore());assertEquals(pair.controlWork().transitions(),pair.candidateWork().transitions());
        }
    }
    @Test void policySchemaThreeKeepsBudgetAndTacticTypedAndClosed()throws Exception{
        Path project=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(project!=null&&!Files.isDirectory(project.resolve("config")))project=project.getParent();assertNotNull(project);
        final Path frozen=project;String text=Files.readString(project.resolve("config/rentz-contextual-policy.yaml"));Path file=directory.resolve("policy.yaml");
        Files.writeString(file,text);var accepted=PolicyInputs.read(file,frozen);assertEquals(8,accepted.worlds());assertEquals(65536,accepted.maxTransitions());assertEquals(PolicySettings.Tactic.PUBLIC_TARGET_AWARE,accepted.tactic());
        for(String mutant:List.of(text.replace("maxTransitions: 65536","maxTransitions: 65536.0"),text.replace("maxTransitions: 65536","maxTransitions: 0"),text.replace("maxTransitions: 65536","maxTransitions: 1000001"),text.replace("tactic: PUBLIC_TARGET_AWARE","tactic: UNKNOWN"),text.replace("objective: HAND_MARGIN","objective: UNKNOWN"),text.replace("maxTransitions: 65536","maxTransitions: 65536\n  hiddenBudget: 12"))){Files.writeString(file,mutant);assertThrows(IllegalArgumentException.class,()->PolicyInputs.read(file,frozen));}
    }
    @Test void freshDistributionsCannotBeReopenedByAnotherCandidateOrFolder() throws Exception {
        Path registry=directory.resolve("exposure.json");
        EvaluationRegistry.expose(registry,4,List.of(7L,8L),"DEVELOPMENT","dev","source-a");String before=Files.readString(registry);
        assertThrows(IllegalArgumentException.class,()->EvaluationRegistry.expose(registry,4,List.of(8L),"FRESH_EVALUATION","renamed-folder","source-b"));
        assertEquals(before,Files.readString(registry));
        EvaluationRegistry.expose(registry,4,List.of(90L),"FRESH_EVALUATION","fresh","source-b");before=Files.readString(registry);
        assertThrows(IllegalArgumentException.class,()->EvaluationRegistry.expose(registry,4,List.of(90L),"DEVELOPMENT","after-view","source-c"));assertEquals(before,Files.readString(registry));
        EvaluationRegistry.expose(registry,3,List.of(90L),"FRESH_EVALUATION","different-deck","source-b");
    }
    @Test void corruptOrLockedRegistryIsPreservedAndRefused() throws Exception {
        Path registry=directory.resolve("exposure.json");Files.writeString(registry,"{");
        assertThrows(java.io.IOException.class,()->EvaluationRegistry.expose(registry,4,List.of(9L),"FRESH_EVALUATION","fresh","source"));assertEquals("{",Files.readString(registry));
        Files.writeString(registry,"{\"schemaVersion\":1,\"domain\":\"rentz\",\"generator\":\"jvm-shuffle-eight-cards-v1\",\"exposures\":[{}]}");String before=Files.readString(registry);
        assertThrows(IllegalArgumentException.class,()->EvaluationRegistry.expose(registry,4,List.of(9L),"FRESH_EVALUATION","fresh","source"));assertEquals(before,Files.readString(registry));
        Path other=directory.resolve("other.json");
        try(var channel=FileChannel.open(directory.resolve("other.json.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
            assertThrows(IllegalStateException.class,()->EvaluationRegistry.expose(other,4,List.of(9L),"FRESH_EVALUATION","fresh","source"));assertFalse(Files.exists(other));
        }
    }
    @Test void independentTransitionAuditRejectsWrongScoreAndPreservesTheValidNeighbor() {
        var profile=ScoreProfile.laboratory();var game=new RentzMatch(4,31,profile);game.choose(0,Contract.WHIST,false);
        for(int n=0;n<3;n++){int actor=game.observe(0).turn();game.play(actor,LabPolicy.card(game.observe(actor)));}
        int actor=game.observe(0).turn();var before=game.observe(actor);Card card=LabPolicy.card(before);game.play(actor,card);var after=game.observe(actor);
        assertDoesNotThrow(()->OfflineGameAudit.card(before,card,after,profile));
        var wrong=new ScoreProfile(10,25,20,100,11,100,List.of(300,200,100,0,0,0));
        assertThrows(IllegalStateException.class,()->OfflineGameAudit.card(before,card,after,wrong));
    }
    @Test void completeNormalMatchesCoverEveryTableAndCuratedGamesKeepOnlyTheRootPrivateInput() throws Exception {
        var settings=new PolicySettings(1,0.05,3,0.3,2);var profile=ScoreProfile.laboratory();
        for(int players=3;players<=6;players++){var game=OfflineArena.play(players,29,0,OfflineArena.Kind.NORMAL,OfflineArena.Kind.BASELINE,profile,settings,false);assertEquals(players*8,game.history().size());}
        var protocol=new OfflineArena.Protocol("component-arena","DEVELOPMENT",4,List.of(73L),List.of(OfflineArena.Kind.BASELINE),OfflineArena.Kind.NORMAL,1,1);
        Path output=directory.resolve("run");var summary=OfflineArena.run(protocol,profile,settings,output,directory.resolve("registry.json"),"component-fixture");
        assertEquals(4,summary.games().size());assertFalse(summary.retained().isEmpty());assertTrue(summary.retained().size()<=2);
        var json=new ObjectMapper();
        for(String retained:summary.retained()) {
            var game=json.readTree(output.resolve(retained).toFile()).required("episode");int seat=game.required("candidateSeat").intValue();
            for(var move:game.required("moves"))if(move.required("actor").intValue()!=seat){assertTrue(move.required("actorInput").isNull());assertTrue(move.required("decision").isNull());}
        }
    }
    @Test void protocolKeepsExactSeedsObjectivesAndTheFrozenReference()throws Exception{
        String valid="""
                {"id":"goal","phase":"DEVELOPMENT","players":4,"seeds":[9223372036854775807],"opponents":["NORMAL","HARD_REFERENCE"],"candidate":"HARD","worlds":1,"retainPerFamily":0,"policy":"rentz-match-policy.yaml"}
                """;
        Path file=directory.resolve("protocol.json");Files.writeString(file,valid);
        var parsed=OfflineArena.readProtocol(file);assertEquals(Long.MAX_VALUE,parsed.seeds().get(0));assertEquals("rentz-match-policy.yaml",parsed.policy());
        for(String mutant:List.of(valid.replace("9223372036854775807","1.0"),valid.replace("\"players\":4","\"players\":\"4\""),valid.replace("\"id\":\"goal\"","\"id\":\"goal\",\"id\":\"other\""),valid+"{}",valid.replace("rentz-match-policy.yaml","../escape.yaml"))){Files.writeString(file,mutant);assertThrows(Exception.class,()->OfflineArena.readProtocol(file));}
        assertEquals(new PolicySettings(8,.05,3,.3,2),PolicySettings.handMarginReference());
        Path project=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(project!=null&&!Files.isDirectory(project.resolve("config")))project=project.getParent();
        assertNotNull(project);var settings=PolicyInputs.read(project.resolve("config/rentz-match-policy.yaml"),project);
        assertEquals(PolicySettings.Objective.MATCH_WIN_SHARE,settings.objective());assertNotEquals(settings,PolicySettings.defaults());
    }

    @ParameterizedTest
    @CsvSource({
            "123, 281474976710779",
            "0, -9223372036854775808",
            "-1, 9223372036854775807"
    })
    void protocolRejectsGeneratorAliasesAndKeepsDistinctSignedSeeds(long seed, long alias) throws Exception {
        var profile = ScoreProfile.laboratory();
        var original = new RentzMatch(4, seed, profile);
        var aliased = new RentzMatch(4, alias, profile);
        original.reveal(0);
        aliased.reveal(0);
        assertEquals(original.observe(0).hand(), aliased.observe(0).hand(), "The engine deals the same hand");

        Path file = directory.resolve("protocol.json");
        var template = """
                {"id":"seeds","phase":"FRESH_EVALUATION","players":4,"seeds":[%d,%d],
                 "opponents":["NORMAL"],"candidate":"HARD","worlds":1,"retainPerFamily":0}
                """;
        Files.writeString(file, template.formatted(seed, alias));
        var refusal = assertThrows(JsonMappingException.class, () -> OfflineArena.readProtocol(file));
        assertInstanceOf(IllegalArgumentException.class, refusal.getCause());
        Files.writeString(file, template.formatted(seed, seed + 1));
        assertEquals(List.of(seed, seed + 1), OfflineArena.readProtocol(file).seeds());
    }

    @Test void arenaRefusesAReplayedDistributionBeforeProducingGameEvidence() throws Exception {
        Path registry = directory.resolve("exposure.json");
        EvaluationRegistry.expose(registry, 3, List.of(123L), "DEVELOPMENT", "first", "source-a");
        EvaluationRegistry.initialize(registry, registry, "fixture-reviewer", "fixture-history");
        byte[] before = Files.readAllBytes(registry);
        var protocol = new OfflineArena.Protocol("alias-fresh", "FRESH_EVALUATION", 3, List.of(123L + (1L << 48)),
                List.of(OfflineArena.Kind.BASELINE), OfflineArena.Kind.NORMAL, 1, 1);
        Path output = directory.resolve("arena");
        var refusal = assertThrows(IllegalArgumentException.class, () -> OfflineArena.run(protocol, ScoreProfile.laboratory(),
                new PolicySettings(1, .05, 3, .3, 2), output, registry, "source-b"));
        assertTrue(refusal.getMessage().contains("Distribution already exposed"));
        assertArrayEquals(before, Files.readAllBytes(registry));
        assertFalse(Files.exists(output));
    }
    @Test void freshArenaCannotReplaceAnExposedRegistryWithAnUnusedPath() throws Exception {
        Path existing = directory.resolve("established.json");
        Path replacement = directory.resolve("unused.json");
        EvaluationRegistry.expose(existing, 3, List.of(487L), "DEVELOPMENT", "first", "source-a");
        byte[] before = Files.readAllBytes(existing);
        var protocol = new OfflineArena.Protocol("fresh", "FRESH_EVALUATION", 3, List.of(487L + (1L << 48)),
                List.of(OfflineArena.Kind.BASELINE), OfflineArena.Kind.NORMAL, 1, 0);
        Path output = directory.resolve("substitution");
        assertThrows(IllegalArgumentException.class, () -> OfflineArena.run(protocol, ScoreProfile.laboratory(),
                new PolicySettings(1, .05, 3, .3, 2), output, replacement, "source-b"));
        assertArrayEquals(before, Files.readAllBytes(existing));
        assertFalse(Files.exists(replacement));
        assertFalse(Files.exists(output));
    }

    @Test void freshArenaUsesReviewedHistoryAndRefusesACopyAtAnotherPath() throws Exception {
        Path registry = directory.resolve("shared.json");
        Path copy = directory.resolve("copied.json");
        EvaluationRegistry.expose(registry, 3, List.of(487L), "DEVELOPMENT", "prior", "source-a");
        var settings = new PolicySettings(1, .05, 3, .3, 2);
        var protocol = new OfflineArena.Protocol("fresh", "FRESH_EVALUATION", 3, List.of(488L),
                List.of(OfflineArena.Kind.BASELINE), OfflineArena.Kind.NORMAL, 1, 0);
        Path output = directory.resolve("fresh");
        assertThrows(IllegalArgumentException.class, () ->
                OfflineArena.run(protocol, ScoreProfile.laboratory(), settings, output, registry, "source-b"));
        assertFalse(Files.exists(output));
        var json = new ObjectMapper();
        var unreviewed = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(registry.toFile());
        var original = unreviewed.withArray("exposures").get(0);
        unreviewed.withArray("exposures").removeAll();
        Path incompleteHistory = directory.resolve("incomplete.json");
        json.writeValue(incompleteHistory.toFile(), unreviewed);
        byte[] before = Files.readAllBytes(registry);
        assertThrows(IllegalArgumentException.class, () ->
                EvaluationRegistry.initialize(registry, incompleteHistory, "fixture-reviewer", "missing-history"));
        assertArrayEquals(before, Files.readAllBytes(registry));
        EvaluationRegistry.initialize(registry, registry, "fixture-reviewer", "fixture-history");
        Files.copy(registry, copy);
        var refusal = assertThrows(IllegalArgumentException.class, () ->
                OfflineArena.run(protocol, ScoreProfile.laboratory(), settings, output, copy, "source-b"));
        assertTrue(refusal.getMessage().contains("bound to another path"));
        assertFalse(Files.exists(output));
        assertThrows(IllegalArgumentException.class, () ->
                EvaluationRegistry.initialize(registry, registry, "fixture-reviewer", "second-review"));
        var result = OfflineArena.run(protocol, ScoreProfile.laboratory(), settings, output, registry, "source-b");
        assertEquals(3, result.games().size());
        var updated = json.readTree(registry.toFile());
        assertEquals("fixture-history", updated.required("review").required("reference").textValue());
        var exposures = updated.required("exposures");
        assertEquals(2, exposures.size());
        assertEquals(original, exposures.get(0));
        assertEquals(488L, exposures.get(1).required("seed").longValue());
    }

    @Test void nativeExposureBlocksFreshUntilCapturedAndSurvivesWithoutAReport() throws Exception {
        Path registry = directory.resolve("native-exposure.json");
        Path empty = Files.writeString(directory.resolve("history.json"),
                "{\"schemaVersion\":1,\"domain\":\"rentz\",\"generator\":\"jvm-shuffle-eight-cards-v1\",\"exposures\":[]}");
        EvaluationRegistry.initialize(registry, empty, "fixture", "reviewed empty history");
        String token = "a".repeat(32);
        String otherToken = "b".repeat(32);
        var scoring = ScoreProfile.laboratory();
        var settings = new PolicySettings(1, .05, 3, .3, 2);
        var alias = new OfflineArena.Protocol("alias", "FRESH_EVALUATION", 3, List.of(487L + (1L << 48)),
                List.of(OfflineArena.Kind.BASELINE), OfflineArena.Kind.NORMAL, 1, 0);
        Path output = directory.resolve("alias");
        Path journal = Files.writeString(directory.resolve("development-journal.json"), "{\"players\":3,\"seed\":487}");
        atf.rentz.android.RentzNativeFlow.main(new String[]{"begin-exposure", registry.toString(), token});
        assertThrows(IllegalStateException.class, () -> EvaluationRegistry.beginNativeExposure(registry, otherToken));
        var pending = assertThrows(IllegalStateException.class, () ->
                OfflineArena.run(alias, scoring, settings, output, registry, "source-a"));
        assertTrue(pending.getMessage().contains("unresolved native exposure"));
        assertFalse(Files.exists(output));

        byte[] before = Files.readAllBytes(registry);
        Path marker = registry.resolveSibling("native-exposure.json.native-pending");
        byte[] unbound = Files.readAllBytes(marker);
        Path foreign = Files.writeString(directory.resolve("foreign-journal.json"), "{\"players\":3,\"seed\":488}");
        assertThrows(IllegalArgumentException.class, () -> completeNativeExposure(registry, token, foreign));
        assertArrayEquals(unbound, Files.readAllBytes(marker));

        bindNativeExposure(registry, token, journal);
        byte[] bound = Files.readAllBytes(marker);
        bindNativeExposure(registry, token, journal);
        assertArrayEquals(bound, Files.readAllBytes(marker), "Binding the same captured bytes is idempotent");
        assertThrows(IllegalArgumentException.class, () -> bindNativeExposure(registry, token, foreign));
        assertThrows(IllegalArgumentException.class, () -> completeNativeExposure(registry, otherToken, journal));
        assertThrows(IllegalArgumentException.class, () -> completeNativeExposure(registry, token, foreign));
        Path altered = Files.writeString(directory.resolve("altered-capture.json"), Files.readString(journal) + "\n");
        assertThrows(IllegalArgumentException.class, () -> completeNativeExposure(registry, token, altered));
        assertArrayEquals(before, Files.readAllBytes(registry));
        assertArrayEquals(bound, Files.readAllBytes(marker));
        assertThrows(IllegalStateException.class, () ->
                OfflineArena.run(alias, scoring, settings, output, registry, "source-a"));

        completeNativeExposure(registry, token, journal);
        assertFalse(Files.exists(marker));
        var refused = assertThrows(IllegalArgumentException.class, () ->
                OfflineArena.run(alias, scoring, settings, output, registry, "source-a"));
        assertTrue(refused.getMessage().contains("Distribution already exposed"));
        assertFalse(Files.exists(output));

        var distinct = new OfflineArena.Protocol("distinct", "FRESH_EVALUATION", 3, List.of(488L),
                List.of(OfflineArena.Kind.BASELINE), OfflineArena.Kind.NORMAL, 1, 0);
        assertEquals(3, OfflineArena.run(distinct, scoring, settings, directory.resolve("distinct"), registry, "source-a")
                .games().size());
    }

    @Test void nativeExposureRetriesOnlyTheCommittedCaptureAndPreservesOtherReservations() throws Exception {
        Path registry = directory.resolve("recovery.json");
        Path marker = directory.resolve("recovery.json.native-pending");
        String token = "a".repeat(32);
        Path journal = Files.writeString(directory.resolve("captured.json"), "{\"players\":3,\"seed\":487}");
        Path altered = Files.writeString(directory.resolve("altered.json"), Files.readString(journal) + "\n");
        EvaluationRegistry.beginNativeExposure(registry, token);
        bindNativeExposure(registry, token, journal);
        byte[] bound = Files.readAllBytes(marker);
        completeNativeExposure(registry, token, journal);
        byte[] committed = Files.readAllBytes(registry);
        assertThrows(IllegalArgumentException.class, () -> EvaluationRegistry.beginNativeExposure(registry, token));

        // Simulate interruption after the atomic ledger write and before marker deletion.
        Files.write(marker, bound);
        assertThrows(IllegalArgumentException.class, () -> completeNativeExposure(registry, token, altered));
        assertArrayEquals(committed, Files.readAllBytes(registry));
        assertArrayEquals(bound, Files.readAllBytes(marker));
        completeNativeExposure(registry, token, journal);
        assertFalse(Files.exists(marker));
        assertArrayEquals(committed, Files.readAllBytes(registry));

        completeNativeExposure(registry, token, journal);
        assertThrows(IllegalArgumentException.class, () -> completeNativeExposure(registry, token, altered));
        String nextToken = "b".repeat(32);
        EvaluationRegistry.beginNativeExposure(registry, nextToken);
        bindNativeExposure(registry, nextToken, altered);
        byte[] nextMarker = Files.readAllBytes(marker);
        completeNativeExposure(registry, token, journal);
        assertArrayEquals(nextMarker, Files.readAllBytes(marker));
        assertArrayEquals(committed, Files.readAllBytes(registry));
    }

    @Test void legacyPendingCommitRequiresTheVerifiedCaptureBeforeItCanBeResumed() throws Exception {
        var json = new ObjectMapper();
        Path registry = directory.resolve("legacy.json");
        Path marker = directory.resolve("legacy.json.native-pending");
        String token = "a".repeat(32);
        Path journal = Files.writeString(directory.resolve("verified.json"), "{\"players\":3,\"seed\":487}");
        var legacy = json.createObjectNode().put("schemaVersion", 1).put("domain", "rentz")
                .put("generator", "jvm-shuffle-eight-cards-v1");
        legacy.putArray("exposures").addObject().put("players", 3).put("seed", 487)
                .put("phase", "DEVELOPMENT").put("batch", "native-shared-ui-" + token)
                .put("sourceIdentity", "android-native-laboratory-v1").put("state", "EXPOSED_BEFORE_EXECUTION");
        json.writeValue(registry.toFile(), legacy);
        Files.writeString(marker, token);

        assertThrows(IllegalArgumentException.class, () -> completeNativeExposure(registry, token, journal));
        assertEquals(token, Files.readString(marker));
        assertEquals(legacy, json.readTree(registry.toFile()));
        bindNativeExposure(registry, token, journal);
        completeNativeExposure(registry, token, journal);
        assertFalse(Files.exists(marker));
        var exposures = json.readTree(registry.toFile()).required("exposures");
        assertEquals(1, exposures.size());
        assertTrue(exposures.get(0).required("sourceIdentity").asText().startsWith("android-native-laboratory-v1:sha256:"));
        byte[] committed = Files.readAllBytes(registry);
        completeNativeExposure(registry, token, journal);
        assertArrayEquals(committed, Files.readAllBytes(registry));
    }

    private static void bindNativeExposure(Path registry, String token, Path journal) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(journal));
        atf.rentz.android.RentzNativeFlow.main(new String[]{"bind-exposure", registry.toString(), token,
                HexFormat.of().formatHex(digest)});
    }

    private static void completeNativeExposure(Path registry, String token, Path journal) throws Exception {
        atf.rentz.android.RentzNativeFlow.main(
                new String[]{"complete-exposure", journal.toString(), registry.toString(), token});
    }

    @Test void promotionRefusesAGenuineRunWithDifferentScoring() throws Exception {
        var settings = new PolicySettings(1, .05, 3, .3, 2);
        var expectedScoring = ScoreProfile.laboratory();
        var otherScoring = new ScoreProfile(10, 25, 20, 100, 11, 100, expectedScoring.finishRewards());
        var protocol = new OfflineArena.Protocol("score-identity", "DEVELOPMENT", 3, List.of(64L),
                List.of(OfflineArena.Kind.BASELINE), OfflineArena.Kind.NORMAL, 1, 1);
        var expected = new atf.rentz.arena.PromotionAdmission.Expected(ArenaAdmissionTest.SOURCE, settings, expectedScoring);
        var trusted = OfflineArena.run(protocol, expectedScoring, settings, directory.resolve("trusted"),
                directory.resolve("trusted-exposure.json"), ArenaAdmissionTest.SOURCE);
        var other = OfflineArena.run(protocol, otherScoring, settings, directory.resolve("other"),
                directory.resolve("other-exposure.json"), ArenaAdmissionTest.SOURCE);
        assertTrue(atf.rentz.arena.PromotionAdmission.inspect(trusted, protocol, expected).coherent());
        var refusal = atf.rentz.arena.PromotionAdmission.inspect(other, protocol, expected);
        assertFalse(refusal.coherent());
        assertFalse(refusal.promotes());
        assertTrue(refusal.findings().stream().anyMatch(finding -> finding.contains("score profile")));
        var otherExpected = new atf.rentz.arena.PromotionAdmission.Expected(ArenaAdmissionTest.SOURCE, settings, otherScoring);
        var json = new ObjectMapper();
        Path output = directory.resolve("other");
        var replayed = json.readValue(output.resolve("summary.json").toFile(), OfflineArena.Summary.class);
        assertEquals(otherScoring, replayed.scoreProfile());
        assertTrue(atf.rentz.arena.PromotionAdmission.inspect(replayed, protocol, otherExpected).coherent());
        assertFalse(replayed.retained().isEmpty());
        for (String name : replayed.retained()) {
            var recorded = json.readTree(output.resolve(name).toFile()).required("scoreProfile");
            assertEquals(otherScoring, json.treeToValue(recorded, ScoreProfile.class));
        }
        Path report = output.resolve("arena-extent.html");
        OfflineArenaReport.publish(replayed, protocol, report);
        assertTrue(Files.readString(report).contains("trickReward=11"));
    }

    @Test void cliRequiresTheScoreProfileInTheFrozenManifest() throws Exception {
        Path project = Files.createDirectories(directory.resolve("infrastructure/workspaces/frozen"));
        Path registry = directory.resolve("learning/exposure.json");
        Path sentinel = Files.writeString(project.resolve("sentinel"), "source");
        String digest = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(sentinel)));
        var json = new ObjectMapper();
        Path manifest = directory.resolve("manifest.json");
        json.writeValue(manifest.toFile(), List.of(Map.of("path", "sentinel", "sha256", digest)));
        Path protocolFile = directory.resolve("protocol.json");
        var protocol = new OfflineArena.Protocol("scoring-manifest", "DEVELOPMENT", 3, List.of(65L),
                List.of(OfflineArena.Kind.BASELINE), OfflineArena.Kind.NORMAL, 1, 0);
        json.writeValue(protocolFile.toFile(), protocol);
        Path output = directory.resolve("run");
        var refusal = assertThrows(IllegalArgumentException.class, () -> OfflineArena.main(new String[] {
                project.toString(), protocolFile.toString(), output.toString(),
                registry.toString(), manifest.toString()}));
        assertTrue(refusal.getMessage().contains("Frozen score profile"));
        assertFalse(Files.exists(output));
    }

    @Test void referenceCandidatePublishesTheLegacyPolicyItActuallyPlays() throws Exception {
        var protocol = new OfflineArena.Protocol("reference-identity", "DEVELOPMENT", 3, List.of(73L),
                List.of(OfflineArena.Kind.NORMAL), OfflineArena.Kind.HARD_REFERENCE, 1, 0);
        var requested = new PolicySettings(8, .05, 3, .3, 2, PolicySettings.Objective.MATCH_POINTS,
                PolicySettings.Tactic.PUBLIC_TARGET_AWARE, 1);
        var output = directory.resolve("reference-identity");
        var result = OfflineArena.run(protocol, ScoreProfile.laboratory(), requested, output,
                directory.resolve("reference-identity-registry.json"), ArenaAdmissionTest.SOURCE);
        var json = new ObjectMapper().valueToTree(result);
        assertEquals("LEGACY", json.at("/effectivePolicies/candidate/search/tactic").asText(),
                "The arena played Hard legacy even though the requested profile uses the public tactic");
        assertEquals("MATCH_POINTS", json.at("/effectivePolicies/evaluationObjective").asText());
        assertEquals("PUBLIC_TARGET_AWARE", json.at("/candidateSettings/tactic").asText());
        var report = output.resolve("arena-extent.html");
        OfflineArenaReport.publish(result, protocol, report);
        String html = Files.readString(report);
        assertTrue(html.contains("Candidat efectiv Actor[kind=HARD_REFERENCE"));
        assertTrue(html.contains("tactic=LEGACY"));
        assertTrue(html.contains("Obiectiv de evaluare MATCH_POINTS"));
    }
    @Test void allArenaKindsBindTheEffectiveCandidateOpponentAndControl() throws Exception {
        var requested = new PolicySettings(8, .05, 8, .3, 2, PolicySettings.Objective.MATCH_POINTS,
                PolicySettings.Tactic.PUBLIC_TARGET_AWARE, 1);
        for (var kind : OfflineArena.Kind.values()) {
            var opponents = List.of(OfflineArena.Kind.HARD, OfflineArena.Kind.NORMAL, OfflineArena.Kind.HARD_REFERENCE);
            var protocol = new OfflineArena.Protocol("actor-" + kind, "DEVELOPMENT", 3, List.of(61L),
                    opponents, kind, 1, 0);
            var result = OfflineArena.run(protocol, ScoreProfile.laboratory(), requested, directory.resolve(kind.name()),
                    directory.resolve(kind + ".json"), ArenaAdmissionTest.SOURCE);
            var policies = result.effectivePolicies();
            var expectedSearch = switch (kind) {
                case HARD -> OfflineArena.settingsFor(protocol, requested);
                case HARD_REFERENCE -> OfflineArena.matchedReferenceSettings(OfflineArena.settingsFor(protocol, requested));
                case NORMAL, BASELINE -> null;
            };
            assertEquals(kind, policies.candidate().kind());
            assertEquals(expectedSearch, policies.candidate().search());
            assertEquals(PolicySettings.Objective.MATCH_POINTS, policies.evaluationObjective());
            assertEquals(PolicySettings.Tactic.PUBLIC_TARGET_AWARE,
                    policies.families().get(OfflineArena.Kind.HARD).opponent().search().tactic());
            assertEquals(PolicySettings.Tactic.LEGACY,
                    policies.families().get(OfflineArena.Kind.HARD_REFERENCE).opponent().search().tactic());
            assertNull(policies.families().get(OfflineArena.Kind.NORMAL).opponent().search());
            for (var family : opponents) {
                var matchup = policies.families().get(family);
                assertEquals(expectedSearch == null ? family : OfflineArena.Kind.HARD_REFERENCE, matchup.control().kind());
                var work = result.games().stream().filter(row -> row.family() == family).toList();
                assertEquals(expectedSearch != null, work.stream().anyMatch(row -> row.candidateWork().searches() > 0));
                assertEquals(matchup.control().search() != null, work.stream().anyMatch(row -> row.controlWork().searches() > 0));
            }
            var verdict = atf.rentz.arena.PromotionAdmission.inspect(result, protocol,
                    new atf.rentz.arena.PromotionAdmission.Expected(ArenaAdmissionTest.SOURCE, requested, ScoreProfile.laboratory()));
            assertTrue(verdict.coherent(), () -> kind + " " + verdict.findings());
            assertFalse(verdict.promotes());
        }
    }
    @Test void promotionRejectsEmptyMissingDuplicateOrWrongObjectiveFamilies() throws Exception {
        var valid=ArenaAdmissionTest.neighbor();
        var fresh=ArenaAdmissionTest.PROTOCOL;
        var settings=ArenaAdmissionTest.SETTINGS;
        var expected=new atf.rentz.arena.PromotionAdmission.Expected(ArenaAdmissionTest.SOURCE,settings,valid.scoreProfile());
        var normal=valid.families().get(0);var reference=valid.families().get(1);
        var altered=new ObjectMapper().valueToTree(reference);
        ((com.fasterxml.jackson.databind.node.ObjectNode)altered.get("assessment")).put("metric","MARGIN");
        var wrong=new ObjectMapper().treeToValue(altered,OfflineArena.FamilySummary.class);
        for(var families:List.of(List.<OfflineArena.FamilySummary>of(),List.of(normal),List.of(normal,normal),List.of(normal,wrong))){
            var summary=new OfflineArena.Summary(fresh.id(),fresh.phase(),valid.sourceIdentity(),valid.referenceParity(),valid.state(),families,valid.games(),valid.retained(),fresh.policy(),settings,valid.referenceSettings(),fresh,valid.effectivePolicies(),valid.scoreProfile());
            assertFalse(OfflineArena.promotable(summary,fresh,expected));
        }
        assertTrue(OfflineArena.promotable(valid,fresh,expected));
        var unmatched=new OfflineArena.Summary(fresh.id(),fresh.phase(),valid.sourceIdentity(),valid.referenceParity(),valid.state(),valid.families(),valid.games(),valid.retained(),fresh.policy(),settings,PolicySettings.handMarginReference(),fresh,valid.effectivePolicies(),valid.scoreProfile());
        assertFalse(OfflineArena.promotable(unmatched,fresh,expected),"A larger reference budget cannot silently qualify the comparison");
        assertFalse(OfflineArena.promotable(valid,new OfflineArena.Protocol(fresh.id(),fresh.phase(),4,List.of(1L),fresh.opponents(),OfflineArena.Kind.HARD,1,0),expected),"An unrelated assessment cannot qualify the declared data");
        assertFalse(OfflineArena.promotable(valid,new OfflineArena.Protocol("other","DEVELOPMENT",4,List.of(1L),fresh.opponents(),OfflineArena.Kind.HARD,1,0),expected));
    }
}
