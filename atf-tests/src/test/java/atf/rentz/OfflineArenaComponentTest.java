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
    @TempDir Path directory;
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
}
