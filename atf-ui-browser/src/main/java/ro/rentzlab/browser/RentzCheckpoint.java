package ro.rentzlab.browser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.GameSession;
import ro.rentzlab.core.Card;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;
import ro.rentzlab.core.sequence.SequenceOpening;

/** Strict, bounded local checkpoint codec for browser storage; commands replay through the J5 session. */
final class RentzCheckpoint {
    private static final String VERSION = "rentz-browser-v1";
    private static final int MAX_CHARACTERS = 1_000_000;
    private static final int MAX_COMMANDS = 5_000;

    private RentzCheckpoint() { }

    static String encode(GameSession.Saved saved) {
        var lines = new ArrayList<String>();
        var header = new ArrayList<String>(List.of(VERSION, Integer.toString(saved.players()),
                Long.toString(saved.seed()), saved.difficulty().name(), saved.dealEnding().name(),
                saved.sequenceOpening().name()));
        saved.opponents().forEach(name -> header.add(encodeText(name)));
        lines.add(String.join("\t", header));
        for (var command : saved.commands()) {
            var card = command.card();
            lines.add(String.join("\t", "c", command.action().name(), Integer.toString(command.seat()),
                    card == null ? "-" : card.suit().name(), card == null ? "-" : Integer.toString(card.rank()),
                    command.contract() == null ? "-" : command.contract().name(), Boolean.toString(command.blind()),
                    command.origin().name()));
        }
        String checkpoint = String.join("\n", lines);
        if (checkpoint.length() > MAX_CHARACTERS) throw new IllegalStateException("Rentz checkpoint exceeds its size limit");
        return checkpoint;
    }

    static GameSession restore(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_CHARACTERS) throw invalid();
        String[] lines = text.split("\n", -1);
        String[] header = lines[0].split("\t", -1);
        if (header.length < 8 || !VERSION.equals(header[0])) throw invalid();
        int players = Integer.parseInt(header[1]);
        if (players < 3 || players > 6 || header.length != 6 + players - 1) throw invalid();
        long seed = Long.parseLong(header[2]);
        RentzPlayer.Difficulty difficulty = RentzPlayer.Difficulty.valueOf(header[3]);
        RentzMatch.DealEnding ending = RentzMatch.DealEnding.valueOf(header[4]);
        SequenceOpening opening = SequenceOpening.valueOf(header[5]);
        var opponents = new ArrayList<String>(players - 1);
        for (int index = 6; index < header.length; index++) opponents.add(decodeText(header[index]));
        if (lines.length - 1 > MAX_COMMANDS) throw invalid();
        var commands = new ArrayList<GameSession.Command>(Math.max(0, lines.length - 1));
        for (int index = 1; index < lines.length; index++) {
            String[] row = lines[index].split("\t", -1);
            if (row.length != 8 || !"c".equals(row[0])) throw invalid();
            var action = GameSession.Action.valueOf(row[1]);
            int seat = Integer.parseInt(row[2]);
            Card card = "-".equals(row[3]) ? null : new Card(Card.Suit.valueOf(row[3]), Integer.parseInt(row[4]));
            if (card == null && !"-".equals(row[4])) throw invalid();
            Contract contract = "-".equals(row[5]) ? null : Contract.valueOf(row[5]);
            boolean blind = switch (row[6]) { case "true" -> true; case "false" -> false; default -> throw invalid(); };
            var origin = GameSession.Origin.valueOf(row[7]);
            commands.add(new GameSession.Command(action, seat, card, contract, blind, origin));
        }
        return GameSession.restore(new GameSession.Saved(1, players, seed, difficulty, commands, opponents, ending, opening));
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid Rentz browser checkpoint");
    }
}
