package ro.rentzlab.app.ui.copy;

import ro.rentzlab.core.Contract;
import ro.rentzlab.core.ScoreProfile;

/** Portable product copy. Platform adapters supply widgets and locale persistence. */
public class RentzText {
    private final boolean romanian;
    public RentzText(boolean romanian) { this.romanian = romanian; }
    public String text(String en, String ro) { return romanian ? ro : en; }
    public String you() { return text("You", "Tu"); }
    public String humanName(String preferred) { return preferred.isBlank() ? you() : preferred; }
    public String mainMenu() { return text("Main menu", "Meniu principal"); }
    public String autoPlay() { return text("Auto play", "Joc automat"); }
    public String startAutoPlayHand() { return text("Auto-play this hand", "Joacă automat mâna curentă"); }
    public String stopAutoPlayHand() { return text("Stop auto-play", "Oprește jocul automat"); }
    public String history() { return text("Score history", "Istoricul scorului"); }
    public String achievements() { return text("Achievements", "Realizări"); }
    public String training() { return text("Training", "Antrenament"); }
    public String close() { return text("Close", "Închide"); }
    public String back() { return text("Back", "Înapoi"); }
    public String points() { return text("Points:", "Puncte:"); }
    public String total() { return text("Total:", "Total:"); }
    public String handProgress(int current, int total) { return text("Hand ", "Mâna ") + current + "/" + total; }
    public String playedContract(Contract contract, boolean blind) {
        return contract == null ? "" : contract(contract) + (blind ? " ×2" : "");
    }
    public String play(String label) { return text("Play ", "Joacă ") + label; }
    public String seatTurn(boolean choosing) { return choosing ? text("Choosing", "Alege") : text("Playing", "Joacă"); }
    public String finishingAwardTable(int players) { return players + text(" players", " jucători"); }
    public String finishingAwardDescription(int players, int place, int points) {
        return finishingAwardTable(players) + text(", place ", ", locul ") + place
                + ", " + points + text(" points", " puncte");
    }
    public String trickScoring(ScoreProfile scoring) {
        return String.format(java.util.Locale.ROOT,
                text("Trick %s · queen %s · diamond %s · K♥ %s. Whist %s · 10♣ %s.",
                        "Levată %s · damă %s · caro %s · K♥ %s. Whist %s · 10♣ %s."),
                points(-scoring.trickPenalty()), points(-scoring.queenPenalty()), points(-scoring.diamondPenalty()),
                points(-scoring.kingPenalty()), points(scoring.trickReward()), points(scoring.tenReward()));
    }
    private static String points(int value) { return value > 0 ? "+" + value : Integer.toString(value).replace('-', '−'); }
    public String contract(Contract contract) {
        if (romanian) return contract.label();
        return switch (contract) {
            case TRICKS -> "Tricks"; case QUEENS -> "Queens"; case DIAMONDS -> "Diamonds";
            case KING_HEARTS -> "King of hearts"; case TOTALS -> "Totals"; case WHIST -> "Whist";
            case TEN_CLUBS -> "Ten of clubs"; case RENTZ -> "Rentz";
        };
    }
}
