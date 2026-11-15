package atf.rentz.distribution.publication;

import atf.rentz.distribution.publication.ReleasePublication.Asset;
import atf.rentz.distribution.publication.ReleasePublication.Payload;
import atf.rentz.distribution.publication.ReleasePublication.Plan;
import atf.rentz.distribution.publication.ReleasePublication.Release;
import atf.rentz.distribution.publication.ReleasePublication.Remote;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** GitHub transport with fixed API/upload origins and no automatic redirects or write retries. */
final class GitHubPublication implements Remote {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private static final int RESPONSE_LIMIT = 8 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final URI api;
    private final URI uploads;
    private final String credential;
    private final Path checkout;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    GitHubPublication(Path checkout, String credential) {
        this(checkout, credential, URI.create("https://api.github.com/"), URI.create("https://uploads.github.com/"));
    }

    GitHubPublication(Path checkout, String credential, URI api, URI uploads) {
        if (credential == null || credential.isBlank() || !credential.matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("PUBLICATION-CREDENTIAL");
        this.checkout = checkout;
        this.credential = credential;
        this.api = api;
        this.uploads = uploads;
    }

    @Override public boolean sourceOnMain(Plan plan) throws IOException {
        JsonNode comparison = get(plan.sourceRepository(), "/compare/" + plan.commit() + "...main?per_page=1", false);
        return List.of("identical", "ahead").contains(comparison.path("status").asText())
                && plan.commit().equals(comparison.path("merge_base_commit").path("sha").asText());
    }

    @Override public boolean privateRepository(String repository) throws IOException {
        JsonNode data = get(repository, "", false);
        return repository.equalsIgnoreCase(data.path("full_name").asText())
                && data.path("private").isBoolean() && data.path("private").booleanValue()
                && "private".equals(data.path("visibility").asText());
    }

    @Override public String tagCommit(String repository, String tag) throws IOException {
        JsonNode ref = get(repository, "/git/ref/tags/" + tag, true);
        if (ref == null) return null;
        if (!("refs/tags/" + tag).equals(ref.path("ref").asText())) throw invalid();
        JsonNode object = ref.path("object");
        for (int depth = 0; depth < 8; depth++) {
            String sha = object.path("sha").asText();
            if (!sha.matches("[a-f0-9]{40}")) throw invalid();
            switch (object.path("type").asText()) {
                case "commit": return sha;
                case "tag": object = get(repository, "/git/tags/" + sha, false).path("object"); break;
                default: throw invalid();
            }
        }
        throw invalid();
    }

    @Override public void pushTag(String repository, String tag, String commit) throws IOException {
        var command = new ProcessBuilder("git", "-C", checkout.toString(), "push", "--porcelain",
                "https://github.com/" + repository + ".git", commit + ":refs/tags/" + tag);
        command.environment().put("GIT_TERMINAL_PROMPT", "0");
        command.environment().put("GCM_INTERACTIVE", "Never");
        command.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = command.start();
        try {
            if (!process.waitFor(90, TimeUnit.SECONDS)) throw new IOException("PUBLICATION-TAG-PUSH-TIMEOUT");
            if (process.exitValue() != 0) throw new IOException("PUBLICATION-TAG-PUSH-REFUSED");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("PUBLICATION-TAG-PUSH-INTERRUPTED");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Override public List<Release> releases(String repository) throws IOException {
        var inventory = new ArrayList<Release>();
        for (JsonNode row : pages(repository, "/releases")) {
            inventory.add(release(row));
        }
        return List.copyOf(inventory);
    }

    @Override public Release release(String repository, long id) throws IOException {
        JsonNode value = get(repository, "/releases/" + id, true);
        return value == null ? null : release(value);
    }

    @Override public long createDraft(Plan plan) throws IOException {
        return positive(write("POST", plan.releaseRepository(), "/releases",
                Map.of("tag_name", plan.tag(), "target_commitish", plan.commit(), "name", "Rentz Lab " + plan.version(),
                        "draft", true, "prerelease", false), 201).path("id"));
    }

    @Override public List<Asset> assets(String repository, long releaseId) throws IOException {
        var assets = new ArrayList<Asset>();
        for (JsonNode row : pages(repository, "/releases/" + releaseId + "/assets")) {
            String digest = row.path("digest").asText();
            assets.add(new Asset(positive(row.path("id")), row.path("name").asText(), row.path("state").asText(),
                    positive(row.path("size")), digest.startsWith("sha256:") ? digest.substring(7) : ""));
        }
        return List.copyOf(assets);
    }

    @Override public void upload(String repository, long releaseId, Payload payload) throws IOException {
        // Use only bytes that were rechecked immediately before this write.
        byte[] bytes = PublicationCommand.payloadBytes(payload);
        URI uri = uploads.resolve("repos/" + repository + "/releases/" + releaseId + "/assets?name=" + payload.name());
        String type = payload.name().endsWith(".apk") ? "application/vnd.android.package-archive" : "text/plain";
        request("POST", uri, HttpRequest.BodyPublishers.ofByteArray(bytes), type, 201, false);
    }

    @Override public void publish(String repository, long releaseId) throws IOException {
        write("PATCH", repository, "/releases/" + releaseId, Map.of("draft", false, "make_latest", "true"), 200);
    }

    private List<JsonNode> pages(String repository, String path) throws IOException {
        var rows = new ArrayList<JsonNode>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode data = get(repository, path + "?per_page=" + PAGE_SIZE + "&page=" + page, false);
            if (!data.isArray() || data.size() > PAGE_SIZE) throw invalid();
            data.forEach(rows::add);
            if (data.size() < PAGE_SIZE) return rows;
        }
        throw new IOException("PUBLICATION-INCOMPLETE-INVENTORY");
    }

    private JsonNode get(String repository, String path, boolean optional) throws IOException {
        return request("GET", api.resolve("repos/" + repository + path), HttpRequest.BodyPublishers.noBody(), null, 200, optional);
    }

    private JsonNode write(String method, String repository, String path, Map<String, Object> value, int status) throws IOException {
        return request(method, api.resolve("repos/" + repository + path),
                HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(value)), "application/json", status, false);
    }

    private JsonNode request(String method, URI uri, HttpRequest.BodyPublisher body, String contentType,
                             int expected, boolean notFoundAllowed) throws IOException {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "ATF-Rentz-publication").header("Authorization", "Bearer " + credential)
                .method(method, body);
        if (contentType != null) request.header("Content-Type", contentType);
        try {
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (notFoundAllowed && response.statusCode() == 404) return null;
                if (response.statusCode() != expected) throw new IOException("PUBLICATION-" + method + "-HTTP-" + response.statusCode());
                byte[] bytes = stream.readNBytes(RESPONSE_LIMIT + 1);
                if (bytes.length > RESPONSE_LIMIT) throw invalid();
                JsonNode json = JSON.readTree(bytes);
                if (json == null || json.isNull()) throw invalid();
                return json;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("PUBLICATION-HTTP-INTERRUPTED");
        }
    }

    private static Release release(JsonNode value) throws IOException {
        if (!value.path("draft").isBoolean() || !value.path("prerelease").isBoolean()) throw invalid();
        return new Release(positive(value.path("id")), value.path("tag_name").asText(),
                value.path("target_commitish").asText(), value.path("draft").booleanValue(), value.path("prerelease").booleanValue());
    }

    private static long positive(JsonNode value) throws IOException {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw invalid();
        return value.longValue();
    }

    private static IOException invalid() { return new IOException("PUBLICATION-GITHUB-RESPONSE"); }
}
