package ro.rentzlab.app.ui.table;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import ro.cardtable.ui.seat.SeatContent;
import ro.cardtable.ui.seat.SeatTypography;
import ro.cardtable.ui.seat.PlayerSeatMetrics;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableSizing;
import ro.cardtable.ui.table.TableViewportPolicy;
import ro.rentzlab.app.ui.chrome.RentzTurnBadge;

@AndroidComponentLayer
public final class RentzTableContentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();
    private final RentzUiText copy = new RentzUiText(RentzUiText.Language.EN);
    @Test public void observedAiHumanHandoffsKeepTheMeasuredSeatPlanAtThreeToSixPlaces() {
        for (int count : new int[]{3, 4, 5, 6}) for (var contract : Contract.values()) {
            var session = ready(count, contract);
            var names = session.displayNames("Ana");
            Map<String, List<TableGeometry.Rect>> baseline = mockMeasuredPlans(session.view(), names);
            var actors = new HashSet<Integer>();
            var violations = new ArrayList<String>();
            int handoffs = 0, moves = 0, previousActor = -1;
            while (session.view().phase() == RentzMatch.Phase.PLAY) {
                var view = session.view(); actors.add(view.turn());
                if (previousActor > 0 && view.turn() == 0) handoffs++;
                var plans = mockMeasuredPlans(view, names);
                for (var profile : baseline.keySet()) if (!baseline.get(profile).equals(plans.get(profile)))
                    violations.add(profile + "/actor=" + view.turn() + "/move=" + moves);
                previousActor = view.turn();
                if (view.turn() != 0) session.advanceBotTurn();
                else if (view.legal().isEmpty()) session.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false));
                else session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, view.legal().get(0), null, false));
                if (++moves > 256) throw new AssertionError("The hand must terminate");
            }
            String key = count + "/" + contract;
            audit.equal("Mock-measured shared frame rectangles stay fixed across observed turns " + key, List.of(), violations);
            audit.equal("Every seat really acted " + key, count, actors.size());
            audit.truth("The real sequence returned from AI to the human " + key, handoffs > 0);
            audit.equal("All eight viewport and three font profiles were evaluated " + key, 24, baseline.size());
        }
    }
    /** Synthetic text measurements exercise the shared geometry; this does not qualify Android raster. */
    private Map<String, List<TableGeometry.Rect>> mockMeasuredPlans(RentzMatch.Observation view, List<String> names) {
        var result = new LinkedHashMap<String, List<TableGeometry.Rect>>();
        var seats = RentzTableContent.seats(view, copy, names, false);
        for (int[] window : new int[][]{{240, 360}, {280, 520}, {320, 599}, {360, 740}, {393, 800}, {414, 896}, {600, 1140}, {800, 600}})
            for (float font : new float[]{1, 1.5f, 2}) {
                int width = window[0]; var viewport = new TableViewportPolicy(window[1], font);
                int seatWidth = TableSizing.seatWidthDp(width, viewport.isCompact());
                int nameHeight = (int) Math.ceil(SeatTypography.NAME_SP * font * 1.4);
                int factHeight = (int) Math.ceil(SeatTypography.FACT_SP * font * 1.4);
                int natural = seats.stream().mapToInt(seat -> nameHeight + seat.facts().size() * factHeight
                        + 2 * viewport.seatVerticalPaddingDp()).max().orElseThrow();
                int heading = viewport.isCompact() ? PlayerSeatMetrics.COMPACT_NAME_REGION_DP
                        : nameHeight + PlayerSeatMetrics.ROOMY_HEADING_RESERVE_DP;
                int reference = PlayerSeatMetrics.referenceFrameHeightPx(natural, nameHeight, heading);
                int badge = RentzTurnBadge.reservedHeight(copy, label -> (int) Math.ceil(12 * font)
                        * Math.max(1, (int) Math.ceil(label.length() * 6 * font / seatWidth)));
                int seatHeight = PlayerSeatMetrics.arrange(reference, badge, 3).heightPx();
                var profile = RentzCardSpace.profile(view.players(), width, font, viewport);
                int gap = viewport.regionGapDp();
                int height = Math.max(window[1] - 150, TableGeometry.minimumHeight(view.players(), seatHeight,
                        profile.center().reservedHeight(), gap));
                result.put(width + "/" + window[1] + "/" + font, RentzCardSpace.plan(width, height, view.players(),
                        seatWidth, seatHeight, gap, profile.center().reservedHeight(), profile.handClearance()).seats());
            }
        return result;
    }
    @Test public void hiddenChoiceNeverProjectsCardsBeforeReveal() {
        for (int count = 3; count <= 6; count++) {
            var session = new GameSession(count, 31, RentzPlayer.Difficulty.NORMAL); var view = session.view();
            audit.equal("Hidden hand " + count, List.of(), RentzTableContent.hand(view, copy, false));
            session.apply(new GameSession.Command(GameSession.Action.REVEAL, 0, null, null, false));
            audit.equal("Eight revealed cards " + count, 8, RentzTableContent.hand(session.view(), copy, false).size());
            audit.truth("Choice cards are not playable " + count, RentzTableContent.hand(session.view(), copy, false).stream().noneMatch(card -> card.playable()));
        }
    }
    @Test public void visibleHandUsesOnlyCurrentLegalIdentityAndPausedInputStaysDisabled() {
        for (int count = 3; count <= 6; count++) {
            var session = ready(count, Contract.WHIST); var view = session.view();
            var cards = RentzTableContent.hand(view, copy, false);
            var expected = new ArrayList<String>();
            for (String suit : List.of("SPADES", "HEARTS", "CLUBS", "DIAMONDS")) {
                for (int rank = 2; rank <= 14; rank++) {
                    String id = suit + ":" + rank;
                    if (view.hand().stream().map(RentzTableContent::id).anyMatch(id::equals)) expected.add(id);
                }
            }
            audit.equal("Cards grouped ♠ ♥ ♣ ♦ then increasing rank " + count, expected, cards.stream().map(card -> card.id()).toList());
            audit.equal("Legal projection " + count, new HashSet<>(view.legal().stream().map(RentzTableContent::id).toList()),
                    new HashSet<>(cards.stream().filter(card -> card.playable()).map(card -> card.id()).toList()));
            var selected = view.legal().get(0);
            audit.truth("Original legal card identity " + count, selected == RentzTableContent.selected(view, RentzTableContent.id(selected)));
            audit.truth("Paused input " + count, RentzTableContent.hand(view, copy, true).stream().noneMatch(card -> card.playable()));
            audit.rejects("Unknown card " + count, IllegalArgumentException.class, () -> RentzTableContent.selected(view, "SPADES:99"));
        }
    }
    @Test public void actorAndFactsFollowRentzEventsAcrossEveryContractWithoutWhistFields() {
        for (Contract contract : Contract.values()) {
            var session = ready(4, contract); int actions = 0;
            boolean constrained = false;
            while (session.view().phase() == RentzMatch.Phase.PLAY) {
                var view = session.view(); var seats = RentzTableContent.seats(view, copy, session.displayNames("Ana"), false);
                audit.equal("Active actor " + contract + "/" + actions, List.of(view.turn()), seats.stream().filter(seat -> seat.active()).map(seat -> seat.seat()).toList());
                for (var seat : seats) {
                    audit.equal("Own stats " + contract + "/" + actions + "/" + seat.seat(), List.of("Δ:", "Σ:"), seat.facts().stream().map(f -> f.label()).toList());
                    audit.equal("Exact confirmed total " + contract + "/" + actions + "/" + seat.seat(), view.totals().get(seat.seat()).toString(), seat.facts().get(1).value());
                }
                var cards = RentzTableContent.hand(view, copy, false);
                audit.equal("Manual input matches the engine for " + contract + "/" + actions,
                        new HashSet<>(view.legal().stream().map(RentzTableContent::id).toList()),
                        new HashSet<>(cards.stream().filter(card -> card.playable()).map(card -> card.id()).toList()));
                if (!constrained && contract != Contract.RENTZ && !view.trick().isEmpty()) {
                    var lead = view.trick().get(0).card().suit();
                    var offSuit = view.hand().stream().filter(card -> card.suit() != lead).findFirst();
                    var following = view.hand().stream().filter(card -> card.suit() == lead).toList();
                    if (offSuit.isPresent() && !following.isEmpty()) {
                        var before = session.save().commands();
                        audit.rejects("Reject off-suit when the lead suit is present in " + contract,
                                IllegalArgumentException.class,
                                () -> RentzTableContent.selected(view, RentzTableContent.id(offSuit.get())));
                        audit.equal("Rejected attempt leaves the journal intact", before, session.save().commands());
                        constrained = true;
                    }
                }
                if (view.legal().isEmpty()) session.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false));
                else session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, RentzTableContent.selected(view, RentzTableContent.id(view.legal().get(0))), null, false));
                session.advanceBots();
                if (++actions > 80) throw new AssertionError("Rentz hand did not terminate");
            }
            if (contract == Contract.WHIST) audit.truth("The Whist hand exercised a follow-suit rejection", constrained);
            audit.equal("Single completed hand " + contract, 1, session.history().size());
            audit.truth("No active actor after completion " + contract,
                    RentzTableContent.seats(session.view(), copy, session.displayNames("Ana"), false).stream().noneMatch(seat -> seat.active()));
        }
    }
    @Test public void signedScoresKeepTheirSeatsAndLocalizeAfterRestore() throws Exception {
        for (int count : List.of(3, 4, 5, 6)) {
            for (Contract contract : List.of(Contract.TRICKS, Contract.WHIST)) {
                var session = ready(count, contract);
                for (int moves = 0; session.view().phase() == RentzMatch.Phase.PLAY; moves++) {
                    if (moves == 8) throw new AssertionError("Expected at most eight player moves");
                    session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false));
                    session.advanceBots();
                }
                var restored = GameSession.restore(session.save());
                var view = restored.view();
                var names = restored.displayNames("Ana");
                int expectedSign = contract == Contract.TRICKS ? -1 : 1;
                audit.truth("Fixture has a point value of the expected sign " + count + "/" + contract,
                        view.dealPoints().stream().anyMatch(points -> Integer.signum(points) == expectedSign));
                for (var language : RentzUiText.Language.values()) {
                    var seats = RentzTableContent.seats(view, new RentzUiText(language), names, false);
                    String label = count + "/" + contract + "/" + language;
                    audit.equal("Every seat retained " + label, count, seats.size());
                    for (int index = 0; index < count; index++) {
                        var seat = seats.get(index);
                        int points = view.dealPoints().get(index);
                        int total = view.totals().get(index);
                        String details = language == RentzUiText.Language.RO ? "Puncte: " : "Points: ";
                        audit.equal("Seat position " + label + "/" + index, index, seat.seat());
                        audit.equal("Score marker order " + label + "/" + index,
                                List.of("Δ: " + points, "Σ: " + total), seat.facts().stream().map(fact -> fact.inlineText()).toList());
                        audit.equal("Accessible description " + label + "/" + index,
                                names.get(index) + ". " + details + points + ". Total: " + total, seat.description());
                    }
                }
            }
        }
    }

    private GameSession ready(int count, Contract contract) {
        var session = new GameSession(count, 31, RentzPlayer.Difficulty.NORMAL);
        session.apply(new GameSession.Command(GameSession.Action.REVEAL, 0, null, null, false));
        session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, contract, false)); return session;
    }
}
