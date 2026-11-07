package ro.rentzlab.app.ui.table;

import org.junit.Test;
import ro.cardtable.ui.table.PlayedCardMetrics;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableSizing;
import ro.cardtable.ui.table.TableViewportPolicy;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@AndroidComponentLayer
public final class RentzCardSpaceTest {
    private record Window(int width, int height, int seatHeight, int boardHeight, float fontScale) { }

    private static final Window COMPACT = new Window(280, 520, 52, 366, 1f);
    private static final Window TALL = new Window(372, 872, 58, 640, 1f);
    private static final Window LARGE_TEXT = new Window(320, 640, 65, 408, 1.35f);
    private static final Window SHORT = new Window(560, 360, 54, 168, 1.2f);

    @Test public void threeSeatsReserveFourSuitRowsUnderBothOpponents() {
        assertWindows(3, new int[]{1, 2}, new int[]{0});
    }

    @Test public void fourSeatsKeepTheOriginalPositionsWhenTheCardsHaveRoom() {
        assertWindows(4, new int[]{1, 2, 3}, new int[]{0});
    }

    @Test public void fiveSeatsKeepTheLowerFlanksClearOfTheCards() {
        assertWindows(5, new int[]{2, 3}, new int[]{0, 1, 4});
    }

    @Test public void sixSeatsKeepTwelveRanksReadableWithoutCoveringTheLowerFlanks() {
        assertWindows(6, new int[]{2, 3, 4}, new int[]{0, 1, 5});
    }

    @Test public void namesAndTrickCardsRetainTheWhistClockwiseOrderAtTheReferenceWidths() {
        var viewport = new TableViewportPolicy(569);
        for (int players = 3; players <= 6; players++) {
            for (int width : new int[]{280, 313, 353, 560, 760}) {
                var trick = PlayedCardMetrics.forTable(players, width, 1f, viewport);
                int height = TableGeometry.minimumHeight(players, 62, trick.centerHeightDp(), 1) + 120;
                var seats = TableGeometry.arrangeDisplay(width, height, players, TableSizing.seatWidthDp(width, true),
                        62, width, trick.centerHeightDp(), 1, true, TableGeometry.CenterAlignment.BALANCED).seats();
                assertEquals(height, seats.get(0).bottom());
                double previousName = -1;
                double previousCard = -1;
                for (int actor = 0; actor < players; actor++) {
                    var slot = trick.slotForSeat(actor);
                    int top = slot.row() * trick.rowStrideDp();
                    var card = new TableGeometry.Rect(slot.leftDp(), top, slot.leftDp() + trick.cardWidthDp(),
                            top + trick.cellHeightDp());
                    double nameAngle = clockwise(seats.get(actor), width, height);
                    double cardAngle = clockwise(card, width, trick.centerHeightDp());
                    assertTrue("Name follows actor " + actor, nameAngle > previousName);
                    assertTrue("Card follows actor " + actor, cardAngle > previousCard);
                    previousName = nameAngle;
                    previousCard = cardAngle;
                }
            }
        }
    }

    @Test public void onlyLegalSequenceCardsGetTintAndForcedPassUsesOneCommand() {
        var copy = new RentzUiText(RentzUiText.Language.RO);
        for (int players = 3; players <= 6; players++) {
            boolean playable = false;
            boolean passed = false;
            for (int seed = 31; seed < 37 && (!playable || !passed); seed++) {
                var session = new GameSession(players, seed, RentzPlayer.Difficulty.NORMAL);
                assertFalse(session.hasForcedHumanPass());
                session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, true));
                for (int turn = 0; turn < 120 && session.view().phase() == RentzMatch.Phase.PLAY; turn++) {
                    var state = session.view();
                    var legalIds = state.legal().stream().map(RentzTableContent::id).toList();
                    var hand = RentzTableContent.hand(state, copy, false);
                    for (var card : hand) {
                        var tint = RentzTableContent.handTint(Contract.RENTZ, card);
                        assertEquals(legalIds.contains(card.id()), tint.isPresent());
                        if (tint.isPresent()) assertEquals(0xfff1f8ec, tint.getAsInt());
                        assertTrue(RentzTableContent.handTint(Contract.WHIST, card).isEmpty());
                    }
                    for (var card : RentzTableContent.hand(state, copy, true)) {
                        assertTrue(RentzTableContent.handTint(Contract.RENTZ, card).isEmpty());
                    }
                    if (state.turn() != 0) {
                        assertFalse(session.hasForcedHumanPass());
                        session.advanceBotTurn();
                    } else if (!state.legal().isEmpty()) {
                        assertFalse(session.hasForcedHumanPass());
                        playable = true;
                        session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, state.legal().get(0), null, false));
                    } else {
                        assertTrue(session.hasForcedHumanPass());
                        assertEquals("Pasează", RentzTableContent.turnLabel(state, copy, true));
                        int revision = session.save().commands().size();
                        session.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false));
                        assertEquals(revision + 1, session.save().commands().size());
                        assertEquals(state.board(), session.view().board());
                        assertFalse(session.hasForcedHumanPass());
                        passed = true;
                    }
                    if (playable && passed) break;
                }
            }
            assertTrue("Fixture exercises legal cards and a forced pass with " + players + " players", playable && passed);
        }
    }

    private static void assertWindows(int players, int[] upper, int[] lower) {
        for (var window : List.of(COMPACT, TALL, LARGE_TEXT, SHORT)) {
            var viewport = new TableViewportPolicy(window.height(), window.fontScale());
            int gap = viewport.regionGapDp();
            int seatWidth = TableSizing.seatWidthDp(window.width(), viewport.isCompact());
            var plan = RentzCardSpace.plan(window.width(), window.boardHeight(), players, seatWidth,
                    window.seatHeight(), gap, 220, 128, 72);
            int budget = plan.band().height();
            assertTrue(budget > 0);
            var sequence = RentzCardSpace.sequence(players, window.width(), budget, window.fontScale());
            int contentHeight = 4 * sequence.rowHeightDp();
            assertTrue(contentHeight <= budget);
            int slotWidth = window.width() / (2 * players) - 2 * sequence.sideInsetDp();
            assertTrue(sequence.labelSp() * window.fontScale() * 2 <= slotWidth + .001f);
            if (window == TALL || window == COMPACT) {
                var original = TableGeometry.arrangeDisplay(window.width(), window.boardHeight(), players,
                        seatWidth, window.seatHeight(), 220, 128, gap, false, TableGeometry.CenterAlignment.BALANCED);
                assertEquals("Use the existing Rentz seat positions", original.seats(), plan.seats());
                assertTrue(sequence.labelSp() > 11);
            }
            if (window == TALL) assertTrue(sequence.rowHeightDp() - 2 * sequence.verticalInsetDp() > 32);
            int top = plan.band().centeredTop(contentHeight);
            int upperBottom = Arrays.stream(upper).map(i -> plan.seats().get(i).bottom()).max().orElseThrow();
            int lowerTop = Arrays.stream(lower).map(i -> plan.seats().get(i).top()).min().orElseThrow();
            assertTrue(top - upperBottom >= gap);
            assertTrue(lowerTop - top - contentHeight >= gap);
            assertTrue(Math.abs((top - upperBottom) - (lowerTop - top - contentHeight)) <= 1);
            var region = new TableGeometry.Rect(0, top, window.width(), top + contentHeight);
            assertTrue(plan.seats().stream().noneMatch(region::overlaps));
            assertTrue(region.bottom() <= plan.seats().get(0).top());
        }
    }

    private static double clockwise(TableGeometry.Rect rectangle, int width, int height) {
        double left = (width - rectangle.left() - rectangle.right()) / 2.0;
        double below = (rectangle.top() + rectangle.bottom() - height) / 2.0;
        double angle = Math.atan2(Math.abs(left) <= 1 ? 0 : left, Math.abs(below) <= 1 ? 0 : below);
        return angle < 0 ? angle + 2 * Math.PI : angle;
    }
}
