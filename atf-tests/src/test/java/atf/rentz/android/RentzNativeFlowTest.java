package atf.rentz.android;

import ro.rentzlab.ai.*;

import atf.rentz.EvaluationRegistry;
import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ro.rentzlab.core.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class RentzNativeFlowTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @TempDir Path directory;
    @Test void nativeReportNamesOnlyTheGameScopeThatActuallyRan(){
        var acceptance=RentzNativeFlow.reportScope("0.6.0",true,"five deals across three contracts at enlarged font");
        assertTrue(acceptance.title().contains("jocuri consecutive complete"));
        assertTrue(acceptance.description().contains("five deals across three contracts at enlarged font"));

        var narrow=RentzNativeFlow.reportScope("0.4.0",false,"one trick hand and menu navigation");
        assertFalse(narrow.title().contains("jocuri consecutive"));
        assertTrue(narrow.description().contains("one trick hand and menu navigation"));
        assertTrue(narrow.description().contains("nu pretinde acceptanța cu jocuri consecutive complete"));
    }
    record Execution(int exit,String output,String error){}
    private Execution invoke(String...args)throws Exception{
        Path run=Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));
        var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx128m","-XX:ActiveProcessorCount=2","-Djava.io.tmpdir="+run,"-cp",System.getProperty("java.class.path"),RentzNativeFlow.class.getName()));
        command.addAll(List.of(args));Path out=run.resolve("out.json"),error=run.resolve("error.log");
        var process=new ProcessBuilder(command).redirectOutput(out.toFile()).redirectError(error.toFile()).start();
        if(!process.waitFor(40,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Owned native-adapter subprocess exceeded its deadline");}
        return new Execution(process.exitValue(),Files.readString(out),Files.readString(error));
    }
    private Path actor(RentzMatch game)throws Exception{
        Path file=directory.resolve("actor.json");JSON.writeValue(file.toFile(),Map.of("view",game.observe(0)));return file;
    }
    @Test void decisionsUseOnlyTheSuppliedPublicViewAndKeepUnicodeTransportExplicit()throws Exception{
        var game=new RentzMatch(4,31,ScoreProfile.laboratory());Path file=actor(game);
        var hidden=invoke("decide",file.toString());assertEquals(0,hidden.exit(),hidden.error());assertEquals("REVEAL",JSON.readTree(hidden.output()).required("action").textValue());
        assertTrue(hidden.output().chars().allMatch(c->c<128));assertEquals("Vezi cărțile",JSON.readTree(hidden.output()).required("label").textValue());
        game.reveal(0);var choice=invoke("decide",actor(game).toString());assertEquals(0,choice.exit(),choice.error());
        var selected=Contract.valueOf(JSON.readTree(choice.output()).required("contract").textValue());assertTrue(game.observe(0).available().contains(selected));
        game.choose(0,Contract.WHIST,false);var move=invoke("decide",actor(game).toString());assertEquals(0,move.exit(),move.error());
        var node=JSON.readTree(move.output());var card=new Card(Card.Suit.valueOf(node.required("suit").textValue()),node.required("rank").intValue());
        assertTrue(game.observe(0).legal().contains(card));assertFalse(Files.exists(directory.resolve("session.json")),"No private journal was supplied to decide");
    }
    @Test void replayAuditAcceptsAValidNeighborAndRejectsAPublicScoreMismatch()throws Exception{
        var game=new RentzMatch(4,31,ScoreProfile.laboratory());var commands=new ArrayList<Map<String,Object>>();
        game.choose(0,Contract.WHIST,false);commands.add(Map.of("action","CHOOSE","seat",0,"contract","WHIST","blind",false));
        for(int n=0;n<4;n++){int seat=game.observe(0).turn();Card card=LabPolicy.card(game.observe(seat));game.play(seat,card);commands.add(Map.of("action","PLAY","seat",seat,"card",card));}
        Path journal=directory.resolve("private.json");JSON.writeValue(journal.toFile(),Map.of("schema",1,"players",4,"seed",31,"profile","LABORATORY_V1","rules","RENTZ_RULES_V2","commands",commands));
        Path file=actor(game);var valid=invoke("audit",journal.toString(),file.toString());assertEquals(0,valid.exit(),valid.error());assertEquals(4,JSON.readTree(valid.output()).required("auditedTransitions").intValue());
        var corrupted=JSON.readTree(file.toFile());((com.fasterxml.jackson.databind.node.ObjectNode)corrupted.required("view")).set("dealPoints",JSON.valueToTree(List.of(999,0,0,0)));JSON.writeValue(file.toFile(),corrupted);
        var invalid=invoke("audit",journal.toString(),file.toString());assertNotEquals(0,invalid.exit());assertTrue(invalid.error().contains("differs"));assertFalse(invalid.output().contains("\"status\":\"PASS\""));
    }
    @Test void auditReplaysCompletedHistoryWhenTheAndroidProjectionOmitsIt() throws Exception {
        var game = new RentzMatch(4, 31, ScoreProfile.laboratory());
        var commands = new ArrayList<Map<String, Object>>();
        game.choose(0, Contract.WHIST, false);
        commands.add(Map.of("action", "CHOOSE", "seat", 0, "contract", "WHIST", "blind", false));
        for (int n = 0; n < 32; n++) {
            int seat = game.observe(0).turn();
            Card card = LabPolicy.card(game.observe(seat));
            game.play(seat, card);
            commands.add(Map.of("action", "PLAY", "seat", seat, "card", card));
        }
        assertEquals(RentzMatch.Phase.DEAL_END, game.observe(0).phase());
        Path journal = directory.resolve("private.json");
        JSON.writeValue(journal.toFile(), Map.of("schema", 1, "players", 4, "seed", 31,
                "profile", "LABORATORY_V1", "rules", "RENTZ_RULES_V2", "commands", commands));
        Path file = actor(game);
        var payload = JSON.readTree(file.toFile());
        var projection = (com.fasterxml.jackson.databind.node.ObjectNode) payload.required("view");
        var completedHands = projection.remove("completedHands");
        assertEquals(1, completedHands.size());
        JSON.writeValue(file.toFile(), payload);

        var valid = invoke("audit", journal.toString(), file.toString());
        assertEquals(0, valid.exit(), valid.error());
        var audited = JSON.readTree(valid.output());
        assertEquals(1, audited.required("completedDeals").intValue());
        assertEquals(completedHands, audited.required("history"));

        projection.set("completedHands", completedHands);
        ((com.fasterxml.jackson.databind.node.ObjectNode) completedHands.get(0)).put("number", 2);
        JSON.writeValue(file.toFile(), payload);
        var altered = invoke("audit", journal.toString(), file.toString());
        assertNotEquals(0, altered.exit());
        assertTrue(altered.error().contains("differs"), altered.error());
    }
    @Test void exposureRefusalPreservesTheFreshRegistryAndNewDevelopmentDataIsAdmitted()throws Exception{
        Path registry=directory.resolve("exposure.json"),journal=directory.resolve("private.json");
        EvaluationRegistry.expose(registry,4,List.of(717L),"FRESH_EVALUATION","fresh","source");String before=Files.readString(registry);
        JSON.writeValue(journal.toFile(),Map.of("players",4,"seed",717));var refused=invoke("expose",journal.toString(),registry.toString());assertNotEquals(0,refused.exit());assertEquals(before,Files.readString(registry));
        JSON.writeValue(journal.toFile(),Map.of("players",4,"seed",718));var accepted=invoke("expose",journal.toString(),registry.toString());assertEquals(0,accepted.exit(),accepted.error());
        assertEquals("DEVELOPMENT",JSON.readTree(registry.toFile()).required("exposures").get(1).required("phase").textValue());
    }
    @Test void unsupportedOrIncompleteCommandsFailWithoutAFalseSuccessReceipt()throws Exception{
        for(var arguments:List.of(new String[]{},new String[]{"unknown"},new String[]{"decide"})){
            var result=invoke(arguments);assertNotEquals(0,result.exit());assertFalse(result.output().contains("PASS"));assertFalse(result.error().isBlank());
        }
    }
}
