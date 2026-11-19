package atf.rentz.distribution;

import atf.rentz.RentzRulePublication;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.ComponentAuditExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static atf.testkit.assertions.AuditedAssertions.*;

/** A published GitHub link is bound to the repository, version and verified bytes. */
@AtfComponentTest
class ReleasePublicLinkTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            ReleasePublicLinkTest.class, "release-public-link.html", "Canonical public GitHub APK proof");
    private static final String REPO = "crististefanita/rentz";
    private static final String URL = "https://github.com/" + REPO
            + "/releases/download/v0.28.0/RentzLab-0.28.0.apk";
    private final ObjectMapper json = new ObjectMapper();
    private ObjectNode identity() {
        return json.createObjectNode().put("apkName", "RentzLab-0.28.0.apk")
                .put("apkBytes", 1234).put("sha256", "a".repeat(64));
    }
    private ObjectNode proof() {
        return json.createObjectNode().put("url", URL).put("verifiedUtc", "2026-11-19T12:00:00Z")
                .put("httpStatus", 200).put("bytes", 1234).put("sha256", "a".repeat(64));
    }
    @Test void acceptsOnlyTheVerifiedCanonicalAsset() throws Exception {
        assertEquals(URL, ReleaseCatalog.publicDownloadUrl(proof(), identity(), "0.28.0", "PUBLISHED", REPO));
        assertThrows(IOException.class, () -> ReleaseCatalog.publicDownloadUrl(
                json.missingNode(), identity(), "0.28.0", "PUBLISHED", REPO));
    }
    @ParameterizedTest
    @ValueSource(strings={"repository", "host", "version", "name", "suffix", "query", "fragment",
            "userinfo", "port", "status", "bytes", "digest", "time", "draft"})
    void refusesForeignOrUnverifiedAssets(String defect) {
        var value = proof();
        switch (defect) {
            case "repository" -> value.put("url", URL.replace("/rentz/", "/other/"));
            case "host" -> value.put("url", URL.replace("github.com", "example.invalid"));
            case "version" -> value.put("url", URL.replace("v0.28.0", "v0.27.0"));
            case "name" -> value.put("url", URL.replace("RentzLab-", "Other-"));
            case "suffix" -> value.put("url", URL.replace(".apk", "-ui-sanity.apk"));
            case "query" -> value.put("url", URL + "?download=1");
            case "fragment" -> value.put("url", URL + "#asset");
            case "userinfo" -> value.put("url", URL.replace("https://", "https://user@"));
            case "port" -> value.put("url", URL.replace("github.com", "github.com:443"));
            case "status" -> value.put("httpStatus", 404);
            case "bytes" -> value.put("bytes", 1235);
            case "digest" -> value.put("sha256", "b".repeat(64));
            case "time" -> value.put("verifiedUtc", "not-a-time");
            case "draft" -> { }
            default -> throw new IllegalArgumentException(defect);
        }
        assertThrows(IOException.class, () -> ReleaseCatalog.publicDownloadUrl(value, identity(),
                "0.28.0", defect.equals("draft") ? "DRAFT" : "PUBLISHED", REPO));
    }
}
