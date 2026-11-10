package ro.rentzlab.core;

import ro.rentzlab.testing.RentzComponentLayer;

import ro.rentzlab.ai.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ro.rentzlab.core.AiFixtures.c;
import static ro.rentzlab.core.AiFixtures.cards;

@RentzComponentLayer
class RentzMatchStrategyTest {
    @Test void matchWinUtilitySharesTiesAndNeverConfusesMarginWithVictory(){
        assertEquals(0.5,MatchUtility.value(List.of(7,7,0),0,PolicySettings.Objective.MATCH_WIN_SHARE));
        assertEquals(0,MatchUtility.value(List.of(7,8,0),0,PolicySettings.Objective.MATCH_WIN_SHARE));
        assertEquals(1,MatchUtility.value(List.of(8,7,0),0,PolicySettings.Objective.MATCH_WIN_SHARE));
    }
    @Test void ownFinalPointsRemainASeparateDeclaredObjective(){
        assertEquals(7,MatchUtility.value(List.of(7,8,0),0,PolicySettings.Objective.MATCH_POINTS));
        assertEquals(3,MatchUtility.value(List.of(7,8,0),0,PolicySettings.Objective.HAND_MARGIN));
    }
    @Test void aRareJackpotCannotMasqueradeAsTheHigherWinChance(){
        var goal=PolicySettings.Objective.MATCH_WIN_SHARE;
        double jackpot=(MatchUtility.value(List.of(1000,0,0),0,goal)+MatchUtility.value(List.of(-1,0,0),0,goal))/2;
        double reliable=(MatchUtility.value(List.of(1,0,0),0,goal)+MatchUtility.value(List.of(1,0,0),0,goal))/2;
        assertTrue(reliable>jackpot,"Two certain narrow wins have more win credit than one jackpot and one loss");
    }
    @Test void declaredMatchObjectiveMustActuallyContinueBeyondTheCurrentHand(){
        var zero=new ScoreProfile(0,0,0,0,0,0,List.of(0,0,0,0,0,0));
        var game=new RentzMatch(3,31,zero);game.reveal(0);
        var settings=new PolicySettings(1,0,3,0.3,2,PolicySettings.Objective.MATCH_WIN_SHARE);
        var decision=new SearchPolicy(zero,settings).choose(game.observe(0));
        assertTrue(decision.method().contains("FULL_MATCH"));
        assertTrue(decision.estimates().stream().allMatch(e->Math.abs(e.meanUtility()-1.0/3)<1e-9),"Every zero-score continuation ends in a three-way tie");
    }
    static List<RentzMatch.HandResult> ledger(int winner){
        var result=new ArrayList<RentzMatch.HandResult>();int[] totals=new int[3];
        for(int n=0;n<23;n++){
            Contract contract=Contract.values()[n/3];var points=new ArrayList<>(List.of(0,0,0));
            if(contract==Contract.RENTZ){points.set(winner,300);totals[winner]+=300;}
            result.add(new RentzMatch.HandResult(n+1,n%3,contract,false,points,List.of(totals[0],totals[1],totals[2])));
        }return result;
    }
    static ScoreProfile sequenceOnly(){return new ScoreProfile(0,0,0,0,0,0,List.of(300,0,0,0,0,0));}
    static OpeningDeal opening(){var deck=Card.deck(3);return new OpeningDeal(List.of(deck.subList(0,8),deck.subList(8,16),deck.subList(16,24)));}
    @Test void identicalCurrentCardsUsePublicTotalsToDistinguishALockedWinFromALockedLoss(){
        var profile=sequenceOnly();var leader=RentzMatch.fromPublicProgress(opening(),81,profile,2,ledger(2));var trailer=RentzMatch.fromPublicProgress(opening(),81,profile,2,ledger(0));leader.reveal(2);trailer.reveal(2);
        assertEquals(leader.observe(2).hand(),trailer.observe(2).hand());assertEquals(KnowledgeSampler.fingerprint(leader.observe(2)),KnowledgeSampler.fingerprint(trailer.observe(2)));
        var policy=new SearchPolicy(profile,new PolicySettings(1,0,3,0.3,2,PolicySettings.Objective.MATCH_WIN_SHARE));
        assertEquals(1,policy.choose(leader.observe(2)).estimates().get(0).meanUtility());
        assertEquals(0,policy.choose(trailer.observe(2)).estimates().get(0).meanUtility());
    }

    @Test void lastHandPolicyUsesThePublicStandingsAtTheTerminalBoundary() {
        var settings = new PolicySettings(2, .05, 3, .3, 2, PolicySettings.Objective.MATCH_WIN_LAST_HAND);
        var policy = new SearchPolicy(sequenceOnly(), settings);
        var early = new RentzMatch(3, 19, sequenceOnly());
        early.reveal(0);
        assertEquals("COMMON_FEASIBLE_WORLDS_ROLLOUT", policy.choose(early.observe(0)).method());
        var penultimate = RentzMatch.fromPublicProgress(opening(), 81, sequenceOnly(), 1, ledger(2).subList(0, 22));
        penultimate.reveal(1);
        assertEquals("COMMON_FEASIBLE_WORLDS_ROLLOUT", policy.choose(penultimate.observe(1)).method());

        var leader = RentzMatch.fromPublicProgress(opening(), 81, sequenceOnly(), 2, ledger(2));
        var trailer = RentzMatch.fromPublicProgress(opening(), 81, sequenceOnly(), 2, ledger(0));
        leader.reveal(2);
        trailer.reveal(2);
        var ahead = policy.choose(leader.observe(2));
        var behind = policy.choose(trailer.observe(2));
        assertEquals("COMMON_FEASIBLE_WORLDS_FULL_MATCH_MATCH_WIN_SHARE", ahead.method());
        assertEquals(1.0, ahead.estimates().get(0).meanUtility());
        assertEquals(0.0, behind.estimates().get(0).meanUtility());
    }

    @Test void finalWhistCanGiveBothTricksToTheTrailingRivalToSecureFirstPlace() {
        var scoring = whistOnly();
        var view = finalWhist(scoring).observe(0);
        var reference = new SearchPolicy(scoring, endgame(PolicySettings.Objective.HAND_MARGIN, 65536)).card(view);
        var proven = new SearchPolicy(scoring, endgame(PolicySettings.Objective.MATCH_WIN_PROVEN, 65536)).card(view);
        assertEquals(c("H9"), reference.selected());
        assertEquals(c("SJ"), proven.selected());
        assertEquals("PUBLIC_PROVEN_MATCH_WIN", proven.method());
        assertTrue(proven.work().transitions() > reference.work().transitions());
        assertTrue(proven.work().transitions() <= proven.work().transitionLimit());

        for (Card spade : cards("SQ SK")) {
            for (Card discard : cards("DA HA")) {
                var game = finalWhist(scoring);
                game.play(0, proven.selected());
                game.play(1, spade);
                game.play(2, discard);
                finishForcedTrick(game);
                assertEquals(List.of(90, 70, 80), game.observe(0).totals());
            }
        }
        var loss = finalWhist(scoring);
        loss.play(0, reference.selected());
        loss.play(1, c("SQ"));
        loss.play(2, c("HA"));
        finishForcedTrick(loss);
        assertEquals(List.of(90, 50, 100), loss.observe(0).totals());
    }

    @Test void aBudgetExhaustedProofLeavesTheReferenceDecisionIntact() {
        var scoring = whistOnly();
        var view = finalWhist(scoring).observe(0);
        var reference = new SearchPolicy(scoring, endgame(PolicySettings.Objective.HAND_MARGIN, 65536)).card(view);
        int limit = reference.work().transitions() + 1;
        var bounded = new SearchPolicy(scoring, endgame(PolicySettings.Objective.MATCH_WIN_PROVEN, limit)).card(view);
        assertEquals(reference.selected(), bounded.selected());
        assertEquals(reference.method(), bounded.method());
        assertEquals(limit, bounded.work().transitions());
    }

    private static ScoreProfile whistOnly() {
        return new ScoreProfile(0, 0, 0, 0, 10, 0, List.of(0, 0, 0, 0, 0, 0));
    }

    private static PolicySettings endgame(PolicySettings.Objective objective, int limit) {
        return new PolicySettings(8, .05, 3, .3, 2, objective, PolicySettings.Tactic.PUBLIC_VOID_AWARE, limit);
    }

    private static RentzMatch finalWhist(ScoreProfile scoring) {
        var contracts = new ArrayList<>(EnumSet.allOf(Contract.class));
        contracts.remove(Contract.WHIST);
        contracts.add(Contract.WHIST);
        var history = new ArrayList<RentzMatch.HandResult>();
        for (int hand = 0; hand < 21; hand++) {
            history.add(new RentzMatch.HandResult(hand + 1, hand % 3, contracts.get(hand / 3), false,
                    List.of(0, 0, 0), List.of(0, 0, 0)));
        }
        history.add(new RentzMatch.HandResult(22, 0, Contract.WHIST, false, List.of(30, 20, 30), List.of(30, 20, 30)));
        history.add(new RentzMatch.HandResult(23, 1, Contract.WHIST, false, List.of(20, 30, 30), List.of(50, 50, 60)));
        var opening = new OpeningDeal(List.of(cards("CA SA D9 CK H9 SJ HQ D10"),
                cards("S10 S9 CJ HK HJ SQ C9 SK"), cards("DQ CQ DK C10 DA H10 HA DJ")));
        var game = RentzMatch.fromPublicProgress(opening, 281, scoring, 2, history);
        game.choose(2, Contract.WHIST, false);
        for (Card card : cards("DQ D9 S10 DJ D10 CJ C10 CA C9 HQ HJ H10 CK HK CQ SA S9 DK")) {
            game.play(game.observe(0).turn(), card);
        }
        return game;
    }

    private static void finishForcedTrick(RentzMatch game) {
        while (game.observe(0).phase() == RentzMatch.Phase.PLAY) {
            int seat = game.observe(0).turn();
            var legal = game.observe(seat).legal();
            assertEquals(1, legal.size());
            game.play(seat, legal.get(0));
        }
    }
    @Test void publicProgressPreservesRemainingChoicesAndIsImmutable(){
        var original=ledger(2);var game=RentzMatch.fromPublicProgress(opening(),9,sequenceOnly(),2,original);
        assertEquals(23,game.observe(2).completedHands().size());assertEquals(Set.of(Contract.RENTZ),game.observe(2).available());
        assertTrue(game.observe(0).available().isEmpty());assertThrows(UnsupportedOperationException.class,()->game.observe(2).completedHands().clear());
        assertThrows(UnsupportedOperationException.class,()->original.get(0).totals().set(0,9));
        game.reveal(2);game.choose(2,Contract.RENTZ,false);var finalTotals=FullMatchRollout.complete(game,sequenceOnly(),PolicySettings.defaults());
        assertEquals(24,game.history().size());assertEquals(RentzMatch.Phase.MATCH_END,game.observe(0).phase());assertEquals(900,finalTotals.stream().mapToInt(Integer::intValue).sum());
    }
    @Test void invalidPublicLedgersCannotInjectScoresChoicesOrExtraHands(){
        var bad=new ArrayList<>(ledger(2));var first=bad.get(0);bad.set(0,new RentzMatch.HandResult(1,0,first.contract(),false,first.points(),List.of(1,0,0)));
        assertThrows(IllegalArgumentException.class,()->RentzMatch.fromPublicProgress(opening(),1,sequenceOnly(),2,bad));
        var repeated=new ArrayList<>(ledger(2));var fourth=repeated.get(3);repeated.set(3,new RentzMatch.HandResult(4,0,Contract.values()[0],false,fourth.points(),fourth.totals()));
        assertThrows(IllegalArgumentException.class,()->RentzMatch.fromPublicProgress(opening(),1,sequenceOnly(),2,repeated));
        assertThrows(IllegalArgumentException.class,()->RentzMatch.fromPublicProgress(opening(),1,sequenceOnly(),0,ledger(2)));
    }
    @Test void nonDefaultObjectivesRefuseMissingPublicHistoryAndInvalidValues(){
        var game=RentzMatch.fromPublicProgress(opening(),1,sequenceOnly(),2,ledger(2));game.reveal(2);var v=game.observe(2);
        var missing=new RentzMatch.Observation(v.seat(),v.players(),v.turn(),v.chooser(),v.handNumber(),v.phase(),v.contract(),v.blind(),v.revealed(),v.hand(),v.legal(),v.handSizes(),v.dealPoints(),v.totals(),v.finishOrder(),v.trick(),v.seen(),v.events(),v.board(),v.available());
        var policy=new SearchPolicy(sequenceOnly(),new PolicySettings(1,0,3,0.3,2,PolicySettings.Objective.MATCH_WIN_SHARE));
        assertThrows(IllegalArgumentException.class,()->policy.choose(missing));assertThrows(IllegalArgumentException.class,()->MatchUtility.value(List.of(1,2),0,PolicySettings.Objective.MATCH_POINTS));
        assertEquals(PolicySettings.Objective.MATCH_WIN_PROVEN, PolicySettings.defaults().objective());
        assertEquals(PolicySettings.Objective.HAND_MARGIN, PolicySettings.handMarginReference().objective());
    }
    @Test void fullMatchGoalsCannotReadRealOpposingCardsOrTheActualFutureSeed(){
        var first=opening();var swapped=new ArrayList<>(first.hands());Collections.swap(swapped,0,1);
        var a=RentzMatch.fromPublicProgress(first,11,sequenceOnly(),2,ledger(2));
        var b=RentzMatch.fromPublicProgress(new OpeningDeal(swapped),987,sequenceOnly(),2,ledger(2));
        a.reveal(2);b.reveal(2);var before=a.observe(2);assertEquals(before,b.observe(2));
        for(var objective:List.of(PolicySettings.Objective.MATCH_POINTS,PolicySettings.Objective.MATCH_WIN_SHARE,
                PolicySettings.Objective.MATCH_WIN_LAST_HAND,PolicySettings.Objective.MATCH_WIN_PROVEN)){
            var policy=new SearchPolicy(sequenceOnly(),new PolicySettings(2,0,3,.3,2,objective));
            assertEquals(policy.choose(before),policy.choose(b.observe(2)));
            a.choose(2,Contract.RENTZ,false);b.choose(2,Contract.RENTZ,false);
            assertEquals(policy.card(a.observe(2)),policy.card(b.observe(2)));
            a=RentzMatch.fromPublicProgress(first,11,sequenceOnly(),2,ledger(2));b=RentzMatch.fromPublicProgress(new OpeningDeal(swapped),987,sequenceOnly(),2,ledger(2));a.reveal(2);b.reveal(2);
        }
        assertEquals(before,a.observe(2));
    }
}
