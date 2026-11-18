package ro.rentzlab.app.ui.chrome;

import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import ro.rentzlab.app.ui.copy.RentzUiText;

/** Reserves the largest measured turn label across phases, as in the Whist adapter. */
public final class RentzTurnBadge {
    public static int reservedHeight(RentzUiText copy, ToIntFunction<String> measure) {
        return List.of(copy.seatTurn(true), copy.seatTurn(false), copy.text("Passing", "Pasează")).stream()
                .map(label -> label.toUpperCase(Locale.ROOT)).mapToInt(measure).max().orElseThrow();
    }
    private RentzTurnBadge() { }
}
