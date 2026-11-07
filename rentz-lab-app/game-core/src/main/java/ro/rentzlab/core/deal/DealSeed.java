package ro.rentzlab.core.deal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Identity of the generator used for the opening deal and later shuffles. */
public final class DealSeed {
    public static final String GENERATOR = "jvm-shuffle-eight-cards-v1";
    private static final long SEED_MASK = (1L << 48) - 1;

    private DealSeed() { }

    /** Canonical representative of java.util.Random's 48-bit initial state. */
    public static long effective(long seed) {
        return seed & SEED_MASK;
    }

    /** Indexes a batch by effective seed while retaining the original labels for replay. */
    public static Map<Long, Long> requireDistinct(Collection<Long> seeds) {
        var byState = new HashMap<Long, Long>();
        for (Long seed : seeds) {
            if (seed == null) throw new IllegalArgumentException("Null deal seed");
            Long prior = byState.putIfAbsent(effective(seed), seed);
            if (prior != null) {
                throw new IllegalArgumentException("Repeated deal generator state: " + prior + " and " + seed);
            }
        }
        return byState;
    }
}
