package atf.rentz.android;

import atf.rentz.RentzRulePublication;
import atf.rentz.distribution.ReleaseCatalog;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.ComponentAuditExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static atf.testkit.assertions.AuditedAssertions.*;

@AtfComponentTest
class AndroidReservationComponentTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            AndroidReservationComponentTest.class, "android-reservation.html", "Injected Android transport; no emulator or adb.exe");
    @TempDir static Path output;
    static JsonNode results;
    @BeforeAll static void executeRealOrchestratorWithAnInjectedDevice() throws Exception {
        Path root = ReleaseCatalog.root();
        var process = new ProcessBuilder("pwsh", "-NoLogo", "-NoProfile", "-File",
                root.resolve("atf-tests/src/test/resources/android-reservation.ps1").toString(),
                "-Project", root.toString(), "-Output", output.toString())
                .redirectErrorStream(true).redirectOutput(output.resolve("process.log").toFile()).start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Fixture deadline"); }
        if (process.exitValue() != 0) throw new IllegalStateException(java.nio.file.Files.readString(output.resolve("process.log")));
        results = new ObjectMapper().readTree(output.resolve("results.json").toFile());
    }
    @ParameterizedTest @ValueSource(strings={"one-owner-across-stages","foreign-phase-has-no-write",
            "existing-package-restored","absent-package-stays-absent","unsafe-tar-before-uninstall",
            "corrupt-backup-keeps-lease","foreign-lease-before-restore","foreign-installed-apk-preserved",
            "wrong-restored-file-refused","changed-archive-metadata-preserves-content","known-test-apk-is-removed-before-restore",
            "lost-uninstall-response-is-recoverable","phase-cannot-move-backwards"})
    void preservationAndOwnershipContract(String name) {
        var matches = new java.util.ArrayList<JsonNode>(); results.forEach(row -> { if (row.path("name").asText().equals(name)) matches.add(row); });
        assertEquals(1, matches.size(), "Exactly one executed scenario: " + name);
        assertEquals("PASS", matches.getFirst().path("status").asText(), name + " · " + matches.getFirst().path("actual").asText());
    }
}
