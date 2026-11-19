package atf.rentz.android;

import atf.rentz.RentzRulePublication;
import atf.rentz.distribution.evidence.AndroidSandboxEvidence;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.ComponentAuditExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static atf.testkit.assertions.AuditedAssertions.*;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256;

@AtfComponentTest
class AndroidSandboxEvidenceTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            AndroidSandboxEvidenceTest.class, "android-sandbox-evidence.html", "RAG rejects unbound preservation; no device");
    @TempDir Path folder;
    @ParameterizedTest @ValueSource(strings={"lease", "file-bytes", "package", "hash", "missing", "absence"})
    void nativeGateRequiresPreservationFromTheSameReservationAndExactOriginalBytes(String defect) throws Exception {
        var json = new ObjectMapper(); Path root = Files.createDirectory(folder.resolve("source"));
        Path raw = Files.createDirectory(folder.resolve("raw")), backup = Files.createDirectory(raw.resolve("sandbox-backup"));
        String owner = "a".repeat(32);
        Files.writeString(raw.resolve("lease.json"), "{\"leaseId\":\"" + owner + "\"}");
        Files.writeString(backup.resolve("original.apk"), "original APK");
        Files.writeString(backup.resolve("original-data.tar"), "original archive");
        var identity = json.createObjectNode().put("package","ro.rentzlab.app.uisanity").put("present",true)
                .put("apkSha256",sha256(Files.readAllBytes(backup.resolve("original.apk"))))
                .put("dataSha256",sha256(Files.readAllBytes(backup.resolve("original-data.tar"))));
        identity.putArray("files").addObject().put("path","files/session.json").put("bytes",8).put("sha256","b".repeat(64));
        var restored = json.createObjectNode().put("status","PASS").put("originalPresent",true);
        restored.set("files",identity.path("files").deepCopy());
        var ui = json.createObjectNode().put("rawRoot",raw.toString());
        var claim = ui.putObject("sandboxPreservation").put("status","PASS").put("leaseId",owner);
        save(json,backup,identity,restored,claim);
        assertDoesNotThrow(() -> AndroidSandboxEvidence.verify(root,ui), "Healthy preservation before mutation: " + defect);
        switch(defect) {
            case "lease" -> claim.put("leaseId","f".repeat(32));
            case "file-bytes" -> Files.writeString(backup.resolve("original.apk"),"foreign APK");
            case "package" -> { identity.put("package","ro.rentzlab.app"); save(json,backup,identity,restored,claim); }
            case "hash" -> claim.put("identitySha256","f".repeat(64));
            case "missing" -> Files.delete(backup.resolve("restored.json"));
            case "absence" -> { identity.put("present",false); restored.put("originalPresent",false); save(json,backup,identity,restored,claim); }
            default -> throw new AssertionError(defect);
        }
        assertThrows(IOException.class, () -> AndroidSandboxEvidence.verify(root,ui), "No native PASS with altered recovery: " + defect);
    }
    private static void save(ObjectMapper json,Path backup,ObjectNode identity,ObjectNode restored,ObjectNode claim) throws IOException {
        json.writeValue(backup.resolve("identity.json").toFile(),identity); json.writeValue(backup.resolve("restored.json").toFile(),restored);
        claim.put("identitySha256",sha256(Files.readAllBytes(backup.resolve("identity.json"))))
                .put("restoredSha256",sha256(Files.readAllBytes(backup.resolve("restored.json"))));
    }
}
