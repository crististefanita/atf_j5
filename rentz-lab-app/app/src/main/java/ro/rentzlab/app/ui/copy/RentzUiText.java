package ro.rentzlab.app.ui.copy;

import ro.cardtable.ui.menu.HomeMenuContent;
import ro.cardtable.ui.profile.PlayerNameDialog;

/** Product copy and contract names belong to Rentz, outside the neutral renderers. */
public final class RentzUiText extends RentzText {
    public enum Language { EN, RO }
    private final Language language;
    public RentzUiText(Language language) { super(language == Language.RO); this.language = language; }
    public Language language() { return language; }
    public HomeMenuContent home() {
        return new HomeMenuContent("Rentz", text("Language", "Limbă"), language == Language.EN ? 0 : 1,
                text("Player", "Jucător"), you(), text("New game", "Partidă nouă"), text("Resume game", "Continuă partida"),
                text("Scores", "Scoruri"), achievements(), text("How to play", "Cum se joacă"));
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
