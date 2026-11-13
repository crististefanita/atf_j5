package atf.rentz.distribution.evidence;

import atf.rentz.distribution.ReleasePaths;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.readExternalFile;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256;

/** Binds both Android build types to the offline-qualified source manifest. */
public final class AndroidSourceEvidence {
    private AndroidSourceEvidence() { }

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public static void verify(Path run, Path componentRun, JsonNode receipt) throws IOException {
        Path sourceManifest = receipt.path("sourceManifest").isTextual() ? Path.of(receipt.path("sourceManifest").asText()) : run.resolve("source-identity.json");
        if (!sourceManifest.toRealPath().startsWith(run) || !sha256(readExternalFile(sourceManifest, 4L * 1024 * 1024))
                .equalsIgnoreCase(receipt.path("sourceManifestSha256").asText())) throw new IOException("RAG-BUILD-MANIFEST");
        JsonNode androidSourceRows = JSON.readTree(readExternalFile(sourceManifest, 4L * 1024 * 1024));
        JsonNode qualifiedSourceRows = JSON.readTree(readExternalFile(componentRun.toRealPath().resolve("source-identity.json"), 4L * 1024 * 1024));
        if (!androidSourceRows.isArray() || androidSourceRows.isEmpty() || !qualifiedSourceRows.isArray())
            throw new IOException("RAG-BUILD-MANIFEST-SCHEMA");
        Set<String> seenAndroidPaths = new HashSet<>();
        int qualifiedAndroidFiles = 0;
        for (JsonNode row : qualifiedSourceRows) if (row.path("path").asText().startsWith("rentz-lab-app/")) qualifiedAndroidFiles++;
        if (androidSourceRows.size() != qualifiedAndroidFiles) throw new IOException("RAG-BUILD-MANIFEST-POPULATION");
        for (JsonNode row : androidSourceRows) {
            String path = ReleasePaths.relative(row.path("path").asText());
            if (!seenAndroidPaths.add(path) || !row.path("sha256").asText().matches("(?i)[a-f0-9]{64}"))
                throw new IOException("RAG-BUILD-MANIFEST-ROW");
            String expected = "";
            for (JsonNode sourceRow : qualifiedSourceRows) if (sourceRow.path("path").asText().equals("rentz-lab-app/" + path)) {
                expected = sourceRow.path("sha256").asText(); break;
            }
            if (expected.isBlank() || !expected.equalsIgnoreCase(row.path("sha256").asText()))
                throw new IOException("RAG-BUILD-MANIFEST-DRIFT");
        }
    }

}
