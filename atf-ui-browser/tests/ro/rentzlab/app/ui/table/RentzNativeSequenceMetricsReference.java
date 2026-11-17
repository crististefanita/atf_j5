package ro.rentzlab.app.ui.table;

/** Emits layout samples from the exact Android RentzCardSpace source for the WEB adapter checks. */
public final class RentzNativeSequenceMetricsReference {
    private record Window(int width, int height, float fontScale) { }

    private static final Window[] WINDOWS = {
            new Window(280, 168, 1f), new Window(280, 366, 1f),
            new Window(372, 640, 1f), new Window(320, 408, 1.35f),
            new Window(560, 168, 1.2f), new Window(280, 235, 1.5f)
    };

    private RentzNativeSequenceMetricsReference() { }

    public static void main(String[] args) {
        for (int players = 3; players <= 6; players++) {
            for (var window : WINDOWS) {
                var layout = RentzCardSpace.sequence(players, window.width(), window.height(), 1f, window.fontScale());
                System.out.println(players + "\t" + window.width() + "\t" + window.height() + "\t"
                        + window.fontScale() + "\t" + layout.rowHeight() + "\t" + layout.faceHeight() + "\t"
                        + layout.faceWidth() + "\t" + layout.advance() + "\t" + layout.start() + "\t"
                        + layout.indexWidth() + "\t" + layout.indexPadding() + "\t" + layout.labelSizePx() + "\t"
                        + layout.stackedIndex());
            }
        }
    }
}
