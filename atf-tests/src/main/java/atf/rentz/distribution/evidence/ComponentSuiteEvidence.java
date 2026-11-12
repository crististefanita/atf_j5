package atf.rentz.distribution.evidence;

import atf.rentz.distribution.ReleaseReadinessRag;
import atf.rentz.reporting.ComponentSuiteExtentReport;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Independently reconciles the component selection, raw suites and canonical Extent. */
public final class ComponentSuiteEvidence {
    private static final String INPUT = "component-suite-input.json";
    private static final String JVM_DIRECTORY = "android-jvm";
    private static final Set<String> MAVEN_MODULES = Set.of("atf-tests", "rentz-lab-app/game-core");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private record SuiteKey(String module, String name) { }
    private record Totals(int tests, int failures, int errors, int skipped) { }

    private ComponentSuiteEvidence() { }

    public static void verify(Path run, Path extent, JsonNode result) throws IOException {
        byte[] inputBytes = ReleaseReadinessRag.readExternalFile(run.resolve(INPUT), 5L * 1024 * 1024);
        if (!ReleaseReadinessRag.sha256(inputBytes).equals(result.path("componentSuiteInputSha256").asText()))
            throw new IOException("RAG-COMPONENT-SUITE-RECEIPT-HASH");
        JsonNode input = JSON.readTree(inputBytes);
        if (!input.isObject() || input.size() != 7 || !input.path("schemaVersion").isIntegralNumber()
                || input.path("schemaVersion").intValue() != 1 || !input.path("suites").isArray()
                || !input.path("executionFailure").isTextual()
                || !input.path("sourceCommit").asText().equals(result.path("sourceCommit").asText())
                || !input.path("sourceManifestSha256").asText().equals(result.path("sourceManifestSha256").asText())
                || !input.path("collection").asText().equals(result.path("collection").path("name").asText()))
            throw new IOException("RAG-COMPONENT-SUITE-SOURCE");

        Map<SuiteKey, Totals> expected = expectedSuites(run, result);
        var reports = new ArrayList<ComponentSuiteExtentReport.SuiteInput>();
        for (JsonNode row : input.path("suites")) {
            if (!row.isObject() || row.size() != 8) throw new IOException("RAG-COMPONENT-SUITE-RECEIPT");
            SuiteKey key = key(row.path("module").asText(), row.path("name").asText());
            Totals counts = totals(row, "failures");
            if (!counts.equals(expected.remove(key))) throw new IOException("RAG-COMPONENT-SUITE-POPULATION: " + key);
            Path xml = run.resolve(key.module().replace('/', '-')).resolve("TEST-" + key.name() + ".xml");
            if (!row.path("xml").isTextual() || !Path.of(row.path("xml").asText()).toAbsolutePath().normalize().equals(xml)
                    || !xml.equals(xml.toRealPath()))
                throw new IOException("RAG-COMPONENT-SUITE-XML-PATH: " + key);
            reports.add(new ComponentSuiteExtentReport.SuiteInput(key.module(), key.name(), xml,
                    row.path("sha256").asText(), counts.tests(), counts.failures(), counts.errors(), counts.skipped()));
        }
        if (!expected.isEmpty() || reports.isEmpty()) throw new IOException("RAG-COMPONENT-SUITE-POPULATION");
        requireExactXmlPopulation(run, reports);
        boolean qualified;
        try {
            qualified = ComponentSuiteExtentReport.verify(extent, input.path("sourceCommit").asText(),
                    input.path("sourceManifestSha256").asText(), input.path("collection").asText(),
                    count(input.path("processExitCode")), input.path("executionFailure").asText(), reports);
        } catch (IOException invalidExtent) {
            throw new IOException("RAG-COMPONENT-SUITE-EXTENT: " + invalidExtent.getMessage(), invalidExtent);
        }
        if (qualified != result.path("componentExtentQualified").asBoolean())
            throw new IOException("RAG-COMPONENT-SUITE-VERDICT");
    }

    private static void requireExactXmlPopulation(Path run, List<ComponentSuiteExtentReport.SuiteInput> suites)
            throws IOException {
        var expected = new HashSet<Path>();
        for (var suite : suites) expected.add(suite.xmlFile());
        for (String relative : Set.of("atf-tests", "rentz-lab-app-game-core", JVM_DIRECTORY)) {
            Path directory = run.resolve(relative);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || !directory.equals(directory.toRealPath()))
                throw new IOException("RAG-COMPONENT-SUITE-XML-PATH: " + relative);
            try (var entries = Files.newDirectoryStream(directory, "TEST-*.xml")) {
                for (Path file : entries)
                    if (!expected.remove(file)) throw new IOException("RAG-COMPONENT-SUITE-XML-POPULATION: " + file.getFileName());
            }
        }
        if (!expected.isEmpty()) throw new IOException("RAG-COMPONENT-SUITE-XML-POPULATION");
    }
    private static Map<SuiteKey, Totals> expectedSuites(Path run, JsonNode result) throws IOException {
        var expected = new LinkedHashMap<SuiteKey, Totals>();
        for (JsonNode row : result.path("results")) {
            String module = row.path("module").asText();
            if (!MAVEN_MODULES.contains(module)) throw new IOException("RAG-COMPONENT-SUITE-MODULE");
            add(expected, key(module, row.path("name").asText()), totals(row, row.has("failures") ? "failures" : "failed"));
        }
        String jvm = result.path("androidJvm").asText();
        if (!jvm.isBlank()) {
            Path directory = run.resolve(JVM_DIRECTORY);
            if (!Path.of(jvm).toAbsolutePath().normalize().equals(directory)) throw new IOException("RAG-COMPONENT-SUITE-JVM-PATH");
            JsonNode mobile = JSON.readTree(ReleaseReadinessRag.readExternalFile(directory.resolve("result.json"), 1_048_576));
            if (!mobile.path("tests").isArray()) throw new IOException("RAG-COMPONENT-SUITE-JVM-RECEIPT");
            for (JsonNode row : mobile.path("tests"))
                add(expected, key(JVM_DIRECTORY, row.path("name").asText()), totals(row, "failures"));
        }
        return expected;
    }

    private static SuiteKey key(String module, String name) throws IOException {
        if ((!MAVEN_MODULES.contains(module) && !JVM_DIRECTORY.equals(module))
                || !name.matches("[\\p{L}\\p{N}_$]+(?:[.][\\p{L}\\p{N}_$]+)*"))
            throw new IOException("RAG-COMPONENT-SUITE-IDENTITY");
        return new SuiteKey(module, name);
    }

    private static void add(Map<SuiteKey, Totals> suites, SuiteKey key, Totals totals) throws IOException {
        if (suites.putIfAbsent(key, totals) != null) throw new IOException("RAG-COMPONENT-SUITE-DUPLICATE: " + key);
    }

    private static Totals totals(JsonNode row, String failures) throws IOException {
        return new Totals(count(row.path("tests")), count(row.path(failures)), count(row.path("errors")), count(row.path("skipped")));
    }

    private static int count(JsonNode value) throws IOException {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0)
            throw new IOException("RAG-RECEIPT-COUNT");
        return value.intValue();
    }
}
