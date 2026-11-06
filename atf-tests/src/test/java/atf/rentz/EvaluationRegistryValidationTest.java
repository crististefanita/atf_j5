package atf.rentz;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class EvaluationRegistryValidationTest {
    private static final String VALID = """
            {"schemaVersion":1,"domain":"rentz","generator":"jvm-shuffle-eight-cards-v1",
             "exposures":[{"players":4,"seed":717,"phase":"FRESH_EVALUATION","batch":"first",
             "sourceIdentity":"source-a","state":"EXPOSED_BEFORE_EXECUTION"}]}
            """;
    @TempDir Path directory;

    static Stream<String> invalidRegistries() {
        return Stream.of(
                VALID.stripTrailing().replaceFirst("}$", ",\"exposures\":[]}"),
                VALID.replace("\"seed\":717", "\"seed\":717,\"seed\":999"),
                VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":1.5"),
                VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":true"),
                VALID.replace("\"domain\":\"rentz\"", "\"domain\":null"),
                VALID.replace("\"batch\":\"first\"", "\"batch\":123"),
                VALID.replace("\"sourceIdentity\":\"source-a\"", "\"sourceIdentity\":{}"),
                VALID.replace("\"players\":4", "\"players\":\"4\""),
                VALID.replace("\"seed\":717", "\"seed\":9223372036854775808"),
                VALID.replace("\"seed\":717", "\"seed\":717.0"),
                VALID.replace("\"state\":", "\"unexpected\":1,\"state\":"),
                VALID.replace("\"domain\":", "\"unexpected\":1,\"domain\":"),
                VALID + " {}",
                VALID.replace("\"batch\":\"first\",", ""));
    }

    @ParameterizedTest(name = "invalid registry #{index}")
    @MethodSource("invalidRegistries")
    void invalidInputIsRefusedWithoutLosingExposureOrChangingBytes(String invalid) throws Exception {
        Path registry = directory.resolve("exposure.json");
        Files.writeString(registry, invalid);
        byte[] before = Files.readAllBytes(registry);
        Exception failure = assertThrows(Exception.class, () ->
                EvaluationRegistry.expose(registry, 4, List.of(718L), "FRESH_EVALUATION", "renamed", "source-b"));
        assertTrue(failure instanceof java.io.IOException || failure instanceof IllegalArgumentException);
        assertArrayEquals(before, Files.readAllBytes(registry), "Refusal must preserve the corrupt input for diagnosis");
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
        Files.writeString(registry, VALID);
        assertThrows(IllegalArgumentException.class, () ->
                EvaluationRegistry.expose(registry, 4, List.of(717L), "FRESH_EVALUATION", "renamed", "source-b"));
        assertEquals(VALID, Files.readString(registry), "The valid neighbor must still remember the original exposure");
    }

    @Test void validLongSeedsAndNewDistributionsRemainSupported() throws Exception {
        Path registry = directory.resolve("exposure.json");
        EvaluationRegistry.expose(registry, 4, List.of(Long.MIN_VALUE, Long.MAX_VALUE), "DEVELOPMENT", "dev", "source-a");
        EvaluationRegistry.expose(registry, 4, List.of(718L), "FRESH_EVALUATION", "fresh", "source-b");
        var entries = new ObjectMapper().readTree(registry.toFile()).required("exposures");
        assertEquals(3, entries.size());
        assertEquals(Long.MIN_VALUE, entries.get(0).required("seed").longValue());
        assertEquals(Long.MAX_VALUE, entries.get(1).required("seed").longValue());
        assertEquals("EXPOSED_BEFORE_EXECUTION", entries.get(2).required("state").textValue());
    }
}
