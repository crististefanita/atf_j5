package atf.rentz.distribution.publication;

import atf.rentz.distribution.retirement.ReleaseRetirement;

import atf.rentz.RentzRulePublication;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.ComponentAuditExtension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static atf.testkit.assertions.AuditedAssertions.*;

@AtfComponentTest
class ReleaseRetirementTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            ReleaseRetirementTest.class, "retirement-components.html", "Injected release channel; no live GitHub mutation");
    static final String SHA = "a".repeat(40), MAIN = "b".repeat(40);
    static final ReleaseRetirement.Plan PLAN = new ReleaseRetirement.Plan("example/rentz", "0.29.0", SHA, MAIN,
            29, "https://github.com/example/rentz/releases/download/v0.29.0/RentzLab-0.29.0.apk");

    @Test void onlyOlderStableEditionsAreArchivedThenRemovedWhileTagsStayOutsideTheWritePort() throws Exception {
        var remote = new Channel();
        assertEquals(List.of(9L, 28L), ReleaseRetirement.retire(PLAN, remote), "Numeric versions, not lexical ordering");
        assertEquals(List.of("archive:9", "delete:9", "archive:28", "delete:28"), remote.writes, "Audit precedes each deletion");
        assertTrue(remote.closed, "Promotion lease released after the last verification");
        assertEquals(List.of("v0.29.0", "v0.30.0", "snapshot"), remote.editions.stream().map(ReleaseRetirement.Edition::tag).toList(),
                "Latest, drafts and non-version records remain available");
    }
    @ParameterizedTest @ValueSource(strings={"permission", "main", "tag", "latest", "readme", "duplicate"})
    void invalidOrStaleAuthorityCannotDeleteAnyEdition(String defect) {
        var remote = new Channel(); remote.defect = defect;
        var denied = assertThrows(IOException.class, () -> ReleaseRetirement.retire(PLAN, remote));
        assertTrue(denied.getMessage().startsWith("RETIREMENT-"), "Explicit refusal diagnostic");
        assertEquals(List.of(), remote.writes, "No archive or DELETE before complete authority");
    }
    @Test void newPublicationBetweenInspectionAndTheLeasePreventsEveryDelete() {
        var remote = new Channel(); remote.changeAfterLease = true;
        assertThrows(IOException.class, () -> ReleaseRetirement.retire(PLAN, remote));
        assertEquals(List.of(), remote.writes, "A newer stable edition invalidates the earlier plan");
        assertTrue(remote.closed, "Lease is released on refusal");
    }
    @Test void ownershipLossAfterArchivalPreventsTheIrreversibleOperation() {
        var remote = new Channel(); remote.loseAfterArchive = true;
        assertThrows(IOException.class, () -> ReleaseRetirement.retire(PLAN, remote));
        assertEquals(List.of("archive:9"), remote.writes, "Archive is retained, DELETE was never sent");
    }
    @Test void anUncertainDeleteResponseIsReconciledByReadingWithoutRepeatingDelete() throws Exception {
        var remote = new Channel(); remote.responseLost = true;
        assertEquals(List.of(9L, 28L), ReleaseRetirement.retire(PLAN, remote), "Confirmed absence resolves response uncertainty");
        assertEquals(2L, remote.writes.stream().filter(x -> x.startsWith("delete:")).count(), "One DELETE per old edition");
    }
    @ParameterizedTest @ValueSource(ints={204, 403, 500})
    void githubDeleteHasOneBoundedRequestAndRequiresAnEmpty204(int status) throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new java.util.concurrent.CopyOnWriteArrayList<String>();
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            exchange.sendResponseHeaders(status, -1); exchange.close();
        });
        server.start();
        try {
            var endpoint = java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            var github = new GitHubPublication(java.nio.file.Path.of("."), "offline_mock", endpoint, endpoint,
                    plan -> { throw new IOException("Unexpected publication lease"); });
            if (status == 204) assertDoesNotThrow(() -> github.deleteRelease("example/rentz", 28), "Successful empty DELETE response");
            else assertThrows(IOException.class, () -> github.deleteRelease("example/rentz", 28), "HTTP refusal is not success");
            assertEquals(List.of("DELETE /repos/example/rentz/releases/28"), requests, "No retry and no source-tag endpoint");
        } finally { server.stop(0); }
    }
    static final class Channel implements ReleaseRetirement.Remote {
        final List<ReleaseRetirement.Edition> editions = new ArrayList<>(List.of(
                new ReleaseRetirement.Edition(28, "v0.28.0", SHA, false, false),
                new ReleaseRetirement.Edition(9, "v0.9.0", SHA, false, false),
                new ReleaseRetirement.Edition(29, "v0.29.0", SHA, false, false),
                new ReleaseRetirement.Edition(30, "v0.30.0", SHA, true, false),
                new ReleaseRetirement.Edition(31, "snapshot", SHA, false, false)));
        final List<String> writes = new ArrayList<>();
        String defect = ""; boolean leased, closed, changeAfterLease, loseAfterArchive, responseLost;
        public ReleaseRetirement.State observe(ReleaseRetirement.Plan plan) {
            var rows = new ArrayList<>(editions);
            if (defect.equals("duplicate")) rows.add(editions.get(0));
            return new ReleaseRetirement.State(!defect.equals("permission"), defect.equals("main") ? SHA : MAIN,
                    defect.equals("tag") ? MAIN : SHA, defect.equals("latest") || leased && changeAfterLease ? 30 : 29,
                    defect.equals("latest") || leased && changeAfterLease ? "v0.30.0" : "v0.29.0",
                    defect.equals("readme") ? "https://example.invalid/stale" : PLAN.apkUrl(), rows);
        }
        public ReleaseRetirement.Lease reserve(ReleaseRetirement.Plan plan) {
            leased = true;
            return new ReleaseRetirement.Lease() {
                public void check() throws IOException { if (loseAfterArchive && !writes.isEmpty()) throw new IOException("RETIREMENT-LEASE"); }
                public void close() { closed = true; }
            };
        }
        public void archive(ReleaseRetirement.Edition edition) { writes.add("archive:" + edition.id()); }
        public void deleteRelease(long id) throws IOException {
            writes.add("delete:" + id); editions.removeIf(x -> x.id() == id);
            if (responseLost) throw new IOException("Controlled response loss after server mutation");
        }
    }
}
