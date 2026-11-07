package atf.rentz.android;

import ro.rentzlab.ai.*;

import atf.rentz.OfflineGameAudit;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.*;
import atf.reporting.evidence.ReportScreenshotPolicy;
import atf.reporting.evidence.profile.ReportEvidenceProfile;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.ui.UiExtentReportSession;
import atf.reporting.ui.model.*;
import atf.reporting.model.*;
import com.fasterxml.jackson.databind.*;
import ro.rentzlab.core.*;
import java.nio.file.*;
import java.util.*;

/** Native acceptance adapter. Decisions read only actor-view.json; the private journal is an audit-only input. */
public final class RentzNativeFlow {
    private static final ObjectMapper JSON=new ObjectMapper().configure(com.fasterxml.jackson.core.json.JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(),true);
    public static void main(String[] args)throws Exception{
        switch (args[0]) {
            case "decide" -> decide(Path.of(args[1]));
            case "audit" -> audit(Path.of(args[1]), Path.of(args[2]));
            case "begin-exposure" -> atf.rentz.EvaluationRegistry.beginNativeExposure(Path.of(args[1]), args[2]);
            case "expose", "complete-exposure" -> recordExposure(args);
            case "report" -> report(Path.of(args[1]));
            default -> throw new IllegalArgumentException("decide, audit, begin-exposure, expose, complete-exposure or report");
        }
    }

    private static void recordExposure(String[] args) throws Exception {
        var saved = JSON.readTree(Path.of(args[1]).toFile());
        var players = saved.required("players");
        var seed = saved.required("seed");
        if (!players.isInt() || !seed.isIntegralNumber() || !seed.canConvertToLong())
            throw new IllegalArgumentException("Native journal exposure needs exact players and seed");
        Path registry = Path.of(args[2]);
        if (args[0].equals("complete-exposure")) {
            atf.rentz.EvaluationRegistry.completeNativeExposure(registry, args[3], players.intValue(), seed.longValue());
        } else {
            atf.rentz.EvaluationRegistry.expose(registry, players.intValue(), List.of(seed.longValue()),
                    "DEVELOPMENT", "native-rentz-sanity", "android-native-laboratory-v1");
        }
        System.out.println("{\"exposure\":\"DEVELOPMENT_RECORDED\"}");
    }
    private static RentzMatch.Observation view(Path file)throws Exception{return JSON.treeToValue(JSON.readTree(file.toFile()).required("view"),RentzMatch.Observation.class);}
    private static void decide(Path file)throws Exception{
        var v=view(file);var decision=JSON.createObjectNode();
        if(v.phase()==RentzMatch.Phase.CHOOSE){
            if(!v.revealed())decision.put("action","REVEAL").put("label","Vezi cărțile");
            else{var selected=new RentzPlayer(RentzPlayer.Difficulty.HARD,ScoreProfile.laboratory(),PolicySettings.defaults()).choose(v);decision.put("action","CHOOSE").put("label","Alege: "+selected.selected().label()).put("contract",selected.selected().name());}
        }else if(v.phase()==RentzMatch.Phase.PLAY){
            if(v.legal().isEmpty())decision.put("action","PASS").put("label","Pas");
            else{var selected=new RentzPlayer(RentzPlayer.Difficulty.HARD,ScoreProfile.laboratory(),PolicySettings.defaults()).card(v);decision.put("action","PLAY").put("label","Joacă "+selected.selected().label()).put("suit",selected.selected().suit().name()).put("rank",selected.selected().rank()).put("method",selected.method());}
        }else decision.put("action","COMPLETE");
        System.out.println(JSON.writeValueAsString(decision));
    }
    private static void audit(Path privateJournal,Path actorView)throws Exception{
        var journal=JSON.readTree(privateJournal.toFile());
        if(journal.required("schema").intValue()!=1||!journal.required("profile").textValue().equals("LABORATORY_V1")||!Set.of("RENTZ_RULES_V1","RENTZ_RULES_V2").contains(journal.path("rules").asText("RENTZ_RULES_V1")))throw new IllegalArgumentException("Native journal profile or rules");
        var score=ScoreProfile.laboratory();var game=new RentzMatch(journal.required("players").intValue(),journal.required("seed").longValue(),score);int audited=0;
        for(var command:journal.required("commands")){
            int seat=command.required("seat").intValue();String action=command.required("action").textValue();
            switch(action){
                case "REVEAL"->game.reveal(seat);
                case "CHOOSE"->game.choose(seat,Contract.valueOf(command.required("contract").textValue()),command.required("blind").booleanValue());
                case "NEXT_HAND"->game.nextHand();
                case "PLAY"->{var before=game.observe(seat);Card card=JSON.treeToValue(command.required("card"),Card.class);game.play(seat,card);OfflineGameAudit.card(before,card,game.observe(seat),score);audited++;}
                case "PASS"->{var before=game.observe(seat);game.pass(seat);OfflineGameAudit.pass(before,game.observe(seat));audited++;}
                default->throw new IllegalArgumentException("Unknown native command");
            }
        }
        var actual=view(actorView);if(!game.observe(0).equals(actual))throw new IllegalStateException("Native public projection differs from replayed UI journal");
        System.out.println(JSON.writeValueAsString(Map.of("status","PASS","auditedTransitions",audited,"completedDeals",game.history().size(),"phase",actual.phase(),"totals",actual.totals(),"points",actual.dealPoints(),"history",game.history())));
    }
    private static void report(Path directory)throws Exception{
        var receipt=JSON.readTree(directory.resolve("ui-result.json").toFile());var points=JSON.readTree(directory.resolve("checkpoints.json").toFile());
        boolean all=receipt.required("status").textValue().equals("PASS");
        try(var session=new ExtentReportSession(directory.resolve("android-extent.html"),ReportingSettings.defaults())){
            var raw=session.startScenario("Rentz Lab · joc și reluare prin interfață","atf.rentz","APK local "+receipt.path("versionName").asText("0.1.0")+", profil de laborator; aplicația originală și partida completă nu sunt calificate prin acest sanity.",List.of("android","rentz","native","sanity"));
            try(var phase=raw.startPhase("Identitatea aplicației și domeniul acceptanței")){
                phase.observation(new ReportObservation("Receipt Android",List.of(new ReportField("APK SHA-256",receipt.path("apkSha256").asText()),new ReportField("Pachet",receipt.path("package").asText()),new ReportField("Dispozitiv",receipt.path("serial").asText()),new ReportField("API Android",receipt.path("api").asText()),new ReportField("Domeniu",receipt.path("scope").asText())),List.of(new ReportObservation.Body("Rezultat, proveniență și limitări",JSON.writerWithDefaultPrettyPrinter().writeValueAsString(receipt),ReportObservation.Format.JSON)),List.of()));phase.complete(all?AtfReportStatus.PASS:AtfReportStatus.FAIL);
            }
            try(var ui=UiExtentReportSession.adaptScenario(raw,ReportEvidenceProfile.forScreenshotPolicy(ReportScreenshotPolicy.EACH_CHECKPOINT),"Android native PNG")){
                try(var phase=ui.startPhase("Visual acceptance observation")){
                    int ordinal=0;
                    for(var point:points){
                        var checks=new ArrayList<AtfAssertion>();boolean passed=true;
                        for(var check:point.required("checks")){boolean ok=check.required("passed").booleanValue();checks.add(new AtfAssertion(check.required("name").asText(),check.required("expected").asText(),check.required("actual").asText(),ok));passed&=ok;}
                        byte[] png=Files.readAllBytes(directory.resolve(point.required("image").asText()));if(png.length<8||png[0]!=(byte)137||png[1]!=80||png[2]!=78||png[3]!=71)throw new IllegalArgumentException("Original PNG required");
                        String title=point.required("title").asText();var observation=new AtfUiActionRecord(++ordinal,"Read Android native display",title,"Păstrează afișajul complet și comparațiile",point.path("observed").asText(),point.path("captureMillis").asLong(),passed?AtfReportStatus.PASS:AtfReportStatus.FAIL,AtfUiEvidenceSource.OBSERVE);
                        phase.step(AtfGherkinKeyword.THEN,title,"Android display",Base64.getEncoder().encodeToString(png),checks,List.of(observation),ReportScreenshotPolicy.CapturePoint.CHECKPOINT,passed?AtfReportStatus.PASS:AtfReportStatus.FAIL);all&=passed;
                    }
                    phase.complete(all?AtfReportStatus.PASS:AtfReportStatus.FAIL);
                }
                ui.complete(all?AtfReportStatus.PASS:AtfReportStatus.FAIL);
            }
        }
    }
}
