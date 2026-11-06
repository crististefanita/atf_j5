package atf.rentz.governance.release;

import atf.testkit.AtfComponentTest;
import atf.rentz.distribution.ApkDownloadTarget;
import atf.rentz.distribution.ApkPayload;
import atf.rentz.distribution.GitHubApkDownload;
import atf.rentz.RentzRulePublication;
import atf.testkit.assertions.ComponentAuditExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static atf.testkit.assertions.AuditedAssertions.*;

/** Real loopback HTTP; tiny ZIP witnesses qualify transport/integrity, not Android installability. */
@AtfComponentTest
@Timeout(30)
class ReleaseDownloadComponentTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT=RentzRulePublication.scoped(ReleaseDownloadComponentTest.class,
            "download-components.html","Release download contracts; no Android execution implied");
    @TempDir Path temporary;
    @ParameterizedTest @ValueSource(strings={"../outside","/absolute","C:/drive","nested\\path","nested//empty","nested/../outside"})
    void archivePathsCannotEscapeTheDeclaredPayload(String path) throws Exception {
        var failure=assertThrows(IOException.class,()->atf.rentz.distribution.ReleasePaths.relative(path));
        assertTrue(failure.getMessage().startsWith("PATH:"),"Canonical-path guard rejected the actual unsafe entry");
    }
    @Test void completeResponseHasTheDeclaredIdentityAndBecomesTheFinalApkOnlyAfterValidation() throws Exception {
        try (var fixture = new Fixture("healthy", false)) {
            var proof = fixture.client().download(fixture.expected, temporary);
            assertEquals(fixture.expected.bytes(), proof.bytes(), "Actual completed response size");
            assertEquals(fixture.expected.sha256(), proof.sha256(), "Actual downloaded bytes match the independent digest");
            assertEquals("PASS", proof.zipCrc(), "Every ZIP entry was read and checked");
            assertEquals("1.2.0", proof.version(), "Actual release version");
            assertEquals(200, proof.httpStatus(), "Actual binary HTTP status");
            assertEquals(fixture.expected.sha256(), ApkPayload.hash(proof.file()), "Final file remains complete");
        }
    }
    @Test void assetRedirectCannotForwardTheApiCredentialToAnotherOrigin() throws Exception {
        try (var fixture = new Fixture("healthy", true)) {
            var proof = fixture.client().download(fixture.expected, temporary);
            assertEquals(fixture.expected.bytes(), proof.bytes(), "Redirected bytes remain complete");
            assertEquals("<absent>", fixture.edgeAuthorization.get(), "The API credential is absent at the asset origin");
            assertEquals("Bearer SYNTHETIC-CREDENTIAL", fixture.apiAuthorization.get(), "Only the API origin receives its credential");
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"404", "error-text", "forged-error-metadata", "short", "oversize", "same-size-change", "bad-crc", "wrong-tag", "draft", "duplicate", "fractional-size"})
    void incompleteDeceptiveOrMisidentifiedResponsesCannotBecomeAnApk(String defect) throws Exception {
        try (var fixture = new Fixture(defect, false)) {
            var failure = assertThrows(IOException.class, () -> fixture.client().download(fixture.expected, temporary),
                    "Rejected HTTP/integrity defect: " + defect);
            String actual = failure.getMessage().split(":", 2)[0];
            assertEquals(expectedFailure(defect), actual, "Specific complete-download rejection: " + defect);
            assertFalse(Files.exists(temporary.resolve(fixture.expected.fileName())), "No invalid final .apk is published");
        }
    }
    private static String expectedFailure(String defect) {
        return switch (defect) {
            case "404" -> "Release download HTTP 404";
            case "error-text", "short" -> "APK response is incomplete";
            case "forged-error-metadata" -> "APK-ZIP";
            case "oversize" -> "APK response exceeds the declared size";
            case "same-size-change" -> "APK-HASH";
            case "bad-crc" -> "APK-CRC";
            case "wrong-tag", "draft" -> "Release identity or publication state differs";
            case "duplicate" -> "Expected exactly one APK asset";
            case "fractional-size" -> "Asset size differs from declared APK";
            default -> throw new IllegalArgumentException(defect);
        };
    }
    private static final class Fixture implements AutoCloseable {
        final HttpServer api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final HttpServer edge = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final AtomicReference<String> edgeAuthorization = new AtomicReference<>("not observed");
        final AtomicReference<String> apiAuthorization = new AtomicReference<>("not observed");
        final ApkDownloadTarget expected;
        Fixture(String defect, boolean redirect) throws Exception {
            byte[] original = archive(), payload = original.clone();
            if (defect.equals("same-size-change") || defect.equals("bad-crc")) {
                int offset = new String(payload, StandardCharsets.ISO_8859_1).indexOf("DEX-WITNESS");
                if (offset < 0) throw new IllegalStateException("Missing independent ZIP payload witness");payload[offset] ^= 1;
            }
            if (defect.equals("error-text") || defect.equals("forged-error-metadata")) payload = "404: Not Found".getBytes(StandardCharsets.UTF_8);
            if (defect.equals("short")) payload = java.util.Arrays.copyOf(original, original.length - 1);
            if (defect.equals("oversize")) payload = java.util.Arrays.copyOf(original, original.length + 1);
            byte[] identity = defect.equals("forged-error-metadata") || defect.equals("bad-crc") ? payload : original;
            expected = new ApkDownloadTarget("example/game", "1.2.0", identity.length, digest(identity));
            var json = new ObjectMapper();var metadata = json.createObjectNode().put("tag_name", "v1.2.0").put("draft", false);
            ObjectNode asset = metadata.putArray("assets").addObject().put("id", 7).put("name", expected.fileName())
                    .put("size", expected.bytes()).put("digest", "sha256:" + expected.sha256()).put("state", "uploaded")
                    .put("browser_download_url", expected.publicUrl());
            if (defect.equals("wrong-tag")) metadata.put("tag_name", "v1.1.0");
            if (defect.equals("draft")) metadata.put("draft", true);
            if (defect.equals("duplicate")) ((com.fasterxml.jackson.databind.node.ArrayNode) metadata.path("assets")).add(asset.deepCopy());
            if (defect.equals("fractional-size")) asset.put("size", expected.bytes() + 0.5);
            byte[] body = payload;
            api.createContext("/repos/example/game/releases/tags/v1.2.0", exchange -> {
                apiAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] bytes = defect.equals("404") ? "404: Not Found".getBytes(StandardCharsets.UTF_8) : json.writeValueAsBytes(metadata);
                exchange.sendResponseHeaders(defect.equals("404") ? 404 : 200, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            edge.createContext("/payload", exchange -> {
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                edgeAuthorization.set(authorization == null ? "<absent>" : authorization);
                exchange.sendResponseHeaders(200, body.length);try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            api.createContext("/repos/example/game/releases/assets/7", exchange -> {
                if (redirect) {
                    exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + edge.getAddress().getPort() + "/payload");
                    exchange.sendResponseHeaders(302, -1);exchange.close();
                } else { exchange.sendResponseHeaders(200, body.length);try (var output = exchange.getResponseBody()) { output.write(body); } }
            });
            api.start();edge.start();
        }
        GitHubApkDownload client() { return new GitHubApkDownload(URI.create("http://127.0.0.1:" + api.getAddress().getPort() + "/"), "SYNTHETIC-CREDENTIAL"); }
        @Override public void close() { api.stop(0);edge.stop(0); }
    }
    private static byte[] archive() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : java.util.List.of("AndroidManifest.xml", "classes.dex")) {
                byte[] content = (name.equals("classes.dex") ? "DEX-WITNESS" : "manifest witness").getBytes(StandardCharsets.UTF_8);
                var crc = new CRC32();crc.update(content);var entry = new ZipEntry(name);entry.setTime(0);
                entry.setMethod(ZipEntry.STORED);entry.setSize(content.length);entry.setCompressedSize(content.length);entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);zip.write(content);zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    private static String digest(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
