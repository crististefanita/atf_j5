package ro.rentzlab.ai.search;

/** Deterministic forward-transition budget; allocation, sampling and wall-clock time are separate costs. */
public final class RolloutBudget {
    public static final class Exhausted extends RuntimeException {
        private Exhausted(){super("Rollout transition budget exhausted",null,false,false);}
    }
    private final int limit;
    private int used;
    public RolloutBudget(int limit){if(limit<1)throw new IllegalArgumentException("Positive transition budget required");this.limit=limit;}
    public void spend(){if(used==limit)throw new Exhausted();used++;}
    public int used(){return used;}
    public int limit(){return limit;}
}
