package atf.rentz.distribution.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.*;

/** Exact isolated-package preservation; archive ownership metadata is outside content identity. */
public final class AndroidSandboxEvidence {
    public static void verify(Path root, JsonNode ui) throws IOException {
        Path raw = externalRun(root, Path.of(ui.path("rawRoot").asText()));
        var claim = ui.path("sandboxPreservation");
        if (!claim.path("status").asText().equals("PASS") || !claim.path("leaseId").asText().matches("[a-f0-9]{32}"))
            throw new IOException("RAG-UI-SANDBOX");
        var json = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        byte[] identityBytes = readExternalFile(raw.resolve("sandbox-backup/identity.json"), 4 * 1024 * 1024);
        byte[] restoredBytes = readExternalFile(raw.resolve("sandbox-backup/restored.json"), 4 * 1024 * 1024);
        if (!sha256(identityBytes).equals(claim.path("identitySha256").asText())
                || !sha256(restoredBytes).equals(claim.path("restoredSha256").asText())) throw new IOException("RAG-UI-SANDBOX-HASH");
        var identity = json.readTree(identityBytes); var restored = json.readTree(restoredBytes);
        var lease = json.readTree(readExternalFile(raw.resolve("lease.json"), 64 * 1024));
        if (!identity.path("package").asText().equals("ro.rentzlab.app.uisanity") || !identity.path("present").isBoolean()
                || !restored.path("status").asText().equals("PASS") || !restored.path("originalPresent").equals(identity.path("present"))
                || !restored.path("files").isArray() || !lease.path("leaseId").equals(claim.path("leaseId")))
            throw new IOException("RAG-UI-SANDBOX-IDENTITY");
        if (identity.path("present").booleanValue()) {
            if (!identity.path("files").isArray() || !identity.path("files").equals(restored.path("files"))
                    || !sha256(readExternalFile(raw.resolve("sandbox-backup/original.apk"), 256L * 1024 * 1024)).equals(identity.path("apkSha256").asText())
                    || !sha256(readExternalFile(raw.resolve("sandbox-backup/original-data.tar"), 64L * 1024 * 1024)).equals(identity.path("dataSha256").asText()))
                throw new IOException("RAG-UI-SANDBOX-CONTENT");
        } else if (!restored.path("files").isEmpty()) throw new IOException("RAG-UI-SANDBOX-ABSENCE");
    }
    private AndroidSandboxEvidence() { }
}
