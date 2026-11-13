package ro.rentzlab.core.sequence;

/** Opening-card rules for the Rentz sequence contract. */
public enum SequenceOpening {
    /** Current product rule: each suit opens with its Jack. */
    JACK,
    /** Replay rule for sessions saved before the all-table Jack rule was introduced. */
    ACTIVE_MIDDLE;

    public int rankFor(int players) {
        if (players < 3 || players > 6) throw new IllegalArgumentException("Players must be 3..6");
        return switch (this) {
            case JACK -> 11;
            case ACTIVE_MIDDLE -> 15 - players;
        };
    }
}
