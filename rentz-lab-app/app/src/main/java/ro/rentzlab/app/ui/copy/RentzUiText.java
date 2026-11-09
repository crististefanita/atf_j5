package ro.rentzlab.app.ui.copy;

import ro.cardtable.ui.menu.HomeMenuContent;
import ro.cardtable.ui.profile.PlayerNameDialog;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.ScoreProfile;

/** Product copy and contract names belong to Rentz, outside the neutral renderers. */
public final class RentzUiText {
    public enum Language { EN, RO }
    private final Language language;
    public RentzUiText(Language language) { this.language = language; }
    public Language language() { return language; }
    public String text(String en, String ro) { return language == Language.RO ? ro : en; }
    public String you() { return text("You", "Tu"); }
    public String humanName(String preferred) { return preferred.isBlank() ? you() : preferred; }
    public String mainMenu() { return text("Main menu", "Meniu principal"); }
    public String autoPlay() { return text("Auto play", "Joc automat"); }
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
    public String trickScoring(ScoreProfile scoring) {
        return String.format(java.util.Locale.ROOT,
                text("Trick %s · queen %s · diamond %s · K♥ %s. Whist %s · 10♣ %s.",
                        "Levată %s · damă %s · caro %s · K♥ %s. Whist %s · 10♣ %s."),
                points(-scoring.trickPenalty()), points(-scoring.queenPenalty()), points(-scoring.diamondPenalty()),
                points(-scoring.kingPenalty()), points(scoring.trickReward()), points(scoring.tenReward()));
    }
    private static String points(int value) { return value > 0 ? "+" + value : Integer.toString(value).replace('-', '−'); }
    public String contract(Contract contract) {
        if (language == Language.RO) return contract.label();
        return switch (contract) {
            case TRICKS -> "Tricks"; case QUEENS -> "Queens"; case DIAMONDS -> "Diamonds";
            case KING_HEARTS -> "King of hearts"; case TOTALS -> "Totals"; case WHIST -> "Whist";
            case TEN_CLUBS -> "Ten of clubs"; case RENTZ -> "Rentz";
        };
    }
    public HomeMenuContent home() {
        return new HomeMenuContent("Rentz", text("Language", "Limbă"), language == Language.EN ? 0 : 1,
                text("Player", "Jucător"), you(), text("New game", "Partidă nouă"), text("Resume game", "Continuă partida"),
                text("Scores", "Scoruri"), text("How to play", "Cum se joacă"));
    }
    public PlayerNameDialog.Text profile() {
        return new PlayerNameDialog.Text(text("Player", "Jucător"), text("Your name", "Numele tău"), text("Save", "Salvează"),
                text("Reset", "Resetează"), close(), text("Use 1–10 letters, digits, spaces, apostrophes or hyphens", "Folosește 1–10 litere, cifre, spații, apostrofuri sau cratime"),
                text("The preference could not be saved", "Preferința nu a putut fi salvată"));
    }
    public String selector(String id) {
        return switch (id) {
            case "interface-language" -> text("Interface language", "Limba interfeței");
            case "rentz-players" -> text("Players", "Jucători");
            case "rentz-difficulty" -> text("Opponents", "Adversari");
            default -> id;
        };
    }
}
