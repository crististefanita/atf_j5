package ro.rentzlab.core.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ScoreStandingsTest {
    @Test void competitionRanksRetainTiesAndStableSeatOrder() {
        var totals = List.of(-30, 20, 20, 0, -30, 5);
        assertEquals(List.of(
                new ScoreStandings.Place(1, 1, 20), new ScoreStandings.Place(2, 1, 20),
                new ScoreStandings.Place(5, 3, 5), new ScoreStandings.Place(3, 4, 0),
                new ScoreStandings.Place(0, 5, -30), new ScoreStandings.Place(4, 5, -30)),
                ScoreStandings.rank(totals));
        assertEquals(List.of(-30, 20, 20, 0, -30, 5), totals);
    }

    @Test void labelUsesTheSamePlatformNeutralTotalFormat() {
        assertEquals("1. Ana · Σ 120", ScoreStandings.label(new ScoreStandings.Place(2, 1, 120), "Ana"));
    }

    @Test void nullScoresAndBlankNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ScoreStandings.rank(java.util.Arrays.asList(1, null)));
        assertThrows(IllegalArgumentException.class,
                () -> ScoreStandings.label(new ScoreStandings.Place(0, 1, 0), " "));
    }
}
