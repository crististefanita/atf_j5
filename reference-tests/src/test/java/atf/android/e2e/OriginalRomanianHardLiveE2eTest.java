package atf.android.e2e;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import atf.android.AdbClient;
import atf.android.e2e.support.AndroidUiFlowTrace;
import atf.android.e2e.support.AndroidUiFlowReportPublisher;
import atf.reporting.contract.*;
import atf.reporting.ui.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Original installed app only. Privileged auditing and public-only decisions are separate seams. */
@AndroidE2eTest
class OriginalRomanianHardLiveE2eTest {
    private static final String PACKAGE="com.qsoft.android.whist";
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final OriginalRomanianPublicDecision.ExpectedProfile PROFILE=OriginalRomanianPublicDecision.ExpectedProfile.ROMANIAN_FOUR_PLAYERS;

    @Test void aCompleteOriginalHardRomanianMatchUsesOnlyOwnAndPublicDecisionInformation()throws Exception {
        assumeTrue(Boolean.getBoolean("atf.original.live"),"Explicit original-app live opt-in required");
        assumeTrue(Boolean.getBoolean("atf.original.exclusiveReady"),"Wait for confirmed exclusive emulator availability");
        Path out=Path.of(System.getProperty("atf.original.evidence")).toAbsolutePath().normalize();
        Files.createDirectories(out);requireLease(out);
        assertFalse(Files.exists(out.resolve("live-run-started.json")),"A new live match needs a fresh evidence folder; never overwrite or silently restart a partial game");
        String retainedSetting=System.getProperty("atf.original.continueFrom","");
        Path retained=retainedSetting.isBlank()?null:Path.of(retainedSetting).toAbsolutePath().normalize();
        if(retained!=null)copyRetainedEvidence(retained,out);
        write(out.resolve("live-run-started.json"),Map.of("package",PACKAGE,"requestedMode","Romanian1-8-1","players",4,"opponents","Hard","decisionPolicy","Accepted corrected Hard; actual private opponent hands are not decision inputs"));
        AdbClient adb=AdbClient.fromSystemProperties();var ui=new AndroidUiDriver(adb,out);var probe=new OriginalWhistProbe(adb,ui,out);
        var trace=new AndroidUiFlowTrace(out,"original-romanian-hard","Original Contract Whist: Hard, Romanian1-8-1, four seats",getClass().getSimpleName(),"atf.android.e2e",PACKAGE,List.of("original-app","romanian","hard","public-only-player","full-match"));
        if(retained!=null){
            var replay=OriginalRomanianTraceReplay.append(retained.resolve("ui-flow-original-romanian-hard.properties"),out,trace);
            trace.recordAction(new AtfUiActionRecord(1,"RECOVERY PROVENANCE — explanatory record","Previous original-app attempt",
                    "Retain the failed attempt; confirm acceptance from the current same game before any new input",
                    "Source="+retained+"; prior status="+replay.priorStatus()+"; prior failure="+replay.priorFailure()+". Prior captured checkpoints are retained; this does not relabel the previous native attempt as PASS.",
                    0,AtfReportStatus.INFO,AtfUiEvidenceSource.OBSERVE));
        }
        trace.recordAction(new AtfUiActionRecord(1,"ORACLE RULE AND CARD LEGEND — explanation, no extra assertion","Romanian four-player profile",
                "Document the independent oracle semantics; no UI action is dispatched by this record",
                "Card IDs: suit*13+rank-2; suit0=spades/pica,1=hearts/inima,2=clubs/trefla,3=diamonds/caro. Ranks7..14(A); four players use32cards. Follow led suit; when void, trump is mandatory if held; otherwise discard. Highest trump wins, otherwise highest card of led suit. Winner leads next. Last bidder cannot make total bids equal hand size. Schedule:1x4,2..7,8x4,7..2,1x4;24hands,94tricks,376plays. Eight-card hands have NO TRUMP; other hands expose one undealt turned trump card. Exact contract scores5+won; missed contract scores-abs(won-bid). ORIGINAL series reward/penalty is+5/-5 after five eligible consecutive hands, then reset. One-card hands do not advance series but an opposite result breaks it. Our independent auditor checks ownership, legal play, winners, cumulative score and series. Actual private opponent holdings are excluded from decision policy inputs.",
                0,AtfReportStatus.INFO,AtfUiEvidenceSource.OBSERVE));
        ui.recordActionsAs(trace::recordAction);Throwable originalFailure=null;int[] totals=new int[4],streaks=new int[4];int humanCards=0,firstDealer=-1,observedMode=-1;String androidApi="not observed";
        try {
            var api=adb.run("shell","getprop","ro.build.version.sdk");api.requireSuccess();androidApi=api.output().trim();
            OriginalWhistState continuationLive=null;
            if(retained!=null){
                // Capture the still-running game BEFORE any force-stop; an accepted input may not yet be on disk.
                // Bringing its existing task forward is allowed; never force-stop or start a new game here.
                requireLease(out);long started=System.nanoTime();
                adb.run("shell","am","start","-W","-n",PACKAGE+"/.Whist").requireSuccess();
                ui.recordAction("Bring retained original task to foreground",PACKAGE,"resume the same saved/running game; never New Game",
                        "ADB accepted foreground activation without force-stop",Duration.ofNanos(System.nanoTime()-started).toMillis(),AtfReportStatus.PASS);
                Thread.sleep(900);requireOriginalFocus(adb,out);
                String resumed=ui.dump();
                if(ui.hasText(resumed,"Resume Game")&&ui.isEnabled(resumed,"Resume Game")){ui.tapText("Resume Game");Thread.sleep(1200);}
                requireOriginalFocus(adb,out);continuationLive=probe.observe("continuation-current");
            }else{
            requireLease(out);String menu=ui.restartToMainMenu(PACKAGE);requireOriginalFocus(adb,out);
            if(ui.hasText(menu,"Resume Game")&&ui.isEnabled(menu,"Resume Game")) {
                ui.tapText("Resume Game");Thread.sleep(2500);requireOriginalFocus(adb,out);
                probe.observe("pre-live-preserved-state",false);
                requireLease(out);ui.restartToMainMenu(PACKAGE);
            }
            requireOriginalFocus(adb,out);ui.tapText("New Game");ui.awaitText("START",Duration.ofSeconds(20));
            String modeLabel=System.getProperty("atf.original.modeLabel","Romanian");
            ui.selectSpinnerOption(0,"4");ui.awaitSpinnerSelection("4",Duration.ofSeconds(15));
            ui.selectSpinnerOption(1,modeLabel);ui.awaitSpinnerSelection(modeLabel,Duration.ofSeconds(15));
            ui.selectSpinnerOption(2,"Hard");ui.awaitSpinnerSelection("Hard",Duration.ofSeconds(15));
            ui.capture("original-hard-setup");
            var selected=ui.texts(ui.dump()).stream().filter(text->List.of("4",modeLabel,"Hard").contains(text)).toList();
            CompleteOriginalMatchE2eTest.checkpoint(trace,out,"SETUP","Select the ORIGINAL Romanian four-player Hard profile","original-hard-setup","4 / "+modeLabel+" / Hard",List.of(new AtfAssertion("Observed explicit setup","4 / "+modeLabel+" / Hard",selected.toString(),selected.containsAll(List.of("4",modeLabel,"Hard")))));
            requireOriginalFocus(adb,out);ui.tapText("START");ui.awaitText("Bid 0",Duration.ofSeconds(20));
            }
            for(int h=0;h<OriginalRomanianAudit.schedule().size();h++) {
                String prefix=String.format("original-romanian-h%02d",h+1);requireOriginalFocus(adb,out);
                boolean recorded=Files.isRegularFile(out.resolve(prefix+"-bidding.save2"));
                OriginalWhistState deal=recorded?OriginalWhistState.read(Files.readAllBytes(out.resolve(prefix+"-bidding.save2"))):probe.observe(prefix+"-bidding");
                if(h==0){firstDealer=deal.dealer;observedMode=deal.mode;}
                var audit=new OriginalRomanianAudit(deal,h,OriginalRomanianAudit.dealerAfter(firstDealer,h),totals,streaks,true,observedMode);
                OriginalWhistState state;int acceptedCards=0;
                if(recorded){
                    requireChecks(audit.start(deal));
                    state=OriginalWhistState.read(Files.readAllBytes(out.resolve(prefix+"-turn00.save2")));
                    requireChecks(audit.verify(state));requireChecks(OriginalRomanianDecisionReceipt.bid(selected(out.resolve(prefix+"-bid-decision.json")),state.bids[0]));
                    for(int turn=1;turn<=deal.handSize&&Files.isRegularFile(out.resolve(prefix+String.format("-turn%02d.save2",turn)));turn++){
                        String label=prefix+String.format("-turn%02d",turn);var before=audit.publicPlays();int remaining=state.remaining[0];
                        state=OriginalWhistState.read(Files.readAllBytes(out.resolve(label+".save2")));
                        requireChecks(audit.verify(state));requireChecks(OriginalRomanianDecisionReceipt.card(selected(out.resolve(label+"-decision.json")),remaining,before,audit.publicPlays(),state.remaining[0]));
                        acceptedCards++;
                    }
                    if(continuationLive!=null&&continuationLive.handIndex==h){
                        int delta=state.remaining[0]-continuationLive.remaining[0];
                        assertTrue(delta==0||delta==1,"A recovered input may consume zero or exactly one own card, never silently skip several inputs");
                        var before=audit.publicPlays();int remaining=state.remaining[0];var checks=new ArrayList<>(audit.verify(continuationLive));
                        String label=prefix+String.format("-turn%02d",acceptedCards+1);
                        if(delta==1){
                            int pending=selected(out.resolve(label+"-decision.json"));
                            checks.addAll(OriginalRomanianDecisionReceipt.card(pending,remaining,before,audit.publicPlays(),continuationLive.remaining[0]));
                            acceptedCards++;
                        }else{
                            label=prefix+"-recovery";
                            boolean identical=before.equals(audit.publicPlays());
                            checks.add(new AtfAssertion("Recovery without own input retains the independently audited public trace","true",Boolean.toString(identical),identical));
                        }
                        copyObservation(out,"continuation-current",label);
                        CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEST BODY","Recover the SAME hand after the retained harness failure; no card input replayed",label,
                                "Prior failed attempt retained separately. Acceptance is observed now, not a fabricated successful ACTION. "+continuationLive.summary(),checks);
                        state=continuationLive;continuationLive=null;
                    }else assertTrue(state.handOver,"An incomplete retained hand must match the current original hand before any input");
                }else{
                if(continuationLive!=null){
                    assertEquals(h,continuationLive.handIndex,"An unrecorded continuation must be the current hand");
                    assertTrue(continuationLive.bidding,"Cannot reconstruct an unrecorded initial deal after card play");
                    continuationLive=null;
                }
                CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEST BODY","Hand"+(h+1)+": public auction, scheduled size and independent rule audit",prefix+"-bidding",deal.summary(),audit.start(deal));
                var bid=OriginalRomanianPublicDecision.chooseBid(deal,streaks,PROFILE);
                Files.writeString(out.resolve(prefix+"-bid-decision.json"),bid.json(),StandardOpenOption.CREATE_NEW);
                requireOriginalFocus(adb,out);String xml=ui.dump();
                if(!ui.hasText(xml,"Bid "+bid.value())){ui.tapText("Bid Other");ui.awaitText("Bid "+bid.value(),Duration.ofSeconds(10));}
                ui.tapText("Bid "+bid.value());Thread.sleep(5500);
                state=observe(probe,adb,out,prefix+"-turn00",true);
                var bidChecks=new ArrayList<>(audit.verify(state));
                bidChecks.addAll(OriginalRomanianDecisionReceipt.bid(bid.value(),state.bids[0]));
                CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEST BODY","Hand"+(h+1)+": submit bid"+bid.value(),prefix+"-turn00",state.summary(),bidChecks);
                }
                humanCards+=acceptedCards;
                while(!state.handOver) {
                    var choice=OriginalRomanianPublicDecision.chooseCard(state,streaks,PROFILE);
                    String label=prefix+String.format("-turn%02d",acceptedCards+1);
                    Path decisionFile=out.resolve(label+"-decision.json");
                    if(Files.exists(decisionFile))assertEquals(selected(decisionFile),choice.value(),"Retained unaccepted intention must agree before retrying the same input");
                    else Files.writeString(decisionFile,choice.json(),StandardOpenOption.CREATE_NEW);
                    int slot=state.hand(0).indexOf(choice.value());assertTrue(slot>=0,"The public-only choice must be held by seat0");
                    int x=Math.round(state.x[0][slot]+Math.min(40,state.width[0][slot]/2));
                    int y=Math.round(state.y[0][slot]+Math.min(40,state.height[0][slot]/2));
                    assertTrue(x>=0&&y>=0,"Observed card geometry must be on screen");
                    var priorPublicPlays=audit.publicPlays();int priorOwnRemaining=state.remaining[0];
                    requireOriginalFocus(adb,out);long started=System.nanoTime();
                    try{adb.run("shell","input","tap",Integer.toString(x),Integer.toString(y)).requireSuccess();}
                    catch(Exception inputFailure){ui.recordAction("Play observed own card",OriginalWhistState.card(choice.value()),"consume exactly one own card",inputFailure.toString(),Duration.ofNanos(System.nanoTime()-started).toMillis(),AtfReportStatus.FAIL);throw inputFailure;}
                    ui.recordAction("Play observed own card",OriginalWhistState.card(choice.value()),"consume exactly one own card","ADB accepted the tap at observed own-card geometry",Duration.ofNanos(System.nanoTime()-started).toMillis(),AtfReportStatus.PASS);
                    Thread.sleep(5500);acceptedCards++;humanCards++;
                    state=observe(probe,adb,out,label,!(h==23&&acceptedCards==deal.handSize));
                    var checks=new ArrayList<>(audit.verify(state));
                    checks.addAll(OriginalRomanianDecisionReceipt.card(choice.value(),priorOwnRemaining,priorPublicPlays,audit.publicPlays(),state.remaining[0]));
                    CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEST BODY","Hand"+(h+1)+", human turn"+acceptedCards+": "+OriginalWhistState.card(choice.value()),label,state.summary(),checks);
                    assertTrue(acceptedCards<=deal.handSize,"Unaccepted inputs cannot loop indefinitely");
                    System.out.println("ORIGINAL_ROMANIAN hand="+(h+1)+"/24; ownCardsPlayed="+acceptedCards+"/"+deal.handSize+"; bids="+Arrays.toString(state.bids)+"; won="+Arrays.toString(state.won));
                }
                assertEquals(deal.handSize,acceptedCards);totals=audit.totals();streaks=audit.streaks();
                Path publicRecord=out.resolve(prefix+"-public-complete.json");
                if(Files.exists(publicRecord))publicRecord=out.resolve(prefix+"-public-revalidated.json");
                write(publicRecord,Map.of("hand",h+1,"size",deal.handSize,"dealer",deal.dealer,"trump",deal.trump,"bids",state.bids,"won",state.won,"publicPlays",audit.publicPlays(),"totals",totals,"streaks",streaks));
                write(out.resolve("live-progress.json"),Map.of("completedHands",h+1,"humanCards",humanCards,"totals",totals,"complete",h==23));
                if(h<23&&(continuationLive==null||continuationLive.handIndex==h)){
                    requireOriginalFocus(adb,out);ui.tapText("Next Hand");ui.awaitText("Bid 0",Duration.ofSeconds(20));
                }
            }
            assertEquals(94,humanCards,"A full Romanian4 schedule contains94 tricks and376 total card plays");
            String finalLabel="original-romanian-h24-turn01";
            boolean originalGameOver=OriginalWhistState.gameOverFromBackup(out.resolve(finalLabel+".ab"));
            CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEARDOWN","Original persisted game-over flag confirms completion of the full match",finalLabel,
                    "Original gameover preference="+originalGameOver,List.of(new AtfAssertion("Original app marks the match over","true",Boolean.toString(originalGameOver),originalGameOver)));
            int best=Arrays.stream(totals).max().orElseThrow();boolean top=totals[0]==best;
            boolean win=totals[0]>Arrays.stream(totals).skip(1).max().orElseThrow();
            write(out.resolve("original-hard-outcome.json"),Map.of("complete",true,"hands",24,"tricks",94,"cardPlays",376,"totals",totals,"humanWinOrTie",top,"humanStrictWin",win,"victoryStatus",win?"PASS":"FAIL","ruleAndScoreStatus","PASS","originalGameOver",originalGameOver));
            if(Boolean.parseBoolean(System.getProperty("atf.original.requireVictory","true"))) {
                String last="original-romanian-h24-turn01";
                CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEARDOWN","Separate match-strength objective: human finishes ahead of every rival",last,Arrays.toString(totals),List.of(new AtfAssertion("Requested victory objective","human score exceeds every rival; a tie is not a strict win",Arrays.toString(totals),win)));
            }
            trace.finish(AtfReportStatus.PASS);
        }catch(Throwable failure){originalFailure=failure;trace.fail(failure);if(failure instanceof Exception e)throw e;throw new AssertionError(failure);}
        finally {
            if(!trace.phaseSnapshots().isEmpty())try {
                AndroidUiFlowReportPublisher.publish(out.resolve("original-romanian-hard-extent-report.html"),PACKAGE,selectedSerial(),androidApi,
                        "Original live game only. Public-only Hard decisions; independent privileged auditing. A lost game is not relabelled as victory. Every captured checkpoint uses the original PNG inline.",List.of(trace.manifest()),trace);
            }catch(Exception publication){if(originalFailure!=null)originalFailure.addSuppressed(publication);else throw publication;}
        }
    }
    private static OriginalWhistState observe(OriginalWhistProbe probe,AdbClient adb,Path out,String label,boolean resume)throws Exception {
        requireOriginalFocus(adb,out);return probe.observe(label,resume);
    }
    private static int selected(Path decision)throws Exception{return JSON.readTree(Files.readString(decision)).path("selectedValue").asInt(-1);}
    private static void requireChecks(List<AtfAssertion> checks){assertTrue(checks.stream().allMatch(AtfAssertion::passed),()->checks.stream().filter(c->!c.passed()).toList().toString());}
    private static void copyRetainedEvidence(Path prior,Path out)throws Exception{
        assertNotEquals(prior,out,"Continuation evidence must use a new folder");
        assertFalse(Files.exists(prior.resolve("original-hard-outcome.json")),"A completed original match is analyzed, not silently replayed");
        try(var files=Files.list(prior)){
            for(Path file:files.filter(Files::isRegularFile).toList()){
                String name=file.getFileName().toString();
                if(name.startsWith("original-")&&!name.endsWith(".html"))Files.copy(file,out.resolve(name));
            }
        }
        Path recovery=out.resolve("prior-failed-attempt");Files.createDirectories(recovery);
        for(String name:List.of("ui-flow-original-romanian-hard.properties","original-romanian-hard-extent-report.html")){
            Path file=prior.resolve(name);if(Files.exists(file))Files.copy(file,recovery.resolve(name));
        }
    }
    private static void copyObservation(Path out,String from,String to)throws Exception{
        for(String suffix:List.of(".png",".save2",".ab","-state.txt",".xml")){
            Path file=out.resolve(from+suffix),target=out.resolve(to+suffix);
            if(Files.exists(file)){assertFalse(Files.exists(target),"Never overwrite captured state during recovery: "+target);Files.copy(file,target);}
        }
    }
    private static void requireOriginalFocus(AdbClient adb,Path out)throws Exception {
        requireLease(out);var result=adb.run("shell","dumpsys","window","windows");result.requireSuccess();String windows=result.output();
        if(windows.lines().noneMatch(line->line.contains("mCurrentFocus")&&line.contains(PACKAGE)))throw new IllegalStateException("Original app lost foreground; stop without sending another game input");
    }
    private static void requireLease(Path out)throws Exception {
        String token=System.getProperty("atf.original.leaseId","");var lease=JSON.readTree(Files.readString(Path.of("C:/work/whist_back/android-ui-lease.json")));
        if(token.isBlank()||!lease.path("active").asBoolean()||!token.equals(lease.path("leaseId").asText())||!selectedSerial().equals(lease.path("serial").asText())||!out.startsWith(Path.of(lease.path("run").asText()).toAbsolutePath().normalize()))throw new IllegalStateException("Original live test requires its own active device lease bound to the selected ADB serial");
    }
    private static String selectedSerial(){return OriginalRomanianDecisionReceipt.selectedAdbSerial(System.getProperty("atf.android.serial"),System.getenv("ATF_ANDROID_SERIAL"));}
    private static void write(Path file,Object value)throws Exception{Files.writeString(file,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");}
}
