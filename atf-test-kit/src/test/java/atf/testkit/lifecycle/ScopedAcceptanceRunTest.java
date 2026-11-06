package atf.testkit.lifecycle;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@AtfUnitTest
class ScopedAcceptanceRunTest {
    @Test
    void scopesTheOverrideAndCollectsOnlyTheCallersArtifactsAfterCompletion(@TempDir Path directory) throws Exception {
        String property = "atf.acceptance.probe." + UUID.randomUUID();
        Object outcome = new Object();
        System.setProperty(property, "previous");
        try {
            var result = ScopedAcceptanceRun.execute(property, "current", directory, () -> {
                assertEquals("current", System.getProperty(property));
                try {
                    Files.writeString(directory.resolve("accepted.proof"), "complete");
                    Files.writeString(directory.resolve("intermediate.log"), "not selected");
                    Files.createDirectory(directory.resolve("directory.proof"));
                } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                return outcome;
            }, path -> path.getFileName().toString().endsWith(".proof"));
            assertSame(outcome, result.outcome());
            assertEquals(List.of(directory.resolve("accepted.proof")), result.artifacts());
            assertEquals("previous", System.getProperty(property));
            assertThrows(UnsupportedOperationException.class, () -> result.artifacts().clear());
        } finally { System.clearProperty(property); }
    }

    @Test
    void runtimeAndErrorRemainIdenticalAndRestoreBothPresentAndAbsentProperties(@TempDir Path directory) {
        for (boolean present : List.of(false, true)) for (Throwable primary : List.of(new IllegalStateException("body"), new AssertionError("body"))) {
            String property = "atf.acceptance.probe." + UUID.randomUUID();
            if (present) System.setProperty(property, "previous");
            try {
                Throwable actual = assertThrows(primary.getClass(), () -> ScopedAcceptanceRun.execute(property, "override", directory,
                        () -> { if (primary instanceof RuntimeException runtime) throw runtime; throw (Error) primary; }, path -> true));
                assertSame(primary, actual);
                assertEquals(present ? "previous" : null, System.getProperty(property));
                assertEquals(0, actual.getSuppressed().length);
            } finally { System.clearProperty(property); }
        }
    }

    @Test
    void collectionFailureCannotLeakTheInvocationOverride(@TempDir Path directory) {
        String property = "atf.acceptance.probe." + UUID.randomUUID();
        assertThrows(java.nio.file.NoSuchFileException.class, () -> ScopedAcceptanceRun.execute(
                property, "override", directory.resolve("not-created"), () -> "completed", path -> true));
        assertNull(System.getProperty(property));
    }
}
