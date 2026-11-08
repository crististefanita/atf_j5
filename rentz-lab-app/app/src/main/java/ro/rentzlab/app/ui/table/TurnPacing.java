package ro.rentzlab.app.ui.table;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Waits for the active seat's first frame, then schedules one current automated move. */
public final class TurnPacing {
    private TurnPacing() { }

    public static void afterVisibleFrame(boolean automaticTurn, boolean collecting,
            Consumer<Runnable> afterFirstFrame, BooleanSupplier frameIsCurrent,
            BiConsumer<Runnable, Long> schedule, Runnable play) {
        ro.cardtable.ui.turn.TurnPacing.afterVisibleFrame(automaticTurn, collecting,
                afterFirstFrame, frameIsCurrent, schedule, play);
    }
}
