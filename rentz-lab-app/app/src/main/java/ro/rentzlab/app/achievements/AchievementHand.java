package ro.rentzlab.app.achievements;

import java.util.ArrayList;
import java.util.List;

import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzBoard;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.sequence.SequenceOpening;

public record AchievementHand(RentzMatch.HandResult result, boolean manualChoice, boolean manualPlay,
        boolean delegated, boolean unknownPlay, boolean opened, boolean extended, boolean complete,
        RentzPlayer.Difficulty difficulty, AchievementTactics tactics, boolean manualMatch) {
    public AchievementHand(RentzMatch.HandResult result, boolean manualChoice, boolean manualPlay,
            boolean delegated, boolean unknownPlay, boolean opened, boolean extended, boolean complete,
            RentzPlayer.Difficulty difficulty, AchievementTactics tactics) {
        this(result, manualChoice, manualPlay, delegated, unknownPlay, opened, extended, complete, difficulty, tactics, false);
    }
    public boolean participated() { return manualChoice || manualPlay || delegated; }
    public boolean manual() { return manualPlay && !delegated && !unknownPlay; }
    public int players() { return result.points().size(); }

    /** Rebuilt from successful commands, including when the engine is replayed in a worker. */
    public static final class Recorder {
        private final int openingRank;
        private final int matchHands;
        private final List<GameSession.Command> moves = new ArrayList<>();
        private boolean manualChoice, manualPlay, delegated, unknownPlay, opened, extended;
        private boolean matchHasManualPlay, matchHasNonManualDecision;
        private Contract contract;

        public Recorder(int players) {
            this(players, SequenceOpening.JACK);
        }

        public Recorder(int players, SequenceOpening sequenceOpening) {
            openingRank = new RentzBoard(players, sequenceOpening).startRank();
            matchHands = players * Contract.values().length;
        }

        public void observe(GameSession.Command command) {
            if (command.action() == GameSession.Action.NEXT_HAND) {
                contract = null;
                manualChoice = manualPlay = delegated = unknownPlay = opened = extended = false;
                moves.clear();
            }
            if (command.action() == GameSession.Action.CHOOSE) contract = command.contract();
            if (command.action() == GameSession.Action.PLAY || command.action() == GameSession.Action.PASS)
                moves.add(command);
            if (command.seat() != 0) return;
            if (command.action() == GameSession.Action.CHOOSE || command.action() == GameSession.Action.PLAY) {
                delegated |= command.origin() == GameSession.Origin.DELEGATED;
                unknownPlay |= command.origin() == GameSession.Origin.UNKNOWN;
                matchHasNonManualDecision |= command.origin() != GameSession.Origin.MANUAL;
            }
            if (command.origin() != GameSession.Origin.MANUAL) return;
            if (command.action() == GameSession.Action.CHOOSE) manualChoice = true;
            if (command.action() == GameSession.Action.PLAY) {
                manualPlay = true;
                matchHasManualPlay = true;
                if (contract == Contract.RENTZ) {
                    opened |= command.card().rank() == openingRank;
                    extended |= command.card().rank() != openingRank;
                }
            }
        }

        public AchievementHand settle(RentzMatch.HandResult result, RentzPlayer.Difficulty difficulty) {
            boolean complete = result.number() == matchHands;
            return new AchievementHand(result, manualChoice, manualPlay, delegated, unknownPlay, opened, extended,
                    complete, difficulty, AchievementTactics.from(result, moves, openingRank),
                    complete && matchHasManualPlay && !matchHasNonManualDecision);
        }
    }
}
