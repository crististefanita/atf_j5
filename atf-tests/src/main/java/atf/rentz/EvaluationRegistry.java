package atf.rentz;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import ro.rentzlab.core.deal.DealSeed;

/** Exposure belongs to a distribution namespace, independently of candidate or output. */
public final class EvaluationRegistry {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> V1_FIELDS = Set.of("schemaVersion", "domain", "generator", "exposures");
    private static final Set<String> V2_FIELDS = Set.of("schemaVersion", "domain", "generator", "exposures", "review");
    private static final Set<String> ENTRY_FIELDS = Set.of("players", "seed", "phase", "batch", "sourceIdentity", "state");
    private static final Set<String> PHASES = Set.of("DEVELOPMENT", "FRESH_EVALUATION");
    private static final String NATIVE_BATCH = "native-shared-ui-";
    private static final String NATIVE_SOURCE = "android-native-laboratory-v1";
    private record NativeCapture(String token, String sha256) {
        String marker() { return token + "\n" + sha256; }
        String source() { return NATIVE_SOURCE + ":sha256:" + sha256; }
    }

    private EvaluationRegistry() { }

    /** Locates the shared registry from the qualified workspace layout, independent of the checkout or protocol. */
    static Path installationRegistry(Path workspace, Path requested) {
        Path parent = workspace.getParent();
        Path infrastructure = parent == null ? null : parent.getParent();
        if (infrastructure == null || infrastructure.getParent() == null
                || !parent.endsWith("workspaces") || !infrastructure.endsWith("infrastructure")) {
            throw new IllegalArgumentException("Arena requires a qualified installation workspace");
        }
        Path registry = infrastructure.getParent().resolve("learning/exposure.json");
        if (!registry.equals(requested.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Arena must use the installation exposure registry");
        return registry;
    }

    /** Qualifies a reviewed v1 history once. In-place migration cannot omit or replace exposures. */
    static void initialize(Path registry, Path reviewedHistory, String reviewer, String reference) throws IOException {
        if (reviewer.isBlank() || reference.isBlank())
            throw new IllegalArgumentException("Registry reviewer and review reference required");
        Path target = registry.toAbsolutePath().normalize();
        checkPhysicalPath(target);
        Files.createDirectories(target.getParent());
        try (var channel = lockChannel(target); var lock = acquire(channel)) {
            boolean exists = Files.exists(target);
            ObjectNode current = exists ? read(target) : null;
            if (exists && current.has("review")) throw new IllegalArgumentException("Registry is already initialized");
            ObjectNode history = read(reviewedHistory);
            if (history.has("review"))
                throw new IllegalArgumentException("Initialization requires reviewed v1 history");
            if (exists) {
                var previous = current.required("exposures");
                var reviewed = history.required("exposures");
                for (int i = 0; i < previous.size(); i++) {
                    if (!previous.get(i).equals(reviewed.get(i)))
                        throw new IllegalArgumentException("Reviewed history must preserve all existing exposures in order");
                }
            }
            history.put("schemaVersion", 2).putObject("review")
                    .put("registry", target.toString()).put("reviewer", reviewer).put("reference", reference);
            write(target, history);
        }
    }

    public static void expose(Path registry, int players, List<Long> seeds, String phase, String batch, String sourceIdentity)
            throws IOException {
        expose(registry, players, seeds, phase, batch, sourceIdentity, false, null);
    }

    static void exposeForArena(Path registry, int players, List<Long> seeds, String phase, String batch, String sourceIdentity)
            throws IOException {
        expose(registry, players, seeds, phase, batch, sourceIdentity, phase.equals("FRESH_EVALUATION"), null);
    }

    public static void beginNativeExposure(Path registry, String token) throws IOException {
        requireNativeToken(token);
        Path target = registry.toAbsolutePath().normalize();
        checkPhysicalPath(target);
        Files.createDirectories(target.getParent());
        try (var channel = lockChannel(target); var lock = acquire(channel)) {
            Path pending = pendingFile(target);
            if (Files.exists(pending, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("An unresolved native exposure already exists");
            if (Files.exists(target) && recordedCapture(read(target).required("exposures"), token) != null)
                throw new IllegalArgumentException("Native exposure token was already committed");
            write(pending, token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public static void bindNativeExposure(Path registry, String token, String captureSha256) throws IOException {
        requireNativeToken(token);
        if (captureSha256 == null || !captureSha256.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("Invalid native capture SHA-256");
        var capture = new NativeCapture(token, captureSha256.toLowerCase(Locale.ROOT));
        Path target = registry.toAbsolutePath().normalize();
        checkPhysicalPath(target);
        try (var channel = lockChannel(target); var lock = acquire(channel)) {
            Path pending = pendingFile(target);
            String marker = readPending(pending);
            if (marker.equals(capture.marker())) return;
            if (!marker.equals(token))
                throw new IllegalArgumentException("Native exposure cannot be rebound to another capture or token");
            write(pending, capture.marker().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public static void completeNativeExposure(Path registry, String token, Path journal) throws IOException {
        requireNativeToken(token);
        byte[] bytes = Files.readAllBytes(journal);
        var saved = JSON.readTree(bytes);
        if (saved == null || !saved.isObject()) throw new IllegalArgumentException("Native journal object required");
        var players = saved.required("players");
        var seed = saved.required("seed");
        if (!players.isInt() || !seed.isIntegralNumber() || !seed.canConvertToLong())
            throw new IllegalArgumentException("Native journal exposure needs exact players and seed");
        var capture = new NativeCapture(token, sha256(bytes));
        expose(registry, players.intValue(), List.of(seed.longValue()), "DEVELOPMENT", NATIVE_BATCH + token,
                capture.source(), false, capture);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static String readPending(Path marker) throws IOException {
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return "";
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Invalid native exposure marker");
        return Files.readString(marker);
    }

    private static ObjectNode recordedCapture(JsonNode exposures, String token) {
        for (var prior : exposures)
            if (prior.path("batch").asText().equals(NATIVE_BATCH + token)) return (ObjectNode) prior;
        return null;
    }

    private static Path pendingFile(Path target) {
        return target.resolveSibling(target.getFileName() + ".native-pending");
    }

    private static void requireNativeToken(String token) {
        if (token == null || !token.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("Invalid native exposure token");
    }

    private static void expose(Path registry, int players, List<Long> seeds, String phase, String batch,
                               String sourceIdentity, boolean requireReview, NativeCapture capture) throws IOException {
        var batchSeeds = List.copyOf(seeds);
        if (players < 3 || players > 6 || batchSeeds.isEmpty() || !PHASES.contains(phase)
                || batch.isBlank() || sourceIdentity.isBlank() || (capture == null && batch.startsWith(NATIVE_BATCH)))
            throw new IllegalArgumentException("Exposure identity");
        var requested = DealSeed.requireDistinct(batchSeeds);
        Path target = registry.toAbsolutePath().normalize();
        if (requireReview && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Fresh evaluation requires an initialized exposure registry");
        checkPhysicalPath(target);
        Files.createDirectories(target.getParent());
        try (var channel = lockChannel(target); var lock = acquire(channel)) {
            Path pending = pendingFile(target);
            if (requireReview && Files.exists(pending, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Fresh evaluation blocked by unresolved native exposure");
            ObjectNode root = Files.exists(target) ? read(target) : emptyHistory();
            if (requireReview && !root.has("review"))
                throw new IllegalArgumentException("Fresh evaluation requires an initialized exposure registry");
            if (root.has("review") && !Path.of(text(root.required("review"), "registry")).equals(target))
                throw new IllegalArgumentException("Reviewed exposure registry is bound to another path");
            var exposed = (ArrayNode) root.required("exposures");
            if (capture != null && resumeCapture(target, pending, root, capture, players, batchSeeds.getFirst()))
                return;
            for (var prior : exposed) {
                if (prior.path("players").intValue() != players) continue;
                long previousSeed = prior.path("seed").longValue();
                Long seed = requested.get(DealSeed.effective(previousSeed));
                if (seed != null && (seed != previousSeed || phase.equals("FRESH_EVALUATION")
                        || prior.path("phase").asText().equals("FRESH_EVALUATION"))) {
                    throw new IllegalArgumentException("Distribution already exposed: " + previousSeed
                            + " (requested seed " + seed + ")");
                }
            }
            for (long seed : batchSeeds) {
                exposed.addObject().put("players", players).put("seed", seed).put("phase", phase).put("batch", batch)
                        .put("sourceIdentity", sourceIdentity).put("state", "EXPOSED_BEFORE_EXECUTION");
            }
            write(target, root);
            if (capture != null) Files.delete(pending);
        }
    }

    /** Verifies the reservation or reconciles an already committed capture under the registry lock. */
    private static boolean resumeCapture(Path target, Path pending, ObjectNode root, NativeCapture capture,
                                         int players, long seed) throws IOException {
        String marker = readPending(pending);
        boolean matches = marker.equals(capture.marker());
        var prior = recordedCapture(root.required("exposures"), capture.token());
        if (prior == null) {
            if (!matches) throw new IllegalArgumentException("Native journal differs from the bound capture");
            return false;
        }
        if (prior.path("players").intValue() != players || prior.path("seed").longValue() != seed
                || !prior.path("phase").asText().equals("DEVELOPMENT"))
            throw new IllegalArgumentException("Native exposure token was recorded for another deal");
        String original = prior.path("sourceIdentity").asText();
        if (matches && original.equals(NATIVE_SOURCE)) {
            // A legacy write can be reconciled only while its verified capture is still reserved.
            prior.put("sourceIdentity", capture.source());
            write(target, root);
        } else if (!original.equals(capture.source())) {
            throw new IllegalArgumentException("Native journal differs from the committed capture");
        }
        if (matches) Files.delete(pending);
        else if (marker.equals(capture.token()) || marker.startsWith(capture.token() + "\n"))
            throw new IllegalArgumentException("Native journal differs from the pending capture");
        return true;
    }

    private static ObjectNode emptyHistory() {
        var root = JSON.createObjectNode().put("schemaVersion", 1).put("domain", "rentz").put("generator", DealSeed.GENERATOR);
        root.putArray("exposures");
        return root;
    }

    private static ObjectNode read(Path file) throws IOException {
        var root = JSON.readTree(file.toFile());
        if (root == null || !root.path("schemaVersion").isInt())
            throw new IllegalArgumentException("Invalid exposure registry; do not reset it");
        int version = root.path("schemaVersion").intValue();
        if (version != 1 && version != 2) throw new IllegalArgumentException("Unsupported exposure registry version");
        requireFields(root, version == 1 ? V1_FIELDS : V2_FIELDS);
        if (!text(root, "domain").equals("rentz") || !text(root, "generator").equals(DealSeed.GENERATOR)
                || !root.path("exposures").isArray())
            throw new IllegalArgumentException("Invalid exposure registry; do not reset it");
        if (version == 2) {
            var review = root.required("review");
            requireFields(review, Set.of("registry", "reviewer", "reference"));
            for (String field : List.of("registry", "reviewer", "reference")) {
                if (text(review, field).isBlank()) throw new IllegalArgumentException("Registry review is incomplete");
            }
        }
        for (var prior : root.path("exposures")) {
            requireFields(prior, ENTRY_FIELDS);
            var players = prior.path("players");
            var seed = prior.path("seed");
            if (!players.isInt() || players.intValue() < 3 || players.intValue() > 6
                    || !seed.isIntegralNumber() || !seed.canConvertToLong() || !PHASES.contains(text(prior, "phase"))
                    || text(prior, "batch").isBlank() || text(prior, "sourceIdentity").isBlank()
                    || !text(prior, "state").equals("EXPOSED_BEFORE_EXECUTION"))
                throw new IllegalArgumentException("Corrupt exposure entry; preserve registry");
        }
        return (ObjectNode) root;
    }

    private static void checkPhysicalPath(Path target) throws IOException {
        for (Path path = target; path != null; path = path.getParent()) {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !path.toRealPath().equals(path))
                throw new IllegalArgumentException("Registry path crosses a filesystem alias");
        }
    }

    private static FileChannel lockChannel(Path target) throws IOException {
        return FileChannel.open(target.resolveSibling(target.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    private static FileLock acquire(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock != null) return lock;
        } catch (OverlappingFileLockException error) {
            throw new IllegalStateException("Registry is busy", error);
        }
        throw new IllegalStateException("Registry is busy");
    }

    private static void write(Path target, ObjectNode root) throws IOException {
        write(target, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
    }

    private static void write(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "rentz-exposure-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void requireFields(JsonNode node, Set<String> expected) {
        if (!node.isObject() || node.size() != expected.size())
            throw new IllegalArgumentException("Invalid registry shape; preserve registry");
        for (String field : expected) {
            if (!node.has(field)) throw new IllegalArgumentException("Missing registry field: " + field);
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isTextual()) throw new IllegalArgumentException("Registry text required: " + field);
        return value.textValue();
    }
}
