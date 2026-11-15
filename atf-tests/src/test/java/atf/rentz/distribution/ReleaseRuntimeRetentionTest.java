package atf.rentz.distribution;

import atf.rentz.RentzRulePublication;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.AuditedAssertions;
import atf.testkit.assertions.ComponentAuditExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
class ReleaseRuntimeRetentionTest {
    @RegisterExtension static final ComponentAuditExtension AUDIT = RentzRulePublication.scoped(
            ReleaseRuntimeRetentionTest.class, "release-runtime-retention.html",
            "Real PowerShell inventory and reversible retention on synthetic paths, with isolated evidence checks");
    @TempDir Path workspace;

    @Test void actualRetentionScriptPreservesDependenciesAndRollsBackFailedProof() throws Exception {
        Path source = ReleaseCatalog.root();
        Path run = workspace.resolve("retention-controls");
        Path log = workspace.resolve("retention-controls.log");
        var child = new ProcessBuilder("pwsh", "-NoProfile", "-File",
                source.resolve("atf-tests/tools/test-runtime-retention.ps1").toString(),
                "-RunRoot", run.toString()).directory(source.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        try {
            AuditedAssertions.assertTrue(child.waitFor(90, TimeUnit.SECONDS), "The isolated retention probes finish");
            var observations = new ObjectMapper().readTree(run.resolve("checks.json").toFile());
            AuditedAssertions.assertEquals(9, observations.size(), "All retention cases execute");
            for (var check : observations) {
                AuditedAssertions.assertEquals(check.required("expected").asText(), check.required("actual").asText(),
                        check.required("name").asText());
            }
            AuditedAssertions.assertEquals(0, child.exitValue(), "PowerShell retention result");
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
    }
}
