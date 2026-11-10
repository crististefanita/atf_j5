package ro.rentzlab.app.ui.score;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.SessionJson;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.HandScoring;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.ScoreProfile;

@AndroidComponentLayer
public final class RentzHistoryReplayTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void completeSixSeatHistoryKeepsEveryConfirmedValueAfterJsonRestore() throws Exception {
        int seats = 6;
        long seed = 0x52454E545A4835L;
        var match = new RentzMatch(seats, seed, ScoreProfile.laboratory());
        var commands = new ArrayList<GameSession.Command>();
        var copy = new RentzUiText(RentzUiText.Language.EN);
        var rom = new RentzUiText(RentzUiText.Language.RO);
        var sums = new int[seats];
        boolean equalAwards = false;
        for (int hand = 1; hand <= 48; hand++) {
            int chooser = match.observe(0).chooser();
            var view = match.observe(chooser);
            var contract = view.available().stream().min(Contract::compareTo).orElseThrow();
            boolean blind = view.available().size() > 1 && hand % 2 == 1;
            if (!blind) {
                match.reveal(chooser);
                commands.add(new GameSession.Command(GameSession.Action.REVEAL, chooser, null, null, false));
            }
            match.choose(chooser, contract, blind);
            commands.add(new GameSession.Command(GameSession.Action.CHOOSE, chooser, null, contract, blind));
            if (hand == 2) audit.equal("Open hand has no confirmed score row", 1, RentzScoreContent.rows(match.history(), copy).size());
            playHand(match, commands);
            var row = RentzScoreContent.rows(match.history(), copy).get(hand - 1);
            var result = match.history().get(hand - 1);
            var details = RentzScoreContent.details(row);
            int factor = blind ? 2 : 1;
            var end = match.observe(0);
            audit.equal("Hand identity " + hand, hand, row.hand());
            audit.equal("Chooser stays in engine seat order for hand " + hand, chooser, row.chooser());
            audit.equal("Blind multiplier marker for hand " + hand, copy.contract(contract) + (blind ? " ×2" : ""), row.label());
            for (int seat = 0; seat < seats; seat++) {
                sums[seat] += result.points().get(seat);
                audit.equal("Confirmed score for hand " + hand + " and seat " + seat, new RentzScoreContent.Value(result.points().get(seat), sums[seat]), row.values().get(seat));
            }
            if (contract == Contract.RENTZ) {
                var order = observedFinishes(end);
                audit.equal("Finishing order from actual plays for hand " + hand, order,
                        details.stream().map(RentzScoreContent.Detail::seat).toList());
                for (int place = 0; place < seats; place++) {
                    int award = List.of(300, 200, 100, 0, 0, 0).get(place) * factor;
                    audit.equal("Place for hand " + hand + " position " + place, place + 1, details.get(place).place());
                    audit.equal("Award for hand " + hand + " position " + place,
                            List.of(new HandScoring.Contribution(Contract.RENTZ, 1, award)), details.get(place).contributions());
                }
                equalAwards |= !order.subList(3, seats).equals(order.subList(3, seats).stream().sorted().toList());
            } else {
                var expected = trickContributions(end, factor);
                for (int seat = 0; seat < seats; seat++) {
                    audit.equal("Captured categories for hand " + hand + " seat " + seat, expected.get(seat), details.get(seat).contributions());
                    audit.equal("Awarded sum for hand " + hand + " seat " + seat, row.values().get(seat).delta(),
                            details.get(seat).contributions().stream().mapToInt(HandScoring.Contribution::points).sum());
                }
            }
            if (hand < 48) {
                match.nextHand();
                commands.add(new GameSession.Command(GameSession.Action.NEXT_HAND, 0, null, null, false));
            }
        }
        audit.equal("Completed six-player schedule", RentzMatch.Phase.MATCH_END, match.observe(0).phase());
        audit.equal("Equal awards include a finish order different from seat order", true, equalAwards);
        var session = GameSession.restore(new GameSession.Saved(1, seats, seed, RentzPlayer.Difficulty.NORMAL, commands));
        String journal = SessionJson.saved(session).toString();
        var restored = SessionJson.restore(journal);
        var expected = RentzScoreContent.rows(match.history(), copy);
        audit.equal("All 48 rows survive the canonical journal", expected, RentzScoreContent.rows(restored.history(), copy));
        audit.equal("Historical places and categories are immutable across the next deal and restore", match.history(), restored.history());
        audit.equal("Journal stays command-based", false, new JSONObject(journal).has("history"));
        var localized = RentzScoreContent.rows(restored.history(), rom);
        audit.equal("Changing copy preserves each score cell", expected.stream().map(RentzScoreContent.Row::values).toList(), localized.stream().map(RentzScoreContent.Row::values).toList());
        writeFixture(journal, expected, commands.size());
    }

    @Test public void totalsRetainBothCategoriesForTheSameDiamondQueen() {
        var captured = List.of(new Card(Card.Suit.DIAMONDS, 12), new Card(Card.Suit.HEARTS, 13), new Card(Card.Suit.CLUBS, 10));
        audit.equal("Diamond queen counts in two categories with blind applied once", -310,
                ScoreProfile.laboratory().trickPoints(Contract.TOTALS, captured, true));
        var old = new RentzMatch.HandResult(1, 0, Contract.RENTZ, false, List.of(0, 0, 0), List.of(0, 0, 0));
        var rows = RentzScoreContent.rows(List.of(old), new RentzUiText(RentzUiText.Language.EN));
        audit.equal("Summary-only history cannot imply arrival order", List.of(), RentzScoreContent.details(rows.get(0)));
    }

    private static List<Integer> observedFinishes(RentzMatch.Observation end) {
        var played = new int[end.players()];
        var order = new ArrayList<Integer>();
        for (var event : end.events())
            if (event.action() == RentzMatch.Action.PLAY && ++played[event.seat()] == 8) order.add(event.seat());
        for (int seat = 0; seat < played.length; seat++) if (!order.contains(seat)) order.add(seat);
        return order;
    }

    private static List<List<HandScoring.Contribution>> trickContributions(RentzMatch.Observation end, int factor) {
        var plays = end.events().stream().filter(event -> event.action() == RentzMatch.Action.PLAY).toList();
        var counts = new int[end.players()][Contract.values().length];
        for (int index = 0; index < plays.size(); index += end.players()) {
            var trick = plays.subList(index, index + end.players());
            var lead = trick.get(0).card().suit();
            var winner = trick.stream().filter(event -> event.card().suit() == lead)
                    .max(java.util.Comparator.comparingInt(event -> event.card().rank())).orElseThrow();
            var facts = counts[winner.seat()];
            facts[Contract.TRICKS.ordinal()]++;
            facts[Contract.WHIST.ordinal()]++;
            for (var event : trick) {
                Card card = event.card();
                if (card.rank() == 12) facts[Contract.QUEENS.ordinal()]++;
                if (card.suit() == Card.Suit.DIAMONDS) facts[Contract.DIAMONDS.ordinal()]++;
                if (card.equals(new Card(Card.Suit.HEARTS, 13))) facts[Contract.KING_HEARTS.ordinal()]++;
                if (card.equals(new Card(Card.Suit.CLUBS, 10))) facts[Contract.TEN_CLUBS.ordinal()]++;
            }
        }
        var categories = end.contract() == Contract.TOTALS ? List.of(Contract.TRICKS, Contract.QUEENS, Contract.DIAMONDS, Contract.KING_HEARTS)
                : List.of(end.contract());
        int[] tariffs = {-10, -25, -20, -100, 0, 10, 100};
        var seats = new ArrayList<List<HandScoring.Contribution>>();
        for (var facts : counts) {
            var breakdown = new ArrayList<HandScoring.Contribution>();
            for (var category : categories) {
                int count = facts[category.ordinal()];
                breakdown.add(new HandScoring.Contribution(category, count, count * tariffs[category.ordinal()] * factor));
            }
            seats.add(breakdown);
        }
        return seats;
    }

    private static void playHand(RentzMatch match, List<GameSession.Command> commands) {
        for (int steps = 0; match.observe(0).phase() == RentzMatch.Phase.PLAY; steps++) {
            if (steps > 500) throw new AssertionError("Hand failed to complete");
            int actor = match.observe(0).turn();
            var legal = match.observe(actor).legal();
            if (legal.isEmpty()) {
                match.pass(actor);
                commands.add(new GameSession.Command(GameSession.Action.PASS, actor, null, null, false));
            } else {
                var card = legal.get(0);
                match.play(actor, card);
                commands.add(new GameSession.Command(GameSession.Action.PLAY, actor, card, null, false));
            }
        }
    }

    private static void writeFixture(String journal, List<RentzScoreContent.Row> rows, int revision) throws Exception {
        String target = System.getProperty("rentz.uiEvidence", "");
        if (target.isBlank()) return;
        Path fixture = Files.createDirectories(Path.of(target).resolve("history-fixture"));
        byte[] journalBytes = journal.getBytes(StandardCharsets.UTF_8);
        Files.write(fixture.resolve("session.json"), journalBytes);
        var json = new JSONObject().put("kind", "confirmed-rentz-history").put("players", 6).put("completedHands", rows.size())
                .put("revision", revision).put("first", point(rows.get(0))).put("last", point(rows.get(rows.size() - 1)))
                .put("journalSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(journalBytes)));
        Files.write(fixture.resolve("fixture.json"), json.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject point(RentzScoreContent.Row row) throws org.json.JSONException {
        return new JSONObject().put("hand", row.hand()).put("label", row.label()).put("chooser", row.chooser())
                .put("delta", row.values().stream().map(RentzScoreContent.Value::delta).toList())
                .put("total", row.values().stream().map(RentzScoreContent.Value::total).toList())
                .put("finishOrder", row.scoring().finishOrder());
    }
}
