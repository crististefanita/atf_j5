package atf.testkit.files;

import atf.testkit.AtfUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfUnitTest
class AtfTestResourceReaderTest {
    @TempDir
    Path temp;

    @Test
    void readsUtf8AndKeepsResolutionInsideTheConfiguredRoot() throws Exception {
        Path file = temp.resolve("fixture.yaml");
        java.nio.file.Files.writeString(file, "value: test\n");

        assertEquals("value: test\n", AtfTestResourceReader.readUtf8(file));
        assertEquals(temp.resolve("fixture.yaml").toAbsolutePath().normalize(),
                AtfTestResourceReader.resolveContained(temp, Path.of("fixture.yaml")));
        assertThrows(IllegalArgumentException.class,
                () -> AtfTestResourceReader.resolveContained(temp, Path.of("..", "outside")));
    }
}
