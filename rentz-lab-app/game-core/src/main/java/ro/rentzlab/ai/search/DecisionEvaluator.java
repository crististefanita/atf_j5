package ro.rentzlab.ai.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import ro.rentzlab.ai.FullMatchRollout;
import ro.rentzlab.ai.KnowledgeSampler;
import ro.rentzlab.ai.MatchUtility;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.ai.SearchPolicy;
import ro.rentzlab.ai.TacticalPolicy;
import ro.rentzlab.ai.tactics.CaptureTactics;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.OpeningDeal;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.ScoreProfile;

/** Whole common-world batches commit atomically; an exhausted prefix cannot favor early alternatives. */
public final class DecisionEvaluator {
    private static final int MAX_HAND_ACTIONS = 512;

    private DecisionEvaluator() { }

    public static <T> SearchPolicy.Decision<T> evaluate(RentzMatch.Observation view, List<T> options,
                                                       boolean choosing, ScoreProfile scoring, PolicySettings settings) {
        var sampler = new KnowledgeSampler(view);
        if (!choosing && settings.tactic() == PolicySettings.Tactic.PUBLIC_CAPTURE_AWARE
                && (view.contract() == Contract.WHIST || view.contract() == Contract.TEN_CLUBS)) {
            Card capture = CaptureTactics.cheapestSafeCapture(view, sampler);
            if (capture != null) {
                @SuppressWarnings("unchecked")
                T preferred = (T) capture;
                if (view.contract() == Contract.TEN_CLUBS) {
                    return new SearchPolicy.Decision<>(preferred, 0, "PUBLIC_TEN_SAFE_CAPTURE", Collections.emptyList());
                }
                options = new ArrayList<>(options);
                options.remove(preferred);
                options.add(0, preferred);
            }
        }
        var random = new Random(KnowledgeSampler.fingerprint(view));
        var budget = new RolloutBudget(settings.maxTransitions());
        var objective = settings.objective();
        boolean lastHandPolicy = objective == PolicySettings.Objective.MATCH_WIN_LAST_HAND;
        boolean provenWinPolicy = objective == PolicySettings.Objective.MATCH_WIN_PROVEN;
        int remainingHands = view.players() * Contract.values().length - view.handNumber() + 1;
        if (lastHandPolicy) {
            objective = remainingHands == 1
                    ? PolicySettings.Objective.MATCH_WIN_SHARE : PolicySettings.Objective.HAND_MARGIN;
        }
        if (provenWinPolicy) objective = PolicySettings.Objective.HAND_MARGIN;
        boolean full = objective != PolicySettings.Objective.HAND_MARGIN;
        var exactWorlds = Collections.<OpeningDeal>emptyList();
        if (!choosing && settings.tactic() == PolicySettings.Tactic.PUBLIC_CAPTURE_AWARE && (!full || remainingHands == 1)) {
            var feasible = sampler.exactSmallWorlds(settings.worlds());
            int remainingActions = view.contract() == Contract.RENTZ ? MAX_HAND_ACTIONS + 1
                    : view.handSizes().stream().mapToInt(Integer::intValue).sum();
            long required = (long) feasible.size() * options.size() * (1 + view.events().size() + remainingActions);
            if (required <= settings.maxTransitions()) exactWorlds = feasible;
        }
        int worldCount = exactWorlds.isEmpty() ? settings.worlds() : exactWorlds.size();
        var moments = new DecisionMoments<>(options);
        boolean exhausted = false;
        for (int worldNumber = 0; worldNumber < worldCount; worldNumber++) {
            long seed = random.nextLong();
            var world = exactWorlds.isEmpty() ? sampler.sample(seed) : exactWorlds.get(worldNumber);
            var values = new double[options.size()];
            var worldGaps = new double[options.size()];
            try {
                for (int option = 0; option < options.size(); option++) {
                    Contract contract = choosing ? (Contract) options.get(option) : view.contract();
                    var simulation = full
                            ? FullMatchRollout.restore(view, world, seed ^ 0x6a09e667f3bcc909L, scoring, contract, budget)
                            : restore(view, world, contract, scoring, budget);
                    if (!choosing) {
                        budget.spend();
                        simulation.play(view.seat(), (Card) options.get(option));
                    }
                    List<Integer> scores;
                    if (full) {
                        scores = FullMatchRollout.complete(simulation, scoring, settings, budget, view.seat());
                    } else {
                        finishHand(simulation, scoring, settings, budget, view.seat());
                        scores = simulation.history().get(0).points();
                    }
                    values[option] = MatchUtility.value(scores, view.seat(), objective);
                    if (full) worldGaps[option] = MatchUtility.leaderGap(scores, view.seat());
                }
            } catch (RolloutBudget.Exhausted stop) {
                exhausted = true;
                break;
            }
            moments.commit(values, worldGaps);
        }
        var work = new SearchPolicy.Work(settings.worlds(), moments.completed(), options.size(),
                budget.used(), budget.limit(), exhausted);
        if (moments.completed() == 0) {
            @SuppressWarnings("unchecked")
            T fallback = (T) (choosing ? TacticalPolicy.choose(view) : TacticalPolicy.card(view, scoring, settings));
            return new SearchPolicy.Decision<>(fallback, 0, "PUBLIC_TACTICAL_BUDGET_FALLBACK", Collections.emptyList(), work);
        }
        var decision = moments.decision(objective, lastHandPolicy && full ? 0 : settings.riskWeight(), full, work);
        if (provenWinPolicy && !choosing && remainingHands == 1 && view.contract() != Contract.RENTZ
                && view.hand().size() == 2 && view.legal().size() == 2 && budget.used() < budget.limit()
                && view.handSizes().stream().allMatch(size -> size <= 2)
                && view.completedHands().size() == view.handNumber() - 1) {
            return refineWithWinProof(view, sampler, scoring, settings, decision);
        }
        return decision;
    }

    private static <T> SearchPolicy.Decision<T> refineWithWinProof(RentzMatch.Observation view, KnowledgeSampler sampler,
            ScoreProfile scoring, PolicySettings settings, SearchPolicy.Decision<T> decision) {
        var work = decision.work();
        var proofBudget = new RolloutBudget(Math.min(4096, work.transitionLimit() - work.transitions()));
        Card proven = LastTricksWinProof.alternative(view, sampler, (Card) decision.selected(),
                settings.worlds(), scoring, proofBudget);
        if (proofBudget.used() == 0) return decision;
        var totalWork = new SearchPolicy.Work(work.requestedWorlds(), work.completedWorlds(), work.alternatives(),
                work.transitions() + proofBudget.used(), work.transitionLimit(), work.exhausted());
        @SuppressWarnings("unchecked")
        T selected = proven == null ? decision.selected() : (T) proven;
        String method = proven == null ? decision.method() : "PUBLIC_PROVEN_MATCH_WIN";
        return new SearchPolicy.Decision<>(selected, decision.worlds(), method, decision.estimates(), totalWork);
    }

    static RentzMatch restore(RentzMatch.Observation view, OpeningDeal world, Contract contract,
                              ScoreProfile scoring, RolloutBudget budget) {
        var game = RentzMatch.fromOpeningDeal(world, 0, scoring, view.chooser(), view.sequenceOpening());
        budget.spend();
        game.choose(view.chooser(), contract, view.blind());
        if (view.phase() == RentzMatch.Phase.PLAY) {
            for (var event : view.events()) {
                if (event.action() == RentzMatch.Action.PLAY) {
                    budget.spend();
                    game.play(event.seat(), event.card());
                } else if (event.action() == RentzMatch.Action.PASS) {
                    budget.spend();
                    game.pass(event.seat());
                }
            }
            var replay = game.observe(view.seat());
            if (replay.turn() != view.turn()
                    || !new HashSet<>(replay.legal()).equals(new HashSet<>(view.legal()))
                    || !replay.dealPoints().equals(view.dealPoints())) {
                throw new IllegalArgumentException("Sampled replay differs from public state");
            }
        }
        return game;
    }

    private static void finishHand(RentzMatch game, ScoreProfile scoring, PolicySettings settings,
                                   RolloutBudget budget, int rootSeat) {
        int actions = 0;
        var state = game.observe(0);
        while (state.phase() == RentzMatch.Phase.PLAY) {
            int actor = state.turn();
            var own = actor == 0 ? state : game.observe(actor);
            budget.spend();
            if (own.legal().isEmpty()) {
                game.pass(actor);
            } else {
                game.play(actor, TacticalPolicy.continuation(own, rootSeat, scoring, settings));
            }
            if (++actions > MAX_HAND_ACTIONS) throw new IllegalStateException("Rollout action bound exceeded");
            state = game.observe(0);
        }
    }
}
