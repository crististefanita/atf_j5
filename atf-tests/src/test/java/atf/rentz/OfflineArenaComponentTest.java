package atf.rentz;

import ro.rentzlab.ai.*;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import ro.rentzlab.core.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
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
    @Test void promotionRejectsEmptyMissingDuplicateOrWrongObjectiveFamilies(){
        var seeds=java.util.stream.LongStream.range(0,8).boxed().toList();
        var fresh=new OfflineArena.Protocol("fresh","FRESH_EVALUATION",4,seeds,List.of(OfflineArena.Kind.NORMAL,OfflineArena.Kind.HARD_REFERENCE),OfflineArena.Kind.HARD,1,0);
        var samples=new ArrayList<atf.rentz.evaluation.ClusteredEvaluation.Sample>();for(int seed=0;seed<8;seed++)for(int seat=0;seat<4;seat++)samples.add(new atf.rentz.evaluation.ClusteredEvaluation.Sample(seed,seat,1,1,1));
        var win=atf.rentz.evaluation.ClusteredEvaluation.assess(samples,4,atf.rentz.evaluation.ClusteredEvaluation.Metric.WIN_SHARE,2);
        var margin=atf.rentz.evaluation.ClusteredEvaluation.assess(samples,4,atf.rentz.evaluation.ClusteredEvaluation.Metric.MARGIN,2);
        var normal=new OfflineArena.FamilySummary(OfflineArena.Kind.NORMAL,32,8,1,1,1,1,0,1,win);
        var reference=new OfflineArena.FamilySummary(OfflineArena.Kind.HARD_REFERENCE,32,8,1,1,1,1,0,1,win);
        var wrong=new OfflineArena.FamilySummary(OfflineArena.Kind.HARD_REFERENCE,32,8,1,1,1,1,0,1,margin);
        var settings=new PolicySettings(1,0,3,.3,2,PolicySettings.Objective.MATCH_WIN_SHARE);
        for(var families:List.of(List.<OfflineArena.FamilySummary>of(),List.of(normal),List.of(normal,normal),List.of(normal,wrong))){
            var summary=new OfflineArena.Summary("fresh","FRESH_EVALUATION","fixture","UNCONFIRMED","MEASURED",families,List.of(),List.of(),"rentz-match-policy.yaml",settings,OfflineArena.matchedReferenceSettings(settings));
            assertFalse(OfflineArena.promotable(summary,fresh));
        }
        var valid=new OfflineArena.Summary("fresh","FRESH_EVALUATION","fixture","UNCONFIRMED","MEASURED",List.of(normal,reference),List.of(),List.of(),"rentz-match-policy.yaml",settings,OfflineArena.matchedReferenceSettings(settings));
        assertTrue(OfflineArena.promotable(valid,fresh));
        var unmatched=new OfflineArena.Summary("fresh","FRESH_EVALUATION","fixture","UNCONFIRMED","MEASURED",valid.families(),List.of(),List.of(),"rentz-match-policy.yaml",settings,PolicySettings.handMarginReference());
        assertFalse(OfflineArena.promotable(unmatched,fresh),"A larger reference budget cannot silently qualify the comparison");
        assertFalse(OfflineArena.promotable(valid,new OfflineArena.Protocol("fresh","FRESH_EVALUATION",4,List.of(1L),fresh.opponents(),OfflineArena.Kind.HARD,1,0)),"An unrelated assessment cannot qualify the declared data");
        assertFalse(OfflineArena.promotable(valid,new OfflineArena.Protocol("other","DEVELOPMENT",4,List.of(1L),fresh.opponents(),OfflineArena.Kind.HARD,1,0)));
    }
}
