package atf.rentz;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
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
            try {
                Files.writeString(pendingFile(target), token, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException pending) {
                throw new IllegalStateException("An unresolved native exposure already exists", pending);
            }
        }
    }

    public static void completeNativeExposure(Path registry, String token, int players, long seed) throws IOException {
        requireNativeToken(token);
        expose(registry, players, List.of(seed), "DEVELOPMENT", "native-shared-ui-" + token,
                "android-native-laboratory-v1", false, token);
    }

    private static Path pendingFile(Path target) {
        return target.resolveSibling(target.getFileName() + ".native-pending");
    }

    private static void requireNativeToken(String token) {
        if (token == null || !token.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("Invalid native exposure token");
    }

    private static void expose(Path registry, int players, List<Long> seeds, String phase, String batch,
                               String sourceIdentity, boolean requireReview, String nativeToken) throws IOException {
        var batchSeeds = List.copyOf(seeds);
        if (players < 3 || players > 6 || batchSeeds.isEmpty() || !PHASES.contains(phase)
                || batch.isBlank() || sourceIdentity.isBlank()) throw new IllegalArgumentException("Exposure identity");
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
            if (nativeToken != null && (!Files.isRegularFile(pending, LinkOption.NOFOLLOW_LINKS)
                    || !nativeToken.equals(Files.readString(pending))))
                throw new IllegalArgumentException("Native exposure token differs from the pending marker");
            ObjectNode root = Files.exists(target) ? read(target) : emptyHistory();
            if (requireReview && !root.has("review"))
                throw new IllegalArgumentException("Fresh evaluation requires an initialized exposure registry");
            if (root.has("review") && !Path.of(text(root.required("review"), "registry")).equals(target))
                throw new IllegalArgumentException("Reviewed exposure registry is bound to another path");
            var exposed = (ArrayNode) root.required("exposures");
            for (var prior : exposed) {
                if (nativeToken != null && prior.path("batch").asText().equals(batch)) {
                    if (prior.path("players").intValue() != players || prior.path("seed").longValue() != batchSeeds.getFirst()
                            || !phase.equals(prior.path("phase").asText())
                            || !sourceIdentity.equals(prior.path("sourceIdentity").asText()))
                        throw new IllegalArgumentException("Native exposure token was recorded for another deal");
                    // A previous process may have stopped after the atomic registry write.
                    Files.delete(pending);
                    return;
                }
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
            if (nativeToken != null) Files.delete(pending);
        }
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
        Path temporary = Files.createTempFile(target.getParent(), "rentz-exposure-", ".tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), root);
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
