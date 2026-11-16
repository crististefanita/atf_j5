package ro.rentzlab.browser;

import java.util.ArrayList;
import java.util.List;
import ro.cardtable.browser.BrowserGame;
import ro.cardtable.browser.BrowserGame.History;
import ro.cardtable.browser.BrowserGame.Played;
import ro.cardtable.browser.BrowserGame.Seat;
import ro.cardtable.browser.BrowserGame.View;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.GameSession.Command;
import ro.rentzlab.app.GameSession.Origin;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch.Observation;
import ro.rentzlab.core.RentzMatch.Phase;

final class RentzTable implements BrowserGame.Match {
    private final GameSession game;
    private final List<String> names;

    RentzTable(GameSession game, String name) {
        this.game = game;
        names = game.displayNames(name);
        advance(false);
    }

    @Override public void act(String action) {
        if (action.equals("auto")) {
            advance(true);
            return;
        }
        var state = game.view();
        var parts = action.split(":", 2);
        switch (parts[0]) {
            case "reveal" -> submit(GameSession.Action.REVEAL, null, null, false, Origin.MANUAL);
            case "choose", "blind" -> submit(GameSession.Action.CHOOSE, null, Contract.valueOf(parts[1]),
                    parts[0].equals("blind"), Origin.MANUAL);
            case "card" -> {
                var card = state.legal().stream().filter(candidate -> key(candidate).equals(action)).findFirst().orElseThrow();
                submit(GameSession.Action.PLAY, card, null, false, Origin.MANUAL);
            }
            case "next" -> submit(GameSession.Action.NEXT_HAND, null, null, false, Origin.MANUAL);
            default -> throw new IllegalArgumentException("Unknown Rentz action");
        }
        advance(false);
    }

    private void submit(GameSession.Action action, Card card, Contract contract, boolean blind, Origin origin) {
        game.apply(new Command(action, 0, card, contract, blind, origin));
    }

    private void advance(boolean auto) {
        for (int turns = 0; turns < 200; turns++) {
            if (game.hasForcedHumanPass()) submit(GameSession.Action.PASS, null, null, false, Origin.AUTOMATIC);
            else if (game.hasPendingBotTurn()) game.advanceBotTurn();
            else if (auto && game.hasPendingHumanDecision()) game.advanceHumanTurn();
            else return;
        }
        throw new IllegalStateException("Rentz turn bound exceeded");
    }

    @Override public View view() {
        var state = game.view();
        var seats = new ArrayList<Seat>();
        boolean activeHand = state.phase() == Phase.CHOOSE || state.phase() == Phase.PLAY;
        for (int seat = 0; seat < names.size(); seat++) {
            seats.add(Seat.at(seat, names.size(), names.get(seat), "Mână " + state.handSizes().get(seat),
                    "Total " + state.totals().get(seat), activeHand && seat == state.turn()));
        }
        var hand = state.hand().stream().map(card -> face(card,
                state.phase() == Phase.PLAY && state.turn() == 0 && state.legal().contains(card) ? key(card) : "")).toList();
        List<Played> played = state.trick().stream().map(play -> new Played(play.seat(), face(play.card(), ""))).toList();
        var board = state.board().stream().map(card -> face(card, "")).toList();
        return new View("Mâna " + state.handNumber(), state.contract() == null ? "Rentz" : state.contract().label(),
                prompt(state), seats, hand, played, board, controls(state), history(state));
    }

    private List<BrowserGame.Action> controls(Observation state) {
        var moves = new ArrayList<BrowserGame.Action>();
        if (state.phase() == Phase.DEAL_END) moves.add(new BrowserGame.Action("next", "Mâna următoare", true));
        if (state.phase() == Phase.CHOOSE && state.turn() == 0) {
            if (!state.revealed()) moves.add(new BrowserGame.Action("reveal", "Arată cărțile"));
            if (state.revealed() || game.blindChoiceAvailable()) {
                boolean blind = !state.revealed();
                state.available().stream().sorted().forEach(contract -> moves.add(new BrowserGame.Action(
                        (blind ? "blind:" : "choose:") + contract.name(), contract.label() + (blind ? " · orb" : ""))));
            }
        }
        if (game.hasPendingHumanDecision()) moves.add(new BrowserGame.Action("auto", "Joacă automat"));
        return moves;
    }

    private History history(Observation state) {
        var headings = new ArrayList<>(List.of("Mână", "Sub-joc"));
        headings.addAll(names);
        return new History(headings, state.completedHands().stream().map(result -> {
            var row = new ArrayList<>(List.of("" + result.number(), result.contract().label()));
            result.totals().forEach(total -> row.add("" + total));
            return (List<String>) row;
        }).toList());
    }

    private static String prompt(Observation state) {
        return switch (state.phase()) {
            case CHOOSE -> "Alege sub-jocul";
            case PLAY -> "Alege o carte";
            case DEAL_END -> "Mână încheiată";
            case MATCH_END -> "Partidă încheiată";
        };
    }

    private static String key(Card card) { return "card:" + card.suit().name() + ":" + card.rank(); }

    private static BrowserGame.Card face(Card card, String action) {
        String label = card.label();
        int suit = label.length() - 1;
        return new BrowserGame.Card(label.substring(0, suit), label.substring(suit), label, action);
    }
}
