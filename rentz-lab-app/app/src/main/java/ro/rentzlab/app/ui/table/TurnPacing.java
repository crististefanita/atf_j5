package ro.rentzlab.app.ui.table;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Waits for the active seat's first frame, then schedules one current automated move. */
public final class TurnPacing {
    private static final long MOVE_DELAY_MILLIS = 550L;

    private TurnPacing() { }

    public static void afterVisibleFrame(boolean automaticTurn, boolean collecting,
            Consumer<Runnable> afterFirstFrame, BooleanSupplier frameIsCurrent,
            BiConsumer<Runnable, Long> schedule, Runnable play) {
        if (!automaticTurn || collecting || !frameIsCurrent.getAsBoolean()) return;
        afterFirstFrame.accept(() -> {
            if (!frameIsCurrent.getAsBoolean()) return;
            schedule.accept(() -> {
                if (frameIsCurrent.getAsBoolean()) play.run();
            }, MOVE_DELAY_MILLIS);
        });
    }
}
