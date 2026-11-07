package ro.rentzlab.app;

import static ro.rentzlab.app.ui.collection.UiLists.immutable;

import android.app.*;
import android.os.*;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import ro.cardtable.ui.menu.HomeMenuContent;
import ro.cardtable.ui.profile.PlayerNameDialog;
import ro.cardtable.ui.screens.ScreenViews;
import ro.rentzlab.app.lifecycle.SessionController;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.app.ui.navigation.*;
import ro.rentzlab.app.ui.profile.*;
import ro.rentzlab.app.ui.screens.*;
import ro.rentzlab.app.ui.table.*;
import ro.rentzlab.app.ui.window.WindowContent;
import ro.rentzlab.app.ui.window.BackNavigation;
import ro.rentzlab.core.*;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Lifecycle and commands stay in Rentz; neutral widgets receive only public presentation data. */
public final class MainActivity extends Activity {
    private SessionController controller;
    private ProfileSettings profile;
    private ScreenViews views;
    private final SetupSelection setup = new SetupSelection();
    private final TrickPreview preview = new TrickPreview();
    private final Handler display = new Handler(Looper.getMainLooper());
    private String screen = "MENU", historyReturn = "MENU";
    private long generation;
    private boolean active;
    private AlertDialog historyDialog;
    private Exception pendingProblem;
    private Runnable unregisterBack = () -> { };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        profile = new ProfileSettings(new AndroidPreferences(this));
        views = new ScreenViews(this, id -> copy().selector(id), WindowContent::protect);
        controller = new SessionController(((RentzApplication) getApplication()).sessions(), Executors.newSingleThreadExecutor(), this::runOnUiThread,
                new SessionController.Listener() {
                    public void ready() { if (active) redraw(); }
                    public void failed(Exception error) { showFailure(error); }
                });
        if (saved != null) {
            screen = saved.getString("route", "MENU");
            if (!java.util.Set.of("MENU", "SETUP", "HELP", "GAME", "HISTORY").contains(screen)) screen = "MENU";
            setup.players(saved.getInt("players", 4));
            setup.difficulty(ro.rentzlab.ai.RentzPlayer.Difficulty.valueOf(saved.getString("difficulty", "HARD")));
            historyReturn = saved.getString("historyReturn", "MENU");
        }
        try { controller.activate(); } catch (Exception error) { showFailure(error); }
        unregisterBack = BackNavigation.register(this, this::back);
    }
    private RentzUiText copy() { return new RentzUiText(profile.language()); }
    private void route(String value) { screen = value; redraw(); }
    private void redraw() {
        if (!active || isFinishing() || isDestroyed()) return;
        display.removeCallbacksAndMessages(null);
        long frame = ++generation;
        var copy = copy(); var menus = new RentzMenus(views);
        java.util.function.IntConsumer language = index -> { if (frame == generation) language(index); };
        switch (screen) {
            case "SETUP" -> menus.setup(copy, setup, controller.session() != null, language, this::start, () -> route("MENU"), () -> frame == generation);
            case "HELP" -> menus.help(copy, () -> route("MENU"));
            case "GAME" -> game(frame);
            case "HISTORY" -> history(frame);
            default -> menus.home(copy, profile.preferredName(), controller.session() != null, new HomeMenuContent.Actions(
                    this::profile, () -> route("SETUP"), this::resume, this::openHistory, () -> route("HELP")), language);
        }
        publish();
    }
    private void language(int index) {
        var next = index == 0 ? RentzUiText.Language.EN : RentzUiText.Language.RO;
        if (next == profile.language()) return;
        try { profile.language(next); redraw(); } catch (IllegalStateException error) { showFailure(error); }
    }
    private void profile() {
        PlayerNameDialog.show(this, copy().profile(), profile.preferredName(), DisplayName.MAX_LENGTH, DisplayName::normalize,
                value -> { profile.name(value); redraw(); });
    }
    private void start() {
        if (controller.newGame(setup.players(), System.currentTimeMillis(), setup.difficulty())) {
            preview.reset(controller.session().view()); route("GAME");
        }
    }
    private void resume() {
        if (controller.session() == null) return;
        preview.reset(controller.session().view());
        if (controller.resume()) route("GAME");
    }
    private void game(long frame) {
        var session = controller.session();
        if (session == null) { route("MENU"); return; }
        var view = session.view(); var copy = copy();
        long now = SystemClock.elapsedRealtime();
        var visible = preview.display(view, now); boolean holding = preview.remaining(now) > 0;
        var page = views.gamePage(); views.applyTableWindowChrome();
        var header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        var options = new Button(this); options.setText("☰"); options.setTextSize(20); options.setAllCaps(false);
        options.setBackgroundColor(android.graphics.Color.TRANSPARENT); options.setTextColor(android.graphics.Color.WHITE);
        options.setContentDescription(copy.text("Options", "Opțiuni")); options.setEnabled(!controller.isBusy());
        options.setOnClickListener(ignored -> {
            var menu = new PopupMenu(this, options);
            menu.getMenu().add(copy.history()).setOnMenuItemClickListener(item -> { openHistory(); return true; });
            menu.getMenu().add(copy.mainMenu()).setOnMenuItemClickListener(item -> { route("MENU"); return true; });
            menu.show();
        });
        header.addView(options, new LinearLayout.LayoutParams(views.dp(48), views.dp(48)));
        String title = copy.text("Hand ", "Mâna ") + view.handNumber() + "/" + view.players() * 8
                + (view.contract() == null ? "" : " · " + copy.contract(view.contract()) + (view.blind() ? " ×2" : ""));
        var heading = views.text(title, 16, android.graphics.Color.WHITE, true);
        heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setContentDescription(title + (controller.isBusy() ? copy.text(". Opponents playing", ". Adversarii joacă") : ""));
        header.addView(heading, new LinearLayout.LayoutParams(0, views.dp(48), 1f)); page.addView(header);
        var permit = new InputPermit(session.save().commands().size(), frame);
        var actions = new RentzTablePanel.Actions(
                () -> command(permit, () -> new GameSession.Command(GameSession.Action.REVEAL, 0, null, null, false)),
                () -> choose(permit, false), () -> choose(permit, true),
                () -> command(permit, () -> new GameSession.Command(GameSession.Action.PASS, 0, null, null, false)),
                () -> command(permit, () -> new GameSession.Command(GameSession.Action.NEXT_HAND, 0, null, null, false)),
                id -> command(permit, () -> new GameSession.Command(GameSession.Action.PLAY, 0, RentzTableContent.selected(controller.session().view(), id), null, false)));
        page.addView(new RentzTablePanel(this, view, copy, profile.preferredName(), controller.isBusy() || holding, holding, visible, actions),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (holding) display.postDelayed(() -> { if (active && generation == frame && screen.equals("GAME")) redraw(); }, preview.remaining(now));
    }
    private void choose(InputPermit permit, boolean blind) {
        var state = controller.session().view(); var copy = copy();
        var available = Arrays.stream(Contract.values()).filter(state.available()::contains).collect(immutable());
        new AlertDialog.Builder(this).setTitle(copy.text("Choose game", "Alege sub-jocul") + (blind ? " ×2" : ""))
                .setItems(available.stream().map(copy::contract).toArray(String[]::new), (dialog, which) ->
                        command(permit, () -> new GameSession.Command(GameSession.Action.CHOOSE, 0, null, available.get(which), blind)))
                .setNegativeButton(copy.text("Cancel", "Renunță"), null).show();
    }
    private void command(InputPermit permit, Supplier<GameSession.Command> command) {
        var session = controller.session();
        if (!active || !screen.equals("GAME") || session == null
                || !permit.consume(session.save().commands().size(), generation, controller.isBusy())) return;
        try {
            preview.display(session.view(), SystemClock.elapsedRealtime());
            if (controller.apply(command.get())) redraw();
        } catch (Exception error) { showFailure(error); }
    }
    private void openHistory() { historyReturn = screen.equals("GAME") ? "GAME" : "MENU"; route("HISTORY"); }
    private void history(long frame) {
        if (historyDialog != null && historyDialog.isShowing()) return;
        historyDialog = RentzHistoryView.show(this, controller.session(), copy(), profile.preferredName(), () -> {
            historyDialog = null;
            if (active && generation == frame && screen.equals("HISTORY")) route(historyReturn);
        });
    }
    private void showFailure(Exception error) {
        android.util.Log.e("RentzLab", "UI action failed; checkpoint retained", error); screen = "MENU";
        pendingProblem = active ? null : error;
        if (active) {
            redraw();
            new AlertDialog.Builder(this).setMessage(copy().text("The action could not be completed. Saved data was kept.", "Acțiunea nu a putut fi încheiată. Datele salvate au fost păstrate."))
                    .setPositiveButton(copy().close(), null).show();
        }
    }
    private void publish() {
        if (BuildConfig.DEBUG) try { controller.publish(screen); }
        catch (Exception error) { android.util.Log.e("RentzLab", "Debug projection failed", error); }
    }
    private void back() { if (!controller.isBusy()) route("MENU"); }
    @Override public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (Build.VERSION.SDK_INT < 33 && keyCode == android.view.KeyEvent.KEYCODE_BACK && !event.isCanceled()) {
            back(); return true;
        }
        return super.onKeyUp(keyCode, event);
    }
    @Override protected void onResume() {
        super.onResume(); active = true;
        try {
            boolean changed = controller.activate();
            if (screen.equals("GAME") && controller.session() != null) { preview.reset(controller.session().view()); if (changed) controller.resume(); }
            redraw();
            if (pendingProblem != null) showFailure(pendingProblem);
        } catch (Exception error) { showFailure(error); }
    }
    @Override protected void onPause() {
        active = false; generation++; display.removeCallbacksAndMessages(null);
        if (historyDialog != null) { historyDialog.setOnDismissListener(null); historyDialog.dismiss(); historyDialog = null; }
        controller.deactivate(); super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle saved) {
        saved.putString("route", screen); saved.putString("historyReturn", historyReturn);
        saved.putInt("players", setup.players()); saved.putString("difficulty", setup.difficulty().name()); super.onSaveInstanceState(saved);
    }
    @Override protected void onDestroy() { unregisterBack.run(); controller.close(); super.onDestroy(); }
}
