package ro.rentzlab.app.ui.table;

import org.junit.Test;
import ro.cardtable.ui.table.PlayedCardMetrics;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableSeatLayout;
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

    @Test public void theRecordedSecondHandCanLayOutAndCompleteTheOpponentChoice() {
        var session = new GameSession(4, 1794214887269L, RentzPlayer.Difficulty.NORMAL);
        session.apply(new GameSession.Command(GameSession.Action.REVEAL, 0, null, null, false));
        session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, false));
        for (int turn = 0; turn < 128 && session.view().phase() == RentzMatch.Phase.PLAY; turn++) {
            if (session.hasForcedHumanPass()) {
                session.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false));
            } else if (session.hasPendingHumanDecision()) {
                session.advanceHumanTurn();
            } else {
                session.advanceBotTurn();
            }
        }
        assertEquals(RentzMatch.Phase.DEAL_END, session.view().phase());
        session.apply(new GameSession.Command(GameSession.Action.NEXT_HAND, 0, null, null, false));
        assertEquals(2, session.view().handNumber());
        assertEquals(1, session.view().turn());
        assertEquals(RentzMatch.Phase.CHOOSE, session.view().phase());

        float density = 440f / 160;
        int width = 968, height = 1259, seatHeight = 164;
        var viewport = new TableViewportPolicy(Math.round(1920 / density));
        int widthDp = Math.round(width / density);
        int seatWidth = Math.round(density * TableSizing.seatWidthDp(widthDp, viewport.isCompact()));
        int centerWidth = Math.round(220 * density);
        int gap = Math.round(viewport.regionGapDp() * density);
        assertThrows(IllegalArgumentException.class, () -> TableGeometry.arrangeDisplay(width, height, 4,
                seatWidth, seatHeight, centerWidth, 0, gap, false, TableGeometry.CenterAlignment.BALANCED));
        int centerHeight = RentzCardSpace.minimumControlsHeightPx(4, session.view().phase(), density,
                PlayedCardMetrics.forTable(4, widthDp, 1f, viewport));
        var layout = TableGeometry.arrangeDisplay(width, height, 4, seatWidth, seatHeight,
                centerWidth, centerHeight, gap, false, TableGeometry.CenterAlignment.BALANCED);
        assertEquals(centerHeight, layout.center().bottom() - layout.center().top());
        assertTrue(layout.seats().stream().noneMatch(layout.center()::overlaps));

        session.advanceBotTurn();
        assertEquals(RentzMatch.Phase.PLAY, session.view().phase());
        assertNotNull(session.view().contract());
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

    @Test public void fourSeatRentzBandIsComparedWithTheSharedTrickBandAtCompactAndTallSizes() {
        for (var window : List.of(COMPACT, TALL)) {
            var viewport = new TableViewportPolicy(window.height(), window.fontScale());
            int gap = viewport.regionGapDp();
            int width = Math.min(760, Math.max(240, window.width() - 40));
            int seatWidth = TableSizing.seatWidthDp(width, viewport.isCompact());
            var rentz = RentzCardSpace.plan(width, window.boardHeight(), 4, seatWidth,
                    window.seatHeight(), gap, 220, 128, 72);
            var trick = PlayedCardMetrics.forTable(4, width, window.fontScale(), viewport);
            var shared = TableGeometry.arrangeDisplay(width, window.boardHeight(), 4, seatWidth,
                    window.seatHeight(), width, trick.centerHeightDp(), gap, true,
                    TableGeometry.CenterAlignment.BALANCED);
            int sharedBand = centerBand(shared.seats(), 4, gap);
            assertEquals("Same four-player seat population", 4, shared.seats().size());
            assertTrue("Rentz sequence never gains room by changing the shared seat reference",
                    rentz.band().height() <= sharedBand);
        }
    }

    @Test public void fourSeatSequenceUsesTheSameFootprintAsTricksAtNativeDensity() {
        float density = 440f / 160;
        var viewport = new TableViewportPolicy(Math.round(1920 / density));
        int widthDp = 352, width = Math.round(widthDp * density), height = 1259, seatHeight = 164;
        int seatWidth = Math.round(TableSizing.seatWidthDp(widthDp, viewport.isCompact()) * density);
        int gap = Math.round(viewport.regionGapDp() * density);
        var trick = PlayedCardMetrics.forTable(4, widthDp, 1f, viewport);
        int measuredTrick = Math.max(Math.round(trick.centerHeightDp() * density),
                Math.round((trick.rows() - 1) * trick.rowStrideDp() * density)
                        + Math.round(trick.cellHeightDp() * density));
        var trickSeats = TableGeometry.arrangeDisplay(width, height, 4, seatWidth, seatHeight, width,
                measuredTrick, gap, true, TableGeometry.CenterAlignment.BALANCED).seats();
        int oldFootprint = Math.round(RentzCardSpace.REFERENCE_HEIGHT_DP * density);
        var old = RentzCardSpace.plan(width, height, 4, seatWidth, seatHeight, gap, 605, oldFootprint, 198);
        assertNotEquals("The old reference shifted the flank seats", trickSeats, old.seats());
        var corrected = RentzCardSpace.plan(width, height, 4, seatWidth, seatHeight, gap, 605,
                RentzCardSpace.referenceHeightPx(4, 0, density, trick), 198);
        assertEquals(trickSeats, corrected.seats());
        int completedControlsHeight = Math.round(70 * density);
        int restoredHeight = Math.max(completedControlsHeight,
                RentzCardSpace.minimumControlsHeightPx(4, RentzMatch.Phase.DEAL_END, density, trick));
        var restoredSeats = TableGeometry.arrangeDisplay(width, height, 4, seatWidth, seatHeight, 605,
                restoredHeight, gap, false, TableGeometry.CenterAlignment.BALANCED).seats();
        assertEquals("Restoring a completed trick game preserves the four seat rectangles", trickSeats, restoredSeats);
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

    private static int centerBand(List<TableGeometry.Rect> seats, int players, int gap) {
        var anchors = TableSeatLayout.positions(players);
        int upperBottom = 0;
        int lowerTop = seats.get(0).top();
        for (int index = 0; index < anchors.length; index++) {
            var seat = seats.get(index);
            switch (anchors[index]) {
                case NORTH, NORTH_WEST, NORTH_EAST -> upperBottom = Math.max(upperBottom, seat.bottom());
                case SOUTH, SOUTH_WEST, SOUTH_EAST -> lowerTop = Math.min(lowerTop, seat.top());
                default -> throw new IllegalArgumentException("Unexpected table anchor");
            }
        }
        return lowerTop - upperBottom - 2 * gap;
    }
}
