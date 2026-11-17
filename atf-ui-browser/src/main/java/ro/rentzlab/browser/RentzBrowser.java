package ro.rentzlab.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static java.util.Map.entry;
import ro.cardtable.browser.BrowserExperience;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import ro.cardtable.browser.BrowserGame;
import ro.cardtable.browser.BrowserGame.Token;
import ro.cardtable.browser.BrowserServer;
import ro.cardtable.names.PlayerNames;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.core.RentzMatch.Observation;
import ro.rentzlab.core.ScoreProfile;

public final class RentzBrowser implements BrowserGame {
    private final LongSupplier seeds;
    private final Map<String, RentzBrowserProfile.Values> profiles = new ConcurrentHashMap<>();
    public RentzBrowser(LongSupplier seeds) { this.seeds = seeds; }

    public static BrowserExperience localProfile(RentzBrowserProfile.Store name, RentzBrowserProfile.Store progress,
            RentzBrowserProfile.Store rankings) {
        return new RentzBrowserProfile(name, progress, rankings);
    }

    public static void main(String[] args) throws Exception {
        BrowserServer.launch(new RentzBrowser(RandomGenerator.getDefault()::nextLong), args);
    }

    @Override public String title() { return "Rentz Lab"; }

    /** Reuse the common card-table shell; the engine and the Rentz contract remain J5-owned. */
    @Override public String surface() { return "whist"; }

    /** Pure-Java mirror of the Rentz Android theme and shared card-table metrics. */
    @Override public List<Token> palette() {
        return List.of(
                token("paper", "#f6f5ed"), token("surface", "#fffef8"), token("pine", "#1d362b"),
                token("accent", "#306f4e"), token("felt", "#174f3c"),
                token("seat-fill", "#1f5743"), token("seat-line", "#4c7660"), token("seat-text", "#d3efe2"),
                token("turn-fill", "#f1e6c8"), token("turn-line", "#dcb569"), token("turn-text", "#29362e"),
                token("score-paper", "#fffef8"), token("score-stripe", "#f1f5ee"),
                token("score-human", "#f1e6c8"), token("score-header", "#dfeada"),
                token("score-rule", "#b4c3af"), token("score-ink", "#29362e"), token("score-muted", "#69756a"),
                token("hint-rejected-fill", "#ffeee8"), token("hint-rejected-line", "#b7544d"),
                token("hint-legal-fill", "#fff6d7"), token("hint-legal-line", "#c99537"),
                token("played-fill", "#fffef8"), token("played-line", "#bfc9bf"),
                token("played-red", "#ad393a"), token("played-dark", "#2a352f"),
                token("played-player", "#c2d3c5"),
                dimension("rank-scale", "1"), dimension("hand-rank-cap", "16px"),
                dimension("played-rank-cap", "18px"), dimension("played-suit-cap", "26px"),
                dimension("badge-rank-cap", "9px"), dimension("played-radius", "5px"),
                dimension("played-stroke", "1px"), dimension("hint-stroke", "1.5px"),
                dimension("hand-face-width", "63px"), dimension("hand-face-height", "88px"),
                dimension("trump-face-width", "32px"), dimension("trump-face-height", "48px"),
                dimension("hand-slot", "116px"), dimension("score-head", "38px"), dimension("score-row", "43px"));
    }

    private static Token token(String name, String color) { return new Token("--whist-" + name, color); }
    private static Token dimension(String name, String value) { return token(name, value); }

    @Override public int autoDelayMillis() { return RentzTable.AUTOMATED_ACTION_DELAY_MILLIS; }

    @Override public boolean hasProfile() { return true; }

    @Override public BrowserExperience openProfile(String client) {
        var values = profiles.computeIfAbsent(client, ignored -> new RentzBrowserProfile.Values());
        return new RentzBrowserProfile(values);
    }

    public String checkpoint(BrowserGame.Match match) {
        if (!(match instanceof RentzTable table)) return null;
        return RentzCheckpoint.encode(table.saved());
    }

    public BrowserGame.Match restore(String checkpoint, String playerName) {
        return new RentzTable(RentzCheckpoint.restore(checkpoint), PlayerNames.normalize(playerName));
    }

    public static List<Map<String, Object>> sequence(BrowserGame.Match match) {
        if (!(match instanceof RentzTable table)) return List.of();
        return sequence(table.observation());
    }

    static List<Map<String, Object>> sequence(ro.rentzlab.core.RentzMatch.Observation observation) {
        if (observation.contract() != ro.rentzlab.core.Contract.RENTZ) return List.of();
        var suits = ro.rentzlab.core.Card.Suit.values();
        var rows = new ArrayList<Map<String, Object>>();
        var cells = RentzTable.sequence(observation);
        for (int index = 0; index < cells.size(); index++) {
            int suitIndex = index;
            var suit = switch (suits[index]) {
                case CLUBS -> "♣"; case DIAMONDS -> "♦"; case HEARTS -> "♥"; case SPADES -> "♠";
            };
            var entries = cells.get(index).stream().map(cell -> Map.<String, Object>of(
                    "label", cell.label(), "played", cell.played(), "legal", cell.legal(),
                    "opening", cell.opening(), "action", cell.action())).toList();
            rows.add(Map.of("suit", suit, "cells", entries, "suitIndex", suitIndex));
        }
        return List.copyOf(rows);
    }

    public static List<Map<String, Object>> sequencePlayback(BrowserGame.Match match) {
        return match instanceof RentzTable table ? table.sequencePlayback() : List.of();
    }

    /** Public one-response cue for the native Rentz arrival pause; never part of a checkpoint. */
    public static Map<String, Object> arrival(BrowserGame.Match match, Observation before) {
        return match instanceof RentzTable table ? table.arrival(before) : null;
    }

    public static Observation observation(BrowserGame.Match match) {
        return match instanceof RentzTable table ? table.observation() : null;
    }

    @Override public List<Field> fields() {
        return fields("ro");
    }

    @Override public List<Field> fields(String language) {
        boolean ro = !"en".equals(language);
        var seats = new ArrayList<Option>();
        for (int count = 3; count <= 6; count++) seats.add(new Option("" + count, "" + count));
        return List.of(new Field("name", ro ? "Numele tău" : "Your name", "Tu", List.of()),
                new Field("players", ro ? "Jucători" : "Players", "4", seats),
                new Field("mode", ro ? "Joc" : "Game", "RENTZ", List.of(new Option("RENTZ", "Rentz"))),
                new Field("difficulty", ro ? "Nivelul adversarilor" : "Opponent level", "NORMAL",
                        List.of(new Option("NORMAL", ro ? "Normal" : "Normal"), new Option("HARD", ro ? "Greu" : "Hard"))),
                // The shared J4 setup omits "off" from its 5/10 selector. Keep the
                // disabled selector on a valid option so Rentz can start with defaults.
                new Field("series", ro ? "Bonus de serie" : "Streak bonus", "5",
                        List.of(new Option("0", ro ? "Dezactivat" : "Off"), new Option("5", "+5"), new Option("10", "+10"))),
                new Field("streaks", ro ? "Serii" : "Streaks", "no",
                        List.of(new Option("yes", ro ? "Da" : "Yes"), new Option("no", ro ? "Nu" : "No"))),
                new Field("zero", ro ? "Joc fără puncte" : "Zero game", "no",
                        List.of(new Option("yes", ro ? "Da" : "Yes"), new Option("no", ro ? "Nu" : "No"))));
    }

    @Override public Map<String, Object> interfaceCopy(String language) {
        boolean ro = !"en".equals(language);
        String modeTitle = "Rentz";
        ScoreProfile scoring = ScoreProfile.laboratory();
        var schedule = Map.of("3", ro ? "3 jucători · 24 mâini" : "3 players · 24 hands",
                "4", ro ? "4 jucători · 32 mâini" : "4 players · 32 hands",
                "5", ro ? "5 jucători · 40 mâini" : "5 players · 40 hands",
                "6", ro ? "6 jucători · 48 mâini" : "6 players · 48 hands");
        var rules = List.of(
                Map.of("title", ro ? "Desfășurarea partidei" : "Match flow", "body", ro
                        ? "Fiecare jucător alege fiecare dintre cele opt sub-jocuri o singură dată. Alegătorul deschide mâna."
                        : "Each player chooses each of the eight contracts once. The chooser leads the hand."),
                Map.of("title", "Rentz", "body", ro
                        ? "Deschizi de la Q / J / 10 / 9 la 3 / 4 / 5 / 6 jucători. Extinzi cu un rang vecin. Asul oferă încă o tură, iar rangul minim sare următorul jucător. Pasezi doar fără carte legală."
                        : "Open at Q / J / 10 / 9 for 3 / 4 / 5 / 6 players. Extend with a neighbouring rank. Ace grants another turn; the lowest rank skips the next player. Pass only when no legal card exists."),
                Map.of("title", ro ? "Levate fără atu" : "Trick contracts without trump", "body", ro
                        ? "Urmează culoarea cerută când poți. Cea mai mare carte din culoarea cerută câștigă levata."
                        : "Follow suit when possible. The highest card of the led suit wins the trick."),
                Map.of("title", ro ? "Premii de sosire Rentz" : "Rentz finishing awards", "body", finishingAwards(scoring, ro)),
                Map.of("title", ro ? "Punctajul contractelor" : "Contract scoring", "body", trickScoring(scoring, ro)),
                Map.of("title", ro ? "Alegerea pe nevăzute" : "Blind choice", "body", ro
                        ? "Alegi înainte să vezi cărțile și dublezi punctele mâinii pentru toți. Opțiunea nu este disponibilă la ultimul sub-joc rămas. Istoricul păstrează delta și totalul după fiecare mână."
                        : "Choose before seeing the cards to double that hand's points for everyone. Unavailable for the last remaining contract. History preserves each hand's delta and running total."));
        var modeRules = Map.of("RENTZ", Map.of("title", modeTitle,
                "description", ro ? "Opt sub-jocuri, câte unul ales de fiecare jucător." : "Eight contracts, each chosen once by every player.",
                "schedule", schedule, "romanian", false, "extras", List.of()));
        return Map.ofEntries(
                entry("language", ro ? "Limba" : "Language"), entry("home", "Rentz Lab"),
                entry("newGame", ro ? "Partidă nouă" : "New match"), entry("resume", ro ? "Continuă partida" : "Resume match"),
                entry("setup", ro ? "Setări de joc" : "Game setup"), entry("start", ro ? "Începe partida" : "Start match"),
                entry("back", ro ? "Înapoi" : "Back"), entry("name", ro ? "Numele tău" : "Your name"),
                entry("save", ro ? "Salvează" : "Save"), entry("reset", ro ? "Resetează numele" : "Reset name"),
                entry("cancel", ro ? "Anulează" : "Cancel"), entry("nameHint", ro ? "Alege un nume scurt pentru masa de joc." : "Choose a short table name."),
                entry("nameError", ro ? "Numele nu este valid." : "That name is not valid."),
                entry("achievements", ro ? "Realizări" : "Achievements"), entry("progress", ro ? "Progresul realizărilor" : "Achievement progress"),
                entry("close", ro ? "Închide" : "Close"), entry("help", ro ? "Cum se joacă" : "How to play"),
                entry("instructions", ro ? "Rentz · reguli și punctaj" : "Rentz · rules and scoring"),
                entry("instructionsSubtitle", ro ? "Regulile jocului" : "Game rules"),
                entry("rulesAndSchedule", ro ? "Reguli și program" : "Rules and schedule"), entry("modeRules", modeRules),
                entry("scoringDetails", ro ? "Detalii de punctaj" : "Scoring details"),
                entry("scoreSheet", ro ? "Scorul partidei" : "Match score"),
                entry("stopAutoGame", ro ? "Oprește jocul automat" : "Stop auto play"),
                entry("bidNow", ro ? "ALEGE" : "CHOOSE"), entry("playNow", ro ? "JOACĂ" : "PLAY"),
                entry("rules", rules), entry("streaks", ro ? "Serii" : "Streaks"),
                entry("bid", ro ? "Alege sub-jocul" : "Choose contract"),
                entry("history", ro ? "Istoricul scorului" : "Score history"),
                entry("mainMenu", ro ? "Meniu principal" : "Main menu"), entry("gameMenu", ro ? "Meniul partidei" : "Match menu"),
                entry("trump", ro ? "Sub-joc" : "Contract"), entry("hand", ro ? "Cărțile tale" : "Your cards"),
                entry("savedGameError", ro ? "Partida salvată nu a putut fi restaurată." : "The saved match could not be restored."),
                entry("storageError", ro ? "Salvarea locală nu este disponibilă în acest browser." : "Local saving is unavailable in this browser."),
                entry("ranking", ro ? "Rezultate" : "Results"), entry("rankingHint", ro ? "Rezultatele rămân în profilul acestui browser." : "Results stay in this browser profile."),
                entry("gameType", ro ? "Joc" : "Game"), entry("busy", ro ? "O comandă este deja în curs." : "A command is already in progress."));
    }

    private static String finishingAwards(ScoreProfile scoring, boolean ro) {
        var rows = new ArrayList<String>();
        for (int players = 3; players <= 6; players++) {
            String points = scoring.finishRewardsFor(players).stream()
                    .map(value -> value == 0 ? "0" : (value > 0 ? "+" : "−") + Math.abs(value))
                    .collect(java.util.stream.Collectors.joining(" / "));
            rows.add(players + (ro ? " jucători: " : " players: ") + points);
        }
        return String.join("; ", rows);
    }

    private static String trickScoring(ScoreProfile scoring, boolean ro) {
        return String.format(java.util.Locale.ROOT,
                ro ? "Levată %s · damă %s · caro %s · K♥ %s. Whist %s · 10♣ %s. La Totale se adună primele patru categorii. Alegerea pe nevăzute dublează punctele mâinii."
                        : "Trick %s · queen %s · diamond %s · K♥ %s. Whist %s · 10♣ %s. Totals combines the first four categories. Blind choice doubles the hand points.",
                score(-scoring.trickPenalty()), score(-scoring.queenPenalty()), score(-scoring.diamondPenalty()),
                score(-scoring.kingPenalty()), score(scoring.trickReward()), score(scoring.tenReward()));
    }

    private static String score(int value) {
        return value > 0 ? "+" + value : Integer.toString(value).replace('-', '−');
    }

    @Override public Match start(Map<String, String> config) {
        String name = PlayerNames.normalize(config.get("name"));
        var session = new GameSession(Integer.parseInt(config.get("players")), seeds.getAsLong(),
                RentzPlayer.Difficulty.valueOf(config.get("difficulty")), name);
        return new RentzTable(session, name);
    }
}
