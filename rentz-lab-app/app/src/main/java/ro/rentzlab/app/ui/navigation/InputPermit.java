package ro.rentzlab.app.ui.navigation;

/** One UI frame can issue at most one command, only against its original revision and route. */
public final class InputPermit {
    private final int revision;
    private final long route;
    private boolean consumed;
    public InputPermit(int revision, long route) { this.revision = revision; this.route = route; }
    public boolean consume(int currentRevision, long currentRoute, boolean busy) {
        if (consumed || busy || revision != currentRevision || route != currentRoute) return false;
        consumed = true;
        return true;
    }
}
