package ro.rentzlab.app.ui.table;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Schedules one automated move after its active seat has appeared. */
public final class TurnPacing {
    private static final long MOVE_DELAY_MILLIS = 550L;

    private TurnPacing() { }

    public static void afterVisibleFrame(boolean automaticTurn, boolean collecting, BooleanSupplier frameIsCurrent,
            BiConsumer<Runnable, Long> schedule, Runnable play) {
        if (!automaticTurn || collecting || !frameIsCurrent.getAsBoolean()) return;
        schedule.accept(() -> {
            if (frameIsCurrent.getAsBoolean()) play.run();
        }, MOVE_DELAY_MILLIS);
    }
}
