package ro.rentzlab.core.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.HandScoring;

final class ScoreSettlementTest {
    @Test void rentzRowsFollowFinishOrderAndPreserveSeatLedgerAndContributions() {
        var contribution = new HandScoring.Contribution(Contract.RENTZ, 1, 10);
        var scoring = new HandScoring(List.of(List.of(contribution), List.of(), List.of()), List.of(2, 0, 1));
        var deltas = List.of(10, 0, 5);
        var totals = List.of(30, 20, 15);
        assertEquals(List.of(
                new ScoreSettlement.PlayerLine(2, 1, 5, 15, List.of()),
                new ScoreSettlement.PlayerLine(0, 2, 10, 30, List.of(contribution)),
                new ScoreSettlement.PlayerLine(1, 3, 0, 20, List.of())),
                ScoreSettlement.project(Contract.RENTZ, deltas, totals, scoring));
        assertEquals(List.of(10, 0, 5), deltas);
        assertEquals(List.of(30, 20, 15), totals);
    }

    @Test void nonRentzRowsStayInSeatOrderEvenWhenScoringCarriesFinishOrder() {
        var scoring = new HandScoring(List.of(List.of(), List.of(), List.of()), List.of(2, 0, 1));
        assertEquals(List.of(
                new ScoreSettlement.PlayerLine(0, 0, -5, 15, List.of()),
                new ScoreSettlement.PlayerLine(1, 0, 0, 20, List.of()),
                new ScoreSettlement.PlayerLine(2, 0, 5, 25, List.of())),
                ScoreSettlement.project(Contract.QUEENS, List.of(-5, 0, 5), List.of(15, 20, 25), scoring));
    }

    @Test void malformedRentzFinishOrderFallsBackToSeatOrderInsteadOfDroppingAPlayer() {
        var scoring = new HandScoring(List.of(List.of(), List.of(), List.of()), List.of(2, 2, 1));
        var rows = ScoreSettlement.project(Contract.RENTZ, List.of(0, 10, 5), List.of(0, 10, 5), scoring);
        assertEquals(List.of(0, 1, 2), rows.stream().map(ScoreSettlement.PlayerLine::seat).toList());
        assertEquals(List.of(0, 0, 0), rows.stream().map(ScoreSettlement.PlayerLine::finishPlace).toList());
    }

    @Test void eachSeatRequiresBothConfirmedValues() {
        var scoring = new HandScoring(List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> ScoreSettlement.project(Contract.RENTZ, List.of(0, 1), List.of(1), scoring));
        assertThrows(IllegalArgumentException.class,
                () -> ScoreSettlement.project(Contract.RENTZ, java.util.Arrays.asList(0, null), List.of(1, 2), scoring));
    }
}
