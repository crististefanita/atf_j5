package ro.rentzlab.app.ui.screens;

import android.app.AlertDialog;
import android.widget.Spinner;
import ro.cardtable.ui.menu.*;
import ro.cardtable.ui.screens.ScreenViews;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.app.ui.navigation.SetupSelection;
import ro.rentzlab.app.ui.score.RentzAwardGrid;
import ro.rentzlab.app.ui.score.RentzScoreContent;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.core.ScoreProfile;
import java.util.List;
import java.util.function.IntConsumer;

/** Shared menu composition; every route and capability is supplied by the Rentz application. */
public final class RentzMenus {
    private final ScreenViews views;
    public RentzMenus(ScreenViews views) { this.views = views; }
    public void home(RentzUiText copy, String name, boolean resume, HomeMenuContent.Actions actions, IntConsumer language,
            Runnable achievements) {
        views.applyStandardWindowChrome();
        var labels = copy.home();
        var page = views.page(null, labels.title());
        new HomeMenu(views).languagePicker(page, labels.languageLabel(), labels.selectedLanguage(), language);
        for (var control : labels.controls(name, resume, actions)) {
            if (control.tag().equals("btn_instructions")) views.addQuietButton(page, copy.achievements(), achievements);
            if (control.kind() == HomeMenuContent.Kind.QUIET) views.addQuietButton(page, control.label(), control.action());
            else views.addButton(page, control.label(), control.tag(), control.enabled(), control.action(), control.primary());
        }
    }
    public void setup(RentzUiText copy, SetupSelection selection, boolean replacing, IntConsumer language, Runnable start, Runnable back,
            java.util.function.BooleanSupplier current) {
        views.applyStandardWindowChrome();
        var page = views.page(null, copy.text("New game", "Partidă nouă"));
        new HomeMenu(views).languagePicker(page, copy.home().languageLabel(), copy.home().selectedLanguage(), language);
        Spinner players = views.spinner(List.of("3", "4", "5", "6"), "rentz-players"); players.setSelection(selection.players() - 3);
        Spinner level = views.spinner(List.of("Normal", "Hard"), "rentz-difficulty"); level.setSelection(selection.difficulty() == RentzPlayer.Difficulty.NORMAL ? 0 : 1);
        views.addSetupSelector(page, copy.text("Players", "Jucători"), players);
        views.addSetupSelector(page, copy.text("Opponents", "Adversari"), level);
        views.addText(page, copy.text("Laboratory profile · eight sub-games", "Profil laborator · opt sub-jocuri"), 13, ScreenViews.MUTED, false, 12, 4);
        views.addButton(page, copy.text("Start game", "Începe partida"), "btn_start_game", true, () -> {
            selection.players(players.getSelectedItemPosition() + 3);
            selection.difficulty(level.getSelectedItemPosition() == 0 ? RentzPlayer.Difficulty.NORMAL : RentzPlayer.Difficulty.HARD);
            if (!replacing) start.run();
            else new AlertDialog.Builder(views.activity()).setMessage(copy.text("Replace the current game?", "Înlocuiești partida curentă?"))
                    .setNegativeButton(copy.text("Cancel", "Renunță"), null).setPositiveButton(copy.text("Start", "Începe"), (dialog, which) -> start.run()).show();
        }, true);
        views.addQuietButton(page, copy.back(), back);
        // Selection changes are captured before a language-triggered re-render.
        players.setOnItemSelectedListener(listener(index -> { if (current.getAsBoolean()) selection.players(index + 3); }));
        level.setOnItemSelectedListener(listener(index -> { if (current.getAsBoolean()) selection.difficulty(index == 0 ? RentzPlayer.Difficulty.NORMAL : RentzPlayer.Difficulty.HARD); }));
    }
    private android.widget.AdapterView.OnItemSelectedListener listener(IntConsumer changed) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { changed.accept(position); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
    }
    public void help(RentzUiText copy, ScoreProfile scoring, Runnable back) {
        views.applyStandardWindowChrome();
        var page = views.page("Rentz", copy.text("How to play", "Cum se joacă"));
        views.addRule(page, copy.text("Eight cards", "Opt cărți"), copy.text("Each player chooses each sub-game once. The chooser leads the hand.", "Fiecare jucător alege fiecare sub-joc o dată. Alegătorul deschide mâna."));
        views.addRule(page, "Rentz", copy.text("Open at Q / J / 10 / 9 for 3 / 4 / 5 / 6 players. Extend with a neighbouring rank. Ace: another turn; lowest rank: skip. Pass only with no legal card.", "Deschizi din Q / J / 10 / 9 la 3 / 4 / 5 / 6 jucători. Extinzi cu rang vecin. As: încă o tură; rang minim: săritură. Pas numai fără carte legală."));
        views.addRule(page, copy.text("Rentz finishing awards", "Premii de sosire Rentz"),
                copy.text("Base points by place. A dash marks an unavailable place.",
                        "Puncte de bază pe loc. Linia marchează un loc inexistent."));
        page.addView(RentzAwardGrid.create(views, copy, RentzScoreContent.finishingAwards(scoring)), views.matchWrap());
        views.addRule(page, copy.text("Tricks without trump", "Levate fără atu"), copy.text("Follow suit when possible. The highest card of the led suit wins.", "Urmezi culoarea cerută când poți. Cea mai mare carte a culorii cerute câștigă."));
        views.addRule(page, copy.text("Laboratory scoring", "Punctaj de laborator"), copy.trickScoring(scoring));
        views.addRule(page, copy.text("Blind choice", "Pe nevăzute"), copy.text("Choose before revealing the hand to double the hand points for everyone. Unavailable for the last remaining sub-game.", "Alegi înainte să vezi mâna și dublezi punctele mâinii pentru toți. Indisponibil la ultimul sub-joc rămas."));
        views.addQuietButton(page, copy.back(), back);
    }
}
