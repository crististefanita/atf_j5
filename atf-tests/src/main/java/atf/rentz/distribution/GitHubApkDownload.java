package atf.rentz.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Read-only GitHub acquisition: metadata identity, complete stream, digest and every ZIP CRC. */
public final class GitHubApkDownload {
    private static final int METADATA_BUDGET = 1_048_576;
    private static final Set<String> ASSET_HOSTS = Set.of("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com");
    private final URI api;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build();

    public GitHubApkDownload(String token) { this(URI.create("https://api.github.com/"), token); }
    /** An explicit loopback endpoint supports offline transport component tests. */
    public GitHubApkDownload(URI api, String token) {
        boolean github = api.equals(URI.create("https://api.github.com/"));
        boolean loopback = "http".equals(api.getScheme()) && "127.0.0.1".equals(api.getHost()) && api.getPath().equals("/");
        if (!github && !loopback) throw new IllegalArgumentException("Unsupported release API origin");
        if (token != null && (token.contains("\r") || token.contains("\n"))) throw new IllegalArgumentException("Invalid release credential characters");
        this.api = api;this.token = token;
    }
    public record Proof(String version, String publicUrl, long bytes, String sha256, String zipCrc, int httpStatus, Path file) { }

    public Proof download(ApkDownloadTarget target, Path outputDirectory) throws IOException, InterruptedException {
        JsonNode release = metadata(api.resolve("repos/" + target.repository() + "/releases/tags/v" + target.version()));
        require(release.path("tag_name").asText().equals("v" + target.version()) && release.path("draft").isBoolean()
                && !release.path("draft").booleanValue(), "Release identity or publication state differs");
        require(release.path("assets").isArray(), "Release asset inventory is missing");
        var assets = java.util.stream.StreamSupport.stream(release.path("assets").spliterator(), false)
                .filter(asset -> asset.path("name").asText().equals(target.fileName())).toList();
        require(assets.size() == 1, "Expected exactly one APK asset");JsonNode asset = assets.getFirst();
        require(asset.path("size").isIntegralNumber() && asset.path("size").canConvertToLong()
                && asset.path("size").longValue() == target.bytes(), "Asset size differs from declared APK");
        require(asset.path("digest").asText().equals("sha256:" + target.sha256())
                && asset.path("state").asText().equals("uploaded")
                && asset.path("browser_download_url").asText().equals(target.publicUrl()), "Asset hash, state or URL differs");
        require(asset.path("id").isIntegralNumber() && asset.path("id").canConvertToLong() && asset.path("id").longValue() > 0, "Asset ID is invalid");
        Files.createDirectories(outputDirectory);Path destination = outputDirectory.resolve(target.fileName());
        require(!Files.exists(destination), "Download destination already exists");
        URI uri = api.resolve("repos/" + target.repository() + "/releases/assets/" + asset.path("id").longValue());
        Path partial = Files.createTempFile(outputDirectory, ".rentz-download-", ".part");long count = 0;
        var response = response(uri, "application/octet-stream");
        try (var input = response.body();var output = Files.newOutputStream(partial)) {
            byte[] buffer = new byte[16384];int read;
            while ((read = input.read(buffer)) != -1) {
                count = Math.addExact(count, read);require(count <= target.bytes(), "APK response exceeds the declared size");output.write(buffer, 0, read);
            }
        }
        require(count == target.bytes(), "APK response is incomplete");ApkPayload.verify(partial, target.bytes(), target.sha256());
        Files.move(partial, destination);
        return new Proof(release.path("tag_name").asText().substring(1), asset.path("browser_download_url").asText(),
                Files.size(destination), ApkPayload.hash(destination), "PASS", response.statusCode(), destination);
    }
    private JsonNode metadata(URI uri) throws IOException, InterruptedException {
        var response = response(uri, "application/vnd.github+json");
        try (var input = response.body()) {
            byte[] bytes = input.readNBytes(METADATA_BUDGET + 1);require(bytes.length <= METADATA_BUDGET, "Release metadata exceeds its budget");
            JsonNode value = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
            require(value != null && value.isObject(), "Release metadata is not an object");return value;
        }
    }
    private HttpResponse<java.io.InputStream> response(URI initial, String accept) throws IOException, InterruptedException {
        URI uri = initial;
        for (int redirect = 0; redirect < 6; redirect++) {
            require(allowed(uri), "Unexpected release redirect origin");
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("Accept", accept)
                    .header("User-Agent", "ATF-Rentz-release-download");
            if (sameOrigin(uri, api) && token != null && !token.isBlank()) request.header("Authorization", "Bearer " + token);
            var response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status == 200) return response;
            try (var ignored = response.body()) {
                if (!List.of(301, 302, 303, 307, 308).contains(status)) throw new IOException("Release download HTTP " + status);
                String location = response.headers().firstValue("Location").orElseThrow(() -> new IOException("Release redirect has no location"));
                uri = uri.resolve(location);
            }
        }
        throw new IOException("Release redirect limit exceeded");
    }
    private boolean allowed(URI uri) {
        if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getHost() == null || uri.getScheme() == null) return false;
        if (api.getScheme().equals("http")) return uri.getScheme().equals("http") && uri.getHost().equals("127.0.0.1");
        return uri.getScheme().equals("https") && ASSET_HOSTS.contains(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443);
    }
    private static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equals(second.getScheme()) && first.getHost().equals(second.getHost()) && first.getPort() == second.getPort();
    }
    private static void require(boolean valid, String message) throws IOException { if (!valid) throw new IOException(message); }
}
