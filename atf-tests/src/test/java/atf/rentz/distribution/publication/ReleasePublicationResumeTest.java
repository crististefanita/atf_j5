package atf.rentz.distribution.publication;

import atf.rentz.RentzRulePublication;
import atf.rentz.distribution.evidence.ReleaseEvidenceFiles;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.AuditedAssertions;
import atf.testkit.assertions.ComponentAuditExtension;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.RegisterExtension;

import static atf.rentz.distribution.publication.ReleasePublication.*;

@AtfComponentTest
class ReleasePublicationResumeTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            ReleasePublicationResumeTest.class, "release-publication-resume.html",
            "Qualified GitHub publication resumes from durable remote state using an offline mock");

    private static final String COMMIT = "a".repeat(40);
    private static final Payload APK = new Payload("RentzLab-0.27.0.apk", Path.of("fixture.apk"), 901, "b".repeat(64));
    private static final Payload CHECKSUM = new Payload("RentzLab-0.27.0.apk.sha256", Path.of("fixture.sha256"), 86, "c".repeat(64));
    private static final Plan PLAN = new Plan("owner/atf_j5", "owner/atf_j5_releases", "0.27.0", COMMIT, APK, CHECKSUM);
    @TempDir Path scratch;
    private final List<Long> remembered = new ArrayList<>();
    private final List<Step> trace = new ArrayList<>();

    @Test void existingTagAndDraftWithApkOnlyUploadTheMissingChecksum() throws Exception {
        var github = draft();
        github.assets.add(asset(11, APK));
        long actual = run(github, 0);
        AuditedAssertions.assertEquals(81L, actual, "Recover draft ID");
        AuditedAssertions.assertEquals(List.of(81L), remembered, "Save ID before continuing");
        AuditedAssertions.assertEquals(List.of("upload:" + CHECKSUM.name(), "publish"), github.writes,
                "Preserve existing tag, draft and APK");
        github.writes.clear();
        AuditedAssertions.assertEquals(81L, run(github, 81), "Published release reuses ID");
        AuditedAssertions.assertEquals(List.of(), github.writes, "No repeated mutations after publish");
    }

    @Test void interruptedUploadIsDiscoveredOnNextRun() throws Exception {
        var github = draft();
        github.interruptUpload = true;
        AuditedAssertions.assertEquals("Write outcome unknown", failure(github, 0), "Stop on uncertain response");
        AuditedAssertions.assertEquals(List.of(81L), remembered, "ID persisted before upload");
        github.writes.clear();
        AuditedAssertions.assertEquals(81L, run(github, 81), "Resume after losing upload response");
        AuditedAssertions.assertEquals(List.of("upload:" + CHECKSUM.name(), "publish"), github.writes,
                "Never upload the same APK a second time");
    }

    @Test void conflictingTagOrAssetBlocksBeforeAnyMutation() throws Exception {
        var wrongTag = draft();
        wrongTag.releaseTag = "e".repeat(40);
        AuditedAssertions.assertEquals("PUBLICATION-TAG-CONFLICT", failure(wrongTag, 81), "Reject different tag commit");
        AuditedAssertions.assertEquals(List.of(), wrongTag.writes, "Leave other work intact");
        var wrongApk = draft();
        wrongApk.assets.add(new Asset(11, APK.name(), "uploaded", APK.bytes(), "d".repeat(64)));
        AuditedAssertions.assertEquals("PUBLICATION-ASSET-CONFLICT", failure(wrongApk, 81), "Reject changed APK bytes");
        AuditedAssertions.assertEquals(List.of(), wrongApk.writes, "Do not overwrite mismatched asset");
    }

    @Test void recordedDraftCannotBeRecreatedAndUnpushedSourceCannotPublish() throws Exception {
        var github = draft();
        github.item = null;
        AuditedAssertions.assertEquals("PUBLICATION-RELEASE-ID", failure(github, 81), "Reject vanished recorded draft");
        AuditedAssertions.assertEquals(List.of(), github.writes, "Absence of recorded draft is ambiguous");
        github.onMain = false;
        AuditedAssertions.assertEquals("PUBLICATION-SOURCE-NOT-MAIN", failure(github, 0), "Require qualified source on main");
        AuditedAssertions.assertEquals(List.of(), github.writes, "Refused source promotion performs no writes");
    }

    @Test void rejectedTagPushStopsBeforeDraftAndCanBeRetried() throws Exception {
        var github = new Mock();
        github.rejectPush = true;
        AuditedAssertions.assertEquals("Tag push refused", failure(github, 0), "Report failed tag step");
        AuditedAssertions.assertEquals(List.of("tag:owner/atf_j5"), github.writes, "Stop before private tag or draft");
        github.rejectPush = false;
        github.writes.clear();
        AuditedAssertions.assertEquals(81L, run(github, 0), "Retry from remote state");
        AuditedAssertions.assertEquals(List.of("tag:owner/atf_j5", "tag:owner/atf_j5_releases", "draft",
                "upload:" + APK.name(), "upload:" + CHECKSUM.name(), "publish"), github.writes,
                "Only missing operations are performed in dependency order");
    }

    @Test void lostDraftResponseIsReconciledFromTheSameDurableJournal() throws Exception {
        var github = new Mock();
        github.interruptDraft = true;
        Path journal = scratch.resolve("publication.json");
        AuditedAssertions.assertThrows(IOException.class,
                () -> PublicationCommand.resumeWithJournal(PLAN, github, journal, trace::add), "Interrupted create stops");
        var saved = new ObjectMapper().readTree(journal.toFile());
        AuditedAssertions.assertEquals(0L, saved.path("releaseId").asLong(), "Unobserved release ID is not invented");
        github.writes.clear();
        AuditedAssertions.assertEquals(81L, PublicationCommand.resumeWithJournal(PLAN, github, journal, trace::add),
                "Find the draft by tag when create response was lost");
        AuditedAssertions.assertEquals(List.of("upload:" + APK.name(), "upload:" + CHECKSUM.name(), "publish"), github.writes,
                "Missing work only");
        AuditedAssertions.assertEquals(81L, new ObjectMapper().readTree(journal.toFile()).path("releaseId").asLong(),
                "Immutable remote ID persisted before writes");
    }

    @Test void journalIdentityAndConcurrentOperatorAreRefusedBeforeRemoteWrites() throws Exception {
        var github = draft();
        Path journal = scratch.resolve("journal.json");
        PublicationCommand.resumeWithJournal(PLAN, github, journal, trace::add);
        github.writes.clear();
        Plan different = new Plan(PLAN.sourceRepository(), PLAN.releaseRepository(), PLAN.version(), "f".repeat(40), APK, CHECKSUM);
        AuditedAssertions.assertEquals("PUBLICATION-JOURNAL-IDENTITY", rejected(different, github, journal), "Journal cannot be rebound");
        try (var lock = java.nio.channels.FileChannel.open(scratch.resolve("journal.json.lock"), java.nio.file.StandardOpenOption.WRITE);
             var ignored = lock.lock()) {
            AuditedAssertions.assertEquals("PUBLICATION-JOURNAL-IN-USE", rejected(PLAN, github, journal), "Exclusive journal");
        }
        AuditedAssertions.assertEquals(List.of(), github.writes, "No remote writes on local journal conflict");
    }

    @Test void missingNativeGateOrForgedReceiptMakesCandidateIneligible() throws Exception {
        var mapper = new ObjectMapper();
        Path build = Files.createDirectory(scratch.resolve("build"));
        Path apk = Files.writeString(build.resolve(APK.name()), "signed-fixture-artifact");
        String hash = atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256(Files.readAllBytes(apk));
        Path checksum = Files.writeString(scratch.resolve(CHECKSUM.name()), hash + "  " + APK.name() + "\n");
        Path receipt = build.resolve("build-qualification.json");
        mapper.writeValue(receipt.toFile(), java.util.Map.of("productApk", apk.toString()));
        String receiptHash = atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256(Files.readAllBytes(receipt));
        var rag = mapper.createObjectNode();
        rag.putObject("candidate").put("repository", PLAN.sourceRepository()).put("candidateVersion", PLAN.version())
                .put("currentReleaseVersion", "0.26.0").put("sourceCommit", PLAN.commit()).put("worktreeState", "CLEAN");
        rag.putObject("releaseWorkflow").put("readyForMainPromotionReview", true);
        rag.putArray("conflicts");
        var gates = rag.putArray("gates");
        for (String gate : List.of("offline-regression", "component-extent-report", "android-jvm", "local-catalog",
                                   "native-ui", "native-ui-report")) gates.addObject().put("id", gate).put("status", "PASS");
        gates.addObject().put("id", "release-build").put("status", "PASS").put("artifact", APK.name())
                .put("receiptSha256", receiptHash).put("artifactBytes", Files.size(apk)).put("artifactSha256", hash);
        AuditedAssertions.assertEquals(hash, PublicationCommand.qualifiedPlan(rag, rag.deepCopy(), build, checksum).apk().sha256(),
                "All seven source-bound gates admit exact APK");
        Path recordedReport = scratch.resolve("release-rag-report.json");
        mapper.writeValue(recordedReport.toFile(), rag);
        var recorded = mapper.readTree(recordedReport.toFile());
        AuditedAssertions.assertEquals(hash, PublicationCommand.qualifiedPlan(recorded, rag, build, checksum).apk().sha256(),
                "The report read from disk must admit the same qualified APK");
        var changedSize = recorded.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) changedSize.path("gates").get(6))
                .put("artifactBytes", Files.size(apk) + 1);
        AuditedAssertions.assertThrows(IOException.class,
                () -> PublicationCommand.qualifiedPlan(changedSize, rag, build, checksum), "A different byte count blocks publication");
        var textSize = recorded.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) textSize.path("gates").get(6))
                .put("artifactBytes", Long.toString(Files.size(apk)));
        AuditedAssertions.assertThrows(IOException.class,
                () -> PublicationCommand.qualifiedPlan(textSize, rag, build, checksum), "Text cannot stand in for a byte count");
        var missingNative = rag.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) missingNative.path("gates").get(4)).put("status", "NOT_RUN");
        AuditedAssertions.assertThrows(IOException.class,
                () -> PublicationCommand.qualifiedPlan(missingNative, missingNative, build, checksum), "Native gate still required");
        AuditedAssertions.assertThrows(IOException.class,
                () -> PublicationCommand.qualifiedPlan(rag, missingNative, build, checksum), "Report must match current evaluation");
        Files.writeString(apk, "altered-fixture-artifact");
        AuditedAssertions.assertThrows(IOException.class,
                () -> PublicationCommand.qualifiedPlan(rag, rag.deepCopy(), build, checksum), "APK must match verified bytes");
    }

    @Test void actualRestAdapterDiscoversDraftFromAuthenticatedListAndUploadsOnlyMissingFile() throws Exception {
        try (var github = new DraftApi(false, false, List.of(
                edition(82, "v0.26.9", false, false),
                edition(83, "v0.99.0", true, false),
                edition(84, "v0.100.0", false, true)), List.of())) {
            AuditedAssertions.assertEquals(81L, github.resume(0), "Publish a newer stable candidate");
            String route = "/repos/" + PLAN.releaseRepository() + "/releases/81";
            AuditedAssertions.assertEquals(List.of("POST " + route + "/assets?name=" + CHECKSUM.name(), "PATCH " + route),
                    github.writes, "Only the missing checksum and final publication go over HTTP");
        }
    }

    @Test void olderDraftBehindStableOnSecondPageSendsZeroPatches() throws Exception {
        var editions = new ArrayList<Release>();
        for (int id = 82; id <= 180; id++) editions.add(edition(id, "v0.1." + id, false, false));
        editions.add(edition(181, "v0.100.0", false, false));
        try (var github = new DraftApi(false, true, editions, List.of())) {
            AuditedAssertions.assertEquals(List.of(), github.refusalWrites(81, "PUBLICATION-NEWER-STABLE"),
                    "No HTTP writes, including zero PATCH, when numeric ordering finds a newer stable release on page two");
        }
    }

    @Test void stableReleaseAppearingDuringChecksumUploadStopsBeforePatch() throws Exception {
        try (var github = new DraftApi(false, false, List.of(), List.of(edition(82, "v0.28.0", false, false)))) {
            String route = "/repos/" + PLAN.releaseRepository() + "/releases/81";
            AuditedAssertions.assertEquals(List.of("POST " + route + "/assets?name=" + CHECKSUM.name()),
                    github.refusalWrites(81, "PUBLICATION-NEWER-STABLE"),
                    "Refresh the stable inventory just before promotion and send zero PATCH");
        }
    }

    @Test void alreadyPublishedEditionIsRecoveredWithoutPatchWhenANewerOneExists() throws Exception {
        try (var github = new DraftApi(true, true, List.of(edition(82, "v0.28.0", false, false)), List.of())) {
            AuditedAssertions.assertEquals(81L, github.resume(81), "Recover the original published ID");
            AuditedAssertions.assertEquals(List.of(), github.writes, "Recover read-only even after another release shipped");
        }
    }

    @Test void unstableVersionTagInPublishedInventoryRefusesWrites() throws Exception {
        try (var github = new DraftApi(false, true, List.of(edition(82, "unversioned", false, false)), List.of())) {
            AuditedAssertions.assertEquals(List.of(), github.refusalWrites(81, "PUBLICATION-STABLE-VERSION"),
                    "An ambiguous stable tag never permits PATCH");
        }
    }

    private static Release edition(long id, String tag, boolean draft, boolean prerelease) {
        return new Release(id, tag, COMMIT, draft, prerelease);
    }

    private final class DraftApi implements AutoCloseable {
        private final ObjectMapper mapper = new ObjectMapper();
        private final List<String> writes = new CopyOnWriteArrayList<>();
        private final AtomicBoolean published;
        private final AtomicBoolean checksumPresent;
        private final List<Release> initial;
        private final List<Release> afterUpload;
        private final Plan plan;
        private final HttpServer server;

        DraftApi(boolean published, boolean checksumPresent, List<Release> initial, List<Release> afterUpload)
                throws IOException {
            this.published = new AtomicBoolean(published);
            this.checksumPresent = new AtomicBoolean(checksumPresent);
            this.initial = List.copyOf(initial);
            this.afterUpload = List.copyOf(afterUpload);
            Path file = Files.writeString(scratch.resolve(CHECKSUM.name()), "qualified checksum fixture");
            var checksum = new Payload(CHECKSUM.name(), file, Files.size(file),
                    ReleaseEvidenceFiles.sha256(Files.readAllBytes(file)));
            plan = new Plan(PLAN.sourceRepository(), PLAN.releaseRepository(), PLAN.version(), COMMIT, APK, checksum);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::respond);
            server.start();
        }

        long resume(long id) throws IOException {
            var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            return ReleasePublication.resume(plan, new GitHubPublication(scratch, "test_credential", endpoint, endpoint,
                            ignored -> new PromotionLease("fixture-owner", () -> { })),
                    id, remembered::add, trace::add);
        }

        List<String> refusalWrites(long id, String diagnostic) {
            String actual;
            try {
                actual = "Published release " + resume(id);
            } catch (IOException refused) {
                actual = refused.getMessage();
            }
            AuditedAssertions.assertEquals(diagnostic, actual, "Publication is refused with the expected diagnostic");
            return List.copyOf(writes);
        }

        private void respond(HttpExchange request) throws IOException {
            String url = request.getRequestURI().getPath();
            String method = request.getRequestMethod();
            if (!method.equals("GET")) writes.add(method + " " + request.getRequestURI());
            Release candidate = edition(81, plan.tag(), !published.get(), false);
            int status = 200;
            Object response;
            if (!"Bearer test_credential".equals(request.getRequestHeaders().getFirst("Authorization"))) {
                status = 401;
                response = Map.of();
            } else if (url.contains("/compare/")) {
                response = Map.of("status", "ahead", "merge_base_commit", Map.of("sha", COMMIT));
            } else if (url.endsWith("/git/ref/tags/" + plan.tag())) {
                response = Map.of("ref", "refs/tags/" + plan.tag(), "object", Map.of("type", "commit", "sha", COMMIT));
            } else if (url.endsWith("/atf_j5_releases")) {
                response = Map.of("full_name", PLAN.releaseRepository(), "private", true, "visibility", "private");
            } else if (method.equals("POST") && url.endsWith("/releases/81/assets")) {
                checksumPresent.set(Arrays.equals(Files.readAllBytes(plan.checksum().file()), request.getRequestBody().readAllBytes()));
                status = 201;
                response = Map.of("id", 12);
            } else if (method.equals("PATCH") && url.endsWith("/releases/81")) {
                var update = mapper.readTree(request.getRequestBody());
                published.set(!update.path("draft").asBoolean(true) && "true".equals(update.path("make_latest").asText()));
                response = Map.of("id", 81);
            } else if (url.endsWith("/releases/81/assets")) {
                response = checksumPresent.get() ? List.of(assetJson(11, APK), assetJson(12, plan.checksum()))
                        : List.of(assetJson(11, APK));
            } else if (url.endsWith("/releases/81")) {
                response = releaseJson(candidate);
            } else if (url.endsWith("/releases") && method.equals("GET")) {
                var inventory = new ArrayList<Release>();
                inventory.add(candidate);
                inventory.addAll(initial);
                if (checksumPresent.get()) inventory.addAll(afterUpload);
                int offset = request.getRequestURI().getQuery().endsWith("&page=2") ? 100 : 0;
                response = inventory.stream().skip(offset).limit(100).map(DraftApi::releaseJson).toList();
            } else {
                status = 404;
                response = Map.of();
            }
            byte[] bytes = mapper.writeValueAsBytes(response);
            request.sendResponseHeaders(status, bytes.length);
            try (var output = request.getResponseBody()) { output.write(bytes); }
        }

        private static Map<String, Object> releaseJson(Release release) {
            return Map.of("id", release.id(), "tag_name", release.tag(), "target_commitish", release.target(),
                    "draft", release.draft(), "prerelease", release.prerelease());
        }

        @Override public void close() { server.stop(0); }
    }

    private static java.util.Map<String, Object> assetJson(long id, Payload payload) {
        return java.util.Map.of("id", id, "name", payload.name(), "state", "uploaded",
                "size", payload.bytes(), "digest", "sha256:" + payload.sha256());
    }

    private String rejected(Plan plan, Mock github, Path journal) throws IOException {
        try { PublicationCommand.resumeWithJournal(plan, github, journal, trace::add); }
        catch (IOException failure) { return failure.getMessage(); }
        return "unexpected success";
    }

    private long run(Mock github, long id) throws IOException {
        return ReleasePublication.resume(PLAN, github, id, remembered::add, trace::add);
    }

    private String failure(Mock github, long id) throws IOException {
        try { run(github, id); }
        catch (IOException exception) { return exception.getMessage(); }
        return "unexpected success";
    }

    private static Asset asset(long id, Payload payload) {
        return new Asset(id, payload.name(), "uploaded", payload.bytes(), payload.sha256());
    }

    private static Mock draft() {
        var mock = new Mock();
        mock.sourceTag = COMMIT;
        mock.releaseTag = COMMIT;
        mock.item = new Release(81, PLAN.tag(), COMMIT, true, false);
        return mock;
    }

    private static final class Mock implements Remote {
        final List<String> writes = new ArrayList<>();
        final List<Asset> assets = new ArrayList<>();
        String sourceTag;
        String releaseTag;
        boolean onMain = true;
        boolean interruptUpload;
        boolean interruptDraft;
        boolean rejectPush;
        Release item;

        @Override public boolean sourceOnMain(Plan plan) { return onMain; }
        @Override public boolean privateRepository(String repository) { return true; }
        @Override public String tagCommit(String repository, String tag) {
            return repository.equals(PLAN.sourceRepository()) ? sourceTag : releaseTag;
        }
        @Override public void pushTag(String repository, String tag, String commit) throws IOException {
            writes.add("tag:" + repository);
            if (rejectPush) throw new IOException("Tag push refused");
            if (repository.equals(PLAN.sourceRepository())) sourceTag = commit;
            else releaseTag = commit;
        }
        @Override public List<Release> releases(String repository) {
            return item == null ? List.of() : List.of(item);
        }
        @Override public Release release(String repository, long id) { return item; }
        @Override public long createDraft(Plan plan) throws IOException {
            writes.add("draft");
            item = new Release(81, plan.tag(), plan.commit(), true, false);
            if (interruptDraft) {
                interruptDraft = false;
                throw new IOException("Draft outcome unknown");
            }
            return item.id();
        }
        @Override public List<Asset> assets(String repository, long releaseId) { return List.copyOf(assets); }
        @Override public void upload(String repository, long releaseId, Payload payload) throws IOException {
            writes.add("upload:" + payload.name());
            assets.add(asset(11 + assets.size(), payload));
            if (interruptUpload) {
                interruptUpload = false;
                throw new IOException("Write outcome unknown");
            }
        }
        @Override public PromotionLease reservePromotion(Plan plan) {
            return new PromotionLease("fixture-owner", () -> { });
        }

        @Override public void publish(String repository, long releaseId) {
            writes.add("publish");
            item = new Release(item.id(), item.tag(), item.target(), false, false);
        }
    }
}
