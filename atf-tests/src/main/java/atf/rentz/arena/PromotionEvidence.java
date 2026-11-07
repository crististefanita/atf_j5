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

/** Complete finite paired population; aggregate claims are checked separately. */
final class PromotionEvidence {
    private record Key(Kind family, long seed, int seat) { }
    private PromotionEvidence() { }
    static Map<Kind, List<Paired>> population(List<Paired> games, Protocol protocol, ArenaPolicies policies) {
        require(games != null && games.size() == protocol.opponents().size() * protocol.seeds().size() * protocol.players(),
                "exact paired population required");
        var result = new LinkedHashMap<Kind, List<Paired>>();
        protocol.opponents().forEach(kind -> result.put(kind, new ArrayList<>()));
        var keys = new HashSet<Key>();
        var seeds = new HashSet<>(protocol.seeds());
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
            require(credit(game.winCredit()) && credit(game.controlWinCredit()), "win credit outside [0,1]");
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
    private static boolean credit(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
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
