package atf.rentz.distribution.publication;

import atf.rentz.distribution.retirement.ReleaseRetirement;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.regex.Pattern;

/** The GitHub adapter shares the publication channel; it has no source-tag deletion operation. */
final class GitHubRetirement implements ReleaseRetirement.Remote {
    private final GitHubPublication github;
    private final GitPromotionChannel channel;
    private final Path archive;
    private final ReleaseRetirement.Plan plan;
    private ReleasePublication.PromotionLease lease;
    private Long pendingDelete;
    GitHubRetirement(GitHubPublication github, GitPromotionChannel channel, Path archive, ReleaseRetirement.Plan plan) {
        this.github = github; this.channel = channel; this.archive = archive; this.plan = plan;
    }
    @Override public ReleaseRetirement.State observe(ReleaseRetirement.Plan expected) throws IOException {
        if (!plan.equals(expected)) throw new IOException("RETIREMENT-PLAN-DRIFT");
        String main = github.get(plan.repository(), "/branches/main", false).path("commit").path("sha").asText();
        String source = github.tagCommit(plan.repository(), plan.tag());
        var latest = github.get(plan.repository(), "/releases/latest", false);
        var rows = new ArrayList<ReleaseRetirement.Edition>();
        for (var release : github.releases(plan.repository())) {
            rows.add(new ReleaseRetirement.Edition(release.id(), release.tag(),
                    release.tag().equals(plan.tag()) ? source : release.target(), release.draft(), release.prerelease()));
        }
        String link = readmeLink(main);
        boolean write = github.publicRepository(plan.repository());
        if (pendingDelete != null && rows.stream().noneMatch(row -> row.id() == pendingDelete)) {
            lease.confirmPatch(); pendingDelete = null;
        }
        return new ReleaseRetirement.State(write, main, source == null ? "" : source,
                latest.path("id").asLong(-1), latest.path("tag_name").asText(), link, rows);
    }
    private String readmeLink(String main) throws IOException {
        if (!main.matches("[a-f0-9]{40}")) throw new IOException("RETIREMENT-MAIN");
        var file = github.get(plan.repository(), "/contents/README.md?ref=" + main, false);
        if (!file.path("encoding").asText().equals("base64") || file.path("size").asLong(-1) < 1
                || file.path("size").asLong() > 1_048_576) throw new IOException("RETIREMENT-README");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(file.path("content").asText().replace("\n", "").replace("\r", "")); }
        catch (IllegalArgumentException invalid) { throw new IOException("RETIREMENT-README", invalid); }
        if (bytes.length != file.path("size").longValue()) throw new IOException("RETIREMENT-README");
        return readmeLink(new String(bytes, StandardCharsets.UTF_8), plan.apkUrl());
    }
    static String readmeLink(String readme, String expected) throws IOException {
        String text = readme.replace("\r\n", "\n");
        var sections = Pattern.compile("(?ms)^## Jocul[ \\t]*(?:\\n|\\z)(.*?)(?=^## |\\z)").matcher(text);
        if (!sections.find()) throw new IOException("RETIREMENT-README");
        var lines = sections.group(1).lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (sections.find() || lines.size() != 2) throw new IOException("RETIREMENT-README");
        var apk = Pattern.compile("^- \\[[^\\[\\]]+\\]\\(([^()\\s]+)\\)$").matcher(lines.getFirst());
        if (!apk.matches() || !apk.group(1).equals(expected)
                || !lines.getLast().matches("- \\[[^\\[\\]]+\\]\\(https://crististefanita\\.github\\.io/rentz/\\)"))
            throw new IOException("RETIREMENT-README");
        var downloads = Pattern.compile("\\]\\(([^)\\s]*(?:/releases/(?:download|tag)/|\\.apk)[^)\\s]*)\\)").matcher(text);
        int count = 0;
        while (downloads.find()) { if (!downloads.group(1).equals(expected)) throw new IOException("RETIREMENT-README"); count++; }
        if (count != 1) throw new IOException("RETIREMENT-README");
        return expected;
    }
    @Override public ReleaseRetirement.Lease reserve(ReleaseRetirement.Plan expected) throws IOException {
        if (!plan.equals(expected)) throw new IOException("RETIREMENT-PLAN-DRIFT");
        lease = channel.reserve(plan.tag());
        return new ReleaseRetirement.Lease() {
            public void check() throws IOException {
                if (!lease.owner().equals(channel.owner())) throw new IOException("RETIREMENT-CHANNEL-OWNER");
            }
            public void close() throws IOException { lease.close(); }
        };
    }
    @Override public void archive(ReleaseRetirement.Edition edition) throws IOException {
        var record = github.get(plan.repository(), "/releases/" + edition.id(), false);
        if (record.path("id").asLong(-1) != edition.id() || !record.path("tag_name").asText().equals(edition.tag())
                || record.path("draft").asBoolean(true) || record.path("prerelease").asBoolean(true)
                || !record.path("target_commitish").asText().equals(edition.source())) throw new IOException("RETIREMENT-EDITION-DRIFT");
        var mapper = new ObjectMapper(); var proof = mapper.createObjectNode();
        proof.set("release", record); proof.set("assets", mapper.valueToTree(github.assets(plan.repository(), edition.id())));
        String tagSource = github.tagCommit(plan.repository(), edition.tag());
        if (tagSource == null) throw new IOException("RETIREMENT-ARCHIVE-TAG");
        proof.put("preservedTagSource", tagSource);
        Files.write(archive.resolve("release-" + edition.id() + ".json"), mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(proof), StandardOpenOption.CREATE_NEW, StandardOpenOption.SYNC);
    }
    @Override public void deleteRelease(long id) throws IOException {
        pendingDelete = id; lease.startPatch(); github.deleteRelease(plan.repository(), id);
    }
}
