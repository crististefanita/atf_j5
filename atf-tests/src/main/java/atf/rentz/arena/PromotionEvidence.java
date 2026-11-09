package atf.rentz.arena;

import atf.rentz.OfflineArena.Kind;
import atf.rentz.OfflineArena.Paired;
import atf.rentz.OfflineArena.Protocol;
import atf.rentz.OfflineArena.SeatWork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.core.ScoreProfile;

/** Complete finite paired population; aggregate claims are checked separately. */
final class PromotionEvidence {
    private record Key(Kind family, long seed, int seat) { }
    private record ScoreBounds(int quantum, long minimum, long seatMaximum, long opponentsMaximum) {
        static ScoreBounds forMatch(ScoreProfile profile, int players) {
            long penalties = 8L * profile.trickPenalty() + 4L * profile.queenPenalty()
                    + 2L * players * profile.diamondPenalty() + profile.kingPenalty();
            long rewards = 8L * profile.trickReward() + profile.tenReward();
            var rentz = profile.finishRewards().subList(0, players).stream()
                    .sorted(java.util.Comparator.reverseOrder()).toList();
            long opponentsAwards = rentz.stream().limit(players - 1L).mapToLong(Integer::longValue).sum();
            // Each chooser has every contract. Totals repeats the four penalties, and blind can double a deal.
            long doubled = 2L * players;
            return new ScoreBounds(scoreQuantum(profile, players), -2L * doubled * penalties,
                    doubled * (rewards + rentz.getFirst()), doubled * (rewards + opponentsAwards));
        }
    }
    private PromotionEvidence() { }
    static Map<Kind, List<Paired>> population(List<Paired> games, Protocol protocol, ArenaPolicies policies,
                                               ScoreProfile scoring) {
        require(games != null && games.size() == protocol.opponents().size() * protocol.seeds().size() * protocol.players(),
                "exact paired population required");
        var result = new LinkedHashMap<Kind, List<Paired>>();
        protocol.opponents().forEach(kind -> result.put(kind, new ArrayList<>()));
        var keys = new HashSet<Key>();
        var seeds = new HashSet<>(protocol.seeds());
        int players = protocol.players();
        scoring.assertMatchSafe(players);
        var bounds = ScoreBounds.forMatch(scoring, players);
        long transitions = 0, nanos = 0;
        int fallbacks = 0, limited = 0;
        for (var game : games) {
            require(game != null && result.containsKey(game.family()) && game.candidate() == protocol.candidate()
                    && seeds.contains(game.seed()) && game.seat() >= 0 && game.seat() < protocol.players(),
                    "game identity differs from declared protocol");
            require(keys.add(new Key(game.family(), game.seed(), game.seat())), "duplicate rotation");
            require(Double.isFinite(game.controlMargin()) && Double.isFinite(game.candidateMargin())
                    && Double.isFinite(game.delta()) && Double.compare(game.delta(), game.candidateMargin() - game.controlMargin()) == 0,
                    "finite margins and independently consistent delta required");
            require(scorePossible(game.rootScore(), game.candidateMargin(), game.winCredit(), players, bounds),
                    "candidate score or opponent sum contradicts the trusted score profile");
            require(scorePossible(game.controlScore(), game.controlMargin(), game.controlWinCredit(), players, bounds),
                    "control score or opponent sum contradicts the trusted score profile");
            require(credit(game.rootScore(), game.candidateMargin(), game.winCredit(), players, bounds.quantum()),
                    "candidate win credit contradicts the score margin or table size");
            require(credit(game.controlScore(), game.controlMargin(), game.controlWinCredit(), players, bounds.quantum()),
                    "control win credit contradicts the score margin or table size");
            require(game.elapsedMillis() >= 0 && game.maxDecisionNanos() >= 0
                    && game.searchedDecisions() >= 0 && game.exactShortcuts() >= 0, "negative game measurements");
            work(game.candidateWork(), game.seat(), policies.candidate().search());
            work(game.controlWork(), game.seat(), policies.families().get(game.family()).control().search());
            require(game.searchedDecisions() <= game.candidateWork().searches()
                    && game.maxDecisionNanos() <= game.candidateWork().decisionNanos(), "game measurements contradict seat work");
            for (var work : List.of(game.candidateWork(), game.controlWork())) {
                transitions = Math.addExact(transitions, work.transitions());
                nanos = Math.addExact(nanos, work.decisionNanos());
                fallbacks = Math.addExact(fallbacks, work.fallbacks());
                limited = Math.addExact(limited, work.limitedSearches());
            }
            result.get(game.family()).add(game);
        }
        return result;
    }
    private static int scoreQuantum(ScoreProfile profile, int players) {
        int result = 0;
        for (int points : new int[] {profile.trickPenalty(), profile.queenPenalty(), profile.diamondPenalty(),
                profile.kingPenalty(), profile.trickReward(), profile.tenReward()}) {
            result = gcd(result, points);
        }
        for (int place = 0; place < players; place++) {
            result = gcd(result, profile.finishRewards().get(place));
        }
        return result;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }

    private static boolean scorePossible(int score, double margin, double winCredit, int players, ScoreBounds bounds) {
        int quantum = bounds.quantum();
        if (quantum == 0) return score == 0 && margin == 0 && winCredit == 1.0 / players;
        int opponents = players - 1;
        long sum = opponentSum(score, margin, opponents);
        // Round to recover the integer sum, then reproduce the producer's floating-point margin.
        return score >= bounds.minimum() && score <= bounds.seatMaximum()
                && sum >= bounds.minimum() && sum <= bounds.opponentsMaximum()
                && score % quantum == 0 && sum % quantum == 0
                && margin == score - (double) sum / opponents;
    }

    private static long opponentSum(int score, double margin, int opponents) {
        return Math.round((score - margin) * opponents);
    }

    private static boolean credit(int score, double margin, double value, int players, int quantum) {
        if (value == 0) return true;
        if (margin < 0) return false;
        if (margin == 0) return value == 1.0 / players;
        int opponents = players - 1;
        long deficit = (long) score * opponents - opponentSum(score, margin, opponents);
        for (int leaders = 1; leaders < players; leaders++) {
            if (value == 1.0 / leaders)
                return deficit >= (long) (players - leaders) * quantum;
        }
        return false;
    }
    private static void work(SeatWork work, int seat, PolicySettings settings) {
        require(work != null && work.seat() == seat && work.searches() >= 0 && work.requestedWorlds() >= 0
                && work.completedWorlds() >= 0 && work.completedWorlds() <= work.requestedWorlds()
                && work.transitions() >= 0 && work.fallbacks() >= 0 && work.limitedSearches() >= 0
                && work.limitedSearches() <= work.searches() && work.fallbacks() <= work.searches()
                && work.decisionNanos() >= 0, "invalid seat work");
        if (settings == null) {
            require(work.searches() == 0 && work.requestedWorlds() == 0 && work.transitions() == 0,
                    "non-search policy reports search work");
        } else {
            require(work.requestedWorlds() == Math.multiplyExact(work.searches(), settings.worlds())
                    && work.transitions() <= Math.multiplyExact((long) work.searches(), settings.maxTransitions()),
                    "work exceeds or contradicts declared search budget");
        }
    }
    static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException("ARENA-ADMISSION: " + reason);
    }
}
