package ro.rentzlab.app.ui.navigation;

import ro.rentzlab.ai.RentzPlayer;

/** Selections survive language changes without persisting or changing the active game. */
public final class SetupSelection {
    private int players = 4;
    private RentzPlayer.Difficulty difficulty = RentzPlayer.Difficulty.HARD;
    public int players() { return players; }
    public RentzPlayer.Difficulty difficulty() { return difficulty; }
    public void players(int value) {
        if (value < 3 || value > 6) throw new IllegalArgumentException("Rentz supports 3–6 players");
        players = value;
    }
    public void difficulty(RentzPlayer.Difficulty value) { difficulty = java.util.Objects.requireNonNull(value); }
}
