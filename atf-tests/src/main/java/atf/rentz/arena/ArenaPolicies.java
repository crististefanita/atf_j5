package atf.rentz.arena;

import atf.rentz.OfflineArena;
import atf.rentz.OfflineArena.Kind;
import atf.rentz.OfflineArena.Protocol;
import atf.rentz.evaluation.ClusteredEvaluation;
import java.util.EnumMap;
import java.util.Map;
import ro.rentzlab.ai.PolicySettings;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.ScoreProfile;

/** Effective actors and experiment objective are separate parts of an arena run. */
public record ArenaPolicies(PolicySettings.Objective evaluationObjective, Actor candidate, Map<Kind, Matchup> families) {
    public ArenaPolicies {
        if (families != null) families = Map.copyOf(families);
    }

    public record Actor(Kind kind, PolicySettings search) {
        public static Actor forKind(Kind kind, PolicySettings requested) {
            return new Actor(kind, switch (kind) {
                case HARD -> requested;
                case HARD_REFERENCE -> OfflineArena.matchedReferenceSettings(requested);
                case NORMAL, BASELINE -> null;
            });
        }

        public RentzPlayer player(ScoreProfile scoring) {
            return switch (kind) {
                case HARD, HARD_REFERENCE -> new RentzPlayer(RentzPlayer.Difficulty.HARD, scoring, search);
                case NORMAL -> new RentzPlayer(RentzPlayer.Difficulty.NORMAL, scoring, PolicySettings.handMarginReference());
                case BASELINE -> null; // The arena delegates directly to LabPolicy.
            };
        }
    }

    public record Matchup(Actor opponent, Actor control) { }

    public static ArenaPolicies resolve(Protocol protocol, PolicySettings requested) {
        Actor candidate = Actor.forKind(protocol.candidate(), requested);
        var families = new EnumMap<Kind, Matchup>(Kind.class);
        for (Kind family : protocol.opponents()) {
            Actor opponent = Actor.forKind(family, requested);
            Actor control = candidate.search() == null ? opponent : Actor.forKind(Kind.HARD_REFERENCE, requested);
            families.put(family, new Matchup(opponent, control));
        }
        return new ArenaPolicies(requested.objective(), candidate, families);
    }

    public ClusteredEvaluation.Metric metric() {
        return switch (evaluationObjective) {
            case HAND_MARGIN -> ClusteredEvaluation.Metric.MARGIN;
            case MATCH_POINTS -> ClusteredEvaluation.Metric.POINTS;
            case MATCH_WIN_SHARE, MATCH_WIN_LAST_HAND, MATCH_WIN_PROVEN -> ClusteredEvaluation.Metric.WIN_SHARE;
        };
    }
}
