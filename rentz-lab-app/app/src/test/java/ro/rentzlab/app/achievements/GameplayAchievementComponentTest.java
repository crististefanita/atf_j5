package ro.rentzlab.app.achievements;

import static ro.rentzlab.app.achievements.AchievementCatalog.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.ai.RentzPlayer.Difficulty;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.app.ui.screens.AchievementBrowser;
import ro.rentzlab.app.profile.LocalProfile;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

@AndroidComponentLayer
public final class GameplayAchievementComponentTest {
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final LocalProfile PROFILE = new LocalProfile(new UUID(0, 1), ZoneId.of("UTC"));
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void yamlOrderAndThresholdsMatchAllFifteenGameplayMilestones() {
        var catalog = GameplayAchievementCatalog.badges();
        audit.equal("Exact YAML order for all three levels", List.of(
                "play-first-hand", "play-first-target", "play-three-contracts", "play-first-choice", "play-rentz-sequence",
                "play-four-targets", "play-finish-match", "play-two-rentz-top-two", "play-first-win", "play-two-podiums",
                "play-clean-totals", "play-eight-targets", "play-three-wins", "play-rentz-large-first", "play-hard-large-win"),
                catalog.stream().map(Badge::id).toList());
        audit.equal("Exact thresholds for all fifteen", List.of(1, 1, 3, 2, 1, 4, 1, 2, 1, 2, 2, 8, 3, 2, 2),
                catalog.stream().map(Badge::goal).toList());
        for (var level : Level.values())
            audit.equal("Five milestones for " + level, 5L, catalog.stream().filter(b -> b.level() == level).count());
        audit.equal("Training catalog stays separate", 800, AchievementCatalog.badges().size());
        audit.equal("One evidence store knows all unique IDs", 815L,
                AchievementCatalog.all().stream().map(Badge::id).distinct().count());
    }

    @Test public void aHardGoalCanUnlockBeforeAnyMediumGoalAndReplayIsIdempotent() {
        var clean = hand(Contract.TOTALS, false, false, Difficulty.NORMAL, List.of(0, -20, -40, -10),
                Set.of(AchievementTactics.Fact.NO_TRICKS));
        var empty = AchievementProgress.empty(PROFILE);
        var progress = empty.record(4, 19, clean, NOW);
        var choice = badge("play-first-choice");
        audit.equal("The first hand unlocks immediately", 1, progress.count(badge("play-first-hand")));
        audit.equal("Personal choice advances in parallel", 1, progress.count(choice));
        audit.truth("The personal choice series still needs another hand", progress.count(choice) < choice.goal());
        audit.equal("The first clean hand counts toward the hard goal", 1, progress.count(badge("play-clean-totals")));
        audit.equal("A hand does not finish a match", 0, progress.count(badge("play-finish-match")));
        audit.equal("Seed aliases cannot replay the settled hand", progress, progress.record(4, 19 + (1L << 48), clean, NOW));
        var next = progress.record(4, 20, clean, NOW);
        audit.equal("A second distinct choice reaches the series threshold", choice.goal(), next.count(choice));
        audit.equal("Two distinct clean hands unlock before any Medium match goal", 2, next.count(badge("play-clean-totals")));
        audit.equal("The match goal still awaits a final table", 0, next.count(badge("play-finish-match")));
        var opponentTakesNothing = hand(Contract.TOTALS, false, false, Difficulty.NORMAL, List.of(-5, 0, -40, -10), Set.of());
        audit.equal("Same contract with a captured trick is not clean", Eligibility.TACTIC,
                badge("play-clean-totals").eligibility(opponentTakesNothing));
    }

    @Test public void finalRankUsesConfirmedTiesAndCountsEachDistinctMatchOnce() {
        var tied = hand(Contract.TRICKS, true, true, Difficulty.NORMAL, List.of(50, 50, 32, -9), Set.of());
        var early = hand(Contract.TRICKS, false, false, Difficulty.NORMAL, List.of(50, 50, 32, -9), Set.of());
        var assisted = hand(Contract.TRICKS, true, false, Difficulty.NORMAL, List.of(50, 50, 32, -9), Set.of());
        var loss = hand(Contract.TRICKS, true, true, Difficulty.NORMAL, List.of(49, 50, 32, -9), Set.of());
        var first = badge("play-first-win");
        audit.equal("A lead before terminal standings does not qualify", Eligibility.MATCH_IN_PROGRESS, first.eligibility(early));
        audit.equal("A manual final hand cannot hide earlier delegation", Eligibility.NEED_MANUAL, first.eligibility(assisted));
        audit.equal("The runner-up cannot receive a title", Eligibility.TACTIC, first.eligibility(loss));
        audit.equal("Shared first place qualifies", Eligibility.ELIGIBLE, first.eligibility(tied));
        var state = AchievementProgress.empty(PROFILE);
        for (long seed : new long[]{23, 23 + (1L << 48), 24, 25}) state = state.record(4, seed, tied, NOW);
        audit.equal("One win can advance several targets", 1, state.count(first));
        audit.equal("Three distinct victories unlock the series", 3, state.count(badge("play-three-wins")));
        audit.equal("Three victories do not imply a large Hard table", 0, state.count(badge("play-hard-large-win")));
    }

    @Test public void podiumRequiresFinishingAheadOfTheLastRankAtSmallTables() {
        var secondAtThree = hand(Contract.TRICKS, true, true, Difficulty.NORMAL, List.of(30, 20, 50), Set.of());
        var lastAtThree = hand(Contract.TRICKS, true, true, Difficulty.NORMAL, List.of(20, 30, 50), Set.of());
        var thirdAtFour = hand(Contract.TRICKS, true, true, Difficulty.NORMAL, List.of(30, 50, 40, 20), Set.of());
        check("play-two-podiums", secondAtThree, lastAtThree);
        var podium = badge("play-two-podiums");
        var first = AchievementProgress.empty(PROFILE).record(3, 31, secondAtThree, NOW);
        audit.equal("Last place never advances the series", 1, first.record(3, 32, lastAtThree, NOW).count(podium));
        audit.equal("A qualifying third place at a larger table completes the series", podium.goal(),
                first.record(4, 33, thirdAtFour, NOW).count(podium));
    }

    @Test public void recorderKeepsWholeMatchProvenanceAcrossHandsAndAllowsForcedPass() {
        var card = new Card(Card.Suit.CLUBS, 11);
        var finalHand = hand(Contract.TRICKS, true, true, Difficulty.NORMAL, List.of(50, 20, 25, 18), Set.of());
        for (var origin : List.of(GameSession.Origin.MANUAL, GameSession.Origin.DELEGATED, GameSession.Origin.UNKNOWN)) {
            var recorder = new AchievementHand.Recorder(4);
            recorder.observe(command(GameSession.Action.CHOOSE, Contract.RENTZ, null, origin));
            recorder.observe(command(GameSession.Action.NEXT_HAND, null, null, GameSession.Origin.UNKNOWN));
            recorder.observe(command(GameSession.Action.PASS, null, null, GameSession.Origin.AUTOMATIC));
            recorder.observe(command(GameSession.Action.PLAY, null, card, GameSession.Origin.MANUAL));
            var outcome = recorder.settle(finalHand.result(), Difficulty.NORMAL);
            audit.equal("The terminal hand is manual for " + origin, true, outcome.manual());
            audit.equal("Only known manual decisions qualify the whole match for " + origin,
                    origin == GameSession.Origin.MANUAL, outcome.manualMatch());
        }
    }

    @Test public void eachTacticalOrContextualMetricHasPositiveAndNegativeEvidence() {
        var openingOnly = hand(Contract.RENTZ, false, false, Difficulty.NORMAL, List.of(0, 0, 0, 0),
                Set.of(AchievementTactics.Fact.OPEN));
        var extension = hand(Contract.RENTZ, false, false, Difficulty.NORMAL, List.of(0, 0, 0, 0),
                Set.of(AchievementTactics.Fact.EXTEND_LOW, AchievementTactics.Fact.TOP_TWO));
        var wrongContract = hand(Contract.QUEENS, false, false, Difficulty.NORMAL, List.of(0, 0, 0, 0),
                extension.tactics().facts());
        check("play-rentz-sequence", extension, openingOnly);
        check("play-two-rentz-top-two", extension, wrongContract);
        var delegated = new AchievementHand(extension.result(), false, false, true, false, false, false,
                false, Difficulty.NORMAL, extension.tactics());
        check("play-first-hand", extension, delegated);
        var theirChoice = new AchievementHand(extension.result(), false, true, false, false, false, true,
                false, Difficulty.NORMAL, extension.tactics());
        check("play-first-choice", extension, theirChoice);
        check("play-three-contracts", theirChoice, delegated);

        var won = java.util.stream.IntStream.range(0, 64)
                .mapToObj(seed -> AchievementBrowserComponentTest.sample(Contract.RENTZ, 5, Difficulty.NORMAL, seed))
                .filter(AchievementCatalog::target).findFirst().orElseThrow();
        var missed = java.util.stream.IntStream.range(0, 64)
                .mapToObj(seed -> AchievementBrowserComponentTest.sample(Contract.RENTZ, 5, Difficulty.NORMAL, seed))
                .filter(value -> value.manual() && !AchievementCatalog.target(value)).findFirst().orElseThrow();
        check("play-first-target", won, missed);
        check("play-four-targets", won, missed);
        check("play-eight-targets", won, missed);
        check("play-rentz-large-first", won, missed);
        var smallWin = java.util.stream.IntStream.range(0, 64)
                .mapToObj(seed -> AchievementBrowserComponentTest.sample(Contract.RENTZ, 4, Difficulty.NORMAL, seed))
                .filter(AchievementCatalog::target).findFirst().orElseThrow();
        check("play-rentz-large-first", won, smallWin);

        var firstHard = hand(Contract.TRICKS, true, true, Difficulty.HARD, List.of(60, 51, 42, 33, 24), Set.of());
        var firstNormal = hand(Contract.TRICKS, true, true, Difficulty.NORMAL, firstHard.result().totals(), Set.of());
        var third = hand(Contract.TRICKS, true, true, Difficulty.HARD, List.of(42, 51, 60, 33, 24), Set.of());
        var fourth = hand(Contract.TRICKS, true, true, Difficulty.HARD, List.of(33, 51, 60, 42, 24), Set.of());
        check("play-hard-large-win", firstHard, firstNormal);
        audit.equal("A qualifying rank on the wrong difficulty is a context mismatch", Eligibility.CONTEXT,
                badge("play-hard-large-win").eligibility(firstNormal));
        check("play-two-podiums", third, fourth);
        check("play-finish-match", firstHard, openingOnly);
        check("play-three-wins", firstHard, third);
    }

    @Test public void recommendationsRespectTheCurrentTableBeforeTheFirstHand() {
        var copy = new RentzUiText(RentzUiText.Language.RO);
        var rentz = badge("play-rentz-large-first");
        var hard = badge("play-hard-large-win");
        var marks = new LinkedHashMap<String, List<AchievementProgress.Mark>>();
        for (var rule : GameplayAchievementCatalog.badges()) {
            if (rule == rentz || rule == hard) continue;
            marks.put(rule.id(), java.util.stream.IntStream.range(0, rule.goal())
                    .mapToObj(i -> new AchievementProgress.Mark("fixture-" + i, NOW)).toList());
        }
        var progress = new AchievementProgress(PROFILE, marks);
        var small = new AchievementBrowser(copy, progress, null, AchievementBrowser.Collection.GAMEPLAY,
                new AchievementBrowser.Table(4, Difficulty.HARD));
        audit.equal("A small table is guided to change configuration", "Schimbă numărul de jucători sau dificultatea",
                small.recommendation());
        audit.equal("An incompatible goal explains the table requirement before any hand",
                "Masa sau dificultatea curentă nu îndeplinește cerința.", small.details(rentz).latest());
        audit.equal("Incompatible goals are still visible", 15L, small.rows(AchievementBrowser.Query.empty()).stream()
                .filter(row -> row.badge() != null).count());
        var large = new AchievementBrowser(copy, progress, null, AchievementBrowser.Collection.GAMEPLAY,
                new AchievementBrowser.Table(5, Difficulty.NORMAL));
        audit.equal("A large Normal table can pursue the Rentz milestone", rentz.romanian(), large.recommendation());
        marks.put(rentz.id(), List.of(new AchievementProgress.Mark("fixture-1", NOW),
                new AchievementProgress.Mark("fixture-2", NOW)));
        progress = new AchievementProgress(PROFILE, marks);
        var hardTable = new AchievementBrowser(copy, progress, null, AchievementBrowser.Collection.GAMEPLAY,
                new AchievementBrowser.Table(5, Difficulty.HARD));
        audit.equal("The Hard objective is recommended on a qualifying table", hard.romanian(), hardTable.recommendation());
    }

    @Test public void gameplayContractFilterShowsTacticsAndRelevantScoringGoals() {
        var copy = new RentzUiText(RentzUiText.Language.RO);
        var gameplay = new AchievementBrowser(copy, AchievementProgress.empty(PROFILE), null,
                AchievementBrowser.Collection.GAMEPLAY);
        var rentz = new AchievementBrowser.Query("", null, Contract.RENTZ, null, null);
        var totals = new AchievementBrowser.Query("", null, Contract.TOTALS, null, null);
        audit.equal("Rentz filter shows sequence, placement and contract goals",
                List.of("play-first-target", "play-three-contracts", "play-rentz-sequence", "play-four-targets",
                        "play-two-rentz-top-two", "play-eight-targets", "play-rentz-large-first"), filteredIds(gameplay, rentz));
        audit.equal("Totals filter includes clean hands and cross-contract scoring goals",
                List.of("play-first-target", "play-three-contracts", "play-four-targets", "play-clean-totals",
                        "play-eight-targets"), filteredIds(gameplay, totals));
    }

    private static List<String> filteredIds(AchievementBrowser browser, AchievementBrowser.Query query) {
        return browser.rows(query).stream().filter(row -> row.badge() != null).map(row -> row.badge().id()).toList();
    }

    @Test public void profileCodecAndBrowserKeepTrainingAndGameplayProgressSeparate() throws Exception {
        var result = hand(Contract.TRICKS, false, false, Difficulty.NORMAL, List.of(0, -10, -50, -20), Set.of());
        var state = AchievementProgress.empty(PROFILE).record(4, 29, result, NOW);
        var saved = SessionJson.saved(new GameSession(4, 29, Difficulty.NORMAL), state).toString();
        var restored = SessionJson.restoreCheckpoint(saved).progress();
        audit.equal("Old training proof round-trips alongside gameplay", state, restored);
        audit.truth("The legacy first-hand key still has progress", restored.marks().containsKey("first-hand"));
        var copy = new RentzUiText(RentzUiText.Language.RO);
        var gameplay = new AchievementBrowser(copy, restored, result, AchievementBrowser.Collection.GAMEPLAY);
        var training = new AchievementBrowser(copy, restored, result, AchievementBrowser.Collection.TRAINING);
        audit.equal("Gameplay screen keeps 15 objectives", 15, gameplay.total());
        audit.equal("Training screen keeps 800 objectives", 800, training.total());
        audit.equal("Independent unlocked gameplay count", 1L, gameplay.unlocked());
        audit.equal("The next recommendation after the first hand is a scoring target",
                "Prima țintă atinsă", gameplay.recommendation());
        audit.equal("Sections follow the recommended level order", List.of("Ușor · Primele mâini",
                "Mediu · Partide complete", "Greu · Măiestrie"),
                gameplay.rows(AchievementBrowser.Query.empty()).stream().filter(row -> row.badge() == null)
                        .map(AchievementBrowser.Row::title).toList());
        audit.truth("The target detail lists Rentz-specific scoring criteria", gameplay.details(badge("play-first-target"))
                .rule().contains("Ia cel mult o levată"));
    }

    private static GameSession.Command command(GameSession.Action action, Contract contract, Card card, GameSession.Origin origin) {
        return new GameSession.Command(action, 0, card, contract, false, origin);
    }

    private static AchievementHand hand(Contract contract, boolean terminal, boolean manualMatch, Difficulty difficulty,
            List<Integer> totals, Set<AchievementTactics.Fact> facts) {
        int number = terminal ? 8 * totals.size() : 1;
        var settled = new RentzMatch.HandResult(number, 0, contract, false,
                Collections.nCopies(totals.size(), 0), totals);
        return new AchievementHand(settled, true, true, false, false, false, false, terminal, difficulty,
                new AchievementTactics(facts), manualMatch);
    }

    private static Badge badge(String id) {
        return GameplayAchievementCatalog.badges().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private void check(String id, AchievementHand accepted, AchievementHand rejected) {
        audit.equal(id + " accepts the positive proof", Eligibility.ELIGIBLE, badge(id).eligibility(accepted));
        audit.truth(id + " rejects the close counterexample", badge(id).eligibility(rejected) != Eligibility.ELIGIBLE);
    }
}
