package atf.android.e2e;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import atf.android.AdbClient;
import atf.android.e2e.support.AndroidUiFlowTrace;
import atf.android.e2e.support.AndroidUiFlowReportPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import atf.reporting.contract.*;
import atf.reporting.ui.model.AtfUiActionRecord;
import atf.reporting.ui.model.AtfUiEvidenceSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Full original 19-hand Oh Hell match; observes the unmodified Play-installed package. */
@AndroidE2eTest
public class CompleteOriginalMatchE2eTest {
    private static final String PACKAGE = "com.qsoft.android.whist";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void everyMoveTrickAndScoreInACompleteOriginalMatch() throws Exception {
        assumeTrue(Boolean.getBoolean("atf.android.fullMatch") && Boolean.getBoolean("atf.android.exclusiveReady"),
                "Explicit full-match opt-in and exclusive device lease required");
        Path out=Path.of(System.getProperty("atf.match.evidenceDir")).toAbsolutePath();
        Files.createDirectories(out);
        AdbClient adb=AdbClient.fromSystemProperties();
        AndroidUiDriver ui=new AndroidUiDriver(adb,out);
        OriginalWhistProbe probe=new OriginalWhistProbe(adb,ui,out);
        int retainedHands=0;
        if(Boolean.getBoolean("atf.match.continue")) {
            for(int h=0;h<19;h++) {
                int size=h<10?10-h:h-8;
                Path end=out.resolve(String.format("original-h%02d-turn%02d.save2",h+1,size));
                if(Files.exists(end)&&OriginalWhistState.read(Files.readAllBytes(end)).handOver)retainedHands++;
                else break;
            }
            assertTrue(retainedHands>0,"Continuation requires at least one fully captured hand");
            Path oldTrace=out.resolve("ui-flow-match-original.properties");
            if(Files.exists(oldTrace))Files.copy(oldTrace,out.resolve("ui-flow-match-original-diagnostic-"+System.currentTimeMillis()+".properties"));
        }
        AndroidUiFlowTrace trace=new AndroidUiFlowTrace(out,"match-original",
                "Contract Whist — complete original Oh Hell match: every move, trick and score",
                getClass().getSimpleName(),"atf.android.e2e",PACKAGE,
                List.of("original-app","full-match","rules","score","backup-observation"));
        Properties replay=new Properties();replay.setProperty("match.complete","false");
        int[] totals=new int[4];int previousDealer=-1,totalMoves=0,totalTricks=0;
        OriginalWhistState continuationState=null;
        String androidApi="not observed",installedPackage="not observed";
        Throwable flowFailure=null;
        try {
            var api=adb.run("shell","getprop","ro.build.version.sdk");api.requireSuccess();androidApi=api.output().trim();
            var packageState=adb.run("shell","dumpsys","package",PACKAGE);packageState.requireSuccess();
            installedPackage=packageState.output().lines().filter(line->line.contains("versionCode=")||line.contains("versionName=")).findFirst().orElse("version metadata missing").trim();
            assertTrue(installedPackage.contains("versionCode=145"),"The backup parser is qualified for Contract Whist versionCode 145 only");
            requireLease(out);
            String menu=ui.restartToMainMenu(PACKAGE);
            if(retainedHands==0&&ui.hasText(menu,"Resume Game")&&ui.isEnabled(menu,"Resume Game"))
                throw new IllegalStateException("An unrelated saved original-app game exists; preserve it and supply its retained evidence with -ContinueFrom.");
            if(retainedHands>0) {
                requireLease(out);
                ui.tapText("Resume Game");ui.awaitAbsent("Resume Game",Duration.ofSeconds(20));
                continuationState=probe.observe("original-continuation-live-"+System.currentTimeMillis());
                checkpoint(trace,out,"SETUP","Continue the same original match at a retained hand boundary","original-match-setup",
                        "fully captured hands="+retainedHands+"; live="+continuationState.summary(),List.of(new AtfAssertion("Saved match is observable","same retained dealer / history checked below","live state captured",true)));
            } else {
            requireLease(out);
            ui.tapText("New Game");
            ui.awaitText("START",Duration.ofSeconds(15));
            requireLease(out);ui.selectSpinnerOption(0,"4");ui.awaitSpinnerSelection("4",Duration.ofSeconds(15));
            requireLease(out);ui.selectSpinnerOption(1,"Oh Hell");ui.awaitSpinnerSelection("Oh Hell",Duration.ofSeconds(15));
            requireLease(out);ui.selectSpinnerOption(2,"Normal");ui.awaitSpinnerSelection("Normal",Duration.ofSeconds(15));
            ui.capture("original-match-setup");
            checkpoint(trace,out,"SETUP","Select the real four-player Oh Hell ruleset","original-match-setup",
                    "4 players; Oh Hell; Normal AI",List.of(new AtfAssertion("Explicit setup", "4 / Oh Hell / Normal",
                            ui.texts(ui.dump()).toString(),true)));
            requireLease(out);ui.tapText("START");ui.awaitText("Bid 0",Duration.ofSeconds(20));
            }
            for(int h=0;h<19;h++) {
                String prefix=String.format("original-h%02d",h+1);
                boolean retained=h<retainedHands;
                boolean partial=!retained&&continuationState!=null&&h==retainedHands&&Files.exists(out.resolve(prefix+"-bidding.save2"));
                OriginalWhistState deal=retained||partial?OriginalWhistState.read(Files.readAllBytes(out.resolve(prefix+"-bidding.save2")))
                        :probe.observe(prefix+"-bidding");
                int expectedSize=h<10?10-h:h-8;
                List<AtfAssertion> setup=new ArrayList<>();
                OriginalMatchOracle.check(setup,"Observed hand size follows 10→1→10",Integer.toString(expectedSize),
                        Integer.toString(deal.handSize),deal.handSize==expectedSize);
                OriginalMatchOracle.check(setup,"Hand index advances exactly once",Integer.toString(h),
                        Integer.toString(deal.handIndex),deal.handIndex==h);
                OriginalMatchOracle.check(setup,"Dealer rotates one seat clockwise",previousDealer<0?"initial dealer recorded":Integer.toString((previousDealer+1)%4),
                        Integer.toString(deal.dealer),previousDealer<0||deal.dealer==(previousDealer+1)%4);
                OriginalMatchOracle.check(setup,"Live game is Oh Hell / standard playing deck / four seats","6 / 1 / 4",
                        deal.mode+" / "+deal.deck+" / "+deal.players,deal.mode==6&&deal.deck==1&&deal.players==4);
                String xml=retained||partial?Files.readString(out.resolve(prefix+"-bidding.xml")):ui.dump();int prior=Arrays.stream(deal.bids).filter(b->b>=0).sum();
                boolean last=(deal.dealer==0);int forbidden=deal.handSize-prior;
                List<String> disabled=ui.disabledButtonTexts(xml).stream().filter(t->t.matches("Bid \\d+")).toList();
                List<String> expectedDisabled=last&&forbidden>=0&&forbidden<=deal.handSize?List.of("Bid "+forbidden):List.of();
                OriginalMatchOracle.check(setup,"Disabled UI bid matches independent last-bid restriction",expectedDisabled.toString(),
                        disabled.toString(),disabled.equals(expectedDisabled));
                checkpoint(trace,out,"TEST BODY","Hand "+(h+1)+": deal, dealer, trump and bid restriction",prefix+"-bidding",deal.summary(),setup);
                OriginalMatchOracle oracle=new OriginalMatchOracle(deal);
                OriginalWhistState state=null;
                int taps=0;
                if(retained) {
                    for(int t=0;t<=deal.handSize;t++) {
                        String label=prefix+String.format("-turn%02d",t);
                        state=OriginalWhistState.read(Files.readAllBytes(out.resolve(label+".save2")));
                        checkpoint(trace,out,"TEST BODY","Hand "+(h+1)+", retained checkpoint "+t+": revalidate every captured move",
                                label,state.summary(),oracle.verify(state,totals));
                    }
                    taps=deal.handSize;
                    System.out.println("MATCH retained hand="+(h+1)+"; score="+Arrays.toString(oracle.totals));
                } else {
                if(partial) {
                    for(int t=0;t<=deal.handSize;t++) {
                        String label=prefix+String.format("-turn%02d",t);
                        if(!Files.exists(out.resolve(label+".save2")))break;
                        state=OriginalWhistState.read(Files.readAllBytes(out.resolve(label+".save2")));
                        checkpoint(trace,out,"TEST BODY","Hand "+(h+1)+", retained partial checkpoint "+t,
                                label,state.summary(),oracle.verify(state,totals));
                        taps=t;
                    }
                    assertNotNull(state,"Partial continuation requires a captured post-bid state");
                    int actualHumanPlays=deal.handSize-continuationState.remaining[0];
                    assertTrue(actualHumanPlays>=taps&&actualHumanPlays<=taps+1,"Unaccounted human inputs during continuation");
                    String label=prefix+String.format("-turn%02d",actualHumanPlays);
                    String liveLabel=findLatestContinuationLabel(out);
                    if(actualHumanPlays>taps)copyContinuationEvidence(out,liveLabel,label);
                    else label=liveLabel;
                    state=continuationState;taps=actualHumanPlays;
                    checkpoint(trace,out,"TEST BODY","Hand "+(h+1)+": reconcile live state after environment interruption",
                            label,state.summary(),oracle.verify(state,totals));
                } else {
                int humanBid=h%3==0?0:Math.min(1,deal.handSize);
                if(!ui.isEnabled(xml,"Bid "+humanBid))humanBid=humanBid==0?1:0;
                requireLease(out);ui.tapText("Bid "+humanBid);
                Thread.sleep(5500);
                state=probe.observe(prefix+"-turn00");
                checkpoint(trace,out,"TEST BODY","Hand "+(h+1)+": submit bid "+humanBid+" and observe initial AI plays",
                        prefix+"-turn00",state.summary(),oracle.verify(state,totals));
                }
                while(!state.handOver) {
                    assertTrue(state.turn==0,"Expected stable human turn: "+state.summary());
                    int chosen=oracle.chooseHumanCard(state),slot=state.hand(0).indexOf(chosen);
                    int x=Math.round(state.x[0][slot]+Math.min(40,state.width[0][slot]/2));
                    int y=Math.round(state.y[0][slot]+Math.min(40,state.height[0][slot]/2));
                    requireLease(out);
                    adb.run("shell","input","tap",Integer.toString(x),Integer.toString(y)).requireSuccess();
                    Thread.sleep(5500);
                    String label=prefix+String.format("-turn%02d",++taps);
                    // The final score remains observable in the backup even if Resume is disabled after match completion.
                    state=probe.observe(label,!(h==18&&taps==deal.handSize));
                    List<AtfAssertion> checks=oracle.verify(state,totals);
                    OriginalMatchOracle.check(checks,"Selected human card was accepted exactly once",OriginalWhistState.card(chosen),
                            "human remaining="+state.remaining[0],state.remaining[0]==deal.handSize-taps);
                    checkpoint(trace,out,"TEST BODY","Hand "+(h+1)+", human turn "+taps+": "+OriginalWhistState.card(chosen),
                            label,state.summary(),checks);
                    System.out.println("MATCH hand="+(h+1)+"/19; human="+taps+"/"+deal.handSize+"; "+state.summary());
                    assertTrue(taps<=deal.handSize,"A human card input did not advance the observed state");
                }
                }
                assertEquals(deal.handSize,taps,"Each human card was accepted once");
                totals=oracle.totals.clone();previousDealer=deal.dealer;
                totalMoves+=oracle.plays.size();totalTricks+=deal.handSize;
                String k="hand."+h+".";
                replay.setProperty(k+"size",Integer.toString(deal.handSize));
                replay.setProperty(k+"dealer",Integer.toString(deal.dealer));replay.setProperty(k+"trump",Integer.toString(deal.trump));
                replay.setProperty(k+"trumpCard",Integer.toString(deal.trumpCard));
                for(int p=0;p<4;p++)replay.setProperty(k+"cards."+p,csv(oracle.initial.get(p)));
                replay.setProperty(k+"bids",csv(Arrays.stream(state.bids).boxed().toList()));
                replay.setProperty(k+"plays",csv(oracle.plays));replay.setProperty(k+"totals",csv(Arrays.stream(totals).boxed().toList()));
                replay.setProperty("match.handCount",Integer.toString(h+1));
                try(var stream=Files.newOutputStream(out.resolve("original-match.properties"))){replay.store(stream,"Observed original game replay; no copied implementation/assets");}
                if(h<18&&advanceAfterHand(retained,h,retainedHands,continuationState)){
                    requireLease(out);ui.tapText("Next Hand");ui.awaitText("Bid 0",Duration.ofSeconds(20));
                    if(retained)continuationState=null;
                }
            }
            assertEquals(436,totalMoves);assertEquals(109,totalTricks);
            replay.setProperty("match.complete","true");replay.setProperty("match.moves",Integer.toString(totalMoves));
            replay.setProperty("match.tricks",Integer.toString(totalTricks));
            try(var stream=Files.newOutputStream(out.resolve("original-match.properties"))){replay.store(stream,"Complete observed original match");}
            trace.finish(AtfReportStatus.PASS);
        }catch(Throwable failure){flowFailure=failure;trace.fail(failure);throw failure;}
        finally {
            try {
                AndroidUiFlowReportPublisher.publish(out.resolve("original-match-extent-report.html"),PACKAGE+"; "+installedPackage,
                        selectedSerial(),androidApi,"Complete original Contract Whist match; report describes only observed original-app state.",
                        List.of(trace.manifest()),trace);
            } catch(Exception publicationFailure) {
                if(flowFailure!=null)flowFailure.addSuppressed(publicationFailure);else throw publicationFailure;
            }
        }
    }
    private static String selectedSerial() {
        String serial=System.getProperty("atf.android.serial",System.getenv("ATF_ANDROID_SERIAL"));
        return serial==null||serial.isBlank()?"unknown":serial.trim();
    }
    private static void requireLease(Path evidence) throws Exception {
        String token=System.getProperty("atf.original.leaseId","");
        Path leaseFile=Path.of(System.getProperty("atf.android.leasePath","C:/work/whist_back/android-ui-lease.json"));
        if(token.isBlank()||!Files.isRegularFile(leaseFile))throw new IllegalStateException("Original live test requires its active device lease.");
        JsonNode lease=JSON.readTree(Files.readString(leaseFile));
        Path run=Path.of(lease.path("run").asText()).toAbsolutePath().normalize();
        if(!lease.path("active").asBoolean()||!token.equals(lease.path("leaseId").asText())
                ||!selectedSerial().equals(lease.path("serial").asText())||!evidence.toAbsolutePath().normalize().startsWith(run))
            throw new IllegalStateException("Original live test lost its device lease; stop before sending another input.");
    }
    static boolean advanceAfterHand(boolean retained,int handIndex,int retainedHands,OriginalWhistState live) {
        // A pause may accept the last card of a partially captured next hand. Its score is not the retained boundary.
        return !retained || handIndex==retainedHands-1 && live!=null && live.handOver && live.handIndex==retainedHands;
    }
    static String csv(List<Integer> values){return String.join(",",values.stream().map(String::valueOf).toList());}
    private static String findLatestContinuationLabel(Path out)throws Exception {
        try(var files=Files.list(out)) {
            String file=files.map(p->p.getFileName().toString()).filter(n->n.startsWith("original-continuation-live-")&&n.endsWith(".save2"))
                    .sorted().reduce((a,b)->b).orElseThrow();
            return file.substring(0,file.length()-6);
        }
    }
    private static void copyContinuationEvidence(Path out,String from,String to)throws Exception {
        for(String suffix:List.of(".png",".save2",".ab","-state.txt")) {
            Path source=out.resolve(from+suffix),target=out.resolve(to+suffix);
            if(Files.exists(target))Files.copy(target,out.resolve(to+"-before-continuation-"+System.currentTimeMillis()+suffix));
            Files.copy(source,target,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    static void checkpoint(AndroidUiFlowTrace trace,Path out,String phase,String title,String label,
            String actual,List<AtfAssertion> assertions)throws Exception {
        checkpoint(trace,out,phase,title,label,actual,assertions,List.of());
    }
    static void checkpoint(AndroidUiFlowTrace trace,Path out,String phase,String title,String label,
            String actual,List<AtfAssertion> assertions,List<AtfUiActionRecord> inputActions)throws Exception {
        boolean pass=assertions.stream().allMatch(AtfAssertion::passed);
        AtfReportStatus status=pass?AtfReportStatus.PASS:AtfReportStatus.FAIL;
        List<Path> artifacts=new ArrayList<>();
        for(String suffix:List.of(".xml",".save2","-state.txt","-lab-state.properties","-lab-state.json"))if(Files.exists(out.resolve(label+suffix)))artifacts.add(out.resolve(label+suffix));
        AtfGherkinKeyword keyword=phase.equals("SETUP")?AtfGherkinKeyword.GIVEN:
                title.contains("human turn")||title.contains("submit bid")?AtfGherkinKeyword.WHEN:AtfGherkinKeyword.THEN;
        List<AtfUiActionRecord> actions=new ArrayList<>(inputActions);
        actions.add(new AtfUiActionRecord(trace.nextActionSequence(),"Compare captured state with independent rules",
                "Android game","every move, winner and score agrees with the rules",actual,0,status,AtfUiEvidenceSource.OBSERVE));
        trace.checkpoint(phase,keyword.text(),title,"Android game",out.resolve(label+".png"),actions,assertions,status,artifacts);
        assertTrue(pass,()->"Rule mismatch: "+assertions.stream().filter(a->!a.passed()).toList());
    }
}
