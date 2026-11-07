package ro.rentzlab.app.ui.table;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import ro.cardtable.ui.animation.CollectionTarget;
import ro.cardtable.ui.animation.TrickCollectionMotion;

/** Drives the visible cards from one timeline on the Android renderer. */
final class RentzCollectionAnimation {
    private record Clip(boolean children, boolean padding) { }
    private final ViewGroup board;
    private final View center;
    private final List<View> faces;
    private final Map<ViewGroup, Clip> clips = new IdentityHashMap<>();
    private final View.OnLayoutChangeListener resize;

    private RentzCollectionAnimation(ViewGroup board, View center, List<View> faces, Runnable resized) {
        this.board = board;
        this.center = center;
        this.faces = faces;
        resize = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) return;
            cancel();
            resized.run();
        };
        board.addOnLayoutChangeListener(resize);
    }

    static RentzCollectionAnimation start(ViewGroup board, View center, CollectionTarget target,
                                         long deadline, Runnable resized) {
        var seat = board.findViewWithTag("table-seat:" + target.winner());
        if (!measured(seat)) return null;
        var faces = new ArrayList<View>();
        for (int index : target.seats()) {
            View face = board.findViewWithTag("trick-face:" + index);
            if (!measured(face)) return null;
            faces.add(face);
        }
        long remaining = deadline - SystemClock.uptimeMillis();
        if (remaining <= TrickCollectionMotion.COLLECT_MILLIS) return null;
        var run = new RentzCollectionAnimation(board, center, faces, resized);
        run.animate(seat, new Timeline(remaining));
        return run;
    }

    private void animate(View seat, Timeline timeline) {
        int[] target = new int[2];
        seat.getLocationInWindow(target);
        float targetX = target[0] + seat.getWidth() / 2f;
        float targetY = target[1] + seat.getHeight() / 2f;
        for (View face : faces) {
            exposePath(face);
            int[] origin = new int[2];
            face.getLocationInWindow(origin);
            face.animate().translationXBy(targetX - origin[0] - face.getWidth() / 2f)
                    .translationYBy(targetY - origin[1] - face.getHeight() / 2f)
                    .scaleX(.18f).scaleY(.18f).setDuration(timeline.remaining())
                    .setInterpolator(timeline::distance).start();
        }
        center.animate().alpha(0f).setDuration(timeline.remaining())
                .setInterpolator(timeline::fade).start();
    }

    private void exposePath(View face) {
        for (var parent = face.getParent(); parent instanceof ViewGroup group; parent = group.getParent()) {
            if (!clips.containsKey(group)) {
                clips.put(group, new Clip(group.getClipChildren(), group.getClipToPadding()));
                group.setClipChildren(false);
                group.setClipToPadding(false);
            }
            if (group == board) return;
        }
    }

    void cancel() {
        board.removeOnLayoutChangeListener(resize);
        center.animate().cancel();
        faces.forEach(face -> face.animate().cancel());
        clips.forEach((view, clip) -> {
            view.setClipChildren(clip.children());
            view.setClipToPadding(clip.padding());
        });
        clips.clear();
    }

    private static boolean measured(View view) {
        return view != null && view.getWidth() > 0 && view.getHeight() > 0;
    }

    record Timeline(long remaining) {
        private float motion(float fraction) {
            long hold = remaining - TrickCollectionMotion.COLLECT_MILLIS;
            return Math.max(0, Math.min(1, (remaining * fraction - hold) / TrickCollectionMotion.COLLECT_MILLIS));
        }

        float distance(float fraction) {
            float progress = motion(fraction);
            if (progress < .1f) {
                float opening = progress / .1f;
                return .14f * opening * (2 - opening);
            }
            // Keep the faces separate on departure and reach the seat before the fade begins.
            if (progress < .32f) return .14f + .08f * smooth((progress - .1f) / .22f);
            if (progress < .74f) return .22f + .78f * smooth((progress - .32f) / .42f);
            return 1;
        }

        private static float smooth(float value) { return value * value * (3 - 2 * value); }

        float fade(float fraction) {
            if (fraction >= 1) return 1;
            return Math.max(0, Math.min(1, (motion(fraction) - .8f) / .2f));
        }
    }
}
