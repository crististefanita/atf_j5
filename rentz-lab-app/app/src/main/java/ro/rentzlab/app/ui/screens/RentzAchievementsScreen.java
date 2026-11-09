package ro.rentzlab.app.ui.screens;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import ro.cardtable.ui.screens.ScreenViews;
import ro.cardtable.ui.theme.TablePalette;
import ro.rentzlab.app.achievements.AchievementCatalog;
import ro.rentzlab.app.achievements.AchievementHand;
import ro.rentzlab.app.achievements.AchievementProgress;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.Contract;

/** Native, virtualized achievement browser with a scrollable filter sheet and readable details. */
public final class RentzAchievementsScreen {
    public static final class State {
        private AchievementBrowser.Query query = AchievementBrowser.Query.empty();
        private int position;
        private String selected;

        public void save(Bundle bundle) {
            bundle.putString("achievementQuery", query.text());
            bundle.putString("achievementLevel", enumName(query.level()));
            bundle.putString("achievementContract", enumName(query.contract()));
            bundle.putString("achievementStatus", enumName(query.status()));
            bundle.putString("achievementMode", enumName(query.mode()));
            bundle.putInt("achievementPosition", position);
            bundle.putString("achievementSelected", selected);
        }

        public void restore(Bundle bundle) {
            query = new AchievementBrowser.Query(bundle.getString("achievementQuery", ""),
                    choice(AchievementCatalog.Level.class, bundle.getString("achievementLevel")),
                    choice(Contract.class, bundle.getString("achievementContract")),
                    choice(AchievementBrowser.Status.class, bundle.getString("achievementStatus")),
                    choice(AchievementCatalog.PlayMode.class, bundle.getString("achievementMode")));
            position = bundle.getInt("achievementPosition");
            selected = bundle.getString("achievementSelected");
        }

        private static String enumName(Enum<?> value) { return value == null ? null : value.name(); }
        private static <E extends Enum<E>> E choice(Class<E> type, String name) {
            return name == null ? null : Enum.valueOf(type, name);
        }
    }

    private final ScreenViews views;

    public RentzAchievementsScreen(ScreenViews views) { this.views = views; }

    public void show(RentzUiText copy, String name, AchievementProgress progress, AchievementHand latest, State state,
            AchievementBrowser.Collection collection, Runnable profile, Runnable training, Runnable back) {
        views.applyTableWindowChrome();
        var page = views.gamePage();
        boolean isTraining = collection == AchievementBrowser.Collection.TRAINING;
        var browser = new AchievementBrowser(copy, progress, latest, collection);
        views.addText(page, isTraining ? copy.training() : copy.achievements(), 22, Color.WHITE, true, 8, 4);
        tableAction(page, copy.text("Local profile", "Profil local") + " · " + name, profile);
        views.addText(page, copy.text("Unlocked ", "Deblocate ") + browser.unlocked() + "/" + browser.total(),
                15, TablePalette.ACTIVE_SEAT_FILL, true, 3, 2);
        var progressBar = new ProgressBar(views.activity(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(browser.total());
        progressBar.setProgress((int) browser.unlocked());
        progressBar.setProgressTintList(ColorStateList.valueOf(TablePalette.ACTIVE_SEAT_STROKE));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(TablePalette.IDLE_SEAT_STROKE));
        progressBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        page.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, views.dp(4)));
        var list = new ListView(views.activity());
        list.setDivider(new ColorDrawable(TablePalette.TABLE_FELT));
        list.setDividerHeight(views.dp(5));
        var empty = views.text(copy.text("No matching achievements", "Nicio realizare găsită"), 15, Color.WHITE, false);
        empty.setGravity(android.view.Gravity.CENTER);
        var body = new android.widget.FrameLayout(views.activity());
        var searchLabel = copy.text("Search and filters", "Caută și filtrează");
        var filterLabel = views.text("", 13, TablePalette.IDLE_SEAT_TEXT, true);
        var rows = new Rows();
        list.setAdapter(rows);
        list.setContentDescription(copy.text("Learning path", "Parcurs de învățare"));
        list.setOnItemClickListener((parent, view, position, id) ->
                details(copy, browser, rows.getItem(position).badge(), state));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int status) { }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                if (visible > 0) state.position = first;
            }
        });
        if (isTraining) tableAction(page, searchLabel, () -> filters(copy, browser, state,
                () -> refresh(state, browser, list, empty, filterLabel, rows, copy)));
        else tableAction(page, copy.training(), training);
        page.addView(filterLabel, views.matchWrap());
        body.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(empty, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tableAction(page, copy.back(), back);
        refresh(state, browser, list, empty, filterLabel, rows, copy);
        if (state.selected != null) {
            rows.items.stream().filter(row -> !row.heading() && row.badge().id().equals(state.selected))
                    .findFirst().ifPresent(row -> details(copy, browser, row.badge(), state));
        }
    }

    private void tableAction(LinearLayout page, String label, Runnable action) {
        var button = new Button(views.activity());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setMinHeight(views.dp(44));
        button.setPadding(views.dp(10), views.dp(5), views.dp(10), views.dp(5));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x28ffffff),
                views.roundedBackground(TablePalette.IDLE_SEAT_FILL, TablePalette.IDLE_SEAT_STROKE, 12), null));
        button.setOnClickListener(view -> action.run());
        var layout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.setMargins(0, views.dp(4), 0, views.dp(4));
        page.addView(button, layout);
    }

    private void refresh(State state, AchievementBrowser browser, ListView list, TextView empty, TextView filterLabel,
            Rows rows, RentzUiText copy) {
        var filtered = browser.rows(state.query);
        int position = Math.min(state.position, Math.max(0, filtered.size() - 1));
        rows.items = filtered;
        rows.notifyDataSetChanged();
        list.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        if (!filtered.isEmpty()) list.setSelection(position);
        filterLabel.setVisibility(state.query.active() ? View.VISIBLE : View.GONE);
        filterLabel.setText(copy.text("Matching achievements ", "Realizări găsite ")
                + filtered.stream().filter(row -> !row.heading()).count());
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
        state.position = 0;
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

    private void details(RentzUiText copy, AchievementBrowser browser, AchievementCatalog.Badge badge, State state) {
        if (badge == null) return;
        state.selected = badge.id();
        var detail = browser.details(badge);
        var content = new LinearLayout(views.activity());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(views.dp(20), views.dp(4), views.dp(20), views.dp(12));
        views.addText(content, detail.progress(), 14, ScreenViews.ACCENT, true, 4, 6);
        views.addText(content, copy.text("Eligibility", "Eligibilitate"), 14, ScreenViews.PINE, true, 8, 2);
        views.addText(content, detail.rule(), 14, ScreenViews.INK, false, 0, 4);
        views.addText(content, detail.context(), 13, ScreenViews.INK, false, 0, 4);
        var contextTitle = badge.mode() == AchievementCatalog.PlayMode.MANUAL_MATCH
                ? copy.text("Current match", "Partida curentă")
                : copy.text("Last completed hand", "Ultima mână încheiată");
        views.addText(content, contextTitle, 14, ScreenViews.PINE, true, 8, 2);
        views.addText(content, detail.latest(), 13, ScreenViews.INK, false, 0, 0);
        var scroll = new ScrollView(views.activity());
        scroll.addView(content);
        var dialog = new AlertDialog.Builder(views.activity()).setTitle(detail.title()).setView(scroll)
                .setPositiveButton(copy.text("Close", "Închide"), null).create();
        dialog.setOnDismissListener(d -> state.selected = null);
        dialog.show();
    }

    private final class Rows extends BaseAdapter {
        private List<AchievementBrowser.Row> items = List.of();
        @Override public int getCount() { return items.size(); }
        @Override public AchievementBrowser.Row getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return valid(position) ? getItem(position).itemId() : AdapterView.INVALID_ROW_ID; }
        @Override public boolean hasStableIds() { return true; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int position) { return valid(position) && !getItem(position).heading(); }
        private boolean valid(int position) { return position >= 0 && position < items.size(); }
        @Override public View getView(int position, View reusable, ViewGroup parent) {
            LinearLayout item;
            if (reusable instanceof LinearLayout existing) {
                item = existing;
            } else {
                item = new LinearLayout(views.activity());
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(views.dp(16), views.dp(8), views.dp(16), views.dp(8));
                item.addView(views.text("", 15, ScreenViews.PINE, true), views.matchWrap());
                item.addView(views.text("", 13, ScreenViews.INK, false), views.matchWrap());
            }
            var row = getItem(position);
            var title = (TextView) item.getChildAt(0);
            var detail = (TextView) item.getChildAt(1);
            boolean unlocked = row.status() == AchievementBrowser.Status.UNLOCKED;
            item.setBackground(row.heading() ? null : views.roundedBackground(
                    unlocked ? TablePalette.ACTIVE_SEAT_FILL : TablePalette.IDLE_SEAT_FILL,
                    unlocked ? TablePalette.ACTIVE_SEAT_STROKE : TablePalette.IDLE_SEAT_STROKE, 12));
            title.setText(row.title());
            title.setTextColor(row.heading() ? TablePalette.ACTIVE_SEAT_FILL : unlocked ? ScreenViews.PINE : Color.WHITE);
            if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(row.heading());
            detail.setText(row.subtitle());
            detail.setTextColor(unlocked ? ScreenViews.PINE : TablePalette.IDLE_SEAT_TEXT);
            detail.setVisibility(row.heading() ? View.GONE : View.VISIBLE);
            return item;
        }
    }
}
