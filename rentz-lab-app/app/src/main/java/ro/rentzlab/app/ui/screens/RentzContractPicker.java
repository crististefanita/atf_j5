package ro.rentzlab.app.ui.screens;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import ro.rentzlab.ai.RentzPlayer;
import ro.rentzlab.app.ui.copy.RentzUiText;
import ro.rentzlab.core.Contract;
import ro.rentzlab.core.RentzMatch;

/** Owns the lifetime of optional advice while keeping contract selection an explicit human action. */
public final class RentzContractPicker implements AutoCloseable {
    private final Activity activity;
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
    private AlertDialog dialog;
    private Future<?> pending;

    public RentzContractPicker(Activity activity) { this.activity = activity; }

    public void show(RentzUiText copy, RentzMatch.Observation view, RentzPlayer.Difficulty difficulty,
            List<Contract> available, boolean blind, BooleanSupplier current, Consumer<Contract> select) {
        dismiss();
        var builder = new AlertDialog.Builder(activity)
                .setTitle(copy.text("Choose game", "Alege sub-jocul") + (blind ? " ×2" : ""))
                .setItems(available.stream().map(copy::contract).toArray(String[]::new),
                        (ignored, which) -> select.accept(available.get(which)))
                .setNegativeButton(copy.text("Cancel", "Renunță"), null);
        boolean canAdvise = ContractAdvice.available(view, blind);
        if (canAdvise) builder.setNeutralButton(copy.text("AI hint", "Sugestie AI"), null);
        var opened = builder.create();
        dialog = opened;
        opened.setOnDismissListener(ignored -> {
            if (dialog == opened) {
                dialog = null;
                cancelPending();
            }
        });
        opened.show();
        if (canAdvise) opened.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(
                ignored -> request(copy, view, difficulty, current, opened));
    }

    private void request(RentzUiText copy, RentzMatch.Observation view, RentzPlayer.Difficulty difficulty,
            BooleanSupplier current, AlertDialog opened) {
        if (pending != null || !current.getAsBoolean()) return;
        var button = opened.getButton(DialogInterface.BUTTON_NEUTRAL);
        button.setEnabled(false);
        button.setText(copy.text("Thinking…", "Se calculează…"));
        pending = worker.submit(() -> {
            String result;
            try { result = copy.contract(ContractAdvice.suggest(view, difficulty)); }
            catch (RuntimeException failure) { result = null; }
            String advice = result;
            activity.runOnUiThread(() -> {
                if (dialog != opened || !opened.isShowing() || !current.getAsBoolean()) return;
                pending = null;
                if (advice == null) {
                    opened.setTitle(copy.text("AI hint unavailable", "Sugestia AI este indisponibilă"));
                    button.setText(copy.text("AI hint", "Sugestie AI"));
                    button.setEnabled(true);
                } else {
                    opened.setTitle(copy.text("AI suggests: ", "AI sugerează: ") + advice);
                    button.setVisibility(View.GONE);
                }
            });
        });
    }

    private void cancelPending() {
        if (pending != null) pending.cancel(true);
        pending = null;
    }
    public void dismiss() {
        var opened = dialog;
        dialog = null;
        cancelPending();
        if (opened != null) opened.dismiss();
    }
    @Override public void close() { dismiss(); worker.shutdownNow(); }
}
