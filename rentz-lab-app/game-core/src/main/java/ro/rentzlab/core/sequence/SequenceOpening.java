package ro.rentzlab.core.sequence;

/** Opening-card rules for the Rentz sequence contract. */
public enum SequenceOpening {
    /** Recorded rule for games that opened every suit with a Jack. */
    JACK,
    /** Upper middle of the ranks in each suit, used in new games and legacy journals without a policy. */
    ACTIVE_MIDDLE;

    public int rankFor(int players) {
        if (players < 3 || players > 6) throw new IllegalArgumentException("Players must be 3..6");
        return switch (this) {
            case JACK -> 11;
            case ACTIVE_MIDDLE -> 15 - players;
        };
    }
}
