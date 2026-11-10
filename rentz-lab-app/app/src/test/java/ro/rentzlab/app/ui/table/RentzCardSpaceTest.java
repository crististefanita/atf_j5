package ro.rentzlab.app.ui.table;

import org.junit.Test;
import ro.cardtable.ui.hand.HandLayoutMetrics;
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
import java.util.Set;
import java.util.stream.Collectors;

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
        int centerHeight = Math.round(density);
        var layout = TableGeometry.arrangeDisplay(width, height, 4, seatWidth, seatHeight,
                centerWidth, centerHeight, gap, false, TableGeometry.CenterAlignment.BALANCED);
        assertEquals(centerHeight, layout.center().bottom() - layout.center().top());
        assertTrue(layout.seats().stream().noneMatch(layout.center()::overlaps));

        session.advanceBotTurn();
        assertEquals(RentzMatch.Phase.PLAY, session.view().phase());
        assertNotNull(session.view().contract());
    }

    @Test public void fourSeatsUseWhistPlayPositionsThroughoutTheContract() {
        assertWindows(4, new int[]{1, 2, 3}, new int[]{0});
    }

    @Test public void fiveSeatsKeepTheLowerFlanksClearOfTheCards() {
        assertWindows(5, new int[]{2, 3}, new int[]{0, 1, 4});
    }

    @Test public void sixSeatsKeepTwelveRanksReadableWithoutCoveringTheLowerFlanks() {
        assertWindows(6, new int[]{2, 3, 4}, new int[]{0, 1, 5});
    }

    @Test public void everySeatUsesTheWhistTrickFootprintAtFractionalNativeDensity() {
        float density = 440f / 160;
        var viewport = new TableViewportPolicy(Math.round(1920 / density));
        int widthDp = 352, width = Math.round(widthDp * density), height = 1259, seatHeight = 164;
        int seatWidth = Math.round(TableSizing.seatWidthDp(widthDp, viewport.isCompact()) * density);
        int gap = Math.round(viewport.regionGapDp() * density);
        for (int players = 3; players <= 6; players++) {
            var trick = PlayedCardMetrics.forTable(players, widthDp, 1f, viewport);
            int measuredTrick = Math.max(Math.round(trick.centerHeightDp() * density),
                    Math.round((trick.rows() - 1) * trick.rowStrideDp() * density)
                            + Math.round(trick.cellHeightDp() * density));
            var whist = TableGeometry.arrangeDisplay(width, height, players, seatWidth, seatHeight, width,
                    measuredTrick, gap, true, TableGeometry.CenterAlignment.BALANCED);
            var rentz = RentzCardSpace.plan(width, height, players, seatWidth, seatHeight, gap,
                    RentzCardSpace.trickHeightPx(density, trick));
            assertEquals(whist.seats(), rentz.seats());
        }
    }

    @Test public void flanksOpenMoreSpaceThanTheOldNarrowReference() {
        int width = 372, height = 640, seatHeight = 58, gap = 8;
        var viewport = new TableViewportPolicy(872);
        int seatWidth = TableSizing.seatWidthDp(width, false);
        for (int players : new int[]{3, 5, 6}) {
            int upper = players == 3 ? 1 : 2;
            var old = TableGeometry.arrangeDisplay(width, height, players, seatWidth, seatHeight,
                    220, 128, gap, false, TableGeometry.CenterAlignment.BALANCED);
            var trick = PlayedCardMetrics.forTable(players, width, 1f, viewport);
            var play = RentzCardSpace.plan(width, height, players, seatWidth, seatHeight, gap, trick.centerHeightDp());
            assertTrue(play.seats().get(upper).bottom() < old.seats().get(upper).bottom());
            if (players >= 5) assertTrue(play.seats().get(1).top() > old.seats().get(1).top());
        }
    }

    @Test public void denseSuitRowsOverlapFacesWhileKeepingEveryIndexAndBothEdgesVisible() {
        float density = 2.25f;
        int width = 630;
        var dense = RentzCardSpace.sequence(6, width, 528, density, 1.5f);
        assertTrue(dense.overlaps());
        assertTrue(dense.faceWidth() > width / 12);
        assertTrue(dense.faceHeight() > Math.round(40 * density));
        assertTrue(dense.stackedIndex());
        assertTrue(dense.labelSizePx() >= 10 * density);
        assertSequenceFits(dense, 12, width, 528);
        assertFalse(RentzCardSpace.sequence(3, 980, 528, density, 1f).overlaps());
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

    @Test public void compactFourSeatBandFitsCardsAndKeepsItsReferenceDuringSettlement() {
        float density = 2.25f;
        int width = 630, height = 739, seatHeight = 225;
        var viewport = new TableViewportPolicy(520, 1.5f);
        int seatWidth = Math.round(TableSizing.seatWidthDp(280, viewport.isCompact()) * density);
        int gap = Math.round(viewport.regionGapDp() * density);
        var trick = PlayedCardMetrics.forTable(4, 280, 1.5f, viewport);
        int reference = RentzCardSpace.trickHeightPx(density, trick);
        var board = RentzCardSpace.plan(width, height, 4, seatWidth, seatHeight, gap, reference);
        int budgetDp = (int) Math.floor(board.band().height() / density);
        assertTrue(budgetDp < trick.centerHeightDp());
        var fitted = RentzCardSpace.fitTrick(trick, budgetDp);
        assertEquals(trick.slots(), fitted.slots());
        assertEquals(trick.cardHeightDp(), fitted.cardHeightDp());
        int renderedHeight = Math.round((fitted.rows() - 1) * fitted.rowStrideDp() * density)
                + Math.round(fitted.cellHeightDp() * density);
        assertTrue(renderedHeight <= board.band().height());
        int top = board.band().centeredTop(renderedHeight);
        var cards = new TableGeometry.Rect(0, top, width, top + renderedHeight);
        assertTrue(board.seats().stream().noneMatch(cards::overlaps));
        assertSame(trick, RentzCardSpace.fitTrick(trick, trick.centerHeightDp()));
        var compact = RentzCardSpace.sequence(4, width, board.band().height(), density, 1.5f);
        assertTrue(compact.faceWidth() > width / 8);
        assertSequenceFits(compact, 8, width, board.band().height());
        int footer = Math.round(48 * density) + Math.round(3 * density);
        var sequence = RentzCardSpace.sequence(4, width, board.band().height() - footer, density, 1.5f);
        assertSequenceFits(sequence, 8, width, board.band().height() - footer);
    }

    @Test public void onlyLegalSequenceCardsGetAnOutlineAcrossMovesTurnsAndForcedPasses() {
        var copy = new RentzUiText(RentzUiText.Language.RO);
        boolean singleChoice = false;
        boolean severalChoices = false;
        for (int players = 3; players <= 6; players++) {
            boolean playable = false;
            boolean passed = false;
            for (int seed = 31; seed < 37 && (!playable || !passed); seed++) {
                var session = new GameSession(players, seed, RentzPlayer.Difficulty.NORMAL);
                assertFalse(session.hasForcedHumanPass());
                session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, true));
                for (int turn = 0; turn < 120 && session.view().phase() == RentzMatch.Phase.PLAY; turn++) {
                    var state = session.view();
                    assertLegalMarks(state, copy);
                    for (var card : RentzTableContent.hand(state, copy, false)) {
                        assertTrue(RentzTableContent.handMark(Contract.WHIST, card).isEmpty());
                    }
                    for (var card : RentzTableContent.hand(state, copy, true)) {
                        assertTrue(RentzTableContent.handMark(Contract.RENTZ, card).isEmpty());
                    }
                    if (state.turn() != 0) {
                        assertFalse(session.hasForcedHumanPass());
                        session.advanceBotTurn();
                    } else if (!state.legal().isEmpty()) {
                        assertFalse(session.hasForcedHumanPass());
                        playable = true;
                        singleChoice |= state.legal().size() == 1;
                        severalChoices |= state.legal().size() > 1;
                        var playedCard = state.legal().get(0);
                        session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, playedCard, null, false));
                        assertFalse(markedCards(session.view(), copy).contains(RentzTableContent.id(playedCard)));
                    } else {
                        assertTrue(session.hasForcedHumanPass());
                        assertTrue(markedCards(state, copy).isEmpty());
                        assertEquals("Pasează", RentzTableContent.turnLabel(state, copy, true));
                        int revision = session.save().commands().size();
                        session.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false));
                        assertEquals(revision + 1, session.save().commands().size());
                        assertEquals(state.board(), session.view().board());
                        assertFalse(session.hasForcedHumanPass());
                        assertTrue(markedCards(session.view(), copy).isEmpty());
                        passed = true;
                    }
                    assertLegalMarks(session.view(), copy);
                    if (playable && passed) break;
                }
            }
            assertTrue("Fixture exercises legal cards and a forced pass with " + players + " players", playable && passed);
        }
        assertTrue("Fixtures exercise both a single legal card and multiple legal cards", singleChoice && severalChoices);
    }

    @Test public void playableOutlineFitsTheVisibleStripOfAnOverlappingCompactHand() {
        var session = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
        var copy = new RentzUiText(RentzUiText.Language.EN);
        assertTrue(markedCards(session.view(), copy).isEmpty());
        session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, Contract.RENTZ, true));
        var hand = RentzTableContent.hand(session.view(), copy, false);
        var marked = hand.stream().filter(card -> RentzTableContent.handMark(Contract.RENTZ, card).isPresent()).toList();
        assertFalse(marked.isEmpty());
        assertTrue(marked.size() < hand.size());
        assertTrue(marked.stream().allMatch(card -> card.description().startsWith("Play ")));

        float density = 2.25f;
        var mark = RentzTableContent.handMark(Contract.RENTZ, marked.get(0)).orElseThrow();
        assertEquals(0xfff1f8ec, mark.fill());
        assertEquals(0xff4e805c, mark.outline());
        assertEquals(3, mark.outlineWidthPx(density));
        int indexWidthPx = Math.round(24 * density);
        var fan = HandLayoutMetrics.fan(hand.size(), 630, density, 1.5f, indexWidthPx);
        assertTrue(fan.advance() < fan.cardWidth());
        assertTrue(mark.outlineWidthPx(density) * 2 < indexWidthPx);
        assertTrue(indexWidthPx <= fan.advance());
    }

    private static Set<String> markedCards(RentzMatch.Observation view, RentzUiText copy) {
        return RentzTableContent.hand(view, copy, false).stream()
                .filter(card -> RentzTableContent.handMark(view.contract(), card).isPresent())
                .map(card -> card.id()).collect(Collectors.toSet());
    }

    private static void assertLegalMarks(RentzMatch.Observation state, RentzUiText copy) {
        var expected = state.phase() == RentzMatch.Phase.PLAY && state.turn() == state.seat()
                ? state.legal().stream().map(RentzTableContent::id).collect(Collectors.toSet()) : Set.of();
        assertEquals(expected, markedCards(state, copy));
    }

    private static void assertWindows(int players, int[] upper, int[] lower) {
        for (var window : List.of(COMPACT, TALL, LARGE_TEXT, SHORT)) {
            var viewport = new TableViewportPolicy(window.height(), window.fontScale());
            int gap = viewport.regionGapDp();
            int seatWidth = TableSizing.seatWidthDp(window.width(), viewport.isCompact());
            var trick = PlayedCardMetrics.forTable(players, window.width(), window.fontScale(), viewport);
            var plan = RentzCardSpace.plan(window.width(), window.boardHeight(), players, seatWidth,
                    window.seatHeight(), gap, trick.centerHeightDp());
            int budget = plan.band().height();
            var sequence = RentzCardSpace.sequence(players, window.width(), budget, 1f, window.fontScale());
            int contentHeight = 4 * sequence.rowHeight();
            assertSequenceFits(sequence, 2 * players, window.width(), budget);
            int maxFootprint = window.boardHeight() - TableGeometry.minimumHeight(players, window.seatHeight(), 0, gap);
            var whist = TableGeometry.arrangeDisplay(window.width(), window.boardHeight(), players,
                    seatWidth, window.seatHeight(), window.width(), Math.min(trick.centerHeightDp(), maxFootprint), gap,
                    true, TableGeometry.CenterAlignment.BALANCED);
            assertEquals(whist.seats(), plan.seats());
            if (window == TALL) assertTrue(sequence.faceHeight() > 40);
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

    private static void assertSequenceFits(RentzCardSpace.Sequence sequence, int columns, int width, int budget) {
        assertTrue(4 * sequence.rowHeight() <= budget);
        assertTrue(sequence.faceHeight() > 0 && sequence.faceHeight() <= sequence.rowHeight());
        assertTrue(sequence.labelSizePx() > 0);
        assertTrue(sequence.left(0) >= 0);
        assertTrue(sequence.left(columns - 1) + sequence.faceWidth() <= width);
        assertTrue(sequence.indexWidth() <= sequence.faceWidth());
        for (int column = 0; column < columns - 1; column++) {
            assertTrue(sequence.left(column) + sequence.indexWidth() <= sequence.left(column + 1));
        }
    }

    private static double clockwise(TableGeometry.Rect rectangle, int width, int height) {
        double left = (width - rectangle.left() - rectangle.right()) / 2.0;
        double below = (rectangle.top() + rectangle.bottom() - height) / 2.0;
        double angle = Math.atan2(Math.abs(left) <= 1 ? 0 : left, Math.abs(below) <= 1 ? 0 : below);
        return angle < 0 ? angle + 2 * Math.PI : angle;
    }

}
