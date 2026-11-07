package atf.rentz;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class EvaluationRegistryValidationTest {
    private static final long ALIAS_PERIOD = 1L << 48;
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

    @ParameterizedTest
    @ValueSource(strings = {"DEVELOPMENT", "FRESH_EVALUATION"})
    void aliasesInOneBatchAreRefusedBeforeCreatingARegistry(String phase) {
        Path registry = directory.resolve("unused/exposure.json");
        assertThrows(IllegalArgumentException.class, () -> EvaluationRegistry.expose(
                registry, 4, List.of(717L, 717L + ALIAS_PERIOD), phase, "aliased", "source-a"));
        assertFalse(Files.exists(registry.getParent()));
    }

    @ParameterizedTest
    @CsvSource({
            "DEVELOPMENT, FRESH_EVALUATION",
            "FRESH_EVALUATION, DEVELOPMENT",
            "FRESH_EVALUATION, FRESH_EVALUATION",
            "DEVELOPMENT, DEVELOPMENT"
    })
    void historicalSeedAliasesAreRefusedWithoutChangingTheOriginalBytes(String previous, String phase)
            throws Exception {
        Path registry = directory.resolve("exposure.json");
        // Use the previous on-disk schema, with the alias as the original label.
        String history = VALID.replace("FRESH_EVALUATION", previous)
                .replace("\"seed\":717", "\"seed\":" + (717L + ALIAS_PERIOD));
        Files.writeString(registry, history);
        byte[] before = Files.readAllBytes(registry);
        assertThrows(IllegalArgumentException.class, () -> EvaluationRegistry.expose(
                registry, 4, List.of(718L, 717L), phase, "renamed", "source-b"));
        assertArrayEquals(before, Files.readAllBytes(registry), "Refuse the entire batch and preserve old labels");

        EvaluationRegistry.expose(registry, 4, List.of(718L), phase, "distinct", "source-b");
        EvaluationRegistry.expose(registry, 3, List.of(717L), phase, "other-table", "source-c");
        var exposures = new ObjectMapper().readTree(registry.toFile()).required("exposures");
        assertEquals(3, exposures.size());
        assertEquals(717L + ALIAS_PERIOD, exposures.get(0).required("seed").longValue());
    }

    @Test void exactDevelopmentReplayRemainsAvailable() throws Exception {
        Path registry = directory.resolve("exposure.json");
        Files.writeString(registry, VALID.replace("FRESH_EVALUATION", "DEVELOPMENT"));
        EvaluationRegistry.expose(registry, 4, List.of(717L), "DEVELOPMENT", "repeat", "source-b");
        var exposures = new ObjectMapper().readTree(registry.toFile()).required("exposures");
        assertEquals(2, exposures.size());
        assertEquals("repeat", exposures.get(1).required("batch").textValue());
    }

    @Test void legacyHistoryWithTwoAliasLabelsRetainsBothAndCanAdmitADistinctState() throws Exception {
        Path registry = directory.resolve("exposure.json");
        var json = new ObjectMapper();
        var history = json.readTree(VALID.replace("FRESH_EVALUATION", "DEVELOPMENT"));
        var exposures = (ArrayNode) history.required("exposures");
        var first = (ObjectNode) exposures.get(0);
        exposures.add(first.deepCopy().put("seed", 717L + ALIAS_PERIOD).put("batch", "second"));
        json.writeValue(registry.toFile(), history);
        byte[] before = Files.readAllBytes(registry);

        assertThrows(IllegalArgumentException.class, () -> EvaluationRegistry.expose(
                registry, 4, List.of(717L + 2 * ALIAS_PERIOD), "FRESH_EVALUATION", "alias", "source-b"));
        assertArrayEquals(before, Files.readAllBytes(registry));
        EvaluationRegistry.expose(registry, 4, List.of(718L), "FRESH_EVALUATION", "distinct", "source-b");
        var recorded = json.readTree(registry.toFile()).required("exposures");
        assertEquals(exposures.get(0), recorded.get(0));
        assertEquals(exposures.get(1), recorded.get(1));
        assertEquals(3, recorded.size());
    }
}
