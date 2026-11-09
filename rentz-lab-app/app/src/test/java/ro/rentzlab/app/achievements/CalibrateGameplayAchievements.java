package ro.rentzlab.app.achievements;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.ai.RentzPlayer.Difficulty;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.GameSession.Action;
import ro.rentzlab.app.GameSession.Command;
import ro.rentzlab.app.GameSession.Origin;
import ro.rentzlab.app.profile.LocalProfile;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

/** Offline acceptance of the exact generated Android catalog on completed matches. */
public final class CalibrateGameplayAchievements {
    enum Mode { PRODUCT, MIXED }
    record Run(String stage, Mode mode, Difficulty difficulty, long firstSeed, int matchesPerTable) { }
    record Plan(int schemaVersion, List<Integer> seatCounts, List<Integer> campaigns, List<Run> runs, HardSimulation hardSimulation) { }
    record HardSimulation(int worlds, int maxTransitions) { }
    record Cell(Run run, int players, List<Match> matches) { }
    record Match(long seed, List<AchievementHand> hands, int rank, int decisions, List<Integer> totals) { }
    record Ledger(long seed, int hands, int decisions, int rank, List<Integer> totals,
            Map<String, Integer> counts, Map<String, Unlock> unlocks) { }
    record Unlock(int match, int hand, int totalHand, Contract contract) { }
    record Campaign(long firstSeed, int length, Map<String, Unlock> unlocks) { }
    record CellReport(String stage, Mode mode, Difficulty difficulty, int players, int games, int hands,
            List<Ledger> matches, List<Campaign> campaigns) { }
    record Rate(String stage, Mode mode, Difficulty difficulty, int players, int window,
            int opportunities, int successes, int medianUnlockHand, int eligibleMatches) { }
    record BadgeReport(String id, String level, int goal, String title, List<Rate> rates) { }
    record Summary(int schemaVersion, int games, int hands, int decisions, Plan plan, List<CellReport> cells, List<BadgeReport> achievements) { }
    private static final LocalProfile PROFILE = new LocalProfile(new UUID(0, 42), ZoneId.of("UTC"));
    private static final Instant FIRST = Instant.parse("2026-11-09T00:00:00Z");

    public static void main(String[] args) throws Exception {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var document = JsonParser.parseString(new String(Files.readAllBytes(Path.of(args[0])), StandardCharsets.UTF_8)).getAsJsonObject();
        if (!document.keySet().equals(java.util.Set.of("schemaVersion", "seatCounts", "campaigns", "runs", "hardSimulation")))
            throw new IllegalArgumentException("Unexpected calibration plan fields");
        var plan = gson.fromJson(document, Plan.class);
        var cells = execute(plan);
        var details = new ArrayList<CellReport>();
        var badgeRates = new LinkedHashMap<String, List<Rate>>();
        int games = 0, hands = 0, decisions = 0;
        for (var cell : cells) {
            var campaigns = new ArrayList<Campaign>();
            var ledgers = new ArrayList<Ledger>();
            int cellHands = 0;
            for (var match : cell.matches()) {
                var counts = new LinkedHashMap<String, Integer>();
                var unlocks = new LinkedHashMap<String, Unlock>();
                var session = apply(AchievementProgress.empty(PROFILE), match, cell.players(), 1, unlocks, 0);
                for (var badge : GameplayAchievementCatalog.badges()) counts.put(badge.id(), session.count(badge));
                ledgers.add(new Ledger(match.seed(), match.hands().size(), match.decisions(), match.rank(),
                        match.totals(), counts, unlocks));
                cellHands += match.hands().size();
                decisions += match.decisions();
            }
            for (int length : plan.campaigns()) {
                if (length == 1) {
                    for (var match : ledgers) campaigns.add(new Campaign(match.seed(), 1, match.unlocks()));
                } else {
                    for (int start = 0; start + length <= cell.matches().size(); start += length)
                        campaigns.add(campaign(cell, start, length));
                }
            }
            for (var badge : GameplayAchievementCatalog.badges()) {
                if (!GameplayAchievementCatalog.supportsTable(badge, cell.players(), cell.run().difficulty())) continue;
                var rates = badgeRates.computeIfAbsent(badge.id(), ignored -> new ArrayList<>());
                for (int length : plan.campaigns()) rates.add(rate(cell, badge, length, campaigns));
            }
            games += cell.matches().size();
            hands += cellHands;
            details.add(new CellReport(cell.run().stage(), cell.run().mode(), cell.run().difficulty(),
                    cell.players(), cell.matches().size(), cellHands, ledgers,
                    campaigns.stream().filter(c -> c.length() > 1).toList()));
        }
        var reports = GameplayAchievementCatalog.badges().stream()
                .map(b -> new BadgeReport(b.id(), b.level().name(), b.goal(), b.romanian(), badgeRates.getOrDefault(b.id(), List.of())))
                .toList();
        var summary = new Summary(1, games, hands, decisions, plan, details, reports);
        Files.write(Path.of(args[1]), gson.toJson(summary).getBytes(StandardCharsets.UTF_8));
        System.out.println("Completed " + games + " matches, " + hands + " hands and " + decisions + " accepted decisions");
    }

    private static List<Cell> execute(Plan plan) throws Exception {
        if (plan.schemaVersion() != 1 || !plan.seatCounts().equals(List.of(3, 4, 5, 6))
                || !plan.campaigns().equals(List.of(1, 3, 8)) || plan.runs().size() != 8
                || plan.hardSimulation().worlds() < 1 || plan.hardSimulation().maxTransitions() < 1)
            throw new IllegalArgumentException("Invalid simulation plan");
        var seeds = new HashSet<Long>();
        var groups = new HashSet<String>();
        for (var run : plan.runs()) {
            if (!List.of("calibration", "holdout").contains(run.stage()) || run.mode() == null
                    || run.difficulty() == null || run.matchesPerTable() < 8
                    || !groups.add(run.stage() + "/" + run.mode() + "/" + run.difficulty()))
                throw new IllegalArgumentException("Invalid or duplicate calibration cohort");
            for (int players : plan.seatCounts())
                for (int i = 0; i < run.matchesPerTable(); i++)
                    if (!seeds.add(seed(run, players, i))) throw new IllegalArgumentException("Overlapping match seeds");
        }
        int workers = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        var executor = Executors.newFixedThreadPool(workers);
        try {
            var futures = new ArrayList<Future<Cell>>();
            for (var run : plan.runs()) {
                for (int players : plan.seatCounts()) {
                    futures.add(executor.submit(() -> {
                        var matches = new ArrayList<Match>();
                        for (int i = 0; i < run.matchesPerTable(); i++)
                            matches.add(play(run, players, seed(run, players, i), plan.hardSimulation()));
                        System.out.println(run.stage() + " " + run.mode() + " " + run.difficulty() + " " + players
                                + " seats passed " + matches.size() + " full matches");
                        return new Cell(run, players, matches);
                    }));
                }
            }
            var cells = new ArrayList<Cell>();
            for (var future : futures) cells.add(future.get());
            return cells;
        } finally {
            executor.shutdownNow();
        }
    }

    private static long seed(Run run, int players, int i) { return run.firstSeed() + (players - 3) * 100L + i; }

    private static Match play(Run run, int players, long seed, HardSimulation hard) {
        var scoring = GameSession.scoringProfile();
        int chooser = run.mode() == Mode.PRODUCT ? 0 : (int) Math.floorMod(seed, players);
        var match = new RentzMatch(players, seed, scoring, chooser);
        var recorder = new AchievementHand.Recorder(players);
        var product = PolicySettings.defaults();
        var normal = new RentzPlayer(Difficulty.NORMAL, scoring, product);
        var hardSettings = new PolicySettings(hard.worlds(), product.riskWeight(), product.ownChainWeight(),
                product.releaseWeight(), product.skipThreatWeight(), product.objective(), product.tactic(),
                hard.maxTransitions());
        var bounded = new RentzPlayer(Difficulty.HARD, scoring, hardSettings);
        var random = new Random(seed ^ 0x428b42ffL);
        var hands = new ArrayList<AchievementHand>();
        int decisions = 0;
        for (int i = 0; i < players * Contract.values().length; i++) {
            int actor = match.observe(0).turn();
            var mode = behavior(run.mode(), run.difficulty(), players, seed, actor);
            var origin = actor == 0 ? Origin.MANUAL : Origin.AUTOMATIC;
            match.reveal(actor);
            var choiceView = match.observe(actor);
            var chosen = switch (mode) {
                case RANDOM -> choiceView.available().stream().sorted().toList().get(random.nextInt(choiceView.available().size()));
                case NORMAL -> normal.choose(choiceView).selected();
                case BOUNDED_HARD -> bounded.choose(choiceView).selected();
            };
            match.choose(actor, chosen, false);
            recorder.observe(new Command(Action.CHOOSE, actor, null, chosen, false, origin));
            decisions++;
            while (match.observe(0).phase() == RentzMatch.Phase.PLAY) {
                actor = match.observe(0).turn();
                var view = match.observe(actor);
                if (view.legal().isEmpty()) {
                    match.pass(actor);
                    recorder.observe(new Command(Action.PASS, actor, null, null, false, Origin.AUTOMATIC));
                } else {
                    var card = select(view, behavior(run.mode(), run.difficulty(), players, seed, actor), normal, bounded, random);
                    match.play(actor, card);
                    recorder.observe(new Command(Action.PLAY, actor, card, null, false,
                            actor == 0 ? Origin.MANUAL : Origin.AUTOMATIC));
                }
                if (++decisions > players * 8 * (players * 10 + 1))
                    throw new IllegalStateException("Unbounded match " + seed);
            }
            var history = match.history();
            hands.add(recorder.settle(history.get(history.size() - 1), run.difficulty()));
            if (i + 1 < players * 8) {
                match.nextHand();
                recorder.observe(new Command(Action.NEXT_HAND, 0, null, null, false, Origin.UNKNOWN));
            }
        }
        var terminal = hands.get(hands.size() - 1);
        if (match.observe(0).phase() != RentzMatch.Phase.MATCH_END || !terminal.manualMatch())
            throw new IllegalStateException("No confirmed manual match finish for " + seed);
        var totals = terminal.result().totals();
        int rank = 1 + (int) totals.stream().filter(score -> score > totals.get(0)).count();
        return new Match(seed, hands, rank, decisions, totals);
    }

    enum Behavior { NORMAL, BOUNDED_HARD, RANDOM }

    private static Behavior behavior(Mode mode, Difficulty difficulty, int seats, long seed, int actor) {
        if (mode == Mode.PRODUCT) return difficulty == Difficulty.HARD && actor != 0 ? Behavior.BOUNDED_HARD : Behavior.NORMAL;
        int role = (int) Math.floorMod(seed + actor, seats);
        if (role == 0) return Behavior.RANDOM;
        return difficulty == Difficulty.HARD && role % 2 == 0 ? Behavior.BOUNDED_HARD : Behavior.NORMAL;
    }

    private static Card select(RentzMatch.Observation view, Behavior mode, RentzPlayer normal, RentzPlayer bounded, Random random) {
        return switch (mode) {
            case RANDOM -> view.legal().get(random.nextInt(view.legal().size()));
            case NORMAL -> normal.card(view).selected();
            case BOUNDED_HARD -> bounded.card(view).selected();
        };
    }

    private static Campaign campaign(Cell cell, int start, int length) {
        var progress = AchievementProgress.empty(PROFILE);
        var unlocks = new LinkedHashMap<String, Unlock>();
        int offset = 0;
        for (int i = 0; i < length; i++) {
            var match = cell.matches().get(start + i);
            progress = apply(progress, match, cell.players(), i + 1, unlocks, offset);
            offset += match.hands().size();
        }
        return new Campaign(cell.matches().get(start).seed(), length, unlocks);
    }

    private static Rate rate(Cell cell, AchievementCatalog.Badge badge, int length, List<Campaign> campaigns) {
        var unlockHands = new ArrayList<Integer>();
        int eligibleMatches = 0;
        for (var match : cell.matches())
            if (match.hands().stream().anyMatch(badge::accepts)) eligibleMatches++;
        int windows = 0;
        for (var campaign : campaigns) {
            if (campaign.length() != length) continue;
            windows++;
            var unlock = campaign.unlocks().get(badge.id());
            if (unlock != null) unlockHands.add(unlock.totalHand());
        }
        unlockHands.sort(Integer::compareTo);
        int median = unlockHands.isEmpty() ? 0 : unlockHands.get((unlockHands.size() - 1) / 2);
        return new Rate(cell.run().stage(), cell.run().mode(), cell.run().difficulty(), cell.players(), length,
                windows, unlockHands.size(), median, eligibleMatches);
    }

    private static AchievementProgress apply(AchievementProgress progress, Match match, int players, int session,
            Map<String, Unlock> unlocks, int offset) {
        int index = 0;
        for (var hand : match.hands()) {
            index++;
            var at = FIRST.plusSeconds((session - 1) * 86_400L + index * 20L);
            progress = progress.recordGameplay(players, match.seed(), hand, at);
            for (var badge : GameplayAchievementCatalog.badges())
                if (!unlocks.containsKey(badge.id()) && progress.count(badge) >= badge.goal())
                    unlocks.put(badge.id(), new Unlock(session, index, offset + index, hand.result().contract()));
        }
        return progress;
    }

}
