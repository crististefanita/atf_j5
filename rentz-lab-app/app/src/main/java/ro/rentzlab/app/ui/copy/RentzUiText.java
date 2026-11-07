package ro.rentzlab.app.ui.copy;

import ro.cardtable.ui.menu.HomeMenuContent;
import ro.cardtable.ui.profile.PlayerNameDialog;
import ro.rentzlab.core.Contract;

/** Product copy and contract names belong to Rentz, outside the neutral renderers. */
public final class RentzUiText {
    public enum Language { EN, RO }
    private final Language language;
    public RentzUiText(Language language) { this.language = language; }
    public Language language() { return language; }
    public String text(String en, String ro) { return language == Language.RO ? ro : en; }
    public String you() { return text("You", "Tu"); }
    public String name(int seat, String preferred) { return seat == 0 ? (preferred.isBlank() ? you() : preferred) : "AI " + seat; }
    public String mainMenu() { return text("Main menu", "Meniu principal"); }
    public String history() { return text("Score history", "Istoricul scorului"); }
    public String close() { return text("Close", "Închide"); }
    public String back() { return text("Back", "Înapoi"); }
    public String points() { return text("Points:", "Puncte:"); }
    public String total() { return text("Total:", "Total:"); }
    public String play(String label) { return text("Play ", "Joacă ") + label; }
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
