package atf.rentz;

import ro.rentzlab.ai.*;

import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import ro.rentzlab.core.*;
import java.nio.file.*;
import java.util.*;
import static atf.testkit.assertions.AuditedAssertions.*;

@AtfComponentTest
class RentzRuleComponentTest {
    private static RentzRulePublication.Report report;
    @BeforeAll static void open() throws Exception { report=new RentzRulePublication.Report(); }
    @AfterAll static void close() throws Exception { if(report!=null)report.close(); }
    @RegisterExtension static final ComponentAuditExtension audit=new ComponentAuditExtension(RentzRuleComponentTest.class,context->new RentzRulePublication(report,context.getDisplayName()));
    @Test void configuredProfileHasIndependentLaboratoryIdentity() throws Exception {
        var profile=RuleProfiles.read(Path.of("../config/rentz-score-profiles.yaml"));
        assertEquals(ScoreProfile.laboratory(),profile,"Typed YAML and explicitly provisional Java profile agree");
        assertEquals(25,profile.queenPenalty(),"Laboratory value; not a claim about reference presets");
    }
    @Test void totalsPreserveEveryOverlappingPenalty() {
        var cards=List.of(new Card(Card.Suit.DIAMONDS,12),new Card(Card.Suit.HEARTS,13),new Card(Card.Suit.CLUBS,8),new Card(Card.Suit.SPADES,7));
        assertEquals(-155,ScoreProfile.laboratory().trickPoints(Contract.TOTALS,cards,false),"Trick, diamond queen and heart king contribute");
        assertEquals(-310,ScoreProfile.laboratory().trickPoints(Contract.TOTALS,cards,true),"Blind applies once to the completed sum");
    }
    @Test void rentzDoesNotBorrowWhistTrumpOrBiddingRules() {
        var led=new TrickRules.Play(0,new Card(Card.Suit.CLUBS,10));
        assertEquals(0,TrickRules.winner(List.of(led,new TrickRules.Play(1,new Card(Card.Suit.HEARTS,14)),new TrickRules.Play(2,new Card(Card.Suit.DIAMONDS,14)))),"Off-suit aces cannot trump");
        assertEquals(8,Contract.values().length,"Base model contains eight game choices, no trick bids");
    }
    @Test void blindCannotBeRequestedAfterSeeingTheHand() {
        var game=new RentzMatch(4,41,ScoreProfile.laboratory());game.reveal(0);var before=game.observe(0);
        assertThrows(IllegalStateException.class,()->game.choose(0,Contract.RENTZ,true),"Blind is a pre-reveal commitment");
        assertEquals(before,game.observe(0),"Refused choice cannot consume the contract");
    }
    @Test void actorReceivesPrivateHandAndOnlyPublicOpponentSizes() {
        var game=new RentzMatch(4,17,ScoreProfile.laboratory());game.choose(0,Contract.WHIST,false);
        var actor=game.observe(2);assertEquals(8,actor.hand().size());assertEquals(List.of(8,8,8,8),actor.handSizes());
        assertTrue(actor.seen().isEmpty(),"No future cards are public before play");
        assertThrows(UnsupportedOperationException.class,()->actor.hand().clear());
    }
    @Test void publicScoreChangesAtTrickSettlementAndKeepsTheBlindCommitment() {
        var game=new RentzMatch(4,17,ScoreProfile.laboratory());game.choose(0,Contract.WHIST,true);
        var initial=game.observe(0);
        for(int action=0;action<3;action++){
            int actor=game.observe(0).turn();game.play(actor,LabPolicy.card(game.observe(actor)));
            assertEquals(List.of(0,0,0,0),game.observe(0).dealPoints(),"Incomplete trick cannot award points");
        }
        int actor=game.observe(0).turn();game.play(actor,LabPolicy.card(game.observe(actor)));
        var settled=game.observe(0);
        assertTrue(settled.blind(),"The public commitment remains visible during the deal");
        assertEquals(20,settled.dealPoints().stream().mapToInt(Integer::intValue).sum(),"One winning trick receives one doubled reward");
        assertEquals(List.of(0,0,0,0),settled.totals(),"Match totals are committed only at deal settlement");
        assertEquals(List.of(0,0,0,0),initial.dealPoints(),"An earlier observation remains a snapshot");
    }
    @Test void invalidYamlCannotSilentlyChangeTheRuleProfile(@org.junit.jupiter.api.io.TempDir Path folder) throws Exception {
        String original=Files.readString(Path.of("../config/rentz-score-profiles.yaml"));
        var variants=List.of(original+"unknown: true\n",original.replace("schemaVersion: 1","schemaVersion: '1'"),
                original.replace("queen: 25","queen: -25"),original.replace("LABORATORY_ONLY","REFERENCE_VERIFIED"),"", "[]");
        int index=0;
        for(String changed:variants){Path file=folder.resolve("profile-"+(index++)+".yaml");Files.writeString(file,changed);
            assertThrows(IllegalArgumentException.class,()->RuleProfiles.read(file),"Invalid fields, types and unsupported qualification are refused");}
        Path duplicate=folder.resolve("duplicate.yaml");Files.writeString(duplicate,original.replace("queen: 25","queen: 25\n  queen: 30"));
        assertThrows(com.fasterxml.jackson.core.JsonParseException.class,()->RuleProfiles.read(duplicate),"Duplicate keys are rejected by the parser");
        Path multiple=folder.resolve("multiple.yaml");Files.writeString(multiple,original+"\n---\nprofile: laboratory\n");
        assertThrows(IllegalArgumentException.class,()->RuleProfiles.read(multiple),"A second YAML document cannot be silently ignored");
    }
    @Test void publisherScreenshotValuesStayPartialAndCannotBecomeANamedPreset() throws Exception {
        Path path=Path.of("../config/rentz-reference-observations.yaml");var evidence=RuleCatalog.evidence(path);
        assertEquals(4,evidence.players(),"Publisher images cover the four-player table");
        var expected=Map.of("trickPenalty",50,"queenPenalty",40,"diamondPenalty",30,"kingHeartsPenalty",200,"tenClubsReward",200,"firstFinishReward",400);
        var actual=new TreeMap<String,Integer>();evidence.readings().forEach((name,reading)->{if(reading.magnitude()!=null)actual.put(name,reading.magnitude());});
        assertEquals(expected,actual,"Directly visible magnitudes; penalties keep their sign in the score model");
        assertEquals("UNCONFIRMED",evidence.presetIdentity(),"Screenshot does not identify Classic or Traditional");
        assertNull(evidence.readings().get("whistTrickReward").magnitude());
        assertNull(evidence.readings().get("otherFinishRewards").magnitude());
        assertEquals(List.of(0,100,200,400),evidence.unorderedFinishAwards(),"An award multiset is not a labeled finishing-order map");
        assertThrows(IllegalArgumentException.class,()->RuleProfiles.read(path),"Partial reference observations cannot configure runtime scoring");
    }
    @Test void ruleManifestResolvesToLiveImplementationAndTestDeclarations() throws Exception {
        var claims=RuleCatalog.claims(Path.of("../config/rentz-rules.yaml"));
        assertDoesNotThrow(()->RuleCatalog.verifyBindings(Path.of(".."),claims),"Each declaration binds to a public engine API and an existing core test method");
        var chooser=claims.stream().filter(c->c.id().equals("CHOOSER-LEADS")).findFirst().orElseThrow();
        assertEquals("PUBLISHER_DECLARED",chooser.status(),"The publisher explicitly states who leads the deal");
        assertEquals("PUBLISHER_DESCRIPTION",chooser.source());
        var bad=new RuleCatalog.Claim("BROKEN","LAB_VARIANT_REQUIRES_REFERENCE_CONFIRMATION","LAB_CONTRACT","RentzMatch.nonexistentMethod",chooser.verification());
        assertThrows(IllegalArgumentException.class,()->RuleCatalog.verifyBindings(Path.of(".."),List.of(bad)),"Renamed or missing API must fail the binding audit");
        var badTest=new RuleCatalog.Claim("BROKEN","LAB_VARIANT_REQUIRES_REFERENCE_CONFIRMATION","LAB_CONTRACT","RentzMatch.choose","RentzBoundaryTest.nonexistentCase");
        assertThrows(IllegalArgumentException.class,()->RuleCatalog.verifyBindings(Path.of(".."),List.of(badTest)),"Missing regression declaration must fail the audit");
    }
    @Test void provenanceCannotPromoteUnknownValuesOrBrokenBindings(@org.junit.jupiter.api.io.TempDir Path folder) throws Exception {
        String observations=Files.readString(Path.of("../config/rentz-reference-observations.yaml"));
        int number=0;
        for(String changed:List.of(observations.replace("presetIdentity: UNCONFIRMED","presetIdentity: Classic"),
                observations.replace("runtimeProfile: false","runtimeProfile: true"),observations.replace("magnitude: null","magnitude: 0"),
                observations.replace("source: choice","source: unknown"),observations.replace("players: 4","players: 5"))) {
            Path file=folder.resolve("evidence-"+(number++)+".yaml");Files.writeString(file,changed);
            assertThrows(IllegalArgumentException.class,()->RuleCatalog.evidence(file),"Partial evidence must retain source, scope and unknown values");
        }
        String manifest=Files.readString(Path.of("../config/rentz-rules.yaml")).replace("\r\n","\n");
        for(String changed:List.of(manifest.replace("status: PUBLISHER_DECLARED\n","status: VERIFIED_BY_SIMULATOR\n"),
                manifest.replace("source: PUBLISHER_DESCRIPTION","source: LAB_CONTRACT"),manifest.replace("id: DECK-RANKS","id: TABLE-SIZE"))) {
            Path file=folder.resolve("manifest-"+(number++)+".yaml");Files.writeString(file,changed);
            assertThrows(IllegalArgumentException.class,()->RuleCatalog.claims(file),"Status, provenance and rule identity must remain coherent");
        }
    }
    @Test void apkRuleDeclarationsKeepArtifactAndResourceAnchorsSeparateFromRuntimeParity(@org.junit.jupiter.api.io.TempDir Path folder) throws Exception {
        Path original=Path.of("../config/rentz-rules.yaml");var claims=RuleCatalog.claims(original);
        var last=claims.stream().filter(c->c.id().equals("BLIND-LAST-CHOICE")).findFirst().orElseThrow();
        assertEquals("PUBLISHER_APK_DECLARED",last.status());assertEquals("APK_RESOURCE:rules_blind",last.source());
        assertEquals("PUBLISHER_APK_DECLARED",claims.stream().filter(c->c.id().equals("FOLLOW-SUIT-NO-TRUMP")).findFirst().orElseThrow().status());
        String text=Files.readString(original);
        for(String changed:List.of(text.replace("APK_RESOURCE:rules_blind","APK_RESOURCE:unknown"),text.replace("apkSha256: 622747ea8a323fd13ac6a6d2d88170ae067b753683b52ff68e2a3daa737e1518","apkSha256: invalid"),text.replace("source: APK_RESOURCE:rules_cards","source: PUBLISHER_DESCRIPTION"))){
            Path file=folder.resolve("bad-apk-source.yaml");Files.writeString(file,changed);assertThrows(IllegalArgumentException.class,()->RuleCatalog.claims(file));
        }
        assertEquals("UNCONFIRMED",RuleCatalog.evidence(Path.of("../config/rentz-reference-observations.yaml")).presetIdentity(),"Instruction resources do not establish numerical preset identity");
    }
    @Test void strategyYamlMatchesTheRuntimeBudgetWeightsAndCodeBindings(@org.junit.jupiter.api.io.TempDir Path folder) throws Exception {
        Path file=Path.of("../config/rentz-policy.yaml");
        assertEquals(PolicySettings.defaults(),PolicyInputs.read(file,Path.of("..")),"Executable coefficients and code-bound rule declarations match the runtime factory");
        String original=Files.readString(file);
        for(String changed:List.of(original.replace("worlds: 8","worlds: 0"),original.replace("riskWeight: 0.05","riskWeight: 'NaN'"),original.replace("card: PUBLIC_TACTICAL","card: OMNISCIENT"),original.replace("TacticalPolicy.card","TacticalPolicy.missing"),original+"\n---\na: 1\n")) {
            Path bad=folder.resolve("bad-policy.yaml");Files.writeString(bad,changed);
            assertThrows(IllegalArgumentException.class,()->PolicyInputs.read(bad,Path.of("..")),"Budget, finite weights, public-information policy and live bindings are enforced");
        }
        Path malformed=folder.resolve("malformed-number.yaml");Files.writeString(malformed,original.replace("riskWeight: 0.05","riskWeight: .nan"));
        assertThrows(com.fasterxml.jackson.core.JsonParseException.class,()->PolicyInputs.read(malformed,Path.of("..")),"Parser rejects its malformed numeric representation before typed policy validation");
    }
    @Test void hardDecisionUsesTheSameAllowedObservationForDifferentRealDeals() {
        var scoring=ScoreProfile.laboratory();var first=new ArrayList<List<Card>>();var deck=Card.deck(4);
        for(int seat=0;seat<4;seat++)first.add(new ArrayList<>(deck.subList(seat*8,seat*8+8)));
        var second=new ArrayList<>(first);Collections.swap(second,1,2);
        var a=RentzMatch.fromOpeningDeal(new OpeningDeal(first),10,scoring,0);var b=RentzMatch.fromOpeningDeal(new OpeningDeal(second),9876,scoring,0);a.reveal(0);b.reveal(0);
        var player=new RentzPlayer(RentzPlayer.Difficulty.HARD,scoring,new PolicySettings(2,0.05,3,0.3,2));
        assertEquals(a.observe(0),b.observe(0),"Only real hidden opponent cards and continuation seed differ");
        assertEquals(player.choose(a.observe(0)),player.choose(b.observe(0)),"Same permitted observation produces the same estimate vector and decision");
    }
}
