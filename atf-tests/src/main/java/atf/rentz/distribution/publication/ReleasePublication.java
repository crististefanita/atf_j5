package atf.rentz.distribution.publication;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Resumes one version by reconciling GitHub state before any write. */
public final class ReleasePublication {
    public record Payload(String name, Path file, long bytes, String sha256) { }
    public record Plan(String sourceRepository, String releaseRepository, String version, String commit,
                       Payload apk, Payload checksum) {
        public String tag() { return "v" + version; }
    }
    public record Release(long id, String tag, String target, boolean draft, boolean prerelease) { }
    public record Asset(long id, String name, String state, long bytes, String sha256) { }
    public record Step(String name, String actual) { }

    public interface Remote {
        boolean sourceOnMain(Plan plan) throws IOException;
        boolean privateRepository(String repository) throws IOException;
        String tagCommit(String repository, String tag) throws IOException;
        void pushTag(String repository, String tag, String commit) throws IOException;
        List<Release> releases(String repository, String tag) throws IOException;
        Release release(String repository, long id) throws IOException;
        long createDraft(Plan plan) throws IOException;
        List<Asset> assets(String repository, long releaseId) throws IOException;
        void upload(String repository, long releaseId, Payload payload) throws IOException;
        void publish(String repository, long releaseId) throws IOException;
    }

    @FunctionalInterface public interface Checkpoint {
        void save(long releaseId) throws IOException;
    }

    private ReleasePublication() { }

    public static long resume(Plan plan, Remote remote, long knownId, Checkpoint checkpoint,
                              Consumer<Step> trace) throws IOException {
        require(remote.sourceOnMain(plan), "SOURCE-NOT-MAIN");
        trace.accept(new Step("Verified source on main", plan.commit()));
        String repository = plan.releaseRepository();
        require(remote.privateRepository(repository), "REPOSITORY-NOT-PRIVATE");
        trace.accept(new Step("Verified private repository", repository));

        String sourceTag = inspectTag(remote, plan, plan.sourceRepository());
        String releaseTag = inspectTag(remote, plan, repository);
        Release release = discover(remote, plan, knownId);
        if (release != null) {
            require(release.draft() || sourceTag != null && releaseTag != null, "MISSING-PUBLISHED-TAG");
            require(releaseTag != null || plan.commit().equals(release.target()), "DRAFT-TARGET");
            checkpoint.save(release.id());
            trace.accept(new Step("Verified existing release ID", Long.toString(release.id())));
            inspectAssets(remote, plan, release);
        }

        ensureTag(remote, plan, plan.sourceRepository(), sourceTag, trace);
        ensureTag(remote, plan, repository, releaseTag, trace);
        if (release == null) {
            trace.accept(new Step("Attempt", "Create draft"));
            long id = remote.createDraft(plan);
            require(id > 0, "RELEASE-ID");
            checkpoint.save(id);
            release = discover(remote, plan, id);
            require(release.draft(), "UNEXPECTED-RELEASE-STATE");
            trace.accept(new Step("Verified draft ID", Long.toString(id)));
        }
        long id = release.id();
        for (Payload payload : List.of(plan.apk(), plan.checksum())) {
            release = discover(remote, plan, id);
            var assets = inspectAssets(remote, plan, release);
            if (!assets.containsKey(payload.name())) {
                require(release.draft(), "PUBLISHED-ASSETS");
                trace.accept(new Step("Attempt", "Upload " + payload.name()));
                remote.upload(repository, id, payload);
                assets = inspectAssets(remote, plan, discover(remote, plan, id));
                require(assets.containsKey(payload.name()), "UPLOAD-NOT-OBSERVED");
            }
            trace.accept(new Step("Verified asset " + payload.name(),
                    "ID " + assets.get(payload.name()).id() + ", SHA-256 " + payload.sha256()));
        }
        require(remote.privateRepository(repository), "REPOSITORY-NOT-PRIVATE");
        require(inspectTag(remote, plan, plan.sourceRepository()) != null
                && inspectTag(remote, plan, repository) != null, "TAG-NOT-OBSERVED");
        release = discover(remote, plan, id);
        require(inspectAssets(remote, plan, release).size() == 2, "ASSET-INVENTORY");
        if (release.draft()) {
            require(remote.sourceOnMain(plan), "SOURCE-NOT-MAIN");
            trace.accept(new Step("Attempt", "Publish verified draft " + id));
            remote.publish(repository, id);
            release = discover(remote, plan, id);
            require(!release.draft() && inspectAssets(remote, plan, release).size() == 2, "PUBLISH-NOT-OBSERVED");
        }
        trace.accept(new Step("Verified published release ID", Long.toString(id)));
        return id;
    }

    private static Release discover(Remote remote, Plan plan, long knownId) throws IOException {
        List<Release> matches = remote.releases(plan.releaseRepository(), plan.tag());
        require(matches.size() <= 1, "DUPLICATE-RELEASE");
        if (matches.isEmpty()) {
            require(knownId == 0, "RELEASE-ID");
            return null;
        }
        long id = matches.getFirst().id();
        require(id > 0 && (knownId == 0 || id == knownId), "RELEASE-ID");
        Release current = remote.release(plan.releaseRepository(), id);
        require(current != null && current.id() == id && plan.tag().equals(current.tag())
                && !current.prerelease(), "RELEASE-ID");
        return current;
    }

    private static Map<String, Asset> inspectAssets(Remote remote, Plan plan, Release release) throws IOException {
        var expected = Map.of(plan.apk().name(), plan.apk(), plan.checksum().name(), plan.checksum());
        var actual = new HashMap<String, Asset>();
        var ids = new HashSet<Long>();
        for (Asset asset : remote.assets(plan.releaseRepository(), release.id())) {
            Payload payload = expected.get(asset.name());
            require(payload != null && asset.id() > 0 && ids.add(asset.id())
                    && "uploaded".equals(asset.state()) && asset.bytes() == payload.bytes()
                    && payload.sha256().equals(asset.sha256())
                    && actual.putIfAbsent(asset.name(), asset) == null, "ASSET-CONFLICT");
        }
        require(release.draft() || actual.size() == expected.size(), "PUBLISHED-ASSETS");
        return actual;
    }

    private static String inspectTag(Remote remote, Plan plan, String repository) throws IOException {
        String observed = remote.tagCommit(repository, plan.tag());
        require(observed == null || observed.equals(plan.commit()), "TAG-CONFLICT");
        return observed;
    }

    private static void ensureTag(Remote remote, Plan plan, String repository, String commit,
                                  Consumer<Step> trace) throws IOException {
        if (commit == null) {
            trace.accept(new Step("Attempt", "Push tag to " + repository));
            remote.pushTag(repository, plan.tag(), plan.commit());
            require(inspectTag(remote, plan, repository) != null, "TAG-NOT-OBSERVED");
        }
        trace.accept(new Step("Verified tag " + repository, plan.commit()));
    }

    private static void require(boolean condition, String diagnostic) throws IOException {
        if (!condition) throw new IOException("PUBLICATION-" + diagnostic);
    }
}
