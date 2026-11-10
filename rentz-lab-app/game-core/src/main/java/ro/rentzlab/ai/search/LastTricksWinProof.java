package ro.rentzlab.ai.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ro.rentzlab.ai.KnowledgeSampler;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.OpeningDeal;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.ScoreProfile;

/** Certifies a sole first place for every feasible deal and every legal reply in the final tricks. */
final class LastTricksWinProof {
    private LastTricksWinProof() { }

    static Card alternative(RentzMatch.Observation view, KnowledgeSampler sampler, Card reference,
                            int worldLimit, ScoreProfile scoring, RolloutBudget budget) {
        var worlds = sampler.exactSmallWorlds(worldLimit);
        if (worlds.isEmpty()) return null;
        Card other = view.legal().get(0).equals(reference) ? view.legal().get(1) : view.legal().get(0);
        try {
            if (!winsInAll(view, worlds, reference, scoring, budget) && winsInAll(view, worlds, other, scoring, budget)) {
                return other;
            }
        } catch (RolloutBudget.Exhausted unproven) {
            return null;
        }
        return null;
    }

    private static boolean winsInAll(RentzMatch.Observation view, List<OpeningDeal> worlds, Card first,
                                    ScoreProfile scoring, RolloutBudget budget) {
        for (var world : worlds) {
            if (!winsInAllReplies(view, world, Collections.singletonList(first), scoring, budget)) return false;
        }
        return true;
    }

    private static boolean winsInAllReplies(RentzMatch.Observation view, OpeningDeal world, List<Card> prefix,
                                           ScoreProfile scoring, RolloutBudget budget) {
        var game = DecisionEvaluator.restore(view, world, view.contract(), scoring, budget);
        var path = new ArrayList<>(prefix);
        int root = view.seat();
        for (Card card : prefix) {
            budget.spend();
            game.play(game.observe(root).turn(), card);
        }
        for (var state = game.observe(root); state.phase() == RentzMatch.Phase.PLAY; state = game.observe(root)) {
            var choices = game.observe(state.turn()).legal();
            if (choices.size() > 1) {
                for (Card reply : choices) {
                    path.add(reply);
                    boolean wins = winsInAllReplies(view, world, path, scoring, budget);
                    path.remove(path.size() - 1);
                    if (!wins) return false;
                }
                return true;
            }
            Card forced = choices.get(0);
            budget.spend();
            game.play(state.turn(), forced);
            path.add(forced);
        }
        var points = game.history().get(0).points();
        long own = (long) view.totals().get(root) + points.get(root);
        for (int seat = 0; seat < view.players(); seat++) {
            if (seat != root && (long) view.totals().get(seat) + points.get(seat) >= own) return false;
        }
        return true;
    }
}
