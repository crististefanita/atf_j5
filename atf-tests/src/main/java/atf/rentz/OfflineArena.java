package atf.rentz;

import atf.rentz.arena.ArenaPolicies;
import atf.rentz.arena.FamilyAnalysis;
import atf.rentz.arena.PromotionAdmission;
import atf.rentz.evaluation.ClusteredEvaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import ro.rentzlab.ai.LabPolicy;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.ai.SearchPolicy;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.ScoreProfile;
import ro.rentzlab.core.deal.DealSeed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete CPU matches, paired distributions, rotated seats, two opponent families and curated
 * receipts.
 */
public final class OfflineArena {
    private static final ObjectMapper JSON =
            new ObjectMapper(
                            com.fasterxml.jackson.core.JsonFactory.builder()
                                    .enable(
                                            com.fasterxml.jackson.core.StreamReadFeature
                                                    .STRICT_DUPLICATE_DETECTION)
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public enum Kind {
        BASELINE,
        NORMAL,
        HARD,
        HARD_REFERENCE
    }

    public record Protocol(
            String id,
            String phase,
            int players,
            List<Long> seeds,
            List<Kind> opponents,
            Kind candidate,
            int worlds,
            int retainPerFamily,
            String policy) {
        public Protocol(
                String id,
                String phase,
                int players,
                List<Long> seeds,
                List<Kind> opponents,
                Kind candidate,
                int worlds,
                int retainPerFamily) {
            this(
                    id,
                    phase,
                    players,
                    seeds,
                    opponents,
                    candidate,
                    worlds,
                    retainPerFamily,
                    "rentz-policy.yaml");
        }

        public Protocol {
            policy = policy == null ? "rentz-policy.yaml" : policy;
            seeds = Card.immutable(seeds);
            opponents = Card.immutable(opponents);
            if (id == null
                    || !id.matches("[a-zA-Z0-9_-]+")
                    || !Set.of("DEVELOPMENT", "FRESH_EVALUATION").contains(phase)
                    || players < 3
                    || players > 6
                    || seeds.isEmpty()
                    || seeds.size() > 64
                    || opponents.isEmpty()
                    || opponents.size() > 3
                    || new HashSet<>(opponents).size() != opponents.size()
                    || candidate == null
                    || worlds < 1
                    || worlds > 128
                    || retainPerFamily < 0
                    || retainPerFamily > 6
                    || !policy.matches("rentz-[a-z-]+\\.yaml"))
                throw new IllegalArgumentException("Arena protocol");
            DealSeed.requireDistinct(seeds);
        }
    }

    public record Move(
            int number,
            int hand,
            int actor,
            String action,
            Object value,
            RentzMatch.Observation actorInput,
            SearchPolicy.Decision<?> decision) {}

    public record SeatWork(
            int seat,
            int searches,
            int requestedWorlds,
            int completedWorlds,
            long transitions,
            int fallbacks,
            int limitedSearches,
            long decisionNanos) {}

    public record CostSummary(
            long candidateTransitions,
            long controlTransitions,
            long candidateNanos,
            long controlNanos,
            int candidateFallbacks,
            int controlFallbacks,
            int candidateLimited,
            int controlLimited) {
        static CostSummary empty() {
            return new CostSummary(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record Episode(
            long seed,
            int candidateSeat,
            Kind family,
            Kind candidate,
            List<Integer> totals,
            List<RentzMatch.HandResult> history,
            List<Move> moves,
            long elapsedMillis,
            long maxDecisionNanos,
            int searchedDecisions,
            int exactShortcuts,
            List<SeatWork> work) {}

    public record Paired(
            long seed,
            int seat,
            Kind family,
            Kind candidate,
            double controlMargin,
            double candidateMargin,
            double delta,
            double winCredit,
            int rootScore,
            long elapsedMillis,
            long maxDecisionNanos,
            int searchedDecisions,
            int exactShortcuts,
            double controlWinCredit,
            int controlScore,
            SeatWork candidateWork,
            SeatWork controlWork) {}

    public record FamilySummary(
            Kind family,
            int pairedGames,
            int seedClusters,
            double meanDelta,
            double winCredit,
            double minimumSeedMean,
            double maximumSeedMean,
            double controlWinCredit,
            double meanPointsDelta,
            ClusteredEvaluation.Assessment assessment,
            CostSummary cost) {
        public FamilySummary(
                Kind family,
                int pairedGames,
                int seedClusters,
                double meanDelta,
                double winCredit,
                double minimumSeedMean,
                double maximumSeedMean,
                double controlWinCredit,
                double meanPointsDelta,
                ClusteredEvaluation.Assessment assessment) {
            this(
                    family,
                    pairedGames,
                    seedClusters,
                    meanDelta,
                    winCredit,
                    minimumSeedMean,
                    maximumSeedMean,
                    controlWinCredit,
                    meanPointsDelta,
                    assessment,
                    CostSummary.empty());
        }
    }

    public record Summary(
            String protocol,
            String phase,
            String sourceIdentity,
            String referenceParity,
            String state,
            List<FamilySummary> families,
            List<Paired> games,
            List<String> retained,
            String policyFile,
            PolicySettings candidateSettings,
            PolicySettings referenceSettings,
            Protocol declaredProtocol,
            ArenaPolicies effectivePolicies,
            ScoreProfile scoreProfile) {
        /** Historical summaries may still be read; missing declarations cannot be admitted. */
        public Summary(String protocol, String phase, String sourceIdentity, String referenceParity, String state,
                       List<FamilySummary> families, List<Paired> games, List<String> retained, String policyFile,
                       PolicySettings candidateSettings, PolicySettings referenceSettings) {
            this(protocol, phase, sourceIdentity, referenceParity, state, families, games, retained, policyFile,
                    candidateSettings, referenceSettings, null, null, null);
        }
        public Summary(String protocol, String phase, String sourceIdentity, String referenceParity, String state,
                       List<FamilySummary> families, List<Paired> games, List<String> retained, String policyFile,
                       PolicySettings candidateSettings, PolicySettings referenceSettings, Protocol declaredProtocol) {
            this(protocol, phase, sourceIdentity, referenceParity, state, families, games, retained, policyFile,
                    candidateSettings, referenceSettings, declaredProtocol, null, null);
        }
    }

    public static PolicySettings matchedReferenceSettings(PolicySettings candidate) {
        var frozen = PolicySettings.handMarginReference();
        return new PolicySettings(
                candidate.worlds(),
                candidate.riskWeight(),
                frozen.ownChainWeight(),
                frozen.releaseWeight(),
                frozen.skipThreatWeight(),
                PolicySettings.Objective.HAND_MARGIN,
                PolicySettings.Tactic.LEGACY,
                candidate.maxTransitions());
    }

    public static PolicySettings settingsFor(Protocol protocol, PolicySettings configured) {
        return new PolicySettings(protocol.worlds(), configured.riskWeight(), configured.ownChainWeight(),
                configured.releaseWeight(), configured.skipThreatWeight(), configured.objective(),
                configured.tactic(), configured.maxTransitions());
    }

    public static Summary run(
            Protocol protocol,
            ScoreProfile scoring,
            PolicySettings configured,
            Path output,
            Path registry,
            String sourceIdentity)
            throws IOException {
        if (Files.exists(output))
            throw new IllegalArgumentException("Fresh arena directory required");
        EvaluationRegistry.exposeForArena(
                registry,
                protocol.players(),
                protocol.seeds(),
                protocol.phase(),
                protocol.id(),
                sourceIdentity);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.createDirectory(output);
        JSON.writerWithDefaultPrettyPrinter()
                .writeValue(output.resolve("protocol.json").toFile(), protocol);
        var settings = settingsFor(protocol, configured);
        var policies = ArenaPolicies.resolve(protocol, settings);
        var pairs = new ArrayList<Paired>();
        var summaries = new ArrayList<FamilySummary>();
        var retained = new ArrayList<String>();
        for (Kind family : protocol.opponents()) {
            var matchup = policies.families().get(family);
            boolean uniform = matchup.control().equals(matchup.opponent());
            var candidates = new ArrayList<Episode>();
            var familyPairs = new ArrayList<Paired>();
            for (long seed : protocol.seeds()) {
                var uniformControl =
                        uniform
                                ? play(
                                        protocol.players(),
                                        seed,
                                        -1,
                                        matchup.control(),
                                        matchup.opponent(),
                                        scoring,
                                        false)
                                : null;
                for (int seat = 0; seat < protocol.players(); seat++) {
                    var control =
                            uniform ? uniformControl : play(
                                            protocol.players(),
                                            seed,
                                            seat,
                                            matchup.control(),
                                            matchup.opponent(),
                                            scoring,
                                            false);
                    var episode =
                            play(
                                    protocol.players(),
                                    seed,
                                    seat,
                                    policies.candidate(),
                                    matchup.opponent(),
                                    scoring,
                                    true);
                    double controlMargin = margin(control.totals(), seat),
                            candidateMargin = margin(episode.totals(), seat);
                    var paired =
                            new Paired(
                                    seed,
                                    seat,
                                    family,
                                    protocol.candidate(),
                                    controlMargin,
                                    candidateMargin,
                                    candidateMargin - controlMargin,
                                    winCredit(episode.totals(), seat),
                                    episode.totals().get(seat),
                                    episode.elapsedMillis(),
                                    episode.maxDecisionNanos(),
                                    episode.searchedDecisions(),
                                    episode.exactShortcuts(),
                                    winCredit(control.totals(), seat),
                                    control.totals().get(seat),
                                    episode.work().get(seat),
                                    control.work().get(seat));
                    pairs.add(paired);
                    familyPairs.add(paired);
                    candidates.add(episode);
                    System.out.println(
                            "arena="
                                    + protocol.id()
                                    + " family="
                                    + family
                                    + " seed="
                                    + seed
                                    + " seat="
                                    + seat
                                    + " delta="
                                    + paired.delta());
                }
            }
            summaries.add(
                    FamilyAnalysis.summarize(
                            family,
                            familyPairs,
                            protocol.seeds(),
                            protocol.players(),
                            policies.metric(),
                            protocol.opponents().size()));
            var selected = FamilyAnalysis.retained(familyPairs, protocol.retainPerFamily());
            for (var pair : selected) {
                var episode =
                        candidates.stream()
                                .filter(
                                        e ->
                                                e.seed() == pair.seed()
                                                        && e.candidateSeat() == pair.seat())
                                .findFirst()
                                .orElseThrow();
                String name = FamilyAnalysis.fileName(pair);
                JSON.writerWithDefaultPrettyPrinter()
                        .writeValue(
                                output.resolve(name).toFile(),
                                Map.of(
                                        "sourceIdentity",
                                        sourceIdentity,
                                        "scoreProfile",
                                        scoring,
                                        "kind",
                                        "OFFLINE_LABORATORY_GAME_NOT_UI",
                                        "paired",
                                        pair,
                                        "effectivePolicies",
                                        Map.of("candidate", policies.candidate(), "matchup", matchup,
                                                "evaluationObjective", policies.evaluationObjective()),
                                        "episode",
                                        episode));
                retained.add(name);
            }
        }
        var summary =
                new Summary(
                        protocol.id(),
                        protocol.phase(),
                        sourceIdentity,
                        "UNCONFIRMED_LABORATORY_SCORING",
                        "MEASURED_NOT_GLOBAL_OPTIMALITY",
                        Card.immutable(summaries),
                        Card.immutable(pairs),
                        Card.immutable(retained),
                        protocol.policy(),
                        settings,
                        matchedReferenceSettings(settings), protocol, policies, scoring);
        JSON.writerWithDefaultPrettyPrinter()
                .writeValue(output.resolve("summary.json").toFile(), summary);
        return summary;
    }

    public static Episode play(
            int players,
            long seed,
            int candidateSeat,
            Kind candidate,
            Kind family,
            ScoreProfile scoring,
            PolicySettings settings,
            boolean retainMoves) {
        return play(players, seed, candidateSeat, ArenaPolicies.Actor.forKind(candidate, settings),
                ArenaPolicies.Actor.forKind(family, settings), scoring, retainMoves);
    }

    private static Episode play(int players, long seed, int candidateSeat, ArenaPolicies.Actor candidate,
                                ArenaPolicies.Actor opponent, ScoreProfile scoring, boolean retainMoves) {
        long started = System.nanoTime(), maxNanos = 0;
        int searches = 0, shortcuts = 0, actions = 0;
        var game = new RentzMatch(players, seed, scoring);
        var opponentPlayer = opponent.player(scoring);
        var candidatePlayer = candidate.equals(opponent) ? opponentPlayer : candidate.player(scoring);
        var moves = new ArrayList<Move>();
        var work = new long[players][7];
        while (game.observe(0).phase() != RentzMatch.Phase.MATCH_END) {
            var publicView = game.observe(0);
            int actor = publicView.turn();
            RentzPlayer player = actor == candidateSeat ? candidatePlayer : opponentPlayer;
            if (publicView.phase() == RentzMatch.Phase.DEAL_END) {
                game.nextHand();
                continue;
            }
            if (publicView.phase() == RentzMatch.Phase.CHOOSE) game.reveal(actor);
            var own = game.observe(actor);
            Object selected = null;
            SearchPolicy.Decision<?> receipt = null;
            String action;
            long t = System.nanoTime(), elapsed = 0;
            if (own.phase() == RentzMatch.Phase.CHOOSE) {
                var decision =
                        player == null
                                ? new SearchPolicy.Decision<>(
                                        LabPolicy.choose(own), 0, "BASELINE", List.of())
                                : player.choose(own);
                elapsed = System.nanoTime() - t;
                receipt = decision;
                selected = decision.selected();
                action = "CHOOSE";
                game.choose(actor, decision.selected(), false);
            } else if (own.legal().isEmpty()) {
                action = "PASS";
                game.pass(actor);
                OfflineGameAudit.pass(own, game.observe(actor));
            } else {
                var decision =
                        player == null
                                ? new SearchPolicy.Decision<>(
                                        LabPolicy.card(own), 0, "BASELINE", List.of())
                                : player.card(own);
                elapsed = System.nanoTime() - t;
                receipt = decision;
                selected = decision.selected();
                action = "PLAY";
                game.play(actor, decision.selected());
                OfflineGameAudit.card(own, decision.selected(), game.observe(actor), scoring);
            }
            if (receipt != null) {
                var measured = receipt.work();
                work[actor][0] += measured.requestedWorlds() > 0 ? 1 : 0;
                work[actor][1] += measured.requestedWorlds();
                work[actor][2] += measured.completedWorlds();
                work[actor][3] += measured.transitions();
                work[actor][4] +=
                        receipt.method().equals("PUBLIC_TACTICAL_BUDGET_FALLBACK") ? 1 : 0;
                work[actor][5] += measured.exhausted() ? 1 : 0;
                work[actor][6] += elapsed;
            }
            if (actor == candidateSeat) {
                maxNanos = Math.max(maxNanos, elapsed);
                if (receipt != null && receipt.worlds() > 0) searches++;
                else if (receipt != null
                        && (receipt.method().equals("PUBLIC_ACE_EXTRA_TURN")
                                || receipt.method().equals("PUBLIC_TEN_MASTER_CAPTURE")
                                || receipt.method().equals("FORCED_LEGAL_CARD"))) shortcuts++;
            }
            if (retainMoves)
                moves.add(
                        new Move(
                                actions + 1,
                                own.handNumber(),
                                actor,
                                action,
                                selected,
                                actor == candidateSeat ? own : null,
                                actor == candidateSeat ? receipt : null));
            if (++actions > 20_000)
                throw new IllegalStateException("Offline match did not terminate");
        }
        validateHistory(game, players);
        var measurements = new ArrayList<SeatWork>();
        for (int seat = 0; seat < players; seat++)
            measurements.add(
                    new SeatWork(
                            seat,
                            (int) work[seat][0],
                            (int) work[seat][1],
                            (int) work[seat][2],
                            work[seat][3],
                            (int) work[seat][4],
                            (int) work[seat][5],
                            work[seat][6]));
        return new Episode(
                seed,
                candidateSeat,
                opponent.kind(),
                candidate.kind(),
                game.observe(0).totals(),
                game.history(),
                Card.immutable(moves),
                (System.nanoTime() - started) / 1_000_000,
                maxNanos,
                searches,
                shortcuts,
                Card.immutable(measurements));
    }

    private static void validateHistory(RentzMatch game, int players) {
        if (game.history().size() != players * 8)
            throw new IllegalStateException("Offline schedule size");
        var selected = new ArrayList<Set<Contract>>();
        for (int seat = 0; seat < players; seat++) selected.add(EnumSet.noneOf(Contract.class));
        int[] totals = new int[players];
        for (var hand : game.history()) {
            if (hand.chooser() != (hand.number() - 1) % players
                    || !selected.get(hand.chooser()).add(hand.contract()))
                throw new IllegalStateException("Chooser schedule");
            for (int seat = 0; seat < players; seat++) {
                totals[seat] += hand.points().get(seat);
                if (totals[seat] != hand.totals().get(seat))
                    throw new IllegalStateException("Cumulative score");
            }
        }
        for (int seat = 0; seat < players; seat++)
            if (!selected.get(seat).equals(EnumSet.allOf(Contract.class))
                    || totals[seat] != game.observe(0).totals().get(seat))
                throw new IllegalStateException("Complete match audit");
    }

    private static double margin(List<Integer> totals, int seat) {
        return totals.get(seat)
                - (totals.stream().mapToDouble(Integer::doubleValue).sum() - totals.get(seat))
                        / (totals.size() - 1);
    }

    private static double winCredit(List<Integer> totals, int seat) {
        int max = Collections.max(totals);
        return totals.get(seat) == max ? 1.0 / totals.stream().filter(n -> n == max).count() : 0;
    }

    public static boolean promotable(Summary result, Protocol protocol, PromotionAdmission.Expected expected) {
        return PromotionAdmission.inspect(result, protocol, expected).promotes();
    }

    public static Protocol readProtocol(Path path) throws IOException {
        var root = JSON.readTree(path.toFile());
        if (!root.isObject()) throw new IllegalArgumentException("Protocol object required");
        var names = new HashSet<String>();
        root.fieldNames().forEachRemaining(names::add);
        var required =
                Set.of(
                        "id",
                        "phase",
                        "players",
                        "seeds",
                        "opponents",
                        "candidate",
                        "worlds",
                        "retainPerFamily");
        if (!names.containsAll(required)
                || names.stream().anyMatch(n -> !required.contains(n) && !n.equals("policy")))
            throw new IllegalArgumentException("Unknown or missing protocol fields");
        for (String field : List.of("id", "phase", "candidate"))
            if (!root.required(field).isTextual())
                throw new IllegalArgumentException("Text protocol field required");
        for (String field : List.of("players", "worlds", "retainPerFamily"))
            if (!root.required(field).isIntegralNumber() || !root.required(field).canConvertToInt())
                throw new IllegalArgumentException("Integer protocol field required");
        if (!root.required("seeds").isArray()
                || !root.required("opponents").isArray()
                || root.has("policy") && !root.get("policy").isTextual())
            throw new IllegalArgumentException("Protocol arrays or policy");
        for (var seed : root.get("seeds"))
            if (!seed.isIntegralNumber() || !seed.canConvertToLong())
                throw new IllegalArgumentException("Exact long seed required");
        for (var kind : root.get("opponents"))
            if (!kind.isTextual()) throw new IllegalArgumentException("Opponent name required");
        return JSON.treeToValue(root, Protocol.class);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 6 && args[0].equals("initialize-registry")) {
            Path registry = EvaluationRegistry.installationRegistry(Path.of(args[1]).toRealPath(), Path.of(args[2]));
            EvaluationRegistry.initialize(registry, Path.of(args[3]), args[4], args[5]);
            System.out.println(registry);
            return;
        }
        if (args.length != 5)
            throw new IllegalArgumentException("project protocol output registry source-manifest");
        Path project = Path.of(args[0]).toRealPath(),
                output = Path.of(args[2]).toAbsolutePath().normalize();
        if (output.startsWith(project))
            throw new IllegalArgumentException("Arena artifacts belong outside the project");
        Path registry = EvaluationRegistry.installationRegistry(project, Path.of(args[3]));
        var protocol = readProtocol(Path.of(args[1]));
        var identity = JSON.readTree(Path.of(args[4]).toFile());
        if (!identity.isArray() || identity.isEmpty())
            throw new IllegalArgumentException("Frozen input manifest required");
        var digest = MessageDigest.getInstance("SHA-256");
        Path scoreProfilePath = project.resolve("config/rentz-score-profiles.yaml");
        boolean verifiedScoring = false;
        for (var row : identity) {
            Path file = project.resolve(row.required("path").textValue()).normalize();
            if (!file.startsWith(project) || !Files.isRegularFile(file))
                throw new IllegalArgumentException("Frozen input path");
            String actual =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(Files.readAllBytes(file)));
            if (!actual.equalsIgnoreCase(row.required("sha256").textValue()))
                throw new IllegalStateException("Frozen source drift: " + file);
            verifiedScoring |= file.equals(scoreProfilePath);
            digest.update(row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if (!verifiedScoring) throw new IllegalArgumentException("Frozen score profile required in source manifest");
        String sourceIdentity = HexFormat.of().formatHex(digest.digest());
        var configured = PolicyInputs.read(project.resolve("config").resolve(protocol.policy()), project);
        var scoring = RuleProfiles.read(scoreProfilePath);
        var result =
                run(
                        protocol,
                        scoring,
                        configured,
                        output,
                        registry,
                        sourceIdentity);
        var admission = PromotionAdmission.inspect(result, protocol, new PromotionAdmission.Expected(sourceIdentity, configured, scoring));
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("admission.json").toFile(), admission);
        if (!admission.coherent()) throw new IllegalStateException("Incoherent arena evidence: " + admission.findings());
        OfflineArenaReport.publish(result, protocol, output.resolve("arena-extent.html"));
        System.out.println(JSON.writeValueAsString(result.families()));
        if (protocol.phase().equals("FRESH_EVALUATION") && !admission.promotes())
            throw new IllegalStateException(
                    "Fresh evidence does not qualify promotion: objective-specific clustered lower"
                        + " bound and both Normal/reference families are required");
    }
}
