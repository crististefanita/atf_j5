package ro.rentzlab.core;

import ro.rentzlab.testing.RentzComponentLayer;

import ro.rentzlab.ai.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ro.rentzlab.core.AiFixtures.*;

@RentzComponentLayer
class RentzHardRefinementTest {
    static PolicySettings aware(int worlds,int limit){return new PolicySettings(worlds,.05,3,.3,2,PolicySettings.Objective.HAND_MARGIN,PolicySettings.Tactic.PUBLIC_TARGET_AWARE,limit);}
    static RentzMatch queenPosition(){
        var game=game(deal(Map.of(0,cards("C7"),1,cards("C8"),2,cards("C9"),3,cards("C10 CQ D7 D8 D9 H7 H8 S7"))),Contract.QUEENS);
        game.play(0,c("C7"));game.play(1,c("C8"));game.play(2,c("C9"));return game;
    }
    @Test void unavoidableLastTrickDoesNotCaptureOwnQueenWhenAnotherWinnerCostsNothing(){
        var game=game(deal(Map.of(0,cards("C7"),1,cards("C8"),2,cards("C9"),3,cards("C10 CQ D7 D8 D9 H7 H8 S7"))),Contract.QUEENS);
        game.play(0,c("C7"));game.play(1,c("C8"));game.play(2,c("C9"));var view=game.observe(3);
        assertEquals(c("CQ"),TacticalPolicy.card(view,ScoreProfile.laboratory(),PolicySettings.handMarginReference()),"Frozen legacy contrast");
        var selected=TacticalPolicy.card(view,ScoreProfile.laboratory(),aware(8,65536));assertEquals(c("C10"),selected);
        game.play(3,selected);assertEquals(0,game.observe(3).dealPoints().get(3));assertEquals(3,game.observe(3).turn());
    }
    @Test void aSafeLosingQueenIsDisposedBeforeAHigherNonPenaltyCard(){
        var game=game(deal(Map.of(0,cards("CA"),1,cards("CQ CK D7 D8 D9 H7 H8 S7"))),Contract.QUEENS);game.play(0,c("CA"));
        assertEquals(c("CK"),TacticalPolicy.card(game.observe(1),ScoreProfile.laboratory(),PolicySettings.handMarginReference()));
        assertEquals(c("CQ"),TacticalPolicy.card(game.observe(1),ScoreProfile.laboratory(),aware(8,65536)));
    }
    static RentzMatch publicTenExit(){
        var game=game(deal(Map.of(0,cards("CK CJ"),1,cards("CQ C9"),2,cards("C7 D7"),3,cards("C8 C10 CA"))),Contract.TEN_CLUBS);
        for(int seat=0;seat<4;seat++)game.play(seat,c(new String[]{"CK","CQ","C7","C8"}[seat]));
        game.play(0,c("CJ"));game.play(1,c("C9"));game.play(2,c("D7"));return game;
    }
    @Test void publicTenMasterUsesTheWinningExitAndThenCollectsTheTarget(){
        var game=publicTenExit();var view=game.observe(3);var settings=aware(8,65536);
        assertEquals(Set.of(c("C10"),c("CA")),new HashSet<>(view.legal()));
        assertEquals(c("C10"),TacticalPolicy.card(view,ScoreProfile.laboratory(),PolicySettings.handMarginReference()));
        var proof=new SearchPolicy(ScoreProfile.laboratory(),settings).card(view);assertEquals(c("CA"),proof.selected());
        assertEquals(0,proof.worlds());assertEquals("PUBLIC_TEN_MASTER_CAPTURE",proof.method());assertTrue(proof.estimates().isEmpty());
        Card exit=TacticalPolicy.card(view,ScoreProfile.laboratory(),settings);assertEquals(c("CA"),exit);game.play(3,exit);
        assertEquals(3,game.observe(3).turn());assertEquals(c("C10"),TacticalPolicy.card(game.observe(3),ScoreProfile.laboratory(),settings));
        game.play(3,c("C10"));for(int seat=0;seat<3;seat++)game.play(seat,LabPolicy.card(game.observe(seat)));
        assertEquals(RentzMatch.Phase.DEAL_END,game.observe(3).phase());assertEquals(100,game.history().get(0).points().get(3));
    }
    @Test void tinyBudgetCannotPublishAnUnpairedPrefixOfAlternatives(){
        var game=new RentzMatch(4,371,ScoreProfile.laboratory());game.reveal(0);var before=game.observe(0);
        var decision=new SearchPolicy(ScoreProfile.laboratory(),aware(8,1)).choose(before);
        assertEquals(0,decision.worlds());assertTrue(decision.estimates().isEmpty());assertTrue(decision.work().exhausted());
        assertEquals(1,decision.work().transitions());assertEquals(1,decision.work().transitionLimit());assertTrue(decision.method().contains("BUDGET_FALLBACK"));assertEquals(before,game.observe(0));
    }
    @Test void completedSearchPublishesMeasuredWorkAndActualCommonWorldCount(){
        var game=queenPosition();var before=game.observe(3);var decision=new SearchPolicy(ScoreProfile.laboratory(),aware(2,65536)).card(before);
        assertEquals(2,decision.worlds());assertEquals(2,decision.work().completedWorlds());assertEquals(2,decision.work().requestedWorlds());
        assertEquals(2,decision.work().alternatives());assertTrue(decision.work().transitions()>0);assertFalse(decision.work().exhausted());assertEquals(before,game.observe(3));
    }
    @Test void aPartiallyEvaluatedWorldIsDiscardedForEveryAlternative(){
        var game=queenPosition();var view=game.observe(3);var profile=ScoreProfile.laboratory();
        var one=new SearchPolicy(profile,aware(1,65536)).card(view);
        var limited=new SearchPolicy(profile,aware(2,one.work().transitions()+1)).card(view);
        assertEquals(1,limited.worlds());assertEquals(one.estimates(),limited.estimates());assertEquals(one.selected(),limited.selected());
        assertTrue(limited.work().exhausted());assertEquals(limited.work().transitionLimit(),limited.work().transitions());
    }
    @Test void anUnknownHigherClubPreventsThePublicCertaintyShortcut(){
        var game=game(deal(Map.of(0,cards("CJ CK"),1,cards("C9"),2,cards("C7"),3,cards("C10 CQ CA"))),Contract.TEN_CLUBS);
        game.play(0,c("CJ"));game.play(1,c("C9"));game.play(2,c("C7"));var view=game.observe(3);
        assertFalse(view.hand().contains(c("CK")));assertFalse(view.seen().contains(c("CK")));
        assertNull(ro.rentzlab.ai.tactics.TargetTactics.tenMaster(view));
    }
    @Test void continuationDoesNotSilentlyUpgradeTheUnknownOpponentModel(){
        var game=game(deal(Map.of(0,cards("C7"),1,cards("C8"),2,cards("C9"),3,cards("C10 CQ D7 D8 D9 H7 H8 S7"))),Contract.QUEENS);
        game.play(0,c("C7"));game.play(1,c("C8"));game.play(2,c("C9"));var view=game.observe(3);var profile=ScoreProfile.laboratory();
        assertEquals(c("C10"),TacticalPolicy.continuation(view,3,profile,aware(8,65536)));
        assertEquals(c("CQ"),TacticalPolicy.continuation(view,0,profile,aware(8,65536)),"Improving the actor continuation must not silently rewrite the declared legacy opponent model");
    }
    @Test void contextualSearchStillCannotReadRealOpposingHandsOrTheFutureSeed(){
        var opening=blockedAce();var swapped=new ArrayList<>(opening.hands());Collections.swap(swapped,1,2);var profile=ScoreProfile.laboratory();
        for(var objective:List.of(PolicySettings.Objective.HAND_MARGIN,PolicySettings.Objective.MATCH_WIN_SHARE)){
            var a=RentzMatch.fromOpeningDeal(opening,19,profile,0);var b=RentzMatch.fromOpeningDeal(new OpeningDeal(swapped),991,profile,0);a.reveal(0);b.reveal(0);
            var policy=new SearchPolicy(profile,new PolicySettings(2,.05,3,.3,2,objective,PolicySettings.Tactic.PUBLIC_TARGET_AWARE,65536));
            assertEquals(a.observe(0),b.observe(0));assertEquals(policy.choose(a.observe(0)),policy.choose(b.observe(0)));
            a.choose(0,Contract.WHIST,false);b.choose(0,Contract.WHIST,false);assertEquals(policy.card(a.observe(0)),policy.card(b.observe(0)));
        }
    }
    @Test void penaltyOrderingUsesTheConfiguredScoreRatherThanTheCardName(){
        var base=ScoreProfile.laboratory();var zeroQueen=new ScoreProfile(base.trickPenalty(),0,base.diamondPenalty(),base.kingPenalty(),base.trickReward(),base.tenReward(),base.finishRewards());
        var game=RentzMatch.fromOpeningDeal(deal(Map.of(0,cards("C7"),1,cards("C8"),2,cards("C9"),3,cards("C10 CQ D7 D8 D9 H7 H8 S7"))),1,zeroQueen,0);game.choose(0,Contract.QUEENS,false);
        game.play(0,c("C7"));game.play(1,c("C8"));game.play(2,c("C9"));
        var chosen=TacticalPolicy.card(game.observe(3),zeroQueen,aware(8,65536));assertEquals(c("CQ"),chosen,"With zero configured queen cost, expending greater strength preserves the declared tie-break");
        game.play(3,chosen);assertEquals(0,game.observe(3).dealPoints().get(3));
    }
    static RentzMatch lowTenExit(){
        var game=game(deal(Map.of(0,cards("CK CJ"),1,cards("CQ C9"),2,cards("C7 D9"),3,cards("C8 C10 CA D7 D8 H7 H8 S7"))),Contract.TEN_CLUBS);
        for(int seat=0;seat<4;seat++)game.play(seat,c(new String[]{"CK","CQ","C7","C8"}[seat]));
        game.play(0,c("CJ"));game.play(1,c("C9"));game.play(2,c("D9"));return game;
    }
    @Test void provedTenExitRepairsALegacySearchErrorWithoutChangingTheRestOfTheTactic(){
        var profile=ScoreProfile.laboratory();var legacy=lowTenExit();var corrected=lowTenExit();var settings=new PolicySettings(8,.05,3,.3,2,PolicySettings.Objective.HAND_MARGIN,PolicySettings.Tactic.PUBLIC_TEN_PROOF_ONLY,65536);
        var oldDecision=new SearchPolicy(profile,PolicySettings.handMarginReference()).card(legacy.observe(3));assertEquals(c("C10"),oldDecision.selected(),"The legacy rollout cannot see its own public winning continuation");
        legacy.play(3,oldDecision.selected());assertEquals(0,legacy.history().get(0).points().get(3));
        var fixed=new SearchPolicy(profile,settings).card(corrected.observe(3));assertEquals(c("CA"),fixed.selected());assertEquals(0,fixed.worlds());assertEquals("PUBLIC_TEN_MASTER_CAPTURE",fixed.method());
        corrected.play(3,fixed.selected());var capture=new SearchPolicy(profile,settings).card(corrected.observe(3));assertEquals(c("C10"),capture.selected());corrected.play(3,capture.selected());
        for(int seat=0;seat<3;seat++)corrected.play(seat,LabPolicy.card(corrected.observe(seat)));
        assertEquals(100,corrected.history().get(0).points().get(3));
        var other=queenPosition().observe(3);assertEquals(TacticalPolicy.card(other,profile,PolicySettings.handMarginReference()),TacticalPolicy.card(other,profile,settings),"The proof-only arm must not activate speculative penalty ordering");
    }
}
