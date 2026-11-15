package atf.rentz.distribution.publication;

import atf.rentz.RentzRulePublication;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.AuditedAssertions;
import atf.testkit.assertions.ComponentAuditExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static atf.rentz.distribution.publication.ReleasePublication.*;

@AtfComponentTest
class ReleasePromotionCoordinationTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            ReleasePromotionCoordinationTest.class, "release-promotion-coordination.html",
            "Competing operators with separate publication journals");
    private static final String SOURCE = "owner/atf_j5";
    private static final String CHANNEL = "owner/atf_j5_releases";
    private static final String COMMIT = "a".repeat(40);
    private static final int SYNC_TIMEOUT_SECONDS = 120;
    @TempDir Path scratch;

    @Test void otherJournalCannotPatchWhileFirstOperatorHoldsFinalInventory() throws Exception {
        var channel = localChannel();
        var shared = new Drafts();
        var newer = shared.operator("0.28.0", 81, channel.first());
        var older = shared.operator("0.27.0", 82, channel.second());
        var lastRead = new CountDownLatch(1);
        var allowPatch = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> PublicationCommand.resumeWithJournal(newer.plan, newer,
                    scratch.resolve("newer.json"), step -> pauseAfterLastInventory(step, lastRead, allowPatch)));
            AuditedAssertions.assertTrue(lastRead.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS), "First operator pauses just before PATCH");
            String second;
            List<String> concurrentPatches;
            try {
                second = outcome(older, scratch.resolve("older.json"));
                concurrentPatches = List.copyOf(shared.patches);
            } finally {
                allowPatch.countDown();
            }
            AuditedAssertions.assertEquals(81L, first.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS).longValue(), "First operator completes");
            AuditedAssertions.assertEquals("PUBLICATION-CHANNEL-IN-USE", second, "Second journal must wait for the same channel");
            AuditedAssertions.assertEquals(List.of(), concurrentPatches, "Neither operator has sent PATCH while the first is paused");
            AuditedAssertions.assertEquals("PUBLICATION-NEWER-STABLE", outcome(older, scratch.resolve("older.json")),
                    "On retry, the older operator reads the new stable release");
            AuditedAssertions.assertEquals(List.of("v0.28.0"), shared.patches, "Exactly the newer release was promoted");
        } finally {
            allowPatch.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) executor.shutdownNow();
        }
    }

    @Test void uncertainPatchRetainsChannelUntilOwnerIsRecovered() throws Exception {
        var channel = localChannel();
        var shared = new Drafts();
        var first = shared.operator("0.28.0", 81, channel.first());
        var next = shared.operator("0.29.0", 82, channel.second());
        first.uncertainPatch = true;
        Path firstJournal = scratch.resolve("interrupted.json");
        Path nextJournal = scratch.resolve("next.json");

        AuditedAssertions.assertEquals("PUBLICATION-MOCK-LOST-PATCH", outcome(first, firstJournal),
                "The PATCH result was lost");
        String owner = channel.first().owner();
        AuditedAssertions.assertTrue(owner != null && owner.equals(channel.second().owner()),
                "Both checkouts see the retained owner token");
        AuditedAssertions.assertEquals("PUBLICATION-CHANNEL-IN-USE", outcome(next, nextJournal),
                "An unresolved PATCH prevents takeover");
        AuditedAssertions.assertEquals(List.of("v0.28.0"), shared.patches, "Only the first PATCH was attempted");

        shared.markPublished(81);
        AuditedAssertions.assertEquals("Published release 81", outcome(first, firstJournal),
                "The interrupted operation can be inspected after publication is observed");
        channel.first().release(owner);
        AuditedAssertions.assertEquals("Published release 82", outcome(next, nextJournal),
                "After conditional recovery, the next candidate promotes");
        AuditedAssertions.assertEquals(null, channel.second().owner(), "Verified completion returns the channel");
    }

    @Test void aPreviousOwnerCannotDeleteAReplacementReservation() throws Exception {
        var channel = localChannel();
        var first = channel.first().reserve("v0.28.0");
        channel.second().release(first.owner());
        try (var next = channel.second().reserve("v0.29.0")) {
            AuditedAssertions.assertThrows(IOException.class, first::close, "A stale owner cannot delete the current ref");
            AuditedAssertions.assertEquals(next.owner(), channel.first().owner(), "The replacement keeps its exact token");
        }
        AuditedAssertions.assertEquals(null, channel.first().owner(), "Only the current owner releases the ref");
    }

    private Channel localChannel() throws Exception {
        Path remote = Files.createDirectory(scratch.resolve("channel.git"));
        Path first = Files.createDirectory(scratch.resolve("first-checkout"));
        Path second = Files.createDirectory(scratch.resolve("second-checkout"));
        initializeGit(remote, "--bare");
        initializeGit(first);
        initializeGit(second);
        String address = remote.toUri().toASCIIString();
        return new Channel(new GitPromotionChannel(first, address), new GitPromotionChannel(second, address));
    }

    private static void initializeGit(Path directory, String... options) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git", "-C", directory.toString(), "init", "--quiet"));
        command.addAll(List.of(options));
        Process git = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!git.waitFor(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS) || git.exitValue() != 0) throw new IOException("Cannot initialize Git fixture");
        } finally {
            if (git.isAlive()) git.destroyForcibly();
        }
    }

    private record Channel(GitPromotionChannel first, GitPromotionChannel second) { }

    private static void pauseAfterLastInventory(Step step, CountDownLatch lastRead, CountDownLatch allowPatch) {
        if (!"Attempt".equals(step.name()) || !step.actual().startsWith("Publish verified draft ")) return;
        lastRead.countDown();
        try {
            if (!allowPatch.await(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out at promotion boundary");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private static String outcome(Operator operator, Path journal) {
        try { return "Published release " + PublicationCommand.resumeWithJournal(operator.plan, operator, journal, step -> { }); }
        catch (IOException failure) { return failure.getMessage(); }
    }

    private static final class Drafts {
        final ConcurrentHashMap<Long, Release> inventory = new ConcurrentHashMap<>();
        final List<String> patches = new CopyOnWriteArrayList<>();

        Operator operator(String version, long id, GitPromotionChannel channel) {
            String apk = "RentzLab-" + version + ".apk";
            var plan = new Plan(SOURCE, CHANNEL, version, COMMIT, new Payload(apk, Path.of(apk), 5, "b".repeat(64)),
                    new Payload(apk + ".sha256", Path.of(apk + ".sha256"), 10, "c".repeat(64)));
            inventory.put(id, new Release(id, plan.tag(), COMMIT, true, false));
            return new Operator(this, plan, channel);
        }

        void markPublished(long id) {
            inventory.computeIfPresent(id, (key, release) -> new Release(id, release.tag(), release.target(), false, false));
        }
    }

    private static final class Operator implements Remote {
        private final Drafts drafts;
        private final Plan plan;
        private final GitPromotionChannel channel;
        private boolean uncertainPatch;

        Operator(Drafts drafts, Plan plan, GitPromotionChannel channel) {
            this.drafts = drafts;
            this.plan = plan;
            this.channel = channel;
        }

        @Override public boolean sourceOnMain(Plan plan) { return true; }
        @Override public boolean privateRepository(String repository) { return true; }
        @Override public String tagCommit(String repository, String tag) { return COMMIT; }
        @Override public void pushTag(String repository, String tag, String commit) { throw new AssertionError("Tag already exists"); }
        @Override public List<Release> releases(String repository) { return List.copyOf(drafts.inventory.values()); }
        @Override public Release release(String repository, long id) { return drafts.inventory.get(id); }
        @Override public long createDraft(Plan plan) { throw new AssertionError("Draft already exists"); }
        @Override public List<Asset> assets(String repository, long id) {
            return List.of(asset(11, plan.apk()), asset(12, plan.checksum()));
        }
        @Override public void upload(String repository, long id, Payload payload) { throw new AssertionError("Asset already exists"); }
        @Override public PromotionLease reservePromotion(Plan plan) throws IOException {
            return channel.reserve(plan.tag());
        }
        @Override public void publish(String repository, long id) throws IOException {
            drafts.patches.add(plan.tag());
            if (uncertainPatch) throw new IOException("PUBLICATION-MOCK-LOST-PATCH");
            drafts.markPublished(id);
        }

        private static Asset asset(long id, Payload payload) {
            return new Asset(id, payload.name(), "uploaded", payload.bytes(), payload.sha256());
        }
    }
}
