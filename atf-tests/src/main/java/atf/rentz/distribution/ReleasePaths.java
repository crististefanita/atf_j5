package atf.rentz.distribution;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.assertNoSymlinkPath;

/** Canonical relative release paths; no platform-dependent escaping or traversal. */
public final class ReleasePaths {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern VERSION = Pattern.compile("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");

    public static boolean isReleaseVersion(String value) {
        return VERSION.matcher(value).matches();
    }

    public static String relative(String value) throws IOException {
        require(value != null && !value.isBlank() && !value.contains("\\") && !value.matches(".*[\\p{Cntrl}:].*"), value);
        require(!value.startsWith("/") && !value.endsWith("/"), value);
        for (String part : value.split("/", -1)) require(!Set.of("", ".", "..").contains(part), value);
        return value;
    }
    private static void require(boolean valid, String value) throws IOException {
        if (!valid) throw new IOException("PATH: " + value);
    }
    public static void validateCorpusPath(String relative) throws IOException {
        String lower = relative.toLowerCase(Locale.ROOT);
        String extension = lower.substring(lower.lastIndexOf('.') + 1);
        if (!Set.of("md", "java", "ps1", "json").contains(extension)
                || lower.startsWith(".git/") || lower.contains("/.git/") || lower.startsWith(".gradle/")
                || lower.contains("/target/") || lower.contains("/build/") || lower.contains("/node_modules/")
                || lower.contains("/artifacts/") || lower.contains("/reports/")
                || lower.matches(".*(?:^|/)(?:\\.env[^/]*|local\\.properties|gradle\\.properties|[^/]*(?:secret|credential|token|keystore|signing)[^/]*)$"))
            throw new IOException("RAG-CORPUS-PATH-FORBIDDEN: " + relative);
    }

    public static JsonNode readMetadata(Path root, String relative, int maxBytes) throws IOException {
        String safe = ReleasePaths.relative(relative);
        validateCorpusPath(safe);
        Path file = root.resolve(safe).normalize();
        assertNoSymlinkPath(file);
        if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > maxBytes)
            throw new IOException("RAG-METADATA-BOUNDARY: " + safe);
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > maxBytes) throw new IOException("RAG-METADATA-LIMIT: " + safe);
        return JSON.readTree(bytes);
    }

    private ReleasePaths() { }
}
