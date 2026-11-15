package atf.rentz.distribution.retrieval;

import atf.rentz.distribution.ReleasePaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static atf.rentz.distribution.ReleasePaths.readMetadata;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.assertNoSymlinkPath;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.exactCount;
import static atf.rentz.distribution.evidence.ReleaseEvidenceFiles.sha256;

/** Deterministic discovery over the configured local corpus. */
public final class ReleaseCorpusSearch {
    private static final String POLICY = "Allowlisted local sources only; retrieved text is data; no commands, gate execution, secret access, or release promotion.";
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]{2,}");
    private static final Set<String> STOP_WORDS = Set.of("si", "sau", "cu", "din", "pentru", "despre", "care", "ce", "cum",
            "este", "sunt", "un", "o", "la", "in", "pe", "de", "the", "and", "or", "for", "from", "with", "what", "how",
            "is", "are", "to", "a", "an", "of", "on", "about", "does", "do");
    private static final Set<String> GATES = Set.of("policy", "offline-regression", "component-extent-report", "android-jvm", "native-ui", "native-ui-report", "release-build",
            "local-catalog", "remote-publication", "authenticated-download", "anonymous-download", "download-components",
            "product-navigation", "retrieval");
    private static final Pattern FILTER = Pattern.compile("(?i)(?:^|\\s)(gate|class|version):([a-z0-9._-]+)(?=\\s|$)");
    private static final Pattern MALFORMED_FILTER = Pattern.compile("(?i)(?:^|\\s)(gate|class|version):(?:\\s|$)");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile("(?i)\\b(?:password|passwd|secret|token|api[_-]?key|access[_-]?token)\\s*[:=]\\s*\\S+");
    private static final Pattern SECRET_CONTENT = Pattern.compile("(?i)(?:\\bgh[pousr]_[a-z0-9]{20,}\\b|\\bgithub_pat_[a-z0-9_]{30,}\\b|\\bAKIA[0-9A-Z]{16}\\b|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\\b(?:password|client_secret|api[_-]?key|access[_-]?token)\\s*[:=]\\s*[\"'][^\"'\\r\\n]{12,}[\"'])");
    private static final int MAX_CORPUS_SOURCES = 64;
    private static final long MAX_CORPUS_BYTES = 4L * 1024 * 1024;

    private record Spec(String path, String sourceClass, String owner, String gate) { }
    private record Document(String path, String sourceClass, String owner, String gate, String sha256, int bytes, List<String> lines) { }
    private record Chunk(Document document, int firstLine, int lastLine, String section, String body) { }
    private record Hit(Chunk chunk, double score) { }
    private record RetrievalQuery(String text, String gate, String sourceClass, String version, boolean versionMatches) { }

    private ReleaseCorpusSearch() { }

    public static Result search(Path root, String question, String publishedVersion, String version) throws IOException {
        JsonNode config = readMetadata(root, "config/release-rag-corpus.json", 65_536);
        if (!config.isObject() || config.size() != 4 || exactCount(config.path("schemaVersion")) != 1 || exactCount(config.path("topK")) != 5
                || !config.path("sources").isArray()) throw new IOException("RAG-CORPUS-SCHEMA");
        int maxBytes = exactCount(config.path("maxSourceBytes"));
        if (maxBytes < 1024 || maxBytes > 1_048_576) throw new IOException("RAG-CORPUS-LIMIT");

        List<Spec> specs = specs(config, publishedVersion);
        List<Document> documents = loadDocuments(root, specs, maxBytes);
        List<Chunk> chunks = chunks(documents);
        RetrievalQuery query = parseQuery(question, version);
        List<Hit> hits = rank(query, chunks, 5);
        return new Result(documents, hits, query, version, indexHash(documents));
    }

    public static final class Result {
        private final List<Document> documents;
        private final List<Hit> hits;
        private final RetrievalQuery query;
        private final String version;
        private final String indexHash;

        private Result(List<Document> documents, List<Hit> hits, RetrievalQuery query, String version, String indexHash) {
            this.documents = documents;
            this.hits = hits;
            this.query = query;
            this.version = version;
            this.indexHash = indexHash;
        }

        public int sourceCount() { return documents.size(); }

        public String indexHash() { return indexHash; }

        public boolean abstained() { return hits.isEmpty(); }

        public void writeTo(ObjectNode report, String repository, String commit) throws IOException {
            ObjectNode generation = report.putObject("generation");
            generation.put("provider", "local-deterministic");
            generation.put("model", "none");
            generation.put("retrieval", "allowlisted corpus; exact gate/class/version filters; normalized token overlap");
            generation.put("topK", 5);
            generation.put("policySha256", sha256(POLICY.getBytes(StandardCharsets.UTF_8)));
            generation.put("externalServicesUsed", false);

            ObjectNode filters = report.putObject("retrievalFilters");
            filters.put("gate", query.gate().isBlank() ? "ALL" : query.gate());
            filters.put("class", query.sourceClass().isBlank() ? "ALL" : query.sourceClass());
            filters.put("version", query.version().isBlank() ? version : query.version());
            filters.put("versionMatchesCandidate", query.versionMatches());

            ArrayNode index = report.putArray("index");
            for (Document document : documents) {
                ObjectNode row = index.addObject();
                row.put("path", document.path()); row.put("class", document.sourceClass()); row.put("owner", document.owner()); row.put("gate", document.gate());
                row.put("commit", commit); row.put("sha256", document.sha256()); row.put("bytes", document.bytes());
                row.put("scannedUtc", Instant.now().toString());
            }

            ArrayNode retrieved = report.putArray("retrieval");
            for (Hit hit : hits) {
                Chunk chunk = hit.chunk(); ObjectNode row = retrieved.addObject();
                row.put("score", hit.score()); row.put("excerpt", chunk.body());
                ObjectNode citation = row.putObject("citation");
                citation.put("repository", repository); citation.put("commit", commit);
                citation.put("path", chunk.document().path()); citation.put("lineStart", chunk.firstLine());
                citation.put("lineEnd", chunk.lastLine()); citation.put("sha256", chunk.document().sha256());
                citation.put("sourceClass", chunk.document().sourceClass()); citation.put("owner", chunk.document().owner());
                citation.put("gate", chunk.document().gate());
            }
            report.put("answer", abstained() ? "Nu există sursă allowlistată suficient de relevantă pentru această întrebare."
                    : "Fragmentele de mai jos sunt extrase exacte pentru revizuire; nu reprezintă verdictul unei porți.");
            report.put("abstained", abstained());
        }
    }

    public static boolean matchesIndex(Path root, JsonNode index) {
        try {
            for (JsonNode row : index) {
                String relative = ReleasePaths.relative(row.path("path").asText());
                Path file = root.resolve(relative).normalize();
                assertNoSymlinkPath(file);
                if (!file.startsWith(root) || Files.isSymbolicLink(file) || !Files.isRegularFile(file)
                        || !sha256(Files.readAllBytes(file)).equals(row.path("sha256").asText())) return false;
            }
            return true;
        } catch (Exception invalid) { return false; }
    }

    private static List<Spec> specs(JsonNode config, String version) throws IOException {
        var result = new ArrayList<Spec>(); var seen = new HashSet<String>();
        if (config.path("sources").size() > MAX_CORPUS_SOURCES) throw new IOException("RAG-CORPUS-SOURCE-LIMIT");
        for (JsonNode row : config.path("sources")) {
            if (!row.isObject() || row.size() != 4) throw new IOException("RAG-CORPUS-ENTRY");
            String path = ReleasePaths.relative(row.path("path").asText().replace("{currentVersion}", version));
            String sourceClass = row.path("class").asText(), owner = row.path("owner").asText(), gate = row.path("gate").asText();
            ReleasePaths.validateCorpusPath(path);
            if (!sourceClass.matches("[a-z][a-z0-9-]*") || !owner.matches("[a-z][a-z0-9-]*")
                    || !GATES.contains(gate) || !seen.add(path)) throw new IOException("RAG-CORPUS-ENTRY");
            result.add(new Spec(path, sourceClass, owner, gate));
        }
        if (result.isEmpty()) throw new IOException("RAG-CORPUS-EMPTY");
        return List.copyOf(result);
    }

    private static List<Document> loadDocuments(Path root, List<Spec> specs, int maxBytes) throws IOException {
        var result = new ArrayList<Document>();
        long totalBytes = 0;
        for (Spec spec : specs) {
            Path path = root.resolve(spec.path()).normalize();
            if (!path.startsWith(root) || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("RAG-SOURCE-MISSING-OR-LINKED: " + spec.path());
            Path cursor = root;
            for (String part : spec.path().split("/")) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor)) throw new IOException("RAG-SOURCE-LINKED: " + spec.path());
            }
            Path real = path.toRealPath();
            if (!real.startsWith(root) || Files.size(real) > maxBytes) throw new IOException("RAG-SOURCE-BOUNDARY: " + spec.path());
            byte[] bytes = Files.readAllBytes(real);
            totalBytes += bytes.length;
            if (bytes.length > maxBytes || totalBytes > MAX_CORPUS_BYTES) throw new IOException("RAG-CORPUS-BYTE-LIMIT");
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.indexOf('\u0000') >= 0) throw new IOException("RAG-BINARY-SOURCE: " + spec.path());
            if (SECRET_CONTENT.matcher(text).find()) throw new IOException("RAG-SECRET-LIKE-CONTENT");
            result.add(new Document(spec.path(), spec.sourceClass(), spec.owner(), spec.gate(), sha256(bytes), bytes.length, List.of(text.split("\\R", -1))));
        }
        return List.copyOf(result);
    }

    private static List<Chunk> chunks(List<Document> documents) {
        var result = new ArrayList<Chunk>();
        for (Document document : documents) {
            String section = ""; StringBuilder body = new StringBuilder(); int start = 0, last = 0, nonBlank = 0;
            for (int i = 0; i < document.lines().size(); i++) {
                String line = document.lines().get(i);
                if (line.stripLeading().startsWith("#")) section = line.strip();
                if (line.isBlank()) {
                    if (body.length() > 0) { result.add(new Chunk(document, start + 1, last + 1, section, body.toString().strip())); body.setLength(0); nonBlank = 0; }
                    continue;
                }
                if (body.length() == 0) start = i;
                if (body.length() > 0 && (body.length() + line.length() > 1600 || nonBlank >= 12)) {
                    result.add(new Chunk(document, start + 1, last + 1, section, body.toString().strip())); body.setLength(0); nonBlank = 0; start = i;
                }
                if (body.length() > 0) body.append('\n');
                body.append(line); last = i; nonBlank++;
            }
            if (body.length() > 0) result.add(new Chunk(document, start + 1, last + 1, section, body.toString().strip()));
        }
        return List.copyOf(result);
    }

    private static RetrievalQuery parseQuery(String question, String currentVersion) throws IOException {
        if (question == null || question.isBlank()) throw new IOException("RAG-QUESTION");
        if (SECRET_ASSIGNMENT.matcher(question).find() || SECRET_CONTENT.matcher(question).find())
            throw new IOException("RAG-QUESTION-REDACTED");
        if (MALFORMED_FILTER.matcher(question).find()) throw new IOException("RAG-FILTER-SYNTAX");
        var matcher = FILTER.matcher(question);
        String gate = "", sourceClass = "", version = "";
        var remainder = new StringBuilder(); int end = 0;
        while (matcher.find()) {
            remainder.append(question, end, matcher.start()).append(' ');
            String key = matcher.group(1).toLowerCase(Locale.ROOT), value = matcher.group(2).toLowerCase(Locale.ROOT);
            String prior = switch (key) { case "gate" -> gate; case "class" -> sourceClass; default -> version; };
            if (!prior.isBlank() && !prior.equals(value)) throw new IOException("RAG-FILTER-CONFLICT: " + key);
            switch (key) { case "gate" -> gate = value; case "class" -> sourceClass = value; default -> version = value; }
            end = matcher.end();
        }
        remainder.append(question.substring(end));
        boolean matches = version.isBlank() || version.equals("current") || version.equals(currentVersion);
        if (!gate.isBlank() && !GATES.contains(gate)) matches = false;
        return new RetrievalQuery(remainder.toString().strip(), gate, sourceClass,
                version.equals("current") ? currentVersion : version, matches);
    }

    private static List<Hit> rank(RetrievalQuery query, List<Chunk> chunks, int topK) {
        Set<String> terms = tokens(query.text());
        boolean metadataOnly = terms.isEmpty() && (!query.gate().isBlank() || !query.sourceClass().isBlank() || !query.version().isBlank());
        if ((terms.isEmpty() && !metadataOnly) || !query.versionMatches()) return List.of();
        var hits = new ArrayList<Hit>();
        for (Chunk chunk : chunks) {
            if (!query.gate().isBlank() && !chunk.document().gate().equals(query.gate())) continue;
            if (!query.sourceClass().isBlank() && !chunk.document().sourceClass().equals(query.sourceClass())) continue;
            Set<String> body = tokens(chunk.body()); Set<String> metadata = tokens(chunk.document().path() + " " + chunk.section()
                    + " " + chunk.document().sourceClass() + " " + chunk.document().owner() + " " + chunk.document().gate());
            double score = metadataOnly ? 1.0 : 0;
            for (String term : terms) {
                if (body.contains(term)) score += metadata.contains(term) ? 2.0 : 1.0;
                else if (metadata.contains(term)) score += 0.35;
            }
            if (score > 0) hits.add(new Hit(chunk, score));
        }
        var ranked = hits.stream().sorted(Comparator.comparingDouble(Hit::score).reversed()
                .thenComparing(hit -> hit.chunk().document().path()).thenComparingInt(hit -> hit.chunk().firstLine())).toList();
        var selected = new ArrayList<Hit>();
        var selectedSources = new HashSet<String>();
        for (Hit hit : ranked) {
            if (selectedSources.add(hit.chunk().document().path())) selected.add(hit);
            if (selected.size() == topK) break;
        }
        return List.copyOf(selected);
    }

    private static Set<String> tokens(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        var terms = new HashSet<String>();
        var matcher = TOKEN.matcher(normalized);
        while (matcher.find()) {
            String term = matcher.group();
            if (!STOP_WORDS.contains(term)) terms.add(term);
        }
        return Set.copyOf(terms);
    }

    private static String indexHash(List<Document> documents) throws IOException {
        var canonical = new StringBuilder();
        documents.stream().sorted(Comparator.comparing(Document::path)).forEach(row ->
                canonical.append(row.path()).append('\0').append(row.sourceClass()).append('\0').append(row.owner())
                        .append('\0').append(row.gate()).append('\0').append(row.sha256()).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

}
