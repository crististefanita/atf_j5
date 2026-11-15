package atf.rentz.distribution.publication;

import atf.rentz.RentzRulePublication;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.AuditedAssertions;
import atf.testkit.assertions.ComponentAuditExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
        var mapper = new ObjectMapper();
        Path file = Files.writeString(scratch.resolve(CHECKSUM.name()), "qualified checksum fixture");
        var checksum = new Payload(CHECKSUM.name(), file, Files.size(file),
                atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256(Files.readAllBytes(file)));
        var plan = new Plan(PLAN.sourceRepository(), PLAN.releaseRepository(), PLAN.version(), COMMIT, APK, checksum);
        var writes = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var published = new java.util.concurrent.atomic.AtomicBoolean();
        var checksumPresent = new java.util.concurrent.atomic.AtomicBoolean();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", request -> {
            String url = request.getRequestURI().getPath();
            String method = request.getRequestMethod();
            int status = 200;
            Object response;
            if (!"Bearer test_credential".equals(request.getRequestHeaders().getFirst("Authorization"))) {
                status = 401;
                response = java.util.Map.of();
            } else if (url.contains("/compare/")) {
                response = java.util.Map.of("status", "ahead", "merge_base_commit", java.util.Map.of("sha", COMMIT));
            } else if (url.endsWith("/git/ref/tags/" + plan.tag())) {
                response = java.util.Map.of("ref", "refs/tags/" + plan.tag(), "object", java.util.Map.of("type", "commit", "sha", COMMIT));
            } else if (url.endsWith("/atf_j5_releases")) {
                response = java.util.Map.of("full_name", PLAN.releaseRepository(), "private", true, "visibility", "private");
            } else if (method.equals("POST") && url.endsWith("/releases/81/assets")) {
                writes.add(method + " " + request.getRequestURI());
                checksumPresent.set(java.util.Arrays.equals(Files.readAllBytes(file), request.getRequestBody().readAllBytes()));
                status = 201;
                response = java.util.Map.of("id", 12);
            } else if (method.equals("PATCH") && url.endsWith("/releases/81")) {
                writes.add(method + " " + request.getRequestURI());
                published.set(!mapper.readTree(request.getRequestBody()).path("draft").asBoolean(true));
                response = java.util.Map.of("id", 81);
            } else if (url.endsWith("/assets")) {
                response = checksumPresent.get() ? List.of(assetJson(11, APK), assetJson(12, checksum)) : List.of(assetJson(11, APK));
            } else {
                var draft = java.util.Map.of("id", 81, "tag_name", PLAN.tag(), "target_commitish", COMMIT,
                        "draft", !published.get(), "prerelease", false);
                response = url.endsWith("/releases") ? List.of(draft) : draft;
            }
            byte[] bytes = mapper.writeValueAsBytes(response);
            request.sendResponseHeaders(status, bytes.length);
            try (var output = request.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var endpoint = java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            var remote = new GitHubPublication(scratch, "test_credential", endpoint, endpoint);
            AuditedAssertions.assertEquals(81L, ReleasePublication.resume(plan, remote, 0, remembered::add, trace::add),
                    "Resume through real HTTP serialization against loopback");
            String route = "/repos/" + PLAN.releaseRepository() + "/releases/81";
            AuditedAssertions.assertEquals(List.of("POST " + route + "/assets?name=" + CHECKSUM.name(), "PATCH " + route),
                    writes, "A draft is listed and opened by ID; only checksum and final publication go over HTTP");
        } finally {
            server.stop(0);
        }
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
        @Override public List<Release> releases(String repository, String tag) {
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
        @Override public void publish(String repository, long releaseId) {
            writes.add("publish");
            item = new Release(item.id(), item.tag(), item.target(), false, false);
        }
    }
}
