package atf.rentz.distribution.retirement;

import java.io.IOException;
import java.util.List;

/** Retirement changes release records only; source tags have no write operation in this port. */
public final class ReleaseRetirement {
    public record Plan(String repository, String version, String source, String main, long releaseId, String apkUrl) {
        public String tag() { return "v" + version; }
    }
    public record Edition(long id, String tag, String source, boolean draft, boolean prerelease) { }
    public record State(boolean write, String main, String tagSource, long latestId, String latestTag,
                        String readmeUrl, List<Edition> editions) {
        public State { editions = List.copyOf(editions); }
    }
    public interface Lease extends AutoCloseable {
        void check() throws IOException;
        @Override void close() throws IOException;
    }
    public interface Remote {
        State observe(Plan plan) throws IOException;
        Lease reserve(Plan plan) throws IOException;
        void archive(Edition edition) throws IOException;
        void deleteRelease(long id) throws IOException;
    }
    public static List<Long> retire(Plan plan, Remote remote) throws IOException {
        require(plan.repository().matches("[A-Za-z0-9-]+/[A-Za-z0-9_.-]+")
                && plan.version().matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
                && plan.source().matches("[a-f0-9]{40}") && plan.main().matches("[a-f0-9]{40}")
                && plan.releaseId() > 0 && plan.apkUrl().equals("https://github.com/" + plan.repository()
                    + "/releases/download/" + plan.tag() + "/RentzLab-" + plan.version() + ".apk"), "PLAN");
        validate(plan, remote.observe(plan));
        var removed = new java.util.ArrayList<Long>();
        try (var lease = remote.reserve(plan)) {
            lease.check();
            var current = remote.observe(plan); validate(plan, current);
            var older = current.editions().stream().filter(row -> !row.draft() && !row.prerelease()
                    && numeric(row.tag()) && compare(row.tag(), plan.tag()) < 0)
                    .sorted((a, b) -> compare(a.tag(), b.tag())).toList();
            for (var edition : older) {
                lease.check();
                var before = remote.observe(plan); validate(plan, before);
                require(before.editions().contains(edition), "EDITION-DRIFT");
                remote.archive(edition);
                lease.check();
                var archived = remote.observe(plan); validate(plan, archived);
                require(archived.editions().contains(edition), "EDITION-DRIFT");
                IOException uncertain = null;
                try { remote.deleteRelease(edition.id()); } catch (IOException lost) { uncertain = lost; }
                // Never repeat DELETE after an uncertain response. Inspect the authoritative inventory.
                lease.check(); var after = remote.observe(plan); validate(plan, after);
                if (after.editions().stream().anyMatch(row -> row.id() == edition.id())) {
                    if (uncertain != null) throw uncertain;
                    throw new IOException("RETIREMENT-DELETE-NOT-CONFIRMED");
                }
                removed.add(edition.id());
            }
        }
        return List.copyOf(removed);
    }
    private static void validate(Plan plan, State state) throws IOException {
        require(state.write() && state.main().equals(plan.main()) && state.tagSource().equals(plan.source())
                && state.latestId() == plan.releaseId() && state.latestTag().equals(plan.tag())
                && state.readmeUrl().equals(plan.apkUrl()), "AUTHORITY");
        var ids = new java.util.HashSet<Long>(); var tags = new java.util.HashSet<String>();
        for (var edition : state.editions()) {
            require(edition.id() > 0 && ids.add(edition.id()) && tags.add(edition.tag()), "INVENTORY");
            require(!numeric(edition.tag()) || edition.draft() || edition.prerelease()
                    || compare(edition.tag(), plan.tag()) <= 0, "NEWER-STABLE");
        }
        require(state.editions().stream().anyMatch(row -> row.id() == plan.releaseId() && row.tag().equals(plan.tag())
                && !row.draft() && !row.prerelease() && row.source().equals(plan.source())), "LATEST");
    }
    private static boolean numeric(String tag) { return tag.matches("v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"); }
    private static int compare(String left, String right) {
        String[] a = left.substring(1).split("\\."), b = right.substring(1).split("\\.");
        for (int i = 0; i < 3; i++) {
            int compared = new java.math.BigInteger(a[i]).compareTo(new java.math.BigInteger(b[i]));
            if (compared != 0) return compared;
        }
        return 0;
    }
    private static void require(boolean value, String code) throws IOException { if (!value) throw new IOException("RETIREMENT-" + code); }
    private ReleaseRetirement() { }
}
