package ro.rentzlab.app.achievements;

import static ro.rentzlab.app.achievements.AchievementCatalog.*;
import static ro.rentzlab.app.achievements.AchievementTactics.Fact.*;
import static ro.rentzlab.app.ui.screens.AchievementBrowser.Status.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.ai.RentzPlayer.Difficulty;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.GameSession.Action;
import ro.rentzlab.app.GameSession.Origin;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.app.ui.screens.AchievementBrowser;
import ro.rentzlab.app.ui.screens.AchievementBrowser.Query;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

@AndroidComponentLayer
public final class AchievementBrowserComponentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final LocalProfile PROFILE = new LocalProfile(new UUID(0, 1), ZoneId.of("UTC"));
    private static final RentzUiText RO = new RentzUiText(RentzUiText.Language.RO);

    @Test public void mockSettlementsActivateTheSameRowsTheAndroidListDisplays() throws Exception {
        var target = badge("s2-rentz-open-twice");
        var progress = AchievementProgress.empty(PROFILE);
        var manual = mock(Contract.RENTZ, 4, 0, true, false, Difficulty.NORMAL, Set.of(OPEN));
        var assisted = mock(Contract.RENTZ, 4, 0, true, true, Difficulty.NORMAL, Set.of(OPEN));
        var locked = row(progress, target);
        audit.equal("Locked row announces its counter", "Ușor · Blocată 0/2", locked.subtitle());

        progress = progress.record(4, 21, assisted, NOW);
        audit.equal("Delegation cannot turn a manual badge green", LOCKED, row(progress, target).status());
        progress = progress.record(4, 21, manual, NOW);
        var ongoing = row(progress, target);
        audit.equal("One distinct manual proof is visible", "Ușor · În progres 1/2", ongoing.subtitle());
        progress = progress.record(4, 21 + (1L << 48), manual, NOW);
        audit.equal("An alias cannot increase the visible counter", 1, row(progress, target).count());
        progress = progress.record(4, 22, manual, NOW.plusSeconds(1));
        var unlocked = row(progress, target);
        audit.equal("Unlocked row announces its counter", "Ușor · Deblocată 2/2", unlocked.subtitle());
        audit.equal("Recycling keeps the same item identity through all states", locked.itemId(), unlocked.itemId());

        var saved = SessionJson.saved(new GameSession(4, 22, Difficulty.NORMAL), progress).toString();
        var restored = SessionJson.restoreCheckpoint(saved).progress();
        audit.equal("Extended badge round-trips through the production codec", unlocked, row(restored, target));
    }

    @Test public void matchingFiltersKeepLearningOrderAndReturnAnExplicitEmptyPopulation() {
        var hand = mock(Contract.RENTZ, 4, 0, true, false, Difficulty.NORMAL, Set.of(OPEN));
        var progress = AchievementProgress.empty(PROFILE).record(4, 2, hand, NOW);
        var model = new AchievementBrowser(RO, progress, hand);
        var rows = model.rows(new Query("DESCHIDE CULOARE DE DOUA ORI", Level.EASY, Contract.RENTZ, IN_PROGRESS, PlayMode.MANUAL));
        audit.equal("Accent-free case-insensitive search combines the four filters", List.of("s2-rentz-open-twice"), ids(rows));
        var english = model.rows(new Query("starting rank", null, Contract.RENTZ, null, null));
        audit.truth("Search also matches explanations in English", ids(english).contains("s2-rentz-open-once"));
        audit.equal("Assistance filter excludes manual-only badges", List.of(),
                ids(model.rows(new Query("DESCHIDE CULOARE", null, Contract.RENTZ, null, PlayMode.ASSISTED))));
        audit.equal("Query that cannot match leaves no orphan section headings", List.of(),
                model.rows(new Query("zzz no contract", null, null, null, null)));
        var full = model.rows(Query.empty());
        var filtered = model.rows(new Query("", null, Contract.RENTZ, null, null));
        audit.equal("Filtering retains original learning order and row keys",
                full.stream().filter(row -> row.badge() != null && row.badge().contract() == Contract.RENTZ).toList(),
                filtered.stream().filter(row -> row.badge() != null).toList());
        audit.equal("Recommendation is the first outstanding lesson", "Prima alegere",
                new AchievementBrowser(RO, null, null).recommendation());
    }

    @Test public void eligibilityExplainsTheActualBlockingCondition() {
        var target = badge("s2-rentz-open-hard-days");
        var normal = mock(Contract.RENTZ, 4, 1, false, false, Difficulty.NORMAL, Set.of(OPEN));
        var assisted = mock(Contract.RENTZ, 4, 1, false, true, Difficulty.HARD, Set.of(OPEN));
        var wrong = mock(Contract.TRICKS, 4, 1, false, false, Difficulty.HARD, Set.of(LOSE_FIRST));
        var missed = mock(Contract.RENTZ, 4, 1, false, false, Difficulty.HARD, Set.of(EXTEND_LOW));
        var success = mock(Contract.RENTZ, 4, 1, false, false, Difficulty.HARD, Set.of(OPEN));
        audit.equal("No confirmed hand", Eligibility.NO_HAND, target.eligibility(null));
        audit.equal("Wrong contract", Eligibility.OTHER_CONTRACT, target.eligibility(wrong));
        audit.equal("Assistance blocked", Eligibility.NEED_MANUAL, target.eligibility(assisted));
        audit.equal("Difficulty context blocked", Eligibility.CONTEXT, target.eligibility(normal));
        audit.equal("Tactic absent", Eligibility.TACTIC, target.eligibility(missed));
        audit.equal("Eligible hand", Eligibility.ELIGIBLE, target.eligibility(success));
        audit.equal("The new first opening goal adds a choice condition to the pilot rule",
                Eligibility.CONTEXT, badge("s2-rentz-open-once").eligibility(success));
        var details = new AchievementBrowser(RO, AchievementProgress.empty(PROFILE), assisted).details(target);
        audit.equal("Detail communicates the eligibility reason independently of color",
                "Jocul manual nu a fost confirmat pentru întreaga mână.", details.latest());
        audit.truth("Detail explains the difficulty and counting unit",
                details.rule().contains("Greu") && details.context().contains("Zile și partide distincte"));
    }

    @Test public void masteryCountsOneDistinctMatchPerProfileDay() {
        var target = badge("s2-rentz-open-hard-days");
        var hard = mock(Contract.RENTZ, 5, 0, true, false, Difficulty.HARD, Set.of(OPEN));
        var normal = mock(Contract.RENTZ, 5, 0, true, false, Difficulty.NORMAL, Set.of(OPEN));
        var progress = AchievementProgress.empty(PROFILE).record(5, 91, hard, NOW);
        progress = progress.record(5, 92, hard, NOW.plusSeconds(100))
                .record(5, 91 + (1L << 48), hard, NOW.plusSeconds(86_400))
                .record(5, 93, normal, NOW.plusSeconds(86_400));
        audit.equal("Same day, same distribution and Normal do not add Hard credit", 1, progress.count(target));
        for (int day = 1; day <= 3; day++)
            progress = progress.record(5, 100 + day, hard, NOW.plusSeconds(day * 86_400L));
        audit.equal("Four different qualifying days activate mastery", UNLOCKED, row(progress, target).status());
    }

    @Test public void legalSeededHandsReachNewObjectivesAcrossAllTableSizesAndDifficultyContexts() {
        var expanded = AchievementCatalog.badges().stream().filter(b -> b.id().startsWith("s2-")).toList();
        var hits = new HashMap<String, Integer>();
        var runs = new HashMap<String, Integer>();
        for (var contract : Contract.values()) {
            var objectives = expanded.stream().filter(b -> b.contract() == contract).toList();
            for (int players = 3; players <= 6; players++) {
                for (var difficulty : Difficulty.values()) {
                    for (int seed = 0; seed < 64; seed++) {
                        var hand = sample(contract, players, difficulty, seed);
                        runs.merge(contract + "/" + players + "/" + difficulty, 1, Integer::sum);
                        for (var badge : objectives)
                            if (badge.accepts(hand)) hits.merge(badge.id(), 1, Integer::sum);
                    }
                }
            }
        }
        audit.equal("All eight contracts, four table sizes and two difficulty contexts", 64, runs.size());
        audit.equal("All calibration cells contain 64 confirmed legal hands", Set.of(64), Set.copyOf(runs.values()));
        audit.equal("Every new objective has at least one qualifying legal opportunity", List.of(),
                expanded.stream().filter(b -> !hits.containsKey(b.id())).map(Badge::id).toList());
        audit.truth("Calibration contains both frequent and conditional objectives",
                Collections.max(hits.values()) > Collections.min(hits.values()));
    }

    static AchievementHand sample(Contract contract, int players, Difficulty difficulty, long seed) {
        // Mix neighbouring seeds to vary the complete deal, then choose only cards the engine says are legal.
        long mixed = new Random(seed).nextLong() ^ (contract.ordinal() * 0x9e3779b97f4a7c15L);
        var match = new RentzMatch(players, mixed, GameSession.scoringProfile());
        var random = new Random(mixed);
        var recorder = new AchievementHand.Recorder(players);
        if ((seed & 2) != 0) {
            play(match, contract, false, null, random);
            match.nextHand();
        }
        play(match, contract, (seed & 1) != 0, recorder, random);
        var results = match.history();
        return recorder.settle(results.get(results.size() - 1), difficulty);
    }

    static void play(RentzMatch match, Contract contract, boolean blind, AchievementHand.Recorder recorder, Random random) {
        var view = match.observe(0);
        int chooser = view.chooser();
        if (!blind) match.reveal(chooser);
        match.choose(chooser, contract, blind);
        record(recorder, new GameSession.Command(Action.CHOOSE, chooser, null, contract, blind,
                chooser == 0 ? Origin.MANUAL : Origin.AUTOMATIC));
        while ((view = match.observe(0)).phase() == RentzMatch.Phase.PLAY) {
            int seat = view.turn();
            var legal = match.observe(seat).legal();
            if (legal.isEmpty()) {
                match.pass(seat);
                record(recorder, new GameSession.Command(Action.PASS, seat, null, null, false, Origin.AUTOMATIC));
            } else {
                Card card = legal.get(random.nextInt(legal.size()));
                match.play(seat, card);
                record(recorder, new GameSession.Command(Action.PLAY, seat, card, null, false,
                        seat == 0 ? Origin.MANUAL : Origin.AUTOMATIC));
            }
        }
    }

    private static void record(AchievementHand.Recorder recorder, GameSession.Command command) {
        if (recorder != null) recorder.observe(command);
    }

    private static AchievementHand mock(Contract contract, int players, int chooser, boolean choice, boolean delegated,
            Difficulty difficulty, Set<AchievementTactics.Fact> facts) {
        var scores = Collections.nCopies(players, 0);
        var result = new RentzMatch.HandResult(1, chooser, contract, false, scores, scores);
        return new AchievementHand(result, choice, true, delegated, false, false, false, false, difficulty, new AchievementTactics(facts));
    }

    private static Badge badge(String id) {
        return AchievementCatalog.badges().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<String> ids(List<AchievementBrowser.Row> rows) {
        return rows.stream().filter(row -> row.badge() != null).map(row -> row.badge().id()).toList();
    }

    private static AchievementBrowser.Row row(AchievementProgress progress, Badge badge) {
        return new AchievementBrowser(RO, progress, null).rows(Query.empty()).stream()
                .filter(row -> badge.equals(row.badge())).findFirst().orElseThrow();
    }
}
