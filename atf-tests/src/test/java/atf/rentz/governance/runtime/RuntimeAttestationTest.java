package atf.rentz.governance.runtime;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class RuntimeAttestationTest {
    @TempDir Path root;

    static class ChildFixture {
        @Test void exercise() throws Exception {
            if (System.getProperty("runtime.fixture.mode").equals("mutate")) {
                Files.writeString(Path.of(System.getProperty("runtime.fixture.resource")), "changed during tests");
            }
        }
    }

    public static void main(String[] args) {
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(ChildFixture.class)).build(), listener);
        if (listener.getSummary().getTestsSucceededCount() != 1
                || listener.getSummary().getTestsFailedCount() != 0) throw new AssertionError("Child test did not pass");
    }

    @Test void realListenerAttestsUnchangedTestPlanBytes() throws Exception {
        assertEquals("PASS", child("stable"));
    }

    @Test void aPassingTestCannotAttestClasspathBytesChangedDuringItsExecution() throws Exception {
        assertEquals("FAIL", child("mutate"));
    }

    private String child(String mode) throws Exception {
        var entries = new ArrayList<String>();
        int directoryIndex = 0;
        Path probeResource = null;
        for (String value : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path origin = Path.of(value);
            if (Files.isDirectory(origin)) {
                Path target = Files.createDirectories(root.resolve("classpath-" + directoryIndex++));
                try (var walk = Files.walk(origin)) {
                    for (Path file : walk.filter(Files::isRegularFile).toList()) {
                        Path copy = target.resolve(origin.relativize(file));
                        Files.createDirectories(copy.getParent());
                        Files.copy(file, copy);
                    }
                }
                entries.add(target.toString());
                if (probeResource == null) probeResource = Files.writeString(target.resolve("runtime-probe.txt"), "original");
            } else entries.add(value);
        }
        assertNotNull(probeResource);
        Path inventory = root.resolve("inventory.json");
        Path receipt = root.resolve("attestation.json");
        var command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Xmx192m", "-XX:ActiveProcessorCount=2", "-Djava.io.tmpdir=" + root,
                "-Drentz.runtime.inventory=" + inventory,
                "-Drentz.runtime.attestation=" + receipt,
                "-Drentz.runtime.token=" + "e".repeat(32),
                "-Druntime.fixture.mode=" + mode,
                "-Druntime.fixture.resource=" + probeResource,
                "-cp", String.join(File.pathSeparator, entries), RuntimeAttestationTest.class.getName()));
        Path output = root.resolve("child.log");
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
                fail("Child launcher deadline exceeded");
            }
            assertEquals(0, process.exitValue(), () -> {
                try { return Files.readString(output); } catch (Exception error) { return error.toString(); }
            });
            assertTrue(Files.isRegularFile(inventory));
            assertTrue(Files.isRegularFile(receipt), "The actual service listener must write its receipt");
            return new ObjectMapper().readTree(receipt.toFile()).required("status").asText();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
