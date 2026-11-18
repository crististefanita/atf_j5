package ro.rentzlab.app.ui.score;

import org.junit.*;
import ro.rentzlab.app.testing.*;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@AndroidComponentLayer
public final class RentzScoreContentTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void shortScoreLabelsAndLegendMatchTheBrowserProjection() {
        audit.equal("Hand points marker", "P", RentzScoreContent.HAND_POINTS_MARK);
        audit.equal("Running total marker", "T", RentzScoreContent.TOTAL_MARK);
        audit.equal("Romanian legend", "P: punctele mâinii · T: total după mână",
                RentzScoreContent.legend(new RentzUiText(RentzUiText.Language.RO)));
        audit.equal("English legend", "P: hand points · T: running total",
                RentzScoreContent.legend(new RentzUiText(RentzUiText.Language.EN)));
    }

    @Test public void helpShowsOnlyThePlacesAvailableAtEachTable() {
        var rows = RentzScoreContent.finishingAwards(GameSession.scoringProfile());
        audit.equal("Three through six seats with zero awards at actual places", List.of(
                List.of(300, 200, 0),
                List.of(300, 200, 100, 0),
                List.of(300, 175, 100, 25, 0),
                List.of(300, 150, 75, 50, 25, 0)), rows);
        audit.rejects("Award matrix cannot gain an unsupported table", UnsupportedOperationException.class, rows::clear);
        audit.rejects("An actual place cannot be removed", UnsupportedOperationException.class, rows.get(3)::clear);
    }

    @Test public void helpDescribesEveryFinishingAwardInBothLanguages() {
        var english = new RentzUiText(RentzUiText.Language.EN);
        var romanian = new RentzUiText(RentzUiText.Language.RO);
        for (var row : RentzScoreContent.finishingAwards(GameSession.scoringProfile())) {
            int players = row.size();
            audit.equal("English table label", players + " players", english.finishingAwardTable(players));
            audit.equal("Romanian table label", players + " jucători", romanian.finishingAwardTable(players));
            for (int index = 0; index < players; index++) {
                int place = index + 1;
                int points = row.get(index);
                audit.equal("English award " + players + "/" + place,
                        players + " players, place " + place + ", " + points + " points",
                        english.finishingAwardDescription(players, place, points));
                audit.equal("Romanian award " + players + "/" + place,
                        players + " jucători, locul " + place + ", " + points + " puncte",
                        romanian.finishingAwardDescription(players, place, points));
            }
        }
    }

    @Test public void helpUsesTheEffectiveProfileWithoutSortingOrInventingPlaces() {
        var alternate = new ScoreProfile(0, 4, 8, 16, 2, 32, List.of(25, 900, 25, 7, 0, 12, 999));
        audit.equal("Non-default awards keep place order, ties and zero; seventh place stays outside the grid", List.of(
                List.of(25, 900, 25),
                List.of(25, 900, 25, 7),
                List.of(25, 900, 25, 7, 0),
                List.of(25, 900, 25, 7, 0, 12)), RentzScoreContent.finishingAwards(alternate));
        audit.equal("English trick tariffs come from the same profile", "Trick 0 · queen −4 · diamond −8 · K♥ −16. Whist +2 · 10♣ +32.",
                new RentzUiText(RentzUiText.Language.EN).trickScoring(alternate));
        audit.equal("Romanian trick tariffs come from the same profile", "Levată 0 · damă −4 · caro −8 · K♥ −16. Whist +2 · 10♣ +32.",
                new RentzUiText(RentzUiText.Language.RO).trickScoring(alternate));
    }

    @Test public void negativeAndPositiveRentzDeltasAndTotalsSurviveRestoreWithoutWhistColumns() {
        for (Contract contract : List.of(Contract.TRICKS, Contract.WHIST)) {
            var session = new GameSession(4, 31, RentzPlayer.Difficulty.NORMAL);
            var copy = new RentzUiText(RentzUiText.Language.EN);
            session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, contract, true)); int moves = 0;
            audit.truth("Current hand score stays hidden during play " + contract,
                    RentzScoreContent.currentHand(session.view(), session.history(), copy).isEmpty());
            while (session.view().phase() == RentzMatch.Phase.PLAY) {
                session.apply(new GameSession.Command(GameSession.Action.PLAY, 0, session.view().legal().get(0), null, false)); session.advanceBots();
                if (++moves > 20) throw new AssertionError("No completed trick hand");
            }
            var rows = RentzScoreContent.rows(session.history(), copy);
            audit.equal("One confirmed history row " + contract, 1, rows.size());
            audit.equal("Eight tricks, blind doubling " + contract, contract == Contract.TRICKS ? -160 : 160, rows.get(0).values().stream().mapToInt(value -> value.delta()).sum());
            audit.equal("Restored exact history " + contract, rows, RentzScoreContent.rows(GameSession.restore(session.save()).history(), copy));
            audit.equal("Totals keep engine seat order " + contract, session.view().totals(), rows.get(0).values().stream().map(value -> value.total()).toList());
            audit.equal("Chooser retained " + contract, 0, rows.get(0).chooser());
            var restored = GameSession.restore(session.save());
            var score = RentzScoreContent.currentHand(restored.view(), restored.history(), copy).orElseThrow();
            audit.equal("Current score matches its history row after restart " + contract, rows.get(0), score);
            audit.equal("Current score has a contribution group for every seat " + contract, 4, RentzScoreContent.details(score).size());
            restored.apply(new GameSession.Command(GameSession.Action.NEXT_HAND, 0, null, null, false));
            audit.truth("The next hand cannot reopen the previous result " + contract,
                    RentzScoreContent.currentHand(restored.view(), restored.history(), copy).isEmpty());
        }
    }

    @Test public void finalStandingsUseCompetitionTiesAndKeepHistoryInSeatOrder() {
        var totals = List.of(-30, 20, 20, 0, -30, 5);
        var end = standingsInput(RentzMatch.Phase.MATCH_END, totals);
        audit.equal("Shared positions leave the next competition rank vacant", List.of(
                new RentzScoreContent.Standing(1, 1, 20), new RentzScoreContent.Standing(2, 1, 20),
                new RentzScoreContent.Standing(5, 3, 5), new RentzScoreContent.Standing(3, 4, 0),
                new RentzScoreContent.Standing(0, 5, -30), new RentzScoreContent.Standing(4, 5, -30)),
                RentzScoreContent.finalStandings(end));
        var names = List.of("Ana", "Mihai", "Ioana", "Dan", "Eva", "Radu");
        audit.equal("Android final labels use the shared platform-neutral format", List.of(
                "1. Mihai · T 20", "1. Ioana · T 20", "3. Radu · T 5", "4. Dan · T 0",
                "5. Ana · T -30", "5. Eva · T -30"),
                RentzScoreContent.finalStandings(end).stream()
                        .map(row -> RentzScoreContent.standingLabel(row, names.get(row.seat()))).toList());
        audit.equal("Final ranking does not mutate seat totals", totals, end.totals());
        audit.truth("An ordinary settled hand has no final standings",
                RentzScoreContent.finalStandings(standingsInput(RentzMatch.Phase.DEAL_END, totals)).isEmpty());
        for (int players = 3; players <= 6; players++) {
            var tied = standingsInput(RentzMatch.Phase.MATCH_END, java.util.Collections.nCopies(players, -50));
            var rows = RentzScoreContent.finalStandings(tied);
            audit.equal("All seats retained " + players, players, rows.size());
            for (int seat = 0; seat < players; seat++) {
                audit.equal("Tied seat order " + players + "/" + seat,
                        new RentzScoreContent.Standing(seat, 1, -50), rows.get(seat));
            }
        }
    }

    @Test public void resultDetailsUseSharedSettlementForEveryContractAndTableSize() {
        var copy = new RentzUiText(RentzUiText.Language.RO);
        for (int players = 3; players <= 6; players++) {
            for (Contract contract : Contract.values()) {
                var session = new GameSession(players, 9100L + players * 10L + contract.ordinal(), RentzPlayer.Difficulty.NORMAL);
                session.apply(new GameSession.Command(GameSession.Action.CHOOSE, 0, null, contract, false));
                int moves = 0;
                while (session.view().phase() == RentzMatch.Phase.PLAY) {
                    if (session.hasForcedHumanPass()) {
                        session.apply(new GameSession.Command(GameSession.Action.PASS, 0, null, null, false));
                    } else if (session.hasPendingHumanDecision()) {
                        session.advanceHumanTurn();
                    } else {
                        session.advanceBotTurn();
                    }
                    if (++moves > 128) throw new AssertionError("Score fixture did not finish " + players + "/" + contract);
                }
                var hand = session.history().get(0);
                var row = RentzScoreContent.currentHand(session.view(), session.history(), copy).orElseThrow();
                var details = RentzScoreContent.details(row);
                audit.equal("One result detail per seat " + players + "/" + contract, players, details.size());
                for (int place = 0; place < players; place++) {
                    int seat = contract == Contract.RENTZ ? hand.scoring().finishOrder().get(place) : place;
                    var detail = details.get(place);
                    audit.equal("Shared row order " + players + "/" + contract + "/" + place, seat, detail.seat());
                    audit.equal("Shared delta " + players + "/" + contract + "/" + place,
                            hand.points().get(seat), detail.score().delta());
                    audit.equal("Shared cumulative total " + players + "/" + contract + "/" + place,
                            hand.totals().get(seat), detail.score().total());
                }
                if (contract == Contract.RENTZ) {
                    audit.equal("Exactly one Rentz zero in history " + players, 1L,
                            hand.points().stream().filter(points -> points == 0).count());
                    audit.equal("Exactly one Rentz zero in result projection " + players, 1L,
                            details.stream().filter(detail -> detail.score().delta() == 0).count());
                }
            }
        }
    }

    @Test public void rentzAwardsAndFinalPlaceFollowTheSeatThatEmptiedItsHand() {
        checkRentzSettlement(List.of(300, 200, 0));
        checkRentzSettlement(List.of(300, 175, 100, 25, 0));
        checkRentzSettlement(List.of(300, 150, 75, 50, 25, 0));
    }

    private void checkRentzSettlement(List<Integer> awards) {
        int players = awards.size();
        var copy = new RentzUiText(RentzUiText.Language.EN);
        var match = new RentzMatch(players, 31, ScoreProfile.laboratory());
        match.choose(0, Contract.RENTZ, false);
        var finishers = new ArrayList<Integer>();
        var cardsPlayed = new int[players];
        int actions = 0;
        audit.truth("No score before settlement at " + players + " seats",
                RentzScoreContent.currentHand(match.observe(0), match.history(), copy).isEmpty());
        while (match.observe(0).phase() == RentzMatch.Phase.PLAY) {
            var actor = match.observe(match.observe(0).turn());
            if (actor.legal().isEmpty()) {
                match.pass(actor.seat());
            } else {
                match.play(actor.seat(), actor.legal().get(0));
                if (++cardsPlayed[actor.seat()] == 8) finishers.add(actor.seat());
            }
            if (++actions > 128) throw new AssertionError("Rentz fixture did not complete its hand");
        }

        audit.equal("Exactly one player has cards left at " + players + " seats", players - 1, finishers.size());
        for (int seat = 0; seat < players; seat++) {
            if (cardsPlayed[seat] < 8) finishers.add(seat);
        }
        var current = RentzScoreContent.currentHand(match.observe(0), match.history(), copy).orElseThrow();
        var details = RentzScoreContent.details(current);
        audit.equal("Public play order determines detail seats at " + players + " seats",
                finishers, details.stream().map(RentzScoreContent.Detail::seat).toList());
        for (int place = 0; place < players; place++) {
            var detail = details.get(place);
            var expected = new RentzScoreContent.Value(awards.get(place), awards.get(place));
            audit.equal("Place ties the award to the visible seat " + players + "/" + place, place + 1, detail.place());
            audit.equal("Exact points at " + players + "/" + place, expected, detail.score());
            audit.equal("Detail and history agree at " + players + "/" + place, expected, current.values().get(detail.seat()));
        }
    }

    private static RentzMatch.Observation standingsInput(RentzMatch.Phase phase, List<Integer> totals) {
        return new RentzMatch.Observation(0, totals.size(), 0, 0, totals.size() * 8, phase,
                Contract.WHIST, false, true, List.of(), List.of(), List.of(), List.of(), totals,
                List.of(), List.of(), List.of(), List.of(), List.of(), Set.of());
    }
}
