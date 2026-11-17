package atf.rentz.distribution.publication;

import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.rentz.distribution.evidence.ReleaseEvidenceFiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256;

/** Explicit qualification of the Git permission boundary on an isolated private repository. */
public final class GitHubPromotionQualification {
    private static final String API = "https://api.github.com/";
    private static final String GIT = "https://github.com/";
    private static final String WRITER_A = "RENTZ_PROMOTION_WRITER_A";
    private static final String WRITER_B = "RENTZ_PROMOTION_WRITER_B";
    private static final String READER = "RENTZ_PROMOTION_READER";
    private static final ObjectMapper JSON = new ObjectMapper();

    record Identity(long account, String login, long repository, boolean pull, boolean push) { }

    private record Actor(Identity identity, String token) {
        @Override public String toString() { return identity.login(); }

        String authorization() {
            String pair = identity.login() + ":" + token;
            return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
        }
    }

    @FunctionalInterface private interface Attempt { void run() throws IOException; }

    private GitHubPromotionQualification() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) throw new IOException("PROMOTION-ARGUMENTS");
        Path checkout = Path.of(args[0]).toRealPath();
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        ReleaseEvidenceFiles.externalRun(checkout, output.getParent());
        ReleaseEvidenceFiles.assertNoSymlinkPath(output);
        Files.createDirectory(output);
        var report = JSON.createObjectNode().put("schemaVersion", 1).put("status", "BLOCKED")
                .put("scope", "Private GitHub test channel; Git refs and identities only");
        var checks = new ArrayList<AtfAssertion>();
        String diagnostic = "PASS";
        try {
            require(args[3].matches("[a-f0-9]{40}"), "SOURCE");
            String repository = testRepository(args[1]);
            report.put("sourceCommit", args[3]).put("repository", repository);
            qualify(output, repository, checks);
            report.put("status", "PASS");
        } catch (IOException | RuntimeException failure) {
            String code = failure.getMessage();
            diagnostic = code != null && code.matches("(PROMOTION|PUBLICATION)-[A-Z0-9-]+") ? code : "PROMOTION-INTERRUPTED";
            report.put("diagnostic", diagnostic);
        }
        boolean passed = diagnostic.equals("PASS");
        checks.add(new AtfAssertion("Qualification verdict", "PASS", diagnostic, passed));
        Path extent = output.resolve("promotion-channel-extent.html");
        writeExtent(extent, checks, passed);
        report.set("checks", JSON.valueToTree(checks));
        report.put("extentSha256", sha256(Files.readAllBytes(extent)));
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("promotion-channel.json").toFile(), report);
        if (!passed) throw new IOException(diagnostic);
    }

    private static void qualify(Path output, String repository, List<AtfAssertion> checks) throws IOException {
        // Resolve all three credentials before opening a network connection.
        String firstToken = token(WRITER_A);
        String secondToken = token(WRITER_B);
        String readerToken = token(READER);
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        Actor first = actor(http, repository, firstToken);
        Actor second = actor(http, repository, secondToken);
        Actor reader = actor(http, repository, readerToken);
        validateIdentities(first.identity(), second.identity(), reader.identity());
        compare(checks, "Distinct authenticated identities",
                "2 writers and 1 reader on the same private repository", "2 writers and 1 reader on the same private repository");
        compare(checks, "Writer A · " + first.identity().login() + " (#" + first.identity().account() + ")", "pull and push", permission(first.identity()));
        compare(checks, "Writer B · " + second.identity().login() + " (#" + second.identity().account() + ")", "pull and push", permission(second.identity()));
        compare(checks, "Reader · " + reader.identity().login() + " (#" + reader.identity().account() + ")", "pull only", permission(reader.identity()));
        JsonNode releases = get(http, "repos/" + repository + "/releases?per_page=1", first.token());
        require(releases.isArray() && releases.isEmpty(), "CHANNEL-HAS-RELEASES");

        String remote = GIT + repository + ".git";
        Path checkout = Files.createDirectory(output.resolve("checkout"));
        channel(checkout, remote, first).initialize();
        var a = channel(checkout, remote, first);
        var b = channel(checkout, remote, second);
        var readOnly = channel(checkout, remote, reader);
        compare(checks, "Initial reservation", null, a.owner());
        compare(checks, "Reader receive-pack service", "HTTP 403", "HTTP " + receivePackStatus(http, remote, reader));
        compare(checks, "Read-only account cannot reserve a free channel", "PUBLICATION-CHANNEL-ACQUIRE", outcome(() -> {
            try (var unexpected = readOnly.reserve("permission-check-reader")) { }
        }));
        compare(checks, "Reservation after reader refusal", null, a.owner());

        String staleOwner;
        try (var previous = b.reserve("permission-check-b")) {
            staleOwner = previous.owner();
            compare(checks, "Writer B acquires", staleOwner, a.owner());
        }
        compare(checks, "Writer B releases", null, a.owner());
        try (var current = a.reserve("permission-check-a")) {
            compare(checks, "Writer A acquires", current.owner(), b.owner());
            compare(checks, "Writer B cannot acquire the held channel", "PUBLICATION-CHANNEL-IN-USE", outcome(() -> {
                try (var unexpected = b.reserve("permission-check-held")) { }
            }));
            compare(checks, "Foreign SHA cannot release the current owner", "PUBLICATION-CHANNEL-RELEASE",
                    outcome(() -> b.release(staleOwner)));
            compare(checks, "Current owner survives the foreign SHA", current.owner(), b.owner());
        }
        compare(checks, "Writer A releases", null, b.owner());
        try (var next = b.reserve("permission-check-b-retry")) {
            compare(checks, "Writer B acquires after A", next.owner(), a.owner());
        }
        compare(checks, "Final reservation", null, a.owner());
    }

    static String testRepository(String repository) throws IOException {
        require(repository.matches("[A-Za-z0-9-]+/[A-Za-z0-9_.-]+"), "REPOSITORY");
        String name = repository.substring(repository.indexOf('/') + 1).toLowerCase(Locale.ROOT);
        require(!List.of(".", "..", "rentz", "atf_j5", "atf_j5_releases", "rentz_releases").contains(name)
                && !name.endsWith(".git"), "PRODUCTION-REPOSITORY");
        return repository;
    }

    static void validateIdentities(Identity first, Identity second, Identity reader) throws IOException {
        require(first.account() > 0 && second.account() > 0 && reader.account() > 0
                && first.account() != second.account() && first.account() != reader.account()
                && second.account() != reader.account(), "DISTINCT-IDENTITIES");
        require(first.repository() > 0 && first.repository() == second.repository() && first.repository() == reader.repository(),
                "REPOSITORY-IDENTITY");
        require(first.pull() && first.push() && second.pull() && second.push() && reader.pull() && !reader.push(), "PERMISSIONS");
    }

    private static Actor actor(HttpClient http, String repository, String token) throws IOException {
        return new Actor(identity(repository, get(http, "user", token), get(http, "repos/" + repository, token)), token);
    }

    static Identity identity(String repository, JsonNode user, JsonNode repo) throws IOException {
        require(user.path("login").asText().matches("[A-Za-z0-9-]+") && user.path("id").isIntegralNumber()
                && user.path("id").canConvertToLong() && "User".equals(user.path("type").asText()), "ACCOUNT");
        require(repository.equalsIgnoreCase(repo.path("full_name").asText()) && repo.path("private").isBoolean()
                && repo.path("private").booleanValue() && repo.path("id").isIntegralNumber()
                && repo.path("id").canConvertToLong(), "PRIVATE-REPOSITORY");
        JsonNode permissions = repo.path("permissions");
        require(permissions.path("pull").isBoolean() && permissions.path("push").isBoolean(), "PERMISSIONS");
        return new Identity(user.path("id").longValue(), user.path("login").asText(), repo.path("id").longValue(),
                permissions.path("pull").booleanValue(), permissions.path("push").booleanValue());
    }

    static void isolateGitCredentials(Map<String, String> environment, String remote, String authorization) {
        environment.keySet().removeIf(key -> inheritedGitConfiguration(key.toUpperCase(Locale.ROOT)));

        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put("GIT_CONFIG_GLOBAL", Path.of(System.getProperty("java.io.tmpdir"), "empty-git-config").toString());
        environment.put("GIT_CONFIG_COUNT", "4");
        environment.put("GIT_CONFIG_KEY_0", "credential.helper");
        environment.put("GIT_CONFIG_VALUE_0", "");
        environment.put("GIT_CONFIG_KEY_1", "http.extraHeader");
        environment.put("GIT_CONFIG_VALUE_1", "");
        environment.put("GIT_CONFIG_KEY_2", "http." + remote + ".extraHeader");
        environment.put("GIT_CONFIG_VALUE_2", "Authorization: " + authorization);
        environment.put("GIT_CONFIG_KEY_3", "http.followRedirects");
        environment.put("GIT_CONFIG_VALUE_3", "false");
    }

    private static boolean inheritedGitConfiguration(String key) {
        return key.startsWith("GIT_CONFIG_") || key.startsWith("GIT_TRACE")
                || List.of("GIT_CURL_VERBOSE", "GIT_ASKPASS", "GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR",
                "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_INDEX_FILE",
                WRITER_A, WRITER_B, READER, "GH_TOKEN", "GITHUB_TOKEN").contains(key);
    }

    private static GitPromotionChannel channel(Path checkout, String remote, Actor actor) {
        return new GitPromotionChannel(checkout, remote, environment -> isolateGitCredentials(environment, remote, actor.authorization()));
    }

    private static int receivePackStatus(HttpClient http, String remote, Actor reader) throws IOException {
        var request = HttpRequest.newBuilder(URI.create(remote + "/info/refs?service=git-receive-pack"))
                .timeout(Duration.ofSeconds(35)).header("Authorization", reader.authorization()).GET().build();
        return send(http, request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static JsonNode get(HttpClient http, String route, String token) throws IOException {
        var request = HttpRequest.newBuilder(URI.create(API + route)).timeout(Duration.ofSeconds(35))
                .header("Authorization", "Bearer " + token).header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28").GET().build();
        var response = send(http, request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            require(response.statusCode() == 200, "GITHUB-HTTP-" + response.statusCode());
            byte[] bytes = body.readNBytes(1024 * 1024 + 1);
            require(bytes.length <= 1024 * 1024, "GITHUB-BODY");
            return JSON.readTree(bytes);
        }
    }

    private static <T> HttpResponse<T> send(HttpClient http, HttpRequest request, HttpResponse.BodyHandler<T> body) throws IOException {
        try { return http.send(request, body); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("PROMOTION-GITHUB-INTERRUPTED");
        } catch (IOException failure) { throw new IOException("PROMOTION-GITHUB-CONNECTION"); }
    }

    private static String token(String variable) throws IOException {
        String token = System.getenv(variable);
        require(token != null && token.matches("[A-Za-z0-9_]{20,512}"), "CREDENTIALS");
        return token;
    }

    private static String outcome(Attempt attempt) throws IOException {
        try { attempt.run(); return "accepted"; }
        catch (IOException failure) {
            String code = failure.getMessage();
            if (code != null && code.matches("PUBLICATION-[A-Z0-9-]+")) return code;
            throw new IOException("PROMOTION-GIT-CONNECTION");
        }
    }

    private static String permission(Identity identity) {
        return identity.push() ? "pull and push" : "pull only";
    }

    private static void compare(List<AtfAssertion> checks, String label, String expected, String actual) throws IOException {
        boolean passed = java.util.Objects.equals(expected, actual);
        checks.add(new AtfAssertion(label, expected == null ? "absent" : expected, actual == null ? "absent" : actual, passed));
        require(passed, "CHECK");
    }

    private static void writeExtent(Path html, List<AtfAssertion> checks, boolean passed) {
        var status = passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        try (var session = new ExtentReportSession(html, new ReportingSettings(240, ReportingSettings.DISABLE_BODY_DISPLAY_LIMIT));
             var scenario = session.startScenario("Rentz · GitHub channel permissions", "Authenticated Git on a private test repository",
                     List.of("release", "git"));
             var phase = scenario.startPhase("Channel identity and ref updates");
             var assertions = phase.startAssertionStep("Expected and observed outcomes")) {
            assertions.assertions(checks);
            assertions.complete(status);
            phase.complete(status);
            scenario.complete(status);
        }
    }

    private static void require(boolean condition, String code) throws IOException {
        if (!condition) throw new IOException("PROMOTION-" + code);
    }
}
