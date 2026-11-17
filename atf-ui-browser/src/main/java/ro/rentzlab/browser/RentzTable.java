package ro.rentzlab.browser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import ro.cardtable.browser.BrowserGame;
import ro.cardtable.browser.BrowserGame.Action;
import ro.cardtable.browser.BrowserGame.Card;
import ro.cardtable.browser.BrowserGame.CollectionCue;
import ro.cardtable.browser.BrowserGame.History;
import ro.cardtable.browser.BrowserGame.HistoryCell;
import ro.cardtable.browser.BrowserGame.HistoryGrid;
import ro.cardtable.browser.BrowserGame.HistoryRow;
import ro.cardtable.browser.BrowserGame.PlayStep;
import ro.cardtable.browser.BrowserGame.Played;
import ro.cardtable.browser.BrowserGame.Score;
import ro.cardtable.browser.BrowserGame.ScoreDetails;
import ro.cardtable.browser.BrowserGame.Seat;
import ro.cardtable.browser.BrowserGame.View;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.app.achievements.AchievementHand;
import ro.rentzlab.app.GameSession.Command;
import ro.rentzlab.app.GameSession.Origin;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzBoard;
import ro.rentzlab.core.RentzMatch.Observation;
import ro.rentzlab.core.RentzMatch.Phase;
import ro.rentzlab.core.TrickRules;
import ro.rentzlab.core.sequence.SequenceOpening;

/** Browser projection of the same session used by Android. Opponent hands never cross this boundary. */
final class RentzTable implements BrowserGame.Match {
    /** Keep bot playback on the pinned card-table-ui first-frame cadence. */
    static final int AUTOMATED_ACTION_DELAY_MILLIS = 550;
    private static final List<ro.rentzlab.core.Card.Suit> HAND_SUITS = List.of(
            ro.rentzlab.core.Card.Suit.SPADES, ro.rentzlab.core.Card.Suit.HEARTS,
            ro.rentzlab.core.Card.Suit.CLUBS, ro.rentzlab.core.Card.Suit.DIAMONDS);
    private static final Comparator<ro.rentzlab.core.Card> HAND_ORDER = Comparator
            .comparingInt((ro.rentzlab.core.Card card) -> HAND_SUITS.indexOf(card.suit()))
            .thenComparingInt(ro.rentzlab.core.Card::rank);
    private final GameSession game;
    private final ArrayList<String> names;
    private List<PlayStep> playback = List.of();
    private List<CollectionCue> collections = List.of();
    private List<Map<String, Object>> sequencePlayback = List.of();
    private String language = "ro";

    RentzTable(GameSession game, String name) {
        this.game = game;
        names = new ArrayList<>(game.displayNames(name));
        advanceBots();
    }

    GameSession.Saved saved() { return game.save(); }
    GameSession session() { return game; }
    ro.rentzlab.core.RentzMatch.Observation observation() { return game.view(); }
    List<ro.rentzlab.core.RentzMatch.HandResult> completedHands() { return game.history(); }
    int acceptedCommandCount() { return game.save().commands().size(); }
    List<AchievementHand> achievementHands() { return game.achievementHands(); }
    List<String> names() { return List.copyOf(names); }
    List<Map<String, Object>> sequencePlayback() { return sequencePlayback; }
    java.util.Map<String, Object> arrival(Observation before) {
        Observation after = game.view();
        if (before == null || before.contract() != Contract.RENTZ || before.phase() != Phase.PLAY
                || after.contract() != Contract.RENTZ
                || (after.phase() != Phase.DEAL_END && after.phase() != Phase.MATCH_END)
                || after.completedHands().size() <= before.completedHands().size()) return null;
        int winner = after.finishOrder().get(0);
        var previousTotals = java.util.stream.IntStream.range(0, names.size())
                .map(seat -> after.totals().get(seat) - after.dealPoints().get(seat)).boxed().toList();
        return java.util.Map.of("winner", names.get(winner),
                "unplayedCards", after.players() * 8 - after.board().size(),
                "previousTotals", previousTotals, "durationMillis", 1800);
    }
    boolean isFinished() { return game.view().phase() == Phase.MATCH_END; }
    List<Integer> finalResults() { return game.view().totals(); }
    void rename(String value) { names.set(0, value); }
    void validateName(String value) {
        ro.rentzlab.app.ui.profile.DisplayName.normalize(value);
    }

    @Override public void setLanguage(String value) {
        if (!"ro".equals(value) && !"en".equals(value)) throw new IllegalArgumentException("Unsupported language");
        language = value;
    }

    @Override public void act(String action) {
        playback = List.of();
        collections = List.of();
        sequencePlayback = List.of();
        Observation before = game.view();
        var steps = new ArrayList<PlayStep>();
        var cues = new ArrayList<CollectionCue>();
        var sequenceSteps = new ArrayList<Map<String, Object>>();
        if ("auto-step".equals(action)) {
            if (game.hasForcedHumanPass()) submit(GameSession.Action.PASS, null, null, false, Origin.AUTOMATIC);
            else if (game.hasPendingHumanDecision()) game.advanceHumanTurn();
            else throw new IllegalStateException("There is no human decision to automate");
            captureRentzPlay(before, game.view(), sequenceSteps, true);
            captureManualCollection(before, game.view(), cues);
            advanceBots(steps, cues, sequenceSteps);
            playback = List.copyOf(steps);
            collections = List.copyOf(cues);
            sequencePlayback = List.copyOf(sequenceSteps);
            return;
        }
        var parts = action.split(":", 2);
        switch (parts[0]) {
            case "reveal" -> submit(GameSession.Action.REVEAL, null, null, false, Origin.MANUAL);
            case "choose", "blind" -> submit(GameSession.Action.CHOOSE, null, Contract.valueOf(parts[1]),
                    parts[0].equals("blind"), Origin.MANUAL);
            case "card" -> {
                var card = game.view().legal().stream().filter(candidate -> key(candidate).equals(action))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Card is not currently legal"));
                submit(GameSession.Action.PLAY, card, null, false, Origin.MANUAL);
            }
            case "next" -> submit(GameSession.Action.NEXT_HAND, null, null, false, Origin.MANUAL);
            default -> throw new IllegalArgumentException("Unknown Rentz action");
        }
        captureRentzPlay(before, game.view(), sequenceSteps, false);
        captureManualCollection(before, game.view(), cues);
        advanceBots(steps, cues, sequenceSteps);
        playback = List.copyOf(steps);
        collections = List.copyOf(cues);
        sequencePlayback = List.copyOf(sequenceSteps);
    }

    private void submit(GameSession.Action action, ro.rentzlab.core.Card card, Contract contract, boolean blind, Origin origin) {
        game.apply(new Command(action, 0, card, contract, blind, origin));
    }

    private void advanceBots() { advanceBots(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()); }

    private void advanceBots(List<PlayStep> steps, List<CollectionCue> cues,
            List<Map<String, Object>> sequenceSteps) {
        for (int turns = 0; turns < 512; turns++) {
            if (game.hasForcedHumanPass()) {
                submit(GameSession.Action.PASS, null, null, false, Origin.AUTOMATIC);
            } else if (game.hasPendingBotTurn()) {
                Observation before = game.view();
                game.advanceBotTurn();
                Observation after = game.view();
                if (!captureRentzPlay(before, after, sequenceSteps, true)) captureBotPlay(before, after, steps, cues);
            } else {
                return;
            }
        }
        throw new IllegalStateException("Rentz bot turn bound exceeded");
    }

    private void captureBotPlay(Observation before, Observation after, List<PlayStep> steps,
            List<CollectionCue> cues) {
        if (after.events().size() <= before.events().size()) return;
        var event = after.events().get(after.events().size() - 1);
        if (event.action() != ro.rentzlab.core.RentzMatch.Action.PLAY || event.card() == null) return;
        var played = new Played(event.seat(), face(event.card()), event.card().label(),
                names.get(event.seat()) + " · " + event.card().label());
        boolean startsTrick = before.contract() != Contract.RENTZ && before.trick().isEmpty();
        CollectionCue cue = collection(before, after);
        steps.add(new PlayStep(played, startsTrick, AUTOMATED_ACTION_DELAY_MILLIS, cue,
                after.contract() == null ? "" : contractName(after.contract()), seatFacts(after)));
        if (cue != null) cues.add(cue);
    }

    private boolean captureRentzPlay(Observation before, Observation after,
            List<Map<String, Object>> sequenceSteps, boolean automated) {
        if (before.contract() != Contract.RENTZ || after.contract() != Contract.RENTZ
                || after.events().size() <= before.events().size()) return false;
        var event = after.events().get(after.events().size() - 1);
        if (event.action() != ro.rentzlab.core.RentzMatch.Action.PLAY || event.card() == null) return false;
        sequenceSteps.add(Map.of("actorSeat", event.seat(),
                "delayMillis", automated ? AUTOMATED_ACTION_DELAY_MILLIS : 0,
                "before", publicFrame(before), "after", publicFrame(after)));
        return true;
    }

    private Map<String, Object> publicFrame(Observation state) {
        var view = view(state);
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("heading", view.heading());
        result.put("subtitle", view.subtitle());
        result.put("prompt", view.prompt());
        result.put("seats", view.seats());
        result.put("hand", view.hand());
        result.put("played", view.played());
        result.put("center", view.center());
        result.put("actions", view.actions());
        result.put("history", view.history());
        result.put("header", view.header());
        result.put("score", view.score());
        result.put("scoreDetails", view.scoreDetails());
        result.put("rentzSequence", RentzBrowser.sequence(state));
        return result;
    }

    private void captureManualCollection(Observation before, Observation after, List<CollectionCue> cues) {
        CollectionCue cue = collection(before, after);
        if (cue != null) cues.add(cue);
    }

    private CollectionCue collection(Observation before, Observation after) {
        if (before.contract() == null || before.contract() == Contract.RENTZ
                || after.board().size() <= before.board().size()) return null;
        var plays = new ArrayList<>(before.trick());
        if (after.events().isEmpty()) return null;
        var last = after.events().get(after.events().size() - 1);
        if (last.action() != ro.rentzlab.core.RentzMatch.Action.PLAY || last.card() == null) return null;
        plays.add(new TrickRules.Play(last.seat(), last.card()));
        if (plays.size() != before.players()) return null;
        int winner = TrickRules.winner(plays);
        var cards = plays.stream().map(play -> new Played(play.seat(), face(play.card()), play.card().label(),
                names.get(play.seat()) + " · " + play.card().label())).toList();
        return new CollectionCue(winner, 500, 450, cards, 250, seatFacts(after));
    }

    private List<String> seatFacts(Observation state) {
        var facts = new ArrayList<String>();
        for (int seat = 0; seat < names.size(); seat++) {
            facts.add(text("Mână ", "Hand ") + state.handSizes().get(seat) + "\n"
                    + text("Total ", "Total ") + state.totals().get(seat));
        }
        return List.copyOf(facts);
    }

    @Override public List<PlayStep> playback() { return playback; }
    @Override public List<CollectionCue> collections() { return collections; }

    @Override public View view() {
        return view(game.view());
    }

    private View view(Observation state) {
        boolean choosing = state.phase() == Phase.CHOOSE;
        boolean playing = state.phase() == Phase.PLAY;
        boolean live = choosing || playing;
        var seats = new ArrayList<Seat>();
        for (int seat = 0; seat < names.size(); seat++) {
            String hand = text("Mână", "Hand") + " " + state.handSizes().get(seat);
            String points = text("Total", "Total") + " " + state.totals().get(seat);
            seats.add(Seat.at(seat, names.size(), names.get(seat), hand, points,
                    live && state.turn() == seat));
        }
        var hand = state.hand().stream().sorted(HAND_ORDER).map(card -> face(card,
                playing && state.turn() == 0 && state.legal().contains(card) ? key(card) : "")).toList();
        var played = state.trick().stream().map(play -> new Played(play.seat(), face(play.card()),
                play.card().label(), names.get(play.seat()) + " · " + play.card().label())).toList();
        String contract = state.contract() == null ? text("Alege sub-jocul", "Choose a contract") : contractName(state.contract());
        String progress = text("Mâna", "Hand") + " " + state.handNumber() + "/" + (state.players() * Contract.values().length);
        String prompt = switch (state.phase()) {
            case CHOOSE -> text("Alege sub-jocul", "Choose a contract");
            case PLAY -> state.turn() == 0 ? text("Rândul tău", "Your turn") : text("Se joacă", "Playing");
            case DEAL_END -> text("Mână încheiată", "Hand complete");
            case MATCH_END -> text("Partidă încheiată", "Match complete");
        };
        var header = new BrowserGame.Header("RENTZ", text("Rentz", "Rentz"), progress,
                state.contract() == null ? "" : contractName(state.contract()),
                state.contract() == null ? "—" : contractName(state.contract()),
                text("Sub-jocul selectat", "Selected contract"), 22f, 14f, 13f);
        var history = history(state);
        boolean scoreScreen = state.phase() == Phase.DEAL_END || state.phase() == Phase.MATCH_END;
        Score score = scoreScreen ? new Score(state.phase() == Phase.MATCH_END
                ? text("Clasament final", "Final standings") : text("Rezultatul mâinii", "Hand result"), currentHandHistory(state)) : null;
        ScoreDetails scoreDetails = scoreScreen ? scoreDetails(state) : null;
        return new View(text("Mâna", "Hand") + " " + state.handNumber(), contract, prompt,
                List.copyOf(seats), hand, played, List.of(), controls(state), history, header, score, scoreDetails);
    }

    private List<Action> controls(Observation state) {
        var moves = new ArrayList<Action>();
        if (state.phase() == Phase.DEAL_END) moves.add(new Action("next", text("Mâna următoare", "Next hand"), true));
        if (state.phase() == Phase.CHOOSE && state.turn() == 0) {
            if (!state.revealed()) moves.add(new Action("reveal", text("Arată cărțile", "Reveal hand")));
            if (state.revealed() || game.blindChoiceAvailable()) {
                boolean blind = !state.revealed();
                state.available().stream().sorted().forEach(contract -> moves.add(new Action(
                        (blind ? "blind:" : "choose:") + contract.name(),
                        contractName(contract) + (blind ? text(" · pe nevăzute", " · blind") : ""))));
            }
        }
        if (game.hasPendingHumanDecision()) moves.add(new Action("auto-step", text("Joc automat", "Auto play")));
        return List.copyOf(moves);
    }

    private History history(Observation state) {
        var headings = new ArrayList<>(List.of(text("Mână", "Hand"), text("Sub-joc", "Contract")));
        headings.addAll(names);
        return new History(headings, state.completedHands().stream().map(hand -> {
            var row = new ArrayList<>(List.of(Integer.toString(hand.number()), contractLabel(hand.contract(), hand.blind())));
            hand.totals().forEach(total -> row.add(Integer.toString(total)));
            return List.copyOf(row);
        }).toList(), historyGrid(state));
    }

    private History currentHandHistory(Observation state) {
        var history = history(state);
        var last = state.completedHands().isEmpty() ? null : state.completedHands().get(state.completedHands().size() - 1);
        if (last == null) return History.EMPTY;
        var row = row(last);
        return new History(history.headings(), List.of(row.stream().map(HistoryCell::total).toList()),
                new HistoryGrid(names, List.of(new HistoryRow(last.number(), contractLabel(last.contract(), last.blind()), row)),
                        text("Mână", "Hand"), text("Puncte mână", "Hand points"), text("Total", "Total"),
                        text("Punctajul mâinii și totalul cumulativ", "Hand points and cumulative total")));
    }

    private HistoryGrid historyGrid(Observation state) {
        var rows = state.completedHands().stream().map(hand -> new HistoryRow(hand.number(),
                contractLabel(hand.contract(), hand.blind()), row(hand))).toList();
        return new HistoryGrid(names, rows, text("Mână", "Hand"), text("Puncte mână", "Hand points"),
                text("Total", "Total"), text("Punctajul mâinii și totalul cumulativ", "Hand points and cumulative total"));
    }

    private List<HistoryCell> row(ro.rentzlab.core.RentzMatch.HandResult hand) {
        var cells = new ArrayList<HistoryCell>();
        for (int seat = 0; seat < hand.points().size(); seat++) {
            int delta = hand.points().get(seat);
            int total = hand.totals().get(seat);
            cells.add(new HistoryCell(Integer.toString(delta), Integer.toString(total),
                    names.get(seat) + " · " + text("Puncte", "Points") + " " + delta + " · "
                            + text("Total", "Total") + " " + total, false, 0));
        }
        return List.copyOf(cells);
    }

    private ScoreDetails scoreDetails(Observation state) {
        var last = state.completedHands().isEmpty() ? null : state.completedHands().get(state.completedHands().size() - 1);
        if (last == null) return new ScoreDetails(text("Rezultat", "Result"), "", "", "", "", List.of(), History.EMPTY);
        String summary = contractLabel(last.contract(), last.blind()) + " · " + text("Mâna", "Hand") + " " + last.number();
        String winner = state.phase() == Phase.MATCH_END ? finalStandings(state) : "";
        String awards = pointsLine(last);
        List<String> rules = last.contract() == Contract.RENTZ
                ? List.of(text("Premiile urmează ordinea în care jucătorii își termină cărțile.",
                                "Awards follow the order in which players empty their hands."),
                        text("Ultimul jucător rămas cu cărți primește 0 puncte.",
                                "The last player still holding cards receives 0 points."),
                        text("Punctele mâinii se adună la totalul fiecărui jucător.",
                                "Hand points are added to each player's cumulative total."))
                : List.of(text("Fiecare sub-joc se joacă o singură dată de fiecare jucător.",
                                "Each contract is played once by each player."),
                        text("Punctele mâinii se adună la totalul fiecărui jucător.",
                                "Hand points are added to each player's cumulative total."));
        return new ScoreDetails(text("Rezultatul mâinii", "Hand result"), summary, "", winner, awards, rules,
                currentHandHistory(state));
    }

    private String finalStandings(Observation state) {
        return String.join("\n", finalStandings(state.totals(), names));
    }

    static List<String> finalStandings(List<Integer> totals, List<String> names) {
        if (totals.size() != names.size()) throw new IllegalArgumentException("Every total needs one seat name");
        var order = java.util.stream.IntStream.range(0, totals.size()).boxed()
                .sorted(java.util.Comparator.<Integer>comparingInt(totals::get).reversed().thenComparingInt(i -> i)).toList();
        var lines = new ArrayList<String>();
        int place = 0;
        for (int index = 0; index < order.size(); index++) {
            int seat = order.get(index);
            if (index == 0 || !totals.get(seat).equals(totals.get(order.get(index - 1)))) place = index + 1;
            lines.add(place + ". " + names.get(seat) + " · " + totals.get(seat));
        }
        return List.copyOf(lines);
    }

    private String pointsLine(ro.rentzlab.core.RentzMatch.HandResult hand) {
        var lines = new ArrayList<String>();
        if (hand.contract() == Contract.RENTZ && hand.scoring().finishOrder().size() == names.size()) {
            for (int place = 0; place < names.size(); place++) {
                int seat = hand.scoring().finishOrder().get(place);
                lines.add((place + 1) + ". " + names.get(seat) + " · " + hand.points().get(seat));
            }
            return String.join("\n", lines);
        }
        for (int seat = 0; seat < names.size(); seat++) lines.add(names.get(seat) + " · " + hand.points().get(seat));
        return String.join("\n", lines);
    }

    private String contractLabel(Contract contract, boolean blind) {
        return contractName(contract) + (blind ? text(" · pe nevăzute ×2", " · blind ×2") : "");
    }

    private String contractName(Contract contract) {
        return switch (contract) {
            case TRICKS -> text("Levate", "Tricks");
            case QUEENS -> text("Dame", "Queens");
            case DIAMONDS -> text("Carouri", "Diamonds");
            case KING_HEARTS -> text("Regele de cupă", "King of hearts");
            case TOTALS -> text("Totale", "Totals");
            case WHIST -> "Whist";
            case TEN_CLUBS -> text("Zece de treflă", "Ten of clubs");
            case RENTZ -> "Rentz";
        };
    }

    private String text(String romanian, String english) { return "en".equals(language) ? english : romanian; }

    static List<List<MapCell>> sequence(Observation state) {
        if (state.contract() != Contract.RENTZ) return List.of();
        var board = new RentzBoard(state.players(), state.sequenceOpening());
        var rows = new ArrayList<List<MapCell>>();
        for (var suit : ro.rentzlab.core.Card.Suit.values()) {
            var row = new ArrayList<MapCell>();
            for (int rank = board.lowestRank(); rank <= 14; rank++) {
                var card = new ro.rentzlab.core.Card(suit, rank);
                boolean legal = state.legal().contains(card);
                row.add(new MapCell(card.label(), state.board().contains(card), legal,
                        rank == board.startRank(), legal ? key(card) : ""));
            }
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    record MapCell(String label, boolean played, boolean legal, boolean opening, String action) { }

    private static String key(ro.rentzlab.core.Card card) { return "card:" + card.suit().name() + ":" + card.rank(); }
    private static Card face(ro.rentzlab.core.Card card) {
        String label = card.label();
        int suit = label.length() - 1;
        return new Card(label.substring(0, suit), label.substring(suit), label, "");
    }
    private static Card face(ro.rentzlab.core.Card card, String action) {
        String label = card.label();
        int suit = label.length() - 1;
        return new Card(label.substring(0, suit), label.substring(suit), label, action);
    }
}
