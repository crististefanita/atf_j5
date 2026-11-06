package atf.android.e2e;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import atf.android.e2e.support.AndroidUiFlowTrace;
import atf.reporting.contract.*;
import atf.reporting.ui.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Rechecks retained original observations offline and publishes detailed, reproducible rule assertions. */
class RecordedOriginalMatchAuditTest {
    @Test void retainedOriginalStatesProveEveryCardWinnerAndScore()throws Exception {
        assumeTrue(Boolean.getBoolean("atf.match.review"));
        Path out=Path.of(System.getProperty("atf.match.evidenceDir"));
        Properties replay=new Properties();try(var in=Files.newInputStream(out.resolve("original-match.properties"))){replay.load(in);}
        assertEquals("true",replay.getProperty("match.complete"));
        AndroidUiFlowTrace trace=new AndroidUiFlowTrace(out,"match-original-audit",
                "Contract Whist — complete live match, independent review of every captured card and score",
                getClass().getSimpleName(),"atf.android.e2e","com.qsoft.android.whist",
                List.of("original-app","recorded-state-audit","19-hands","436-moves","109-tricks"));
        int[] totals=new int[4];int moves=0,tricks=0;
        Properties coverage=new Properties();Map<String,Integer> counts=new TreeMap<>();
        for(String key:List.of("follow-suit","void-play-trump","void-discard","lead-trump","lead-non-trump",
                "trick-without-trump","trick-with-one-trump","trick-with-multiple-trumps","exact-zero-bid",
                "exact-positive-bid","under-bid-target","over-bid-target","void-discard-while-holding-trump",
                "trump-beats-led-suit","off-suit-higher-rank-does-not-win"))counts.put(key,0);
        try {
            CompleteOriginalMatchE2eTest.checkpoint(trace,out,"SETUP","Installed original app; four players; Oh Hell; Normal AI",
                    "original-match-setup","Review of retained live evidence, not a new device execution",List.of(
                            new AtfAssertion("Complete live acquisition exists","19 hands / 436 plays",
                                    replay.getProperty("match.handCount")+" hands / "+replay.getProperty("match.moves")+" plays",
                                    "19".equals(replay.getProperty("match.handCount"))&&"436".equals(replay.getProperty("match.moves")))));
            for(int h=0;h<19;h++) {
                String prefix=String.format("original-h%02d",h+1);
                OriginalWhistState deal=OriginalWhistState.read(Files.readAllBytes(out.resolve(prefix+"-bidding.save2")));
                OriginalMatchOracle oracle=new OriginalMatchOracle(deal);
                List<AtfAssertion> distribution=new ArrayList<>();
                for(int p=0;p<4;p++)distribution.add(new AtfAssertion("Seat "+p+" initial deal",
                        deal.handSize+" unique cards",oracle.initial.get(p).stream().map(OriginalWhistState::card).toList().toString(),true));
                CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEST BODY","Hand "+(h+1)+": complete distribution, dealer and turned trump card",
                        prefix+"-bidding",deal.summary(),distribution);
                OriginalWhistState last=null;
                for(int turn=0;turn<=deal.handSize;turn++) {
                    String label=prefix+String.format("-turn%02d",turn);
                    OriginalWhistState state=OriginalWhistState.read(Files.readAllBytes(out.resolve(label+".save2")));
                    String title;AtfUiActionRecord input;List<AtfAssertion> checks;
                    if(turn==0) {
                        title="Hand "+(h+1)+": submit bid "+state.bids[0]+" and verify the initial AI plays";
                        input=new AtfUiActionRecord(trace.nextActionSequence(),"Original bid button tap","Bid "+state.bids[0],
                                "enabled bid is submitted","accepted human bid="+state.bids[0]+"; corroborated by the retained live state",0,AtfReportStatus.PASS,AtfUiEvidenceSource.ACTION);
                        checks=new ArrayList<>(oracle.verify(state,totals));
                    } else {
                        int intended=oracle.chooseHumanCard(last),slot=last.hand(0).indexOf(intended);
                        int x=Math.round(last.x[0][slot]+Math.min(40,last.width[0][slot]/2));
                        int y=Math.round(last.y[0][slot]+Math.min(40,last.height[0][slot]/2));
                        List<Integer> removed=new ArrayList<>(last.hand(0));removed.removeAll(state.hand(0));
                        boolean accepted=removed.equals(List.of(intended));
                        title="Hand "+(h+1)+", human turn "+turn+": play "+OriginalWhistState.card(intended)+" and verify every following AI move";
                        input=new AtfUiActionRecord(trace.nextActionSequence(),"Original ADB card-slot tap",
                                OriginalWhistState.card(intended)+"; x="+x+"; y="+y,
                                "requested legal card is removed exactly once","accepted card="+removed.stream().map(OriginalWhistState::card).toList()
                                        +"; target reconstructed from the live tap policy and adjacent backups",0,
                                accepted?AtfReportStatus.PASS:AtfReportStatus.FAIL,AtfUiEvidenceSource.ACTION);
                        checks=new ArrayList<>(oracle.verify(state,totals));
                        OriginalMatchOracle.check(checks,"Physical tap accepted exactly the intended human card",OriginalWhistState.card(intended),
                                removed.stream().map(OriginalWhistState::card).toList().toString(),accepted);
                    }
                    CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEST BODY",title,label,state.summary(),checks,List.of(input));
                    last=state;
                }
                assertTrue(last.handOver);assertEquals(deal.handSize*4,oracle.plays.size());
                assertArrayEquals(CompleteWhistLabMatchE2eTest.numbers(replay.getProperty("hand."+h+".totals")),oracle.totals);
                String key="hand."+h+".";
                replay.setProperty(key+"won",CompleteOriginalMatchE2eTest.csv(Arrays.stream(last.won).boxed().toList()));
                replay.setProperty(key+"priorTotals",CompleteOriginalMatchE2eTest.csv(Arrays.stream(totals).boxed().toList()));
                replay.setProperty(key+"names",String.join("; ",deal.names));
                for(int seat=0;seat<4;seat++)replay.setProperty(key+"cardLabels."+seat,
                        oracle.initial.get(seat).stream().map(OriginalWhistState::card).toList().toString());
                countBranches(oracle,counts);
                totals=oracle.totals.clone();moves+=oracle.plays.size();tricks+=deal.handSize;
            }
            assertEquals(436,moves);assertEquals(109,tricks);
            boolean originalGameOver=OriginalWhistState.gameOverFromBackup(out.resolve("original-h19-turn10.ab"));
            assertTrue(originalGameOver,"The real app must itself mark the entire match complete");
            replay.setProperty("match.originalGameOver",Boolean.toString(originalGameOver));
            assertEquals(109,counts.get("lead-trump")+counts.get("lead-non-trump"));
            assertEquals(327,counts.get("follow-suit")+counts.get("void-play-trump")+counts.get("void-discard"));
            assertEquals(76,counts.get("exact-zero-bid")+counts.get("exact-positive-bid")+counts.get("under-bid-target")+counts.get("over-bid-target"));
            int bonus=10*(counts.get("exact-zero-bid")+counts.get("exact-positive-bid"));
            assertEquals(109+bonus,Arrays.stream(totals).sum());
            coverage.setProperty("score.total-trick-points","109");coverage.setProperty("score.total-bonus-points",Integer.toString(bonus));
            coverage.setProperty("score.total-points",Integer.toString(Arrays.stream(totals).sum()));
            try(var stream=Files.newOutputStream(out.resolve("original-match.properties"))){replay.store(stream,"Observed original inputs with independently audited outcomes");}
            counts.forEach((k,v)->coverage.setProperty(k,Integer.toString(v)));
            try(var stream=Files.newOutputStream(out.resolve("rule-coverage.properties"))){coverage.store(stream,"Observed rule branches from the complete original match");}
            CompleteOriginalMatchE2eTest.checkpoint(trace,out,"TEARDOWN","Retain the final original score and complete replay inputs",
                    "original-h19-turn10","Final cumulative totals="+Arrays.toString(totals),List.of(
                            new AtfAssertion("Every original card and trick accounted for","436 cards / 109 tricks",moves+" cards / "+tricks+" tricks",moves==436&&tricks==109)));
            trace.finish(AtfReportStatus.PASS);
        }catch(Throwable failure){trace.fail(failure);throw failure;}
    }
    private static void countBranches(OriginalMatchOracle oracle,Map<String,Integer> counts) {
        List<List<Integer>> hands=new ArrayList<>();for(var h:oracle.initial)hands.add(new ArrayList<>(h));
        int seat=(oracle.deal.dealer+1)%4,lead=-1;List<Integer> trick=new ArrayList<>(),seats=new ArrayList<>();
        for(int card:oracle.plays) {
            final int led=lead;
            if(lead>=0) {
                if(hands.get(seat).stream().anyMatch(c->c/13==led))inc(counts,"follow-suit");
                else {
                    inc(counts,card/13==oracle.deal.trump?"void-play-trump":"void-discard");
                    if(card/13!=oracle.deal.trump&&hands.get(seat).stream().anyMatch(c->c/13==oracle.deal.trump))
                        inc(counts,"void-discard-while-holding-trump");
                }
            }else {lead=card/13;inc(counts,lead==oracle.deal.trump?"lead-trump":"lead-non-trump");}
            hands.get(seat).remove(Integer.valueOf(card));trick.add(card);seats.add(seat);
            if(trick.size()==4) {
                long trumps=trick.stream().filter(c->c/13==oracle.deal.trump).count();
                inc(counts,trumps==0?"trick-without-trump":trumps==1?"trick-with-one-trump":"trick-with-multiple-trumps");
                int best=0;for(int i=1;i<4;i++)if(OriginalMatchOracle.strength(trick.get(i),lead,oracle.deal.trump)>
                        OriginalMatchOracle.strength(trick.get(best),lead,oracle.deal.trump))best=i;
                if(lead!=oracle.deal.trump&&trumps>0)inc(counts,"trump-beats-led-suit");
                if(trumps==0) {
                    final int trickLead=lead,winningRank=trick.get(best)%13;
                    if(trick.stream().anyMatch(c->c/13!=trickLead&&c%13>winningRank))inc(counts,"off-suit-higher-rank-does-not-win");
                }
                seat=seats.get(best);trick.clear();seats.clear();lead=-1;
            }else seat=(seat+1)%4;
        }
        for(int p=0;p<4;p++)inc(counts,oracle.won[p]==oracle.bids[p]?(oracle.bids[p]==0?"exact-zero-bid":"exact-positive-bid"):
                oracle.won[p]<oracle.bids[p]?"under-bid-target":"over-bid-target");
    }
    private static void inc(Map<String,Integer> counts,String key){counts.merge(key,1,Integer::sum);}
}
