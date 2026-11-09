package ro.rentzlab.app.achievements;

import static ro.rentzlab.app.achievements.AchievementCatalog.*;
import static ro.rentzlab.app.achievements.AchievementFullCatalog.Context;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
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
import ro.rentzlab.app.ui.screens.AchievementBrowser.Query;
import ro.rentzlab.app.profile.LocalProfile;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

@AndroidComponentLayer
public final class AchievementFullCatalogComponentTest {
    private static final RentzUiText COPY = new RentzUiText(RentzUiText.Language.RO);
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final LocalProfile PROFILE = new LocalProfile(new UUID(0, 1), ZoneId.of("UTC"));

    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void completeCatalogKeepsBalancedContractTracksAndSearchableSections() {
        var badges = AchievementCatalog.badges();
        for (var contract : Contract.values()) {
            audit.equal("Tier population for " + contract, List.of(50L, 20L, 10L), levels(badges, contract));
        }
        audit.equal("Cross-contract population", List.of(100L, 40L, 20L), levels(badges, null));
        var browser = new AchievementBrowser(COPY, null, null);
        audit.equal("The browser exposes all objective rows", 800L,
                browser.rows(Query.empty()).stream().filter(row -> row.badge() != null).count());
        var hard = browser.rows(new Query("", Level.HARD, null, null, null));
        audit.equal("The mastery filter exposes the complete hard tier", 100L,
                hard.stream().filter(row -> row.badge() != null).count());
        audit.equal("The filtered catalog begins with the mastery section", "4 · Stăpânește jocul", hard.get(0).title());
        audit.equal("The rare own-ten achievement keeps its published identifier", Level.HARD,
                badge("s2-ten_clubs-own_ten-once").level());
        audit.equal("The rare king discard moves to Medium", Level.MEDIUM,
                badge("s2-king_hearts-king_discarded-once").level());
    }

    @Test public void aMockTableCollectionShowsLockedProgressUnlockedAndSurvivesReload() throws Exception {
        var target = badge("s3-cross-at-table-any-table-3");
        var progress = AchievementProgress.empty(PROFILE);
        audit.equal("Collection starts locked", "Mediu · Blocată 0/3", status(progress, target));
        progress = progress.record(3, 17, hand(3), NOW).record(3, 18, hand(3), NOW);
        audit.equal("Two different matches at one table size add a single visible mark",
                "Mediu · În progres 1/3", status(progress, target));
        progress = progress.record(4, 19, hand(4), NOW).record(6, 20, hand(6), NOW);
        audit.equal("Three table sizes unlock the collection", "Mediu · Deblocată 3/3", status(progress, target));
        var saved = SessionJson.saved(new GameSession(4, 20, Difficulty.NORMAL), progress).toString();
        var restored = SessionJson.restoreCheckpoint(saved).progress();
        audit.equal("Stored table keys survive the real codec", List.of("3", "4", "6"),
                restored.marks().get(target.id()).stream().map(AchievementProgress.Mark::key).toList());
        audit.equal("The restored row keeps its exact visual status", status(progress, target), status(restored, target));
    }

    @Test public void existingMarksSurviveRebalancedRulesAndOnlyNewProofsUseTheNewConditions() throws Exception {
        var original = badge("queens-repeat");
        var revised = badge("s2-queens-no_queens-twice");
        var opponentHand = java.util.stream.IntStream.range(0, 64)
                .mapToObj(seed -> AchievementBrowserComponentTest.sample(Contract.QUEENS, 4, Difficulty.NORMAL, seed))
                .filter(candidate -> original.accepts(candidate) && candidate.result().chooser() != 0)
                .findFirst().orElseThrow();
        var oldMark = new AchievementProgress.Mark("4:4:1", NOW);
        var legacy = new AchievementProgress(PROFILE, java.util.Map.of(revised.id(), List.of(oldMark)));
        var saved = SessionJson.saved(new GameSession(4, 30, Difficulty.NORMAL), legacy).toString();
        var restored = SessionJson.restoreCheckpoint(saved).progress();
        audit.equal("The previously confirmed mark is retained", List.of(oldMark), restored.marks().get(revised.id()));
        var after = restored.record(4, 30, opponentHand, NOW.plusSeconds(1));
        audit.equal("An opponent's choice still qualifies for the original general goal", 1, after.count(original));
        audit.equal("The revised goal refuses another opponent choice", 1, after.count(revised));
    }

    @Test public void independentLegalBatchesConfirmRatesAndDistinctEvidenceForTheCatalog() {
        var evidence = new Corpus(AchievementCatalog.badges());
        var mismatches = new ArrayList<String>();
        for (var contract : Contract.values()) calibrate(contract, evidence, mismatches);
        for (int players = 3; players <= 6; players++) {
            for (var difficulty : Difficulty.values()) {
                for (int seed = 0; seed < 10; seed++) {
                    evidence.add(completedMatch(players, difficulty, seed));
                }
            }
        }
        audit.equal("Observed rate floors exactly reproduce the checked-in table", List.of(), mismatches);
        audit.equal("Two disjoint batches of 96 legal samples in each contract, size and difficulty cell",
                12_288, evidence.legalHandSamples);
        audit.equal("Each achievement has enough eligible proofs for its unit and threshold",
                List.of(), evidence.unreachable());
        audit.equal("Equal-scope badges never count the same set of witnessed outcomes",
                List.of(), evidence.duplicates());
    }

    private static void calibrate(Contract contract, Corpus evidence, List<String> mismatches) {
        var contexts = Context.values();
        var tactics = AchievementExpansion.tactics(contract);
        var rates = new int[tactics.size()][contexts.length];
        for (var row : rates) Arrays.fill(row, 100);
        for (int offset : new int[]{0, 128}) {
            int[] opportunities = new int[contexts.length];
            int[][] hits = new int[tactics.size()][contexts.length];
            for (int players = 3; players <= 6; players++) {
                for (var difficulty : Difficulty.values()) {
                    for (int seed = offset; seed < offset + 96; seed++) {
                        var hand = AchievementBrowserComponentTest.sample(contract, players, difficulty, seed);
                        evidence.add(hand);
                        evidence.legalHandSamples++;
                        // The mock delegates every human decision while preserving the accepted cards.
                        evidence.add(assisted(hand));
                        if (!hand.manual()) continue;
                        for (var context : contexts) {
                            int c = context.ordinal();
                            if (!context.applies.test(hand)) continue;
                            opportunities[c]++;
                            for (int i = 0; i < tactics.size(); i++)
                                if (hand.tactics().has(tactics.get(i).fact())) hits[i][c]++;
                        }
                    }
                }
            }
            for (int i = 0; i < tactics.size(); i++) {
                for (int c = 0; c < contexts.length; c++) {
                    int measured = opportunities[c] < 24 || hits[i][c] < 2 ? 0 : 100 * hits[i][c] / opportunities[c];
                    rates[i][c] = Math.min(rates[i][c], measured);
                }
            }
        }
        for (int i = 0; i < tactics.size(); i++) {
            var fact = tactics.get(i).fact();
            for (var context : contexts) {
                int expected = AchievementRateFloor.percent(contract, fact, context);
                int actual = rates[i][context.ordinal()];
                if (expected != actual) mismatches.add(contract + "/" + fact + "/" + context + "=" + actual + " expected " + expected);
            }
        }
    }

    private static AchievementHand completedMatch(int players, Difficulty difficulty, long seed) {
        var random = new Random(seed * 1949 + players * 31L);
        var match = new RentzMatch(players, random.nextLong(), GameSession.scoringProfile());
        AchievementHand last = null;
        for (int round = 0; round < players * Contract.values().length; round++) {
            var chooser = match.observe(0).chooser();
            var choices = match.observe(chooser).available().stream().sorted().toList();
            var contract = choices.get(random.nextInt(choices.size()));
            var recorder = new AchievementHand.Recorder(players);
            AchievementBrowserComponentTest.play(match, contract, false, recorder, random);
            var history = match.history();
            last = recorder.settle(history.get(history.size() - 1), difficulty);
            if (match.observe(0).phase() == RentzMatch.Phase.MATCH_END) return last;
            match.nextHand();
        }
        throw new AssertionError("Engine did not settle the full match");
    }

    private record Signature(Contract contract, Unit unit, int goal, BitSet witnesses) { }

    private static final class Corpus {
        private final List<Badge> badges;
        private final BitSet[] witnesses, contracts, tables;
        private int cursor, legalHandSamples;
        Corpus(List<Badge> badges) {
            this.badges = badges;
            witnesses = array(badges.size());
            contracts = array(badges.size());
            tables = array(badges.size());
        }
        private static BitSet[] array(int length) {
            var rows = new BitSet[length];
            Arrays.setAll(rows, ignored -> new BitSet());
            return rows;
        }
        void add(AchievementHand hand) {
            for (int i = 0; i < badges.size(); i++) {
                if (!badges.get(i).accepts(hand)) continue;
                witnesses[i].set(cursor);
                contracts[i].set(hand.result().contract().ordinal());
                tables[i].set(hand.players());
            }
            cursor++;
        }
        List<String> unreachable() {
            var missing = new ArrayList<String>();
            for (int i = 0; i < badges.size(); i++) {
                var badge = badges.get(i);
                int count = switch (badge.unit()) {
                    case TABLE -> tables[i].cardinality();
                    case CONTRACT -> contracts[i].cardinality();
                    default -> witnesses[i].cardinality();
                };
                if (count < badge.goal()) missing.add(badge.id() + " (" + count + "/" + badge.goal() + ")");
            }
            return missing;
        }
        List<String> duplicates() {
            var seen = new HashMap<Signature, String>();
            var duplicates = new ArrayList<String>();
            for (int i = 0; i < badges.size(); i++) {
                var badge = badges.get(i);
                var key = new Signature(badge.contract(), badge.unit(), badge.goal(), witnesses[i]);
                String previous = seen.putIfAbsent(key, badge.id());
                if (previous != null) duplicates.add(previous + " = " + badge.id());
            }
            return duplicates;
        }
    }

    private static List<Long> levels(List<Badge> badges, Contract contract) {
        return Arrays.stream(Level.values()).map(level -> badges.stream()
                .filter(badge -> badge.contract() == contract && badge.level() == level).count()).toList();
    }

    private static AchievementHand assisted(AchievementHand hand) {
        return new AchievementHand(hand.result(), false, false, true, false, false, false,
                hand.complete(), hand.difficulty(), hand.tactics());
    }

    private static AchievementHand hand(int players) {
        return AchievementBrowserComponentTest.sample(Contract.TRICKS, players, Difficulty.NORMAL, 2);
    }

    private static Badge badge(String id) {
        return AchievementCatalog.badges().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    private static String status(AchievementProgress progress, Badge badge) {
        var query = new Query("", badge.level(), badge.contract(), null, badge.mode());
        return new AchievementBrowser(COPY, progress, null).rows(query).stream()
                .filter(row -> badge.equals(row.badge())).findFirst().orElseThrow().subtitle();
    }
}
