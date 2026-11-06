package ro.rentzlab.core;

import ro.rentzlab.ai.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static ro.rentzlab.core.AiFixtures.*;

class RentzAiTest {
    private final ScoreProfile scoring=ScoreProfile.laboratory();
    private final PolicySettings settings=new PolicySettings(2,0.05,3,0.3,2);

    @Test void negativeTargetIsDiscardedWhenOffSuitCannotWin() {
        var game=game(deal(Map.of(0,cards("C7 C8 C9 C10 CJ CQ CK CA"),1,cards("H7 H8 H9 H10 HJ HQ HK HA"))),Contract.KING_HEARTS);
        game.play(0,c("C7"));Card chosen=TacticalPolicy.card(game.observe(1),scoring,settings);
        assertEquals(c("HK"),chosen);game.play(1,chosen);
        for(int seat=2;seat<4;seat++)game.play(seat,LabPolicy.card(game.observe(seat)));
        assertEquals(0,game.history().get(0).points().get(1));assertEquals(-100,game.history().get(0).points().get(0));
    }
    @Test void safeLoserDisposesOfHigherStrengthAndLastWinnerSpendsTheCheapestCard() {
        var negative=game(deal(Map.of(0,cards("CA"),1,cards("C8 C10 CQ D7 D8 D9 H7 S7"))),Contract.TRICKS);
        negative.play(0,c("CA"));assertEquals(c("CQ"),TacticalPolicy.card(negative.observe(1),scoring,settings));
        var positive=game(deal(Map.of(0,cards("C10"),1,cards("C7"),2,cards("C8"),3,cards("CJ CA D7 D8 D9 H7 H8 S7"))),Contract.WHIST);
        positive.play(0,c("C10"));positive.play(1,c("C7"));positive.play(2,c("C8"));
        assertEquals(c("CJ"),TacticalPolicy.card(positive.observe(3),scoring,settings));
    }
    @Test void tenOfClubsIsNotGivenAwayOnAnUnwinnableOffSuitTrick() {
        var game=game(deal(Map.of(0,cards("H7"),1,cards("C10 D7 D8 DQ DA S7 S8 S9"))),Contract.TEN_CLUBS);
        game.play(0,c("H7"));assertNotEquals(c("C10"),TacticalPolicy.card(game.observe(1),scoring,settings));
    }
    @Test void sequenceOpeningFavorsAnOwnedChainAndAcePreservesEveryOtherContinuation() {
        var chain=game(deal(Map.of(0,cards("CJ C10 C9 C8 C7 DJ HQ HA"))),Contract.RENTZ);
        assertEquals(c("CJ"),TacticalPolicy.card(chain.observe(0),scoring,settings));
        var game=game(blockedAce(),Contract.RENTZ);
        for(int rank=11;rank<=13;rank++)for(int seat=0;seat<4;seat++)game.play(seat,new Card(Card.Suit.values()[seat],rank));
        game.play(0,c("CA"));game.pass(0);
        var before=game.observe(1);assertTrue(before.legal().contains(c("DA")));assertTrue(before.legal().contains(c("D10")));
        var decision=new SearchPolicy(scoring,settings).card(before);
        assertEquals(c("DA"),decision.selected());assertEquals(0,decision.worlds());assertEquals("PUBLIC_ACE_EXTRA_TURN",decision.method());
        var other=new HashSet<>(before.legal());other.remove(c("DA"));game.play(1,decision.selected());
        assertEquals(1,game.observe(1).turn());assertTrue(game.observe(1).legal().containsAll(other));
    }
    @Test void offSuitHistoryRemovesRemainingCardsOfTheLedSuit() {
        var game=game(deal(Map.of(0,cards("C7 C8 C9 C10 CJ CQ CK CA"),1,cards("D7 D8 D9 D10 DJ DQ DK DA"))),Contract.WHIST);
        game.play(0,c("C7"));game.play(1,c("D7"));var view=game.observe(2);var sampler=new KnowledgeSampler(view);
        assertFalse(sampler.allows(1,c("CA")));
        for(int seed=0;seed<8;seed++){var sampled=sampler.sample(seed);assertTrue(sampled.hands().get(1).stream().noneMatch(card->card.suit()==Card.Suit.CLUBS));assertEquals(new HashSet<>(view.hand()),new HashSet<>(AiFixtures.replay(view,sampled).observe(2).hand()));}
    }
    @Test void rentzPassRestrictsThePastFrontierWithoutForbiddingFutureExtensions() {
        var game=game(blockedAce(),Contract.RENTZ);
        for(int rank=11;rank<=13;rank++)for(int seat=0;seat<4;seat++)game.play(seat,new Card(Card.Suit.values()[seat],rank));
        game.play(0,c("CA"));game.pass(0);
        var sampler=new KnowledgeSampler(game.observe(1));
        assertFalse(sampler.allows(0,c("D10")));assertFalse(sampler.allows(0,c("DA")));
        assertTrue(sampler.allows(0,c("D9")),"D9 was not adjacent at the observed pass; it can become legal later");
        for(int seed=0;seed<4;seed++)assertEquals(1,replay(game.observe(1),sampler.sample(seed)).observe(1).turn());
    }
    @ParameterizedTest @ValueSource(ints={3,4,5,6})
    void sampledWorldsReplayPublicStateAndPreserveActorCards(int players) {
        for(var contract:List.of(Contract.TOTALS,Contract.RENTZ)) {
            var game=new RentzMatch(players,13,scoring);game.choose(0,contract,false);int action=0;
            while(game.observe(0).phase()==RentzMatch.Phase.PLAY) {
                int actor=game.observe(0).turn();var before=game.observe(actor);
                if(action%7==0) {
                    var sample=new KnowledgeSampler(before).sample(action+9);var replayed=replay(before,sample).observe(actor);
                    assertEquals(new HashSet<>(before.hand()),new HashSet<>(replayed.hand()));assertEquals(before.turn(),replayed.turn());
                    assertEquals(before.board(),replayed.board());assertEquals(before.trick(),replayed.trick());assertEquals(before.dealPoints(),replayed.dealPoints());
                    assertEquals(before.handSizes(),replayed.handSizes());assertEquals(new HashSet<>(before.legal()),new HashSet<>(replayed.legal()));
                }
                if(before.legal().isEmpty())game.pass(actor);else game.play(actor,LabPolicy.card(before));assertTrue(++action<512);
            }
        }
    }
    @Test void identicalObservationCannotRevealDifferentRealOpposingHandsOrDealSeeds() {
        var first=blockedAce();var swapped=new ArrayList<>(first.hands());Collections.swap(swapped,1,2);
        var a=RentzMatch.fromOpeningDeal(first,11,scoring,0);var b=RentzMatch.fromOpeningDeal(new OpeningDeal(swapped),987,scoring,0);
        a.reveal(0);b.reveal(0);assertEquals(a.observe(0),b.observe(0));
        var policy=new SearchPolicy(scoring,settings);assertEquals(policy.choose(a.observe(0)),policy.choose(b.observe(0)));
        a.choose(0,Contract.WHIST,false);b.choose(0,Contract.WHIST,false);var before=a.observe(0);
        assertEquals(policy.card(a.observe(0)),policy.card(b.observe(0)));assertEquals(before,a.observe(0));
    }
    @Test void samplingIsDiverseAndImmutableWithoutClaimingUniformity() {
        var game=new RentzMatch(4,9,scoring);game.choose(0,Contract.WHIST,false);var sampler=new KnowledgeSampler(game.observe(0));
        var samples=new HashSet<OpeningDeal>();for(int seed=0;seed<12;seed++)samples.add(sampler.sample(seed));assertTrue(samples.size()>1);
        assertThrows(UnsupportedOperationException.class,()->sampler.unknownCards().clear());
        assertEquals(sampler.sample(24),sampler.sample(24));
        var hidden=new RentzMatch(4,2,scoring);assertThrows(IllegalArgumentException.class,()->new KnowledgeSampler(hidden.observe(0)));
    }
    @Test void hardReturnsLegalFiniteEstimatesAndDoesNotMutateTheRealMatch() {
        var game=new RentzMatch(4,17,scoring);game.reveal(0);var policy=new SearchPolicy(scoring,settings);var before=game.observe(0);
        var choice=policy.choose(before);assertTrue(before.available().contains(choice.selected()));assertEquals(before,game.observe(0));assertEquals(2,choice.worlds());
        for(var estimate:choice.estimates()){assertTrue(Double.isFinite(estimate.meanUtility()));assertTrue(Double.isFinite(estimate.selectionScore()));assertTrue(estimate.standardDeviation()>=0);}
        game.choose(0,Contract.WHIST,false);before=game.observe(0);var decision=policy.card(before);assertTrue(before.legal().contains(decision.selected()));assertEquals(before,game.observe(0));
        assertThrows(UnsupportedOperationException.class,()->decision.estimates().clear());
        assertThrows(IllegalArgumentException.class,()->new PolicySettings(0,0,0,0,0));assertThrows(IllegalArgumentException.class,()->new PolicySettings(8,Double.NaN,3,0.3,2));
    }
}
