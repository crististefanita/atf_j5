package ro.rentzlab.app.ui.screens;

import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import ro.cardtable.ui.achievements.AchievementNoticeView;
import ro.cardtable.ui.screens.ScreenViews;
import ro.rentzlab.app.lifecycle.SessionController.AchievementNotice;
import ro.rentzlab.app.ui.copy.RentzUiText;

/** Projects saved Rentz progress into the shared score-page notice. */
public final class AchievementPopup {
    private AchievementPopup() { }

    public static View show(ScreenViews views, FrameLayout slot, RentzUiText copy, AchievementNotice notice,
            Runnable dismiss) {
        String title = notice.matchEnd() ? copy.text("Match achievement progress", "Progres după partidă")
                : copy.text("Achievement progress", "Progres la realizări");
        var lines = new ArrayList<String>();
        var visible = notice.previewChanges();
        for (var change : visible) {
            String name = copy.text(change.badge().english(), change.badge().romanian());
            String status = change.unlocked() ? copy.text("Unlocked", "Deblocată")
                    : change.count() + "/" + change.badge().goal();
            lines.add(name + " · " + status);
        }
        int more = notice.changes().size() - visible.size();
        if (more > 0) lines.add("+" + more + copy.text(" more updated", " în plus"));
        return AchievementNoticeView.show(views, slot, title, lines, copy.close(),
                copy.text("Close achievement progress", "Închide progresul la realizări"), dismiss);
    }
}
