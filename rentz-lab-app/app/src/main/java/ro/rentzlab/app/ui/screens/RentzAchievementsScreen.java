package ro.rentzlab.app.ui.screens;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import ro.cardtable.ui.achievements.AchievementScreen;
import ro.cardtable.ui.screens.ScreenViews;
import ro.rentzlab.app.achievements.AchievementCatalog;
import ro.rentzlab.app.achievements.AchievementHand;
import ro.rentzlab.app.achievements.AchievementProgress;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.Contract;

/** Rentz projection and filters for the shared achievement screen. */
public final class RentzAchievementsScreen {
    public static final class State {
        private AchievementBrowser.Query query = AchievementBrowser.Query.empty();
        private final AchievementScreen.State screen = new AchievementScreen.State();

        public void save(Bundle bundle) {
            bundle.putString("achievementQuery", query.text());
            bundle.putString("achievementLevel", enumName(query.level()));
            bundle.putString("achievementContract", enumName(query.contract()));
            bundle.putString("achievementStatus", enumName(query.status()));
            bundle.putString("achievementMode", enumName(query.mode()));
            screen.save(bundle);
        }

        public void restore(Bundle bundle) {
            query = new AchievementBrowser.Query(bundle.getString("achievementQuery", ""),
                    choice(AchievementCatalog.Level.class, bundle.getString("achievementLevel")),
                    choice(Contract.class, bundle.getString("achievementContract")),
                    choice(AchievementBrowser.Status.class, bundle.getString("achievementStatus")),
                    choice(AchievementCatalog.PlayMode.class, bundle.getString("achievementMode")));
            screen.restore(bundle);
        }

        private static String enumName(Enum<?> value) { return value == null ? null : value.name(); }
        private static <E extends Enum<E>> E choice(Class<E> type, String name) {
            return name == null ? null : Enum.valueOf(type, name);
        }
    }

    private final ScreenViews views;
    private AchievementScreen screen;

    public RentzAchievementsScreen(ScreenViews views) { this.views = views; }

    public void show(RentzUiText copy, String name, AchievementProgress progress, AchievementHand latest, State state,
            AchievementBrowser.Collection collection, AchievementBrowser.Table table, Runnable profile,
            Runnable training, Runnable back) {
        boolean isTraining = collection == AchievementBrowser.Collection.TRAINING;
        var browser = new AchievementBrowser(copy, progress, latest, collection, table);
        int earned = (int) browser.unlocked();
        var labels = new AchievementScreen.Copy(isTraining ? copy.training() : copy.achievements(),
                copy.text("Unlocked ", "Deblocate ") + earned + "/" + browser.total(),
                copy.text("No matching achievements", "Nicio realizare găsită"),
                copy.text("Learning path", "Parcurs de învățare"), copy.close());
        var profileAction = new AchievementScreen.Action(copy.text("Local profile", "Profil local") + " · " + name, profile);
        var collectionAction = isTraining
                ? new AchievementScreen.Action(copy.text("Search and filters", "Caută și filtrează"),
                        () -> filters(copy, browser, state, () -> refresh(copy, browser, state)))
                : new AchievementScreen.Action(copy.training(), training);
        screen = new AchievementScreen(views, state.screen, labels, earned, browser.total(),
                List.of(profileAction, collectionAction), new AchievementScreen.Action(copy.back(), back));
        refresh(copy, browser, state);
        screen.restoreDetail();
    }

    private void refresh(RentzUiText copy, AchievementBrowser browser, State state) {
        var rows = browser.rows(state.query);
        String summary = state.query.active()
                ? copy.text("Matching achievements ", "Realizări găsite ")
                        + rows.stream().filter(row -> !row.heading()).count()
                : "";
        screen.update(rows.stream().map(row -> present(copy, browser, row)).collect(Collectors.toList()), summary);
    }

    private static AchievementScreen.Row present(RentzUiText copy, AchievementBrowser browser, AchievementBrowser.Row row) {
        if (row.heading()) return AchievementScreen.Row.heading(row.itemId(), row.title());
        var badge = row.badge();
        return new AchievementScreen.Row(row.itemId(), badge.id(), row.title(), row.subtitle(),
                row.status() == AchievementBrowser.Status.UNLOCKED, () -> {
                    var detail = browser.details(badge);
                    var contextTitle = badge.mode() == AchievementCatalog.PlayMode.MANUAL_MATCH
                            ? copy.text("Current match", "Partida curentă")
                            : copy.text("Last completed hand", "Ultima mână încheiată");
                    return new AchievementScreen.Detail(detail.progress(), List.of(
                            new AchievementScreen.Section(copy.text("Eligibility", "Eligibilitate"), detail.rule()),
                            new AchievementScreen.Section("", detail.context()),
                            new AchievementScreen.Section(contextTitle, detail.latest())));
                });
    }

    private void filters(RentzUiText copy, AchievementBrowser browser, State state, Runnable refresh) {
        var activity = views.activity();
        var content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(views.dp(20), views.dp(8), views.dp(20), views.dp(8));
        views.addText(content, copy.text("Next to learn", "Următorul pas") + " · " + browser.recommendation(),
                14, ScreenViews.PINE, true, 4, 8);
        var search = new EditText(activity);
        search.setSingleLine();
        search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(96)});
        search.setHint(copy.text("Title or rule", "Titlu sau regulă"));
        search.setContentDescription(copy.text("Search achievements", "Caută realizări"));
        search.setText(state.query.text());
        content.addView(search, views.matchWrap());

        var level = filter(content, copy.text("Difficulty", "Dificultate"), copy.text("Difficulty filter", "Filtru dificultate"),
                copy, AchievementCatalog.Level.values(), state.query.level(), browser::level);
        var contract = filter(content, copy.text("Contract", "Contract"), copy.text("Contract filter", "Filtru contract"),
                copy, Contract.values(), state.query.contract(), copy::contract);
        var status = filter(content, copy.text("Status", "Stare"), copy.text("Status filter", "Filtru stare"),
                copy, AchievementBrowser.Status.values(), state.query.status(), browser::statusLabel);
        var modes = new AchievementCatalog.PlayMode[]{AchievementCatalog.PlayMode.MANUAL, AchievementCatalog.PlayMode.ASSISTED};
        var mode = filter(content, copy.text("Play mode", "Mod de joc"), copy.text("Play mode filter", "Filtru mod de joc"),
                copy, modes, state.query.mode(), value -> value == AchievementCatalog.PlayMode.MANUAL
                        ? copy.text("Manual allowed", "Permite joc manual") : copy.text("Assistance allowed", "Permite asistență"));

        var scroll = new ScrollView(activity);
        scroll.addView(content);
        var dialog = new AlertDialog.Builder(activity)
                .setTitle(copy.text("Search and filters", "Caută și filtrează"))
                .setView(scroll)
                .setPositiveButton(copy.text("Apply", "Aplică"), (d, button) -> apply(state,
                        new AchievementBrowser.Query(search.getText().toString().trim(), level.value(), contract.value(),
                                status.value(), mode.value()), refresh))
                .setNegativeButton(copy.text("Reset", "Resetează"), (d, button) -> apply(state, AchievementBrowser.Query.empty(), refresh))
                .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }

    private static void apply(State state, AchievementBrowser.Query query, Runnable refresh) {
        state.query = query;
        state.screen.resetPosition();
        refresh.run();
    }

    private record Filter<E>(Spinner spinner, List<E> values) {
        E value() { return spinner.getSelectedItemPosition() > 0 ? values.get(spinner.getSelectedItemPosition() - 1) : null; }
    }

    private <E> Filter<E> filter(LinearLayout content, String label, String description, RentzUiText copy,
            E[] options, E selected, Function<E, String> title) {
        var values = Arrays.asList(options);
        var labels = new ArrayList<String>();
        labels.add(copy.text("All", "Toate"));
        for (var value : values) labels.add(title.apply(value));
        var spinner = views.spinner(labels, description);
        spinner.setContentDescription(description);
        spinner.setSelection(values.indexOf(selected) + 1);
        views.addText(content, label, 13, ScreenViews.PINE, true, 6, 0);
        content.addView(spinner, views.matchWrap());
        return new Filter<>(spinner, values);
    }

}
